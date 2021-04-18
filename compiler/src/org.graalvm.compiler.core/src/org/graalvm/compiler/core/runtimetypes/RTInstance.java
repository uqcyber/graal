package org.graalvm.compiler.core.runtimetypes;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.nodes.RuntimeType;

import java.util.Arrays;
import java.util.HashMap;

// An instance of an object class - todo should we just create an instance of the object itself?
public class RTInstance extends RuntimeType {
    private final ResolvedJavaType type;
    private final ResolvedJavaField[] instanceFields;

    public RTInstance(ResolvedJavaType type){ // todo include reference to static class values.
        this.type = type;
        instanceFields = type.getInstanceFields(true);
//        // todo check whether to include superclass fields?
//        for (ResolvedJavaField field: type.getInstanceFields(true)) {
//            instanceFields.put(field, new RTVoid());
//        }
    }

//    public void setField(ResolvedJavaField field, RuntimeType value){
//        instanceFields.replace(field, value);
//    }

//    public RuntimeType getFieldValue(ResolvedJavaField field){
//        return instanceFields.get(field);
//    }

    @Override
    public Boolean getBoolean() {
        return null;
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();

        for(ResolvedJavaField field : instanceFields){
            sb.append("(").append(field.getJavaKind()).append(" ").append( field.getName()).append("), ");
        }
        return super.toString() + "(with fields: " + sb + ")";
    }
}
