package org.graalvm.compiler.core.runtimetypes;

import org.graalvm.compiler.nodes.RuntimeType;

public class RTBoolean extends RuntimeType{
    Boolean value;

    public RTBoolean(Boolean value) {
        super();
        this.value = value;
    }

    @Override
    public RuntimeType add(RuntimeType y_value) {
        return null;
    }

    @Override
    public Boolean getBoolean() {
        return value;
    }

    @Override
    public RuntimeType lessThan(RuntimeType b) {
        System.out.println("checking if boolean is less than some runtimeType " + b + "\n");
        return null;
    }
    @Override
    public String toString() {
        return super.toString() + " with Value (" + value + ")";
    }
}