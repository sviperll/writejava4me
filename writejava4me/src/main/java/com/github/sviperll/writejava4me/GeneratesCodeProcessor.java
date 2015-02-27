/*
 * Copyright (c) 2014, Victor Nazarov <asviraspossible@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation and/or
 *     other materials provided with the distribution.
 *
 *  3. Neither the name of the copyright holder nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.sviperll.writejava4me;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class GeneratesCodeProcessor extends AbstractProcessor {
    private final Map<TypeElement, GeneratesClass[]> annotations = new HashMap<TypeElement, GeneratesClass[]>();
    private final List<String> errors = new ArrayList<String>();

    @Override
    public boolean process(Set<? extends TypeElement> processEnnotations,
                           RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            for (String error: errors) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error);
            }
        } else {
            for (Element element: roundEnv.getElementsAnnotatedWith(GeneratesCode.class)) {
                TypeElement annotationClassElement = (TypeElement)element;
                GeneratesClass[] generatesDirectives = element.getAnnotation(GeneratesCode.class).value();
                annotations.put(annotationClassElement, generatesDirectives);
            }
            for (Element element: roundEnv.getElementsAnnotatedWith(GeneratesClass.class)) {
                TypeElement annotationClassElement = (TypeElement)element;
                GeneratesClass[] generatesDirectives = new GeneratesClass[] {element.getAnnotation(GeneratesClass.class)};
                annotations.put(annotationClassElement, generatesDirectives);
            }
            Generator generator = new Generator(processingEnv, new DefaultMustacheFactory());
            for (Entry<TypeElement, GeneratesClass[]> entry: annotations.entrySet()) {
                TypeElement annotationClassElement = entry.getKey();
                for (Element element: roundEnv.getElementsAnnotatedWith(annotationClassElement)) {
                    for (GeneratesClass directive: entry.getValue()) {
                        List<? extends AnnotationMirror> appliedAnnotations = element.getAnnotationMirrors();
                        for (AnnotationMirror appliedAnnotation: appliedAnnotations) {
                            if (processingEnv.getTypeUtils().isAssignable(annotationClassElement.asType(), appliedAnnotation.getAnnotationType()))
                                generator.generateClass((TypeElement)element, appliedAnnotation, directive);
                        }
                    }
                }
            }
        }
        return true;
    }


    private static class Generator {
        private final ProcessingEnvironment processingEnv;
        private final MustacheFactory mustacheFactory;
        public Generator(ProcessingEnvironment processingEnv, MustacheFactory mustacheFactory) {
            this.processingEnv = processingEnv;
            this.mustacheFactory = mustacheFactory;
        }

        private void generateClass(TypeElement element, AnnotationMirror annotation, GeneratesClass directive) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating " + directive.classNameTemplateString() + " class form " + directive.classTemplateResourcePath() + " template with context " + annotation + " and declaring class " + element.getQualifiedName().toString());
            HashMap<String, Object> thisScope = new HashMap<String, Object>();
            thisScope.put("annotated", element.getSimpleName().toString());
            PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(element);
            String packageName = packageElement.getQualifiedName().toString();
            thisScope.put("package", packageName);

            HashMap<String, Object> annotationScope = toScope(annotation);
            annotationScope.put("this", thisScope);
            Object[] scope = new Object[] {annotationScope, thisScope};
            System.out.println("annotationScope = " + annotationScope);
            System.out.println("thisScope = " + thisScope);

            Mustache classNameMustache = mustacheFactory.compile(new StringReader(directive.classNameTemplateString()), "classNameTemplateString");
            StringWriter stringWriter = new StringWriter();
            classNameMustache.execute(stringWriter, scope);
            stringWriter.flush();
            String className = stringWriter.toString();
            thisScope.put("class", className);

            Mustache mustache = mustacheFactory.compile(directive.classTemplateResourcePath());
            try {
                JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + "." + className, element);
                OutputStream stream = sourceFile.openOutputStream();
                try {
                    Writer writer = new OutputStreamWriter(stream, Charset.defaultCharset());
                    try {
                        mustache.execute(writer, scope);
                    } finally {
                        writer.close();
                    }
                } finally {
                    stream.close();
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        private HashMap<String, Object> toScope(AnnotationMirror annotation) {
            HashMap<String, Object> scope = new HashMap<String, Object>();
            for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry: annotation.getElementValues().entrySet()) {
                scope.put(entry.getKey().getSimpleName().toString(), toScopeValue(entry.getValue()));
            }
            return scope;
        }

        private Object toScopeValue(Object value) {
            if (value instanceof TypeMirror) {
                return toTypeName((TypeMirror)value);
            } else if (value instanceof VariableElement) {
                return toEnumConstantName((VariableElement)value);
            } else if (value instanceof AnnotationMirror) {
                return toScope((AnnotationMirror)value);
            } else if (value instanceof List) {
                List<Object> result = new ArrayList<Object>();
                @SuppressWarnings("unchecked")
                List<? extends AnnotationValue> annotationValues = (List<? extends AnnotationValue>)value;
                for (AnnotationValue annotationValue: annotationValues) {
                    result.add(toScopeValue(annotationValue));
                }
                return result;
            } else if (value instanceof AnnotationValue) {
                AnnotationValue annotationValue = (AnnotationValue)value;
                return toScopeValue(annotationValue.getValue());
            } else {
                System.out.println(value.getClass().getName() + ": " + value);
                return value;
            }
        }
        
        private String toTypeName(TypeMirror type) {
            if (type instanceof ArrayType) {
                ArrayType arrayType = (ArrayType)type;
                return toTypeName(arrayType.getComponentType()) + "[]";
            } else if (type instanceof DeclaredType) {
                DeclaredType declaredType = (DeclaredType)type;
                TypeElement typeDeclaration = (TypeElement)declaredType.asElement();
                return typeDeclaration.getQualifiedName().toString();
            } else {
                throw new UnsupportedOperationException("Not supported");
            }
        }

        private String toEnumConstantName(VariableElement enumConstant) {
            return enumConstant.getSimpleName().toString();
        }
    }
}
