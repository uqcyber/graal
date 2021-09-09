package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

public class RTCharacter extends RuntimeType {
    private final char character;

    public RTCharacter(Character ch) {
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
