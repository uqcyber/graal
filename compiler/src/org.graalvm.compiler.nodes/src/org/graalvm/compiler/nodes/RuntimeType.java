package org.graalvm.compiler.nodes;

public abstract class RuntimeType {
    /**
     * Creates a String representation for this runtimeType
     */
    public String toString() {
        return this.getClass().getSimpleName();
    }

    public abstract Boolean getBoolean();
}
