package org.graalvm.compiler.core.runtimetypes;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.RuntimeType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class RTArray extends RuntimeType {
    // todo arrays of primitive types have default value of 0 and 0.0, else for objects: null

    // Todo should I use an actual array or should I implement this with a HashMap?
//    private final ArrayList<RuntimeType> array;
    private final RuntimeType[] array;
//    private final ResolvedJavaType arrayType;
    private final RuntimeType length;
    private final int resolvedLength;
    private final boolean isPrimitive;
    private final ResolvedJavaType type;

    public RTArray(RuntimeType length, ResolvedJavaType type){
        this.length = length;
        this.type = type;

        if (length instanceof RTInteger){
            this.resolvedLength = ((RTInteger) length).value;
        } else {
            this.resolvedLength = -1;
        }
//        arrayType = type;
        array = new RuntimeType[this.resolvedLength];
//        array = new ArrayList<>();
        isPrimitive = type.isPrimitive();
    }

    //todo deprecate
    public RTArray(int length, Constant value){
        this.resolvedLength = length;
        this.length = new RTInteger(length);
        this.type = null; // todo check logic

//        this.arrayType = ((HotSpotObjectConstant) value).getType();
//        Method method = null;
//        ResolvedJavaType type = null;
//        try {
//            method = value.getClass().getMethod("getType", (Class<?>[]) null);
//        } catch (Exception e){
//            e.printStackTrace();
//        }
//        assert method != null;
//        try {
//            type = (ResolvedJavaType) method.invoke(value);
//        } catch (InvocationTargetException | IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        this.arrayType = type;
//        this.arrayType = value.getClass().cast(value);
        array = new RuntimeType[length];
        isPrimitive = false;
    }

    public RuntimeType getLength(){
        return length;
    }

    public void set_index(RuntimeType index, RuntimeType value){
        if (index instanceof RTInteger) {
            array[((RTInteger) index).value] = value;
//            array.add(((RTInteger) index).value, value);
        } //todo have error message on failure
    }

    public RuntimeType get(RuntimeType index){
        // todo add test for out of bounds.
        if (index instanceof RTInteger){
            return array[((RTInteger) index).value];
//            return array.get(((RTInteger) index).value);
        } else {
            if (isPrimitive){
                return new RTInteger(0);
            } else {
                return new RTVoid();
            }
        }
    }

    public Boolean getBoolean() {
        return null; // Cannot convert directly from array type to boolean type.
    }


    @Override
    public String toString() {
        return super.toString() + "{" +
                "array=" + Arrays.toString(array) +
                "} ";
    }

    // todo
    public Object toObject() {
        return null;
    }

    @Override
    public Class<?> getClazz() {
        return this.type.getClass();
    }
}
