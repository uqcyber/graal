package org.graalvm.compiler.core.runtimetypes;

import jdk.vm.ci.meta.ResolvedJavaField;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.RuntimeType;

import java.lang.reflect.Field;
import java.util.HashMap;

public class RTString extends RTInstance {
    private final char[] actual_value; // Also store in instance Fields for loadField access

    public RTString(ResolvedJavaType type, String string) {
        super(type);
        actual_value = string.toCharArray();
        RuntimeType field_value = new RTArray(actual_value);
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
