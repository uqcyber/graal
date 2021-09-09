package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

public class RTException extends RuntimeType {
    private final Exception exception;

    public RTException(Exception e) {
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
