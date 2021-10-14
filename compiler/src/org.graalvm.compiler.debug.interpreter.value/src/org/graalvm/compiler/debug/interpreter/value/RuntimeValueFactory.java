package org.graalvm.compiler.debug.interpreter.value;

import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.function.Function;

public interface RuntimeValueFactory {
    default RuntimeValue toRuntimeType(Object arg) {
        return toRuntimeType(arg, null);
    };

    RuntimeValue toRuntimeType(Object arg, Function<Class<?>, ResolvedJavaType> lookupJavaTypeMethod);
}
