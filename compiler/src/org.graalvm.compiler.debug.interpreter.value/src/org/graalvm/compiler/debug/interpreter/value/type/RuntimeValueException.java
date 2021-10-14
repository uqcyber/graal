package org.graalvm.compiler.debug.interpreter.value.type;

import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;

public class RuntimeValueException extends RuntimeValue {
    private final Exception exception;

    public RuntimeValueException(Exception e) {
        this.exception = e;
    }

    @Override
    public Object toObject() {
        return exception;
    }

    @Override
    public Class<?> getClazz() {
        return exception.getClass();
    }

    @Override
    public Boolean getBoolean() {
        return null;
    }
}
