/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.NameSymbols;
import com.oracle.svm.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Symbols;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Utf8Symbols;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = BuiltinTraits.NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class, other = PartiallyLayerAware.class)
public final class SymbolsSupport {
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static final SymbolsSupport TEST_SINGLETON = ImageInfo.inImageCode() ? null : new SymbolsSupport();

    final Symbols symbols;
    final Utf8Symbols utf8;
    final NameSymbols names;
    final TypeSymbols types;
    final SignatureSymbols signatures;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SymbolsSupport() {
        int initialSymbolTableCapacity = 4 * 1024;
        symbols = Symbols.fromExisting(SVMSymbols.SYMBOLS.freeze(), initialSymbolTableCapacity, 0);
        // let this resize when first used at runtime
        utf8 = new Utf8Symbols(symbols);
        names = new NameSymbols(symbols);
        types = new TypeSymbols(symbols);
        signatures = new SignatureSymbols(symbols, types);
    }

    public static TypeSymbols getTypes() {
        return currentLayer().types;
    }

    public static SignatureSymbols getSignatures() {
        return currentLayer().signatures;
    }

    public static NameSymbols getNames() {
        return currentLayer().names;
    }

    public static Utf8Symbols getUtf8() {
        return currentLayer().utf8;
    }

    /*
     * Substituted at runtime to return the topmost layer. This ensures that symbols defined at
     * runtime end up in a single location.
     */
    public static SymbolsSupport currentLayer() {
        if (TEST_SINGLETON != null) {
            /*
             * Some unit tests use com.oracle.svm.interpreter.metadata outside the context of
             * native-image.
             */
            assert !ImageInfo.inImageCode();
            return TEST_SINGLETON;
        }
        return LayeredImageSingletonSupport.singleton().lookup(SymbolsSupport.class, false, true);
    }

    public static SymbolsSupport[] layeredSingletons() {
        return MultiLayeredImageSingleton.getAllLayers(SymbolsSupport.class);
    }
}

/**
 * This substitution class is here to avoid a massive refactoring of the symbols support. The only
 * part of this code that needs to be layer-aware is the Symbols class (and its implementation). We
 * simply need getOrCreate to act on the current layer at build-time, and the lookup and getOrCreate
 * calls to be checking all layered singletons at run-time.
 */
@TargetClass(className = "com.oracle.svm.espresso.classfile.descriptors.SymbolsImpl")
final class Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl {
    @Alias//
    ConcurrentHashMap<ByteSequence, Symbol<?>> strongMap;

    @Alias//
    WeakHashMap<ByteSequence, WeakReference<Symbol<?>>> weakMap;

    @Alias//
    ReadWriteLock readWriteLock;

    @Substitute
    @SuppressWarnings({"static-method"})
    public <T> Symbol<T> lookup(ByteSequence byteSequence) {
        for (var singleton : SymbolsSupport.layeredSingletons()) {
            var symbols = SubstrateUtil.cast(singleton.symbols, Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.class);
            Symbol<T> symbol = Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.originalLookup(symbols, byteSequence);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    @Substitute
    <T> Symbol<T> getOrCreate(ByteSequence byteSequence, boolean ensureStrongReference) {
        for (var singleton : SymbolsSupport.layeredSingletons()) {
            var symbols = SubstrateUtil.cast(singleton.symbols, Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.class);
            Symbol<T> symbol = Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.originalLookup(symbols, byteSequence);
            if (symbol != null && !(ensureStrongReference && symbols.isWeak(symbol))) {
                return symbol;
            }
        }
        return Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl.originalGetOrCreate(this, byteSequence, ensureStrongReference);
    }

    @Alias
    public native boolean isWeak(Symbol<?> symbol);
}

final class Util_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl {
    @SuppressWarnings("unchecked")
    static <T> Symbol<T> originalLookup(Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl symbols, ByteSequence byteSequence) {
        // Lock-free fast path, common symbols are usually strongly referenced e.g.
        // Ljava/lang/Object;
        Symbol<T> result = (Symbol<T>) symbols.strongMap.get(byteSequence);
        if (result != null) {
            return result;
        }
        symbols.readWriteLock.readLock().lock();
        try {
            result = (Symbol<T>) symbols.strongMap.get(byteSequence);
            if (result != null) {
                return result;
            }
            WeakReference<Symbol<?>> weakValue = symbols.weakMap.get(byteSequence);
            if (weakValue != null) {
                return (Symbol<T>) weakValue.get();
            } else {
                return null;
            }
        } finally {
            symbols.readWriteLock.readLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Symbol<T> originalGetOrCreate(Target_com_oracle_svm_espresso_classfile_descriptors_SymbolsImpl symbols, ByteSequence byteSequence, boolean ensureStrongReference) {
        // Lock-free fast path, common symbols are usually strongly referenced e.g.
        // Ljava/lang/Object;
        Symbol<T> symbol = (Symbol<T>) symbols.strongMap.get(byteSequence);
        if (symbol != null) {
            return symbol;
        }

        symbols.readWriteLock.writeLock().lock();
        try {
            // Must peek again within the lock because the symbol may have been promoted from weak
            // to strong by another thread; querying only the weak map wouldn't be correct.
            symbol = (Symbol<T>) symbols.strongMap.get(byteSequence);
            if (symbol != null) {
                return symbol;
            }

            if (ensureStrongReference) {
                WeakReference<Symbol<?>> weakValue = symbols.weakMap.remove(byteSequence);
                if (weakValue != null) {
                    // Promote weak symbol to strong.
                    symbol = (Symbol<T>) weakValue.get();
                    // The weak symbol may have been collected.
                    if (symbol != null) {
                        symbols.strongMap.put(symbol, symbol);
                        return symbol;
                    }
                }

                // Create new strong symbol.
                symbol = Target_com_oracle_svm_espresso_classfile_descriptors_Symbols.createSymbolInstanceUnsafe(byteSequence);
                symbols.strongMap.put(symbol, symbol);
                return symbol;
            } else {
                WeakReference<Symbol<?>> weakValue = symbols.weakMap.get(byteSequence);
                if (weakValue != null) {
                    symbol = (Symbol<T>) weakValue.get();
                    // The weak symbol may have been collected.
                    if (symbol != null) {
                        return symbol;
                    }
                }

                // Create new weak symbol.
                symbol = Target_com_oracle_svm_espresso_classfile_descriptors_Symbols.createSymbolInstanceUnsafe(byteSequence);
                symbols.weakMap.put(symbol, new WeakReference<>(symbol));
                return symbol;
            }
        } finally {
            symbols.readWriteLock.writeLock().unlock();
        }
    }
}

@TargetClass(Symbols.class)
final class Target_com_oracle_svm_espresso_classfile_descriptors_Symbols {
    @Alias
    static native <T> Symbol<T> createSymbolInstanceUnsafe(ByteSequence byteSequence);
}

@TargetClass(SymbolsSupport.class)
final class Target_com_oracle_svm_core_hub_registry_SymbolsSupport {
    @Substitute
    public static SymbolsSupport currentLayer() {
        SymbolsSupport[] allLayers = SymbolsSupport.layeredSingletons();
        return allLayers[allLayers.length - 1];
    }
}
