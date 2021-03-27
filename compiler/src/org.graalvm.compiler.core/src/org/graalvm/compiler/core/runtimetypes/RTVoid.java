package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

// Represents an empty / null value (e.g. what is 'returned' from a function with an empty return)
public class RTVoid extends RuntimeType{
    public RTVoid() {
    }

    @Override
    public RTInteger add(RuntimeType y_value) {
        throw new UnsupportedOperationException("Attempted to add to void");
    }

    @Override
    public Boolean getBoolean() {
        throw new UnsupportedOperationException("Void has no associated boolean");
    }

    @Override
    public RTBoolean lessThan(RuntimeType b) {
        throw new UnsupportedOperationException("Attempted to compare void");
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
