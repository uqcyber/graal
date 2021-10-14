package org.graalvm.compiler.debug.interpreter.value.type;

import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;

public class RuntimeValueCharacter extends RuntimeValue {
    private final char character;

    public RuntimeValueCharacter(Character ch) {
        character = ch;
    }

    @Override
    public Object toObject() {
        return character;
    }

    @Override
    public Class<?> getClazz() {
        return Character.class;
    }

    @Override
    public Boolean getBoolean() {
        return false;
    }
}
