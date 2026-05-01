/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.shared.util.SubstrateUtil;

@TargetClass(value = jdk.internal.loader.BuiltinClassLoader.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_jdk_internal_loader_BuiltinClassLoader {

    @Alias Target_jdk_internal_loader_URLClassPath ucp;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = NewConcurrentHashMap.class) //
    private Map<ModuleReference, ModuleReader> moduleToReader;

    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private volatile SoftReference<Map<String, List<URL>>> resourceCache;

    @Alias
    public native void loadModule(ModuleReference mref);

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Target_java_lang_ClassLoader self = SubstrateUtil.cast(this, Target_java_lang_ClassLoader.class);
        Class<?> clazz = self.findLoadedClass(name);
        if (clazz == null) {
            throw new ClassNotFoundException(name);
        }
        return clazz;
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    protected Class<?> defineClass(String cn, Target_jdk_internal_loader_BuiltinClassLoader_LoadedModule loadedModule) {
        /*
         * Avoid dragging in logging & formatting code through
         * ModuleReader->JarFile->Manifest->Attributes
         */
        throw RuntimeClassLoading.throwNoBytecodeClasses(cn);
    }

    @Substitute
    public URL findResource(String mn, String name) {
        Module module = ModuleLayer.boot().findModule(mn).orElse(null);
        return ResourcesHelper.nameToResourceURL(module, name);
    }

    @Substitute
    public InputStream findResourceAsStream(String mn, String name) throws IOException {
        return ResourcesHelper.nameToResourceInputStream(mn, name);
    }

    @Substitute
    public URL findResource(String name) {
        if (!ClassRegistries.respectClassLoader()) {
            // Return only image resources
            return ResourcesHelper.nameToResourceURL(name);
        }

        if (this == Target_jdk_internal_loader_ClassLoaders.bootLoader()) {
            // Workaround for GR-73221: Only retrieve image resources for boot loader
            URL url = ResourcesHelper.nameToResourceURL(name);
            if (url != null) {
                return url;
            }
        }

        // TODO GR-73221: Also look into the modules defined to this loader

        return ucp == null ? null : ucp.findResource(name);
    }

    @Substitute
    public Enumeration<URL> findResources(String name) throws IOException {
        if (!ClassRegistries.respectClassLoader()) {
            // Return only image resources
            return ResourcesHelper.nameToResourceEnumerationURLs(name);
        }

        List<URL> resources = new ArrayList<>();

        if (this == Target_jdk_internal_loader_ClassLoaders.bootLoader()) {
            // Workaround for GR-73221: Only retrieve image resources for boot loader
            resources.addAll(ResourcesHelper.nameToResourceListURLs(name));
        }

        // TODO GR-73221: Also look into the modules defined to this loader

        if (ucp != null) {
            Enumeration<URL> e = ucp.findResources(name);
            if (resources.isEmpty()) {
                return e;
            }
            while (e.hasMoreElements()) {
                URL url = e.nextElement();
                resources.add(url);
            }
        }
        return resources.isEmpty() ? Collections.emptyEnumeration() : Collections.enumeration(resources);
    }

    @Substitute
    private List<URL> findMiscResource(String name) {
        return ResourcesHelper.nameToResourceListURLs(name);
    }

    @Substitute
    private URL findResource(ModuleReference mref, String name) {
        Module module = ModuleLayer.boot().findModule(mref.descriptor().name()).orElse(null);
        return ResourcesHelper.nameToResourceURL(module, name);
    }

    static final class NewConcurrentHashMap implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return new ConcurrentHashMap<>();
        }
    }
}

@TargetClass(value = jdk.internal.loader.BuiltinClassLoader.class, innerClass = "LoadedModule")
final class Target_jdk_internal_loader_BuiltinClassLoader_LoadedModule {
}
