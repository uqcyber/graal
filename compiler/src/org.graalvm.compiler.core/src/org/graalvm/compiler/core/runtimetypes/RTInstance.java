package org.graalvm.compiler.core.runtimetypes;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.RuntimeType;

import java.util.Arrays;
import java.util.HashMap;

// An instance of an object class - todo should we just create an instance of the object itself?
public class RTInstance extends RuntimeType {
    private final ResolvedJavaType type;
    private final HashMap<ResolvedJavaField, RuntimeType> instanceFields;

    public RTInstance(ResolvedJavaType type){ // todo include reference to static class values.
        this.type = type;
        instanceFields = new HashMap<>();



//        // todo check whether to include superclass fields?
        for (ResolvedJavaField field: type.getInstanceFields(true)) {
            instanceFields.put(field, new RTVoid());
        }
    }

    public void setFieldValue(ResolvedJavaField field, RuntimeType value){
        instanceFields.replace(field, value);
    }

    public RuntimeType getFieldValue(ResolvedJavaField field){
        return instanceFields.get(field);
    }

    @Override
    public Boolean getBoolean() {
        return null;
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        // could also iterate over entrySet
        // Format comes from JavaField class
        instanceFields.forEach((field, runtimeType) -> sb.append("(field:").append(field.format("(%f) %t %n:")).append(runtimeType).append(")"));
        return super.toString() + "( Fields: " + sb + ")";
    }
}
