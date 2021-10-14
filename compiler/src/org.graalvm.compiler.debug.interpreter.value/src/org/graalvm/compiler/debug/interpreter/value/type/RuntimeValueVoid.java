package org.graalvm.compiler.debug.interpreter.value.type;


import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;

// Represents an empty / null value (e.g. what is 'returned' from a function with an empty return)
public class RuntimeValueVoid extends RuntimeValue {
    public static final RuntimeValueVoid INSTANCE = new RuntimeValueVoid();

    private RuntimeValueVoid() {
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
