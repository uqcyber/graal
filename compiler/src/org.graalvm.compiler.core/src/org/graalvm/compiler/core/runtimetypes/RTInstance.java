package org.graalvm.compiler.core.runtimetypes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.RuntimeType;

import java.util.Arrays;
import java.util.HashMap;

// An instance of an object class - todo should we just create an instance of the object itself?
public class RTInstance extends RuntimeType {
    protected final ResolvedJavaType type;
    protected final HashMap<ResolvedJavaField, RuntimeType> instanceFields;

    public RTInstance(ResolvedJavaType type) { // todo include reference to static class values.
        this.type = type;
        instanceFields = new HashMap<>();

        // // todo check whether to include superclass fields?
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            instanceFields.put(field, createDefaultType(field));
        }
    }

    // Returns the Java class type of the RTInstance
    public Class<?> getClazz() {
        return this.type.getClass();
    }

    public RuntimeType createDefaultType(ResolvedJavaField field) {
        // Assign appropriate default values for fields:
        // https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html

        // byte, short, int, long, float and double
        JavaKind fieldKind = field.getJavaKind();

        // todo handle other primitive number types more explicitly - flags in RTInteger?/ Make
        // RTInteger into RTNumber?
        switch (fieldKind) {
            case Byte:
                return new RTNumber((byte) 0);
            case Short:
                return new RTNumber((short) 0);
            case Int:
                return new RTNumber(0);
            case Long:
                return new RTNumber((long) 0);
            case Float:
                return new RTNumber((float) 0);
            case Double:
                return new RTNumber((double) 0);
            case Boolean: // boolean should be false
                return new RTBoolean(false);
            default: // String (or any object)
                return new RTVoid(); // todo handle Char: should be \u0000
        }
    }

    public void setFieldValue(ResolvedJavaField field, RuntimeType value) {
        instanceFields.replace(field, value);
    }

    public RuntimeType getFieldValue(ResolvedJavaField field) {
        return instanceFields.get(field);
    }

    @Override
    public Boolean getBoolean() {
        return null;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        // could also iterate over entrySet
        // Format comes from JavaField class
        // return "RTInstance";

        if (instanceFields.containsValue(this)) { // todo potentially infinitely recursive!
            return "RuntimeInstance(with recursive fields)";
        }
        instanceFields.forEach((field, runtimeType) -> sb.append("(field:").append(field.format("(%f) %t %n:")).append(runtimeType).append(")"));
        return super.toString() + "( Fields: " + sb + ")";
    }

    @Override
    public Object toObject() {

        // todo use reflection to construct actual java Object:
        // Using reflection: ResolvedJavaMethod getClassInitializer(); ???
        return null;
    }

}
