package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

// todo Implement Number Type System (class RTNumber extends RuntimeType)
public class RTInteger extends RuntimeType {

    protected int value;

    public RTInteger(int number) {
        super();
        value = number;
    }

    public static RTInteger add(RTInteger x_value, RTInteger y_value) {
        return new RTInteger(x_value.getValue() + y_value.getValue());
    }

    public static RTInteger sub(RTInteger x_value, RTInteger y_value) {
        return new RTInteger(x_value.getValue() + (-1*y_value.getValue()));
    }

    public static RTBoolean lessThan(RTInteger x_value, RTInteger y_value) {
        return new RTBoolean(x_value.getValue() < y_value.getValue());
    }

    public static RTBoolean integerEquals(RTInteger x_value, RTInteger y_value) {
        return new RTBoolean(x_value.getValue() == y_value.getValue());
    }

    public static RTInteger mul(RTInteger x_value, RTInteger y_value) {
        return new RTInteger(x_value.getValue() * y_value.getValue());
    }

    public static RTInteger rightShift(RTInteger x_value, RTInteger y_value){
        return new RTInteger(x_value.getValue() >> y_value.getValue());
    }

    public static RTInteger signedDiv(RTInteger x_value, RTInteger y_value){
        return new RTInteger(x_value.getValue() / y_value.getValue());
    }

    public static RTInteger unsignedRightShift(RTInteger x_value, RTInteger y_value){
        return new RTInteger(x_value.getValue() >>> y_value.getValue());
    }

    public static RTInteger leftShift(RTInteger x_value, RTInteger y_value){
        return new RTInteger(x_value.getValue() << y_value.getValue());
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
        return super.toString() + "(" + value + ")";
    }
}
