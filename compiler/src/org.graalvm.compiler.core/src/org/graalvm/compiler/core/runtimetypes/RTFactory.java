package org.graalvm.compiler.core.runtimetypes;
import org.graalvm.compiler.nodes.RuntimeType;

public class RTFactory {
    // todo possibly move to RuntimeType as a static class method.
    public static RuntimeType toRuntimeType(Object arg){
        // Handle Primitive RuntimeTypes
        if (arg instanceof Boolean){
            return new RTBoolean(((Boolean) arg));
        }
        else if (arg instanceof Byte){
            return new RTNumber(((Byte) arg));
        }
        else if (arg instanceof Short){
            return new RTNumber(((Short) arg));
        }
        else if (arg instanceof Integer){
            return new RTNumber(((Integer) arg));
        }
        else if (arg instanceof Long){
            return new RTNumber(((Long) arg));
        }
        else if (arg instanceof Double){
            return new RTNumber(((Double) arg));
        }
        else if (arg instanceof Float){
            return new RTNumber(((Float) arg));
        }
        else if (arg instanceof Character){ //todo rework -> shouldn't typecast
            return new RTCharacter((Character) arg);
            // Checking if array https://stackoverflow.com/questions/2725533/how-to-see-if-an-object-is-an-array-without-using-reflection
        }
        else if (arg.getClass().isArray()){
            return new RTArray(arg);
        }
        else if (arg instanceof String){
            char[] array = ((String) arg).toCharArray();
            return new RTArray(array);
        }
//        // todo Handle Non Primitive RuntimeTypes
        // Array handling
        // Class Instance Handling
        return null;
    }
}
