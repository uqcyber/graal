package org.graalvm.compiler.nodes;

public abstract class RuntimeType {

    /**
     * Creates a String representation for this runtimeType
     */
    public String toString() {
//        StringBuilder str = new StringBuilder();
//        str.append(toString(Verbosity.Short)).append(" { ");
//        for (Map.Entry<Object, Object> entry : getDebugProperties().entrySet()) {
//            str.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
//        }
//        str.append(" }");
//        return str.toString();
        return this.getClass().getSimpleName();
    }
}
