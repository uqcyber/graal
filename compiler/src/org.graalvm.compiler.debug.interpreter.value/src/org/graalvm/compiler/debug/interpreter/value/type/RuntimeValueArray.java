package org.graalvm.compiler.debug.interpreter.value.type;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValueFactory;

import java.lang.reflect.Array;
import java.util.Arrays;

public class RuntimeValueArray extends RuntimeValue {
    // todo arrays of primitive types have default value of 0 and 0.0, else for objects: null
    // Todo should I use an actual array or should I implement this with a HashMap?

    private final RuntimeValue[] array;
    private final RuntimeValue length;
    private final int resolvedLength;
    private final boolean isPrimitive;
    private final ResolvedJavaType type;
    private Class<?> clazz = null;

    public RuntimeValueArray(RuntimeValue length, ResolvedJavaType type) {
        this.length = length;
        this.type = type;

        if (length.toObject() instanceof Integer) {
            this.resolvedLength = (int) length.toObject();
        } else {
            this.resolvedLength = -1;
        }
        // arrayType = type;
        array = new RuntimeValue[this.resolvedLength];
        // array = new ArrayList<>();
        isPrimitive = type.isPrimitive();
    }

    // Constructor for obj known to be array type
    public RuntimeValueArray(Object obj, RuntimeValueFactory factory) {
        Class<?> componentType = obj.getClass().getComponentType();
        assert obj.getClass().isArray();

        this.resolvedLength = Array.getLength(obj);
        this.length = new RuntimeValueNumber(resolvedLength);
        type = null;
        clazz = obj.getClass();
        array = new RuntimeValue[this.resolvedLength];
        isPrimitive = componentType.isPrimitive();

        // Populate the array
        for (int i = 0; i < Array.getLength(obj); i++) {
            Object currentObj = Array.get(obj, i);
            RuntimeValue rtObj = factory.toRuntimeType(currentObj);
            array[i] = rtObj;
        }
    }

    public int getResolvedLength() {
        return resolvedLength;
    }

    public RuntimeValue[] getArray() {
        return array;
    }

    // todo deprecate
    public RuntimeValueArray(int length, Constant value) {
        this.resolvedLength = length;
        this.length = new RuntimeValueNumber(length);
        this.type = null; // todo check logic

        // this.arrayType = ((HotSpotObjectConstant) value).getType();
        // Method method = null;
        // ResolvedJavaType type = null;
        // try {
        //     method = value.getClass().getMethod("getType", (Class<?>[]) null);
        // } catch (Exception e){
        //     e.printStackTrace();
        // }
        // assert method != null;
        // try {
        //     type = (ResolvedJavaType) method.invoke(value);
        // } catch (InvocationTargetException | IllegalAccessException e) {
        //     e.printStackTrace();
        // }
        // this.arrayType = type;
        // this.arrayType = value.getClass().cast(value);
        array = new RuntimeValue[length];
        isPrimitive = false;
    }

    public RuntimeValue getLength() {
        return length;
    }

    public void set_index(RuntimeValue index, RuntimeValue value) {
        if (index.toObject() instanceof Integer) {
            array[(int) index.toObject()] = value;
        }
        // todo have error message on failure
    }

    public RuntimeValue get(RuntimeValue index) {
        // todo add test for out of bounds.
        if (index.toObject() instanceof Integer) {
            return array[(int) index.toObject()];
        } else {
            if (isPrimitive) {
                return new RuntimeValueNumber(0);
            } else {
                return RuntimeValueVoid.INSTANCE;
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
        Object[] outputArray = new Object[resolvedLength];

        for (int i = 0; i < resolvedLength; i++) {
            outputArray[i] = (array[i]).toObject();
        }

        return outputArray;
    }

    @Override
    public Class<?> getClazz() {
        if (type != null) {
            return this.type.getClass();
        } else if (clazz != null) {
            return clazz;
        }
        return null;
    }
}
