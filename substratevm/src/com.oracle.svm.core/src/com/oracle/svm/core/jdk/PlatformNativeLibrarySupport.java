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
package com.oracle.svm.core.jdk;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;

public interface PlatformNativeLibrarySupport {

    static PlatformNativeLibrarySupport singleton() {
        return ImageSingletons.lookup(PlatformNativeLibrarySupport.class);
    }

    interface NativeLibrary {
        String getCanonicalIdentifier();

        boolean isBuiltin();

        void load() throws UnsatisfiedLinkError;

        PointerBase findSymbol(String name);
    }

    NativeLibrary createLibrary(String canonical, boolean builtIn);

    PointerBase findBuiltinSymbol(String name);

    List<String> defaultBuiltInLibraries = Collections.unmodifiableList(Arrays.asList("java", "nio", "net", "zip"));

    default boolean isBuiltinLibrary(String name) {
        return defaultBuiltInLibraries.contains(name);
    }

    boolean isBuiltinPkgNative(String name);

    boolean initializeBuiltinLibraries();
}
