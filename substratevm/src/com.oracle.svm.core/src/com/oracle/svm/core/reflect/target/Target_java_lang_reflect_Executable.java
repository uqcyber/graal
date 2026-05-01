/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.espresso.classfile.ConstantPool.Tag;
import com.oracle.svm.espresso.classfile.attributes.MethodParametersAttribute;
import com.oracle.svm.shared.util.SubstrateUtil;

@TargetClass(value = Executable.class)
public final class Target_java_lang_reflect_Executable {

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    Target_java_lang_reflect_Executable_ParameterData parameterData;

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    /**
     * Backing metadata for {@link Executable#getParameters()}.
     * <p>
     * For executables created at image-build-time this contains the encoded {@code byte[]} produced
     * at image build time. For run-time-loaded Crema executables this contains the
     * {@link MethodParametersAttribute}. A {@code null} value means there is no
     * {@code MethodParameters} attribute and reflection must synthesize parameter objects.
     */
    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = RawParametersComputer.class)//
    Object parameterMetadata;

    @Substitute
    private Parameter[] getParameters0() {
        if (parameterMetadata == null) {
            return null;
        }
        Executable executable = SubstrateUtil.cast(this, Executable.class);
        DynamicHub declaringClass = DynamicHub.fromClass(executable.getDeclaringClass());
        if (declaringClass.isRuntimeLoaded()) {
            return RuntimeLoadedExecutableParameterHelper.asReflectParameters(executable, (MethodParametersAttribute) parameterMetadata, declaringClass);
        }
        /*
         * Note that the image-time parameter metadata encoding can also encode a possible
         * IllegalArgumentException. We want the decoder to throw this exception. Our caller
         * Executable.parameterData catches it and converts it to a MalformedParametersException.
         */
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseReflectParameters(executable, (byte[]) parameterMetadata, declaringClass);
    }

    @Substitute
    byte[] getTypeAnnotationBytes0() {
        return SubstrateUtil.cast(this, Target_java_lang_reflect_AccessibleObject.class).typeAnnotations;
    }

    static class RawParametersComputer extends ReflectionMetadataComputer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return ImageSingletons.lookup(EncodedRuntimeMetadataSupplier.class).getReflectParametersEncoding((Executable) receiver);
        }
    }
}

@TargetClass(value = Executable.class, innerClass = "ParameterData")
final class Target_java_lang_reflect_Executable_ParameterData {
}

final class RuntimeLoadedExecutableParameterHelper {
    private RuntimeLoadedExecutableParameterHelper() {
    }

    static Parameter[] asReflectParameters(Executable executable, MethodParametersAttribute methodParameters, DynamicHub declaringClass) {
        var constantPool = CremaSupport.singleton().getConstantPool(declaringClass);
        int entryCount = methodParameters.entryCount();
        Parameter[] parameters = new Parameter[entryCount];
        int constantPoolLength = constantPool.length();
        for (int i = 0; i < entryCount; i++) {
            MethodParametersAttribute.Entry entry = methodParameters.entryAt(i);
            int nameIndex = entry.getNameIndex();
            if (nameIndex >= constantPoolLength) {
                throw new IllegalArgumentException("Constant pool index out of bounds");
            }
            String name = null;
            if (nameIndex != 0) {
                if (constantPool.tagAt(nameIndex) != Tag.UTF8) {
                    throw new IllegalArgumentException("Wrong type at constant pool index");
                }
                name = constantPool.utf8At(nameIndex, "parameter name").toString();
            }
            parameters[i] = ReflectionObjectFactory.newParameter(executable, i, name, entry.getAccessFlags());
        }
        return parameters;
    }
}
