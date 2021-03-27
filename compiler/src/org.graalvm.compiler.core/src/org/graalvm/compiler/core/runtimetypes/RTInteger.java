package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

// todo Implement Number Type System (class RTNumber extends RuntimeType)
public class RTInteger extends RuntimeType {

    protected int value;

    public RTInteger(int number) {
        super();
        value = number;
    }

    private static RTInteger negate(RTInteger input){
        int neg_value = input.getValue() * -1;
        return new RTInteger(neg_value);
    }

    public static RTInteger add(RTInteger x_value, RTInteger y_value) {
        int sum = x_value.getValue() + y_value.getValue();
        return new RTInteger(sum);
    }

    public static RTInteger sub(RTInteger x_value, RTInteger y_value) {
        RTInteger neg_y_value = negate(y_value);
        return add(x_value, neg_y_value);
    }

    public static RTBoolean lessThan(RTInteger x_value, RTInteger y_value) {
        return new RTBoolean(x_value.getValue() < y_value.getValue());
    }

    public static RTInteger mul(RTInteger x_value, RTInteger y_value) {
        return new RTInteger(x_value.getValue() * y_value.getValue());
    }

    public int getValue() {
        return value;
    }

    @Override
    public Boolean getBoolean() {
        return value > 0;
    }

    @Override
    public String toString() {
        return super.toString() + " with Value (" + value + ")";
    }
}
