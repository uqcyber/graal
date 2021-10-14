package org.graalvm.compiler.debug.interpreter.value.type;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;

import java.util.HashMap;

// An instance of an object class - todo should we just create an instance of the object itself?
public class RuntimeValueInstance extends RuntimeValue {
    protected final ResolvedJavaType type;
    protected final HashMap<ResolvedJavaField, RuntimeValue> instanceFields;

    public RuntimeValueInstance(ResolvedJavaType type) { // todo include reference to static class values.
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

    public RuntimeValue createDefaultType(ResolvedJavaField field) {
        // Assign appropriate default values for fields:
        // https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html

        // byte, short, int, long, float and double
        JavaKind fieldKind = field.getJavaKind();

        // todo handle other primitive number types more explicitly - flags in RTInteger?/ Make
        // RTInteger into RTNumber?
        switch (fieldKind) {
            case Byte:
                return new RuntimeValueNumber((byte) 0);
            case Short:
                return new RuntimeValueNumber((short) 0);
            case Int:
                return new RuntimeValueNumber(0);
            case Long:
                return new RuntimeValueNumber((long) 0);
            case Float:
                return new RuntimeValueNumber((float) 0);
            case Double:
                return new RuntimeValueNumber((double) 0);
            case Boolean: // boolean should be false
                return RuntimeValueBoolean.of(false);
            default: // String (or any object)
                return RuntimeValueVoid.INSTANCE; // todo handle Char: should be \u0000
        }
    }

    public void setFieldValue(ResolvedJavaField field, RuntimeValue value) {
        instanceFields.replace(field, value);
    }

    public RuntimeValue getFieldValue(ResolvedJavaField field) {
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
