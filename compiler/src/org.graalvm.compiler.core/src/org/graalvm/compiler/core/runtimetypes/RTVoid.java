package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

// Represents an empty / null value (e.g. what is 'returned' from a function with an empty return)
public class RTVoid extends RuntimeType {
    public RTVoid() {
    }

    @Override
    public Boolean getBoolean() {
        throw new UnsupportedOperationException("Void has no associated boolean");
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public Object toObject() {
        return null;
    }

    @Override
    public Class<?> getClazz() {
        return null;
    }
}
