package org.graalvm.compiler.debug.interpreter.value.type;

import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;

public class RuntimeValueBoolean extends RuntimeValue {
    private static final RuntimeValueBoolean TRUE_INSTANCE = new RuntimeValueBoolean(true);
    private static final RuntimeValueBoolean FALSE_INSTANCE = new RuntimeValueBoolean(false);

    private final Boolean value;

    private RuntimeValueBoolean(boolean value) {
        this.value = value;
    }

    public static RuntimeValueBoolean of(boolean value) {
        return value ? TRUE_INSTANCE : FALSE_INSTANCE;
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
