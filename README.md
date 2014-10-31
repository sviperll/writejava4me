writejava4me Easy code generation for Java
==========================================

Works as annotation for annotation.
User defines new annotation and annotate it with @GeneratesClass annotation.
@GeneratesClass defines wich class will be generated.

Example
-------

Imagine that we would like to define both checked exception and RuntimeException when checked exception can't be used.

We can write it like this:

    public class MyException extends Exception {
        private final MyData valuableData;
        public MyException(String message, MyData valuableData) {
            super(message);
            this.valuableData = valuableData;
        }
        public MyData valuableData() {
            return valuableData;
        }
    }

    public class RuntimeMyException extends RuntimeException {
        private final MyException cause;
        public RuntimeMyException(MyException cause) {
            super(cause);
            this.cause = cause;
        }
        @Override
        public MyException getCause() {
            return cause;
        }
    }

If we are going to define many exceptions pairs like this we may want to generate Runtime wrappers automatically.
It will be good to have annotation for this.

    @GenerateRuntimeExceptionWrapper
    public class MyException extends Exception {
        private final MyData valuableData;
        public MyException(String message, MyData valuableData) {
            super(message);
            this.valuableData = valuableData;
        }
        public MyData valuableData() {
            return valuableData;
        }
    }

With writejava4me it's easy to define such annotations. Here is an implementation for @GenerateRuntimeExceptionWrapper

GenerateRuntimeExceptionWrapper.java:

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @Documented
    @GeneratesClass(classNameTemplateString = "Runtime{{annotated}}", classTemplateResourcePath = "RuntimeExceptionWrapper.mustache")
    @interface GenerateRuntimeExceptionWrapper {
    }

RuntimeExceptionWrapper.mustache located in resources folder:

    package {{package}};

    public class {{class}} extends RuntimeException {
        private final {{annotated}} cause;

        public {{class}}({{annotated}} cause) {
            super(cause);
            this.cause = cause;
        }

        @Override
        public {{annotated}} getCause() {
            return cause;
        }
    }

See [examples project](https://github.com/sviperll/writejava4me/tree/master/writejava4me-examples) for more examples

License
-------

writejava4me is under BSD 3-clause license.

Flattr
------

[![Flattr this git repo](http://api.flattr.com/button/flattr-badge-large.png)](https://flattr.com/submit/auto?user_id=sviperll&url=https%3A%2F%2Fgithub.com%2Fsviperll%2Fwritejava4me&title=writejava4me&language=Java&tags=github&category=software)

Installation
------------

Use maven dependency:

    <dependency>
        <groupId>com.github.sviperll</groupId>
        <artifactId>writejava4me</artifactId>
        <version>0.1</version>
    </dependency>
