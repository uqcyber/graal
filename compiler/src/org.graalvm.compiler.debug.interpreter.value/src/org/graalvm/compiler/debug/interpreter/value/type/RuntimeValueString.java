package org.graalvm.compiler.debug.interpreter.value.type;

import jdk.vm.ci.meta.ResolvedJavaField;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.interpreter.value.RuntimeValue;

public class RuntimeValueString extends RuntimeValueInstance {
    private final char[] actual_value; // Also store in instance Fields for loadField access

    public RuntimeValueString(ResolvedJavaType type, String string) {
        super(type);
        actual_value = string.toCharArray();
        RuntimeValue field_value = new RuntimeValueArray(actual_value, null);
        // todo how to get String into resolvedJavaType form? Currently passing type through
        // metaContext
        // alternative: ResolvedJavaType resolvedObjectType = jvmciRuntime.getJavaLangString();
        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            this.instanceFields.put(field, this.createDefaultType(field));
            if (field.getName().equals("value")) { // Overwrites the "value" field with the RTArray
                                                   // of chars.
                this.instanceFields.put(field, field_value);
            }
        }
    }

    @Override
    public Boolean getBoolean() {
        return null;
    }

    public String toString() {
        return "RTString: \"" + this.toObject() + "\"";
    }

    // Returns the Java class type of the RTInstance
    public Class<?> getClazz() {
        return String.class;
    }

    @Override
    public Object toObject() {
        return new String(actual_value);
    }

}
