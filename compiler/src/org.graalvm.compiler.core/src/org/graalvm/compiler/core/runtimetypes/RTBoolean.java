package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

public class RTBoolean extends RuntimeType {
    Boolean value;

    public RTBoolean(Boolean value) {
        super();
        this.value = value;
    }

    @Override
    public Boolean getBoolean() {
        return value;
    }

    @Override
    public String toString() {
        return super.toString() + " with Value (" + value + ")";
    }

    @Override
    public Class<?> getClazz() {
        return Boolean.TYPE;
    }

    public Object toObject() {
        return this.value;
    }
}