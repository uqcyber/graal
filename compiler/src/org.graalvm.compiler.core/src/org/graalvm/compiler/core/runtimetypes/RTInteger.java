package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

// todo Implement Number Type System (class RTNumber extends RuntimeType)
public class RTInteger extends RuntimeType {

    protected int value;

    public RTInteger(int number) {
        super();
        value = number;
    }

    public RTInteger add(RTInteger other){
        return new RTInteger(value + other.getValue());
    }

    public RTBoolean lessThan(RTInteger other){
        return new RTBoolean(this.value < other.getValue());
    }

    @Override
    public RTInteger add(RuntimeType y_value) {
        if (y_value instanceof  RTInteger){
            return this.add((RTInteger) y_value);
        }
        return null;
    }

    @Override
    public Boolean getBoolean() {
        return value > 0;
    }

    @Override
    public RTBoolean lessThan(RuntimeType b) {
        if (b instanceof RTInteger){
            return this.lessThan((RTInteger) b);
        }
        return new RTBoolean(false);
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return super.toString() + " with Value (" + value + ")";
    }
}
