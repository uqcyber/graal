/*
 * Copyright (c) 2009, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.interpreter.value;

import java.lang.reflect.Array;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Abstract superclass of all values generated by the Graal IR interpreter.
 */
public abstract class InterpreterValue {

    public boolean isPrimitive() {
        return false;
    }

    public boolean isArray() {
        return false;
    }

    public PrimitiveConstant asPrimitiveConstant() {
        throw new UnsupportedOperationException("asPrimitiveConstant called on non primitive value");
    }

    public ResolvedJavaType getObjectType() {
        throw new UnsupportedOperationException();
    }

    public abstract JavaKind getJavaKind();

    /**
     * Is this value an exception object that was thrown by an UnwindNode?
     *
     * @return true if this return result represents an exception return from a method call.
     */
    public boolean isUnwindException() {
        return false;
    }

    /**
     * Mark this value as an exception object that has been thrown and is unwinding the stack.
     */
    public void setUnwindException() {
        throw new IllegalArgumentException("Cannot unwind with non-Exception value.");
    }

    public abstract boolean isNull();

    public abstract Object asObject();

    public static InterpreterValue createDefaultOfKind(JavaKind kind) {
        if (kind == JavaKind.Void) {
            return InterpreterValueVoid.INSTANCE;
        } else if (kind == JavaKind.Object) {
            return InterpreterValueNullPointer.INSTANCE;
        } else if (kind.isPrimitive()) {
            return InterpreterValuePrimitive.defaultForPrimitiveKind(kind);
        }
        throw new IllegalArgumentException("Illegal JavaKind");
    }

    /**
     * Graal IR Interpreter value that represents the null pointer.
     *
     * This is a singleton class, with just one value: INSTANCE.
     */
    public static final class InterpreterValueNullPointer extends InterpreterValue {
        public static final InterpreterValueNullPointer INSTANCE = new InterpreterValueNullPointer();

        private InterpreterValueNullPointer() {
        }

        @Override
        public JavaKind getJavaKind() {
            return JavaKind.Object;
        }

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public Object asObject() {
            return null;
        }
    }

    /**
     * Graal IR Interpreter value that represents a void result.
     *
     * This is a singleton class, with just one value: INSTANCE.
     */
    public static final class InterpreterValueVoid extends InterpreterValue {
        public static final InterpreterValueVoid INSTANCE = new InterpreterValueVoid();

        private InterpreterValueVoid() {
        }

        @Override
        public boolean isPrimitive() {
            return true;
        }

        @Override
        public JavaKind getJavaKind() {
            return JavaKind.Void;
        }

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public Object asObject() {
            // Void is uninstantiable
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Get the Class&lt;?&gt; type corresponding to the given JavaType.
     */
    protected static Class<?> getTypeClass(JVMContext jvmContext, JavaType type) throws ClassNotFoundException {
        JavaKind kind = type.getJavaKind();
        if (kind.isPrimitive()) {
            // For primitive types, JavaKind.toJavaClass() is enough.
            return kind.toJavaClass();
        } else if (type.isArray()) {
            // For arrays, the element type may be a complex, like int[][], so
            // we need to call getTypeClass() recursively to get its Class.
            return Array.newInstance(getTypeClass(jvmContext, type.getComponentType()), 0).getClass();
        } else {
            // For objects, load its class.
            return Class.forName(type.toJavaName(), true, jvmContext.getClassLoader());
        }
    }

    /**
     * Coerce any small integer objects up to Integer.
     *
     * @param obj
     * @return the same value as obj, but at least Integer size.
     */
    public static Object coerceUpToInt(Object obj) {
        if (obj instanceof Byte) {
            obj = Integer.valueOf(((Byte) obj).intValue());
        } else if (obj instanceof Short) {
            obj = Integer.valueOf(((Short) obj).intValue());
        } else if (obj instanceof Character) {
            obj = Integer.valueOf(((Character) obj).charValue());
        }
        return obj;
    }
}
