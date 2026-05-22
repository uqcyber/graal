/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.module.ModuleFinder;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.module.ModuleBootstrap")
@SuppressWarnings("unused")
final class Target_jdk_internal_module_ModuleBootstrap {
    // Checkstyle: stop
    /// Preserves the build-time native-access grants selected while building the image. Runtime
    /// `--enable-native-access` values are additive and must be merged into this hosted baseline.
    @Alias @RecomputeFieldValue(kind = Kind.None, isFinal = false) //
    static Set<String> USER_NATIVE_ACCESS_MODULES;
    // Checkstyle: resume

    @Alias
    static native void addExtraReads(ModuleLayer bootLayer);

    @Alias
    static native void addExtraExportsAndOpens(ModuleLayer bootLayer);

    @Alias
    static native void addEnableNativeAccess(ModuleLayer bootLayer);

    @Alias
    static native Set<String> decodeEnableNativeAccess();

    @Alias
    static native ModuleFinder finderFor(String prop);
}

final class ModuleBootstrapSubstitutionsSupport {
    private ModuleBootstrapSubstitutionsSupport() {
    }

    /// Merges runtime `--enable-native-access` selections into the hosted
    /// `ModuleBootstrap.USER_NATIVE_ACCESS_MODULES` value before the original
    /// `ModuleBootstrap.addEnableNativeAccess(ModuleLayer)` runs. The hosted value is part of the
    /// image configuration and remains authoritative; launch-time values only add to it.
    static void mergeRuntimeEnableNativeAccessModules() {
        Set<String> runtimeModules = Target_jdk_internal_module_ModuleBootstrap.decodeEnableNativeAccess();
        if (runtimeModules.isEmpty()) {
            return;
        }
        Set<String> mergedModules = new java.util.LinkedHashSet<>(Target_jdk_internal_module_ModuleBootstrap.USER_NATIVE_ACCESS_MODULES);
        mergedModules.addAll(runtimeModules);
        Target_jdk_internal_module_ModuleBootstrap.USER_NATIVE_ACCESS_MODULES = mergedModules;
    }
}
