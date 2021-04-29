package org.graalvm.compiler.nodes;

public abstract class RuntimeType {
    /**
     * Creates a String representation for this runtimeType
     */
    public String toString() {
        return this.getClass().getSimpleName();
    }

    // Returns Java Object representation of runtime type (used for unit testing)
    public abstract Object toObject();

    public abstract Class<?> getClazz();

    public abstract Boolean getBoolean();
}
