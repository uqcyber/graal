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
package com.oracle.svm.interpreter.ristretto.compile;

import static com.oracle.svm.core.FrameAccess.returnAddressSize;
import static com.oracle.svm.core.deopt.Deoptimizer.createRelockObjectData;
import static com.oracle.svm.interpreter.ristretto.compile.InterpreterDeoptEntryPoints.logger;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptState;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.interpreter.EspressoFrame;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.InterpreterToVM;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Ristretto equivalent of {@link com.oracle.svm.core.deopt.DeoptimizationSupport} for Ristretto
 * compilations that deoptimize back to the Crema interpreter.
 *
 * High-level flow: {@link Deoptimizer} routes Ristretto frames here, this class resolves metadata
 * and builds a {@link RistrettoDeoptimizedInterpreterFrame}, and
 * {@link InterpreterDeoptEntryPoints} consumes the registered entry points to continue execution in
 * the interpreter.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
public class RistrettoDeoptimizationSupport {

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryVoid;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryInt;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryLong;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryFloat;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryDouble;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryObject;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryBoolean;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryByte;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryShort;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class)//
    private CFunctionPointer interpreterEntryChar;

    @Platforms(Platform.HOSTED_ONLY.class)
    public RistrettoDeoptimizationSupport() {
    }

    @Fold
    static RistrettoDeoptimizationSupport get() {
        return ImageSingletons.lookup(RistrettoDeoptimizationSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void initialize(CFunctionPointer entryVoid, CFunctionPointer entryInt, CFunctionPointer entryLong, CFunctionPointer entryFloat, CFunctionPointer entryDouble,
                    CFunctionPointer entryObject,
                    CFunctionPointer entryBoolean, CFunctionPointer entryByte, CFunctionPointer entryShort, CFunctionPointer entryChar) {
        RistrettoDeoptimizationSupport support = get();
        assert support.interpreterEntryVoid == null : "multiple entry stub methods registered";

        support.interpreterEntryVoid = entryVoid;
        support.interpreterEntryInt = entryInt;
        support.interpreterEntryLong = entryLong;
        support.interpreterEntryFloat = entryFloat;
        support.interpreterEntryDouble = entryDouble;
        support.interpreterEntryObject = entryObject;
        support.interpreterEntryBoolean = entryBoolean;
        support.interpreterEntryByte = entryByte;
        support.interpreterEntryShort = entryShort;
        support.interpreterEntryChar = entryChar;
    }

    private static CFunctionPointer getInterpreterEntry(JavaKind returnKind) {
        RistrettoDeoptimizationSupport support = get();
        CFunctionPointer ptr = switch (returnKind) {
            case Void -> support.interpreterEntryVoid;
            case Int -> support.interpreterEntryInt;
            case Long -> support.interpreterEntryLong;
            case Float -> support.interpreterEntryFloat;
            case Double -> support.interpreterEntryDouble;
            case Object -> support.interpreterEntryObject;
            case Boolean -> support.interpreterEntryBoolean;
            case Byte -> support.interpreterEntryByte;
            case Short -> support.interpreterEntryShort;
            case Char -> support.interpreterEntryChar;
            default -> throw VMError.shouldNotReachHere("Unexpected return kind for interpreter entry: " + returnKind);
        };
        assert ptr.rawValue() != 0 : returnKind;
        return ptr;
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    private static long getCodeInfoRelativeIP(CodePointer pc) {
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(pc);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo tetheredCodeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            return CodeInfoAccess.relativeIP(tetheredCodeInfo, pc);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    /**
     * Builds the interpreter-target deoptimized frame for a Ristretto runtime-compiled method.
     */
    public static DeoptimizedFrame createDeoptimizedFrame(Deoptimizer deoptimizer, CodePointer pc, FrameInfoQueryResult frameInfo, CodeInfoQueryResult physicalFrame, boolean eager) {
        RistrettoInstalledCode rCode = validateAndGetInstalledCode(pc);

        final long infoPointRelativeIp = getCodeInfoRelativeIP(pc);
        final Infopoint compilerInfoPoint = rCode.getInfopointForRelativeIP(NumUtil.safeToInt(infoPointRelativeIp));
        BytecodePosition byteCodeStack = compilerInfoPoint.debugInfo.frame();
        assert verifyInfopointAndStackWalk(frameInfo, byteCodeStack, compilerInfoPoint);
        return buildInterpreterFrame(frameInfo, physicalFrame, compilerInfoPoint, rCode, deoptimizer, pc, eager);
    }

    private static RistrettoInstalledCode validateAndGetInstalledCode(CodePointer pc) {
        SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(pc);
        if (installedCode == null) {
            throw VMError.shouldNotReachHere("Could not find Ristretto installed code for pc " + Long.toHexString(pc.rawValue()));
        }

        ResolvedJavaMethod method = installedCode.getMethod();
        if (!(method instanceof RistrettoMethod)) {
            throw VMError.shouldNotReachHere("Could not find Ristretto method for pc " + Long.toHexString(pc.rawValue()));
        }

        if (!(installedCode instanceof RistrettoInstalledCode rCode)) {
            throw VMError.shouldNotReachHere("Found non-Ristretto installed code for pc " + Long.toHexString(pc.rawValue()));
        }
        return rCode;
    }

    private static DeoptimizedFrame buildInterpreterFrame(FrameInfoQueryResult virtualFrameInfo, CodeInfoQueryResult physicalFrame, Infopoint compilerInfoPoint, RistrettoInstalledCode rCode,
                    Deoptimizer deoptimizer, CodePointer pc, boolean pin) {
        FrameInfoQueryResult compiledFrame = virtualFrameInfo;
        RistrettoVirtualInterpreterFrame frameBefore = null;

        BytecodePosition associatedCompiledCodePosition = compilerInfoPoint.debugInfo.getBytecodePosition();

        /* Use current BCI for the top frame and nextBCI for caller frames at invoke boundaries. */
        boolean isTopFrame = true;
        while (compiledFrame != null) {
            final int virtualFrameBCI = compiledFrame.getBci();
            final int compilerAssociatedPosBCI = associatedCompiledCodePosition.getBCI();
            if (virtualFrameBCI != compilerAssociatedPosBCI) {
                throw VMError.shouldNotReachHere("Frame BCIs must match the virtual frame and compiler info point but were " + virtualFrameBCI + " and " + compilerAssociatedPosBCI);
            }

            RistrettoMethod rMethod = (RistrettoMethod) associatedCompiledCodePosition.getMethod();
            InterpreterResolvedJavaMethod interpreterMethod = rMethod.getInterpreterMethod();
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                logger().string("[buf/deopt] create interp frame for method=").string(interpreterMethod.toString()).newline();
            }
            InterpreterFrame interpreterFrame = createInterpreterFrameFromCompiledFrame(interpreterMethod, compiledFrame, deoptimizer);

            // deopt nodes record BCI after last side effect, thus we target to
            int currentBci = compiledFrame.getBci();
            int targetBci;
            if (isTopFrame) {
                targetBci = currentBci;
                isTopFrame = false;
            } else {
                targetBci = BytecodeStream.nextBCI(interpreterMethod.getCode(), currentBci);
            }

            RistrettoVirtualInterpreterFrame currentFrame = new RistrettoVirtualInterpreterFrame(compiledFrame, interpreterFrame, interpreterMethod, currentBci,
                            targetBci, compiledFrame.getNumStack(), frameBefore);
            if (frameBefore != null) {
                frameBefore.setCaller(currentFrame);
            }
            frameBefore = currentFrame;

            // iterate inlining (caller) chain in deoptimized physical frame and associated compiler
            // infopoint
            compiledFrame = compiledFrame.getCaller();
            associatedCompiledCodePosition = associatedCompiledCodePosition.getCaller();
        }

        /*
         * we need to fill some data so logging and stack walking code knows what to do with the
         * frame, the interpreter frames are handled in size by the interpreter logic, we need to
         * patch the original return address
         */
        CodePointer retAdr = FrameAccess.singleton().readReturnAddress(CurrentIsolate.getCurrentThread(), deoptimizer.getDeoptState().getSourceSp());
        frameBefore.setReturnAddress(new DeoptimizedFrame.ReturnAddress(returnAddressSize(), retAdr.rawValue()));

        long frameSize = physicalFrame.getTotalFrameSize();
        InterpreterResolvedJavaMethod bottomMethod = frameBefore.getMethod();
        JavaKind bottomReturnKind = bottomMethod.getSignature().getReturnKind();
        RistrettoDeoptimizedInterpreterFrame deoptimizedInterpreterFrame = new RistrettoDeoptimizedInterpreterFrame(frameSize, frameBefore, rCode, pc, pin);

        deoptimizedInterpreterFrame.setInterpreterEntry(getInterpreterEntry(bottomReturnKind));
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            logger().string("[buf/deopt] returning from buildInterpreterFrame").newline();
        }
        return deoptimizedInterpreterFrame;
    }

    private static boolean verifyInfopointAndStackWalk(FrameInfoQueryResult virtualFrameInfo, BytecodePosition byteCodeStack, Infopoint infopoint) {
        BytecodePosition currentFrame = byteCodeStack;
        FrameInfoQueryResult virtualFrame = virtualFrameInfo;
        while (virtualFrame != null) {
            int virtualFrameBCI = virtualFrame.getBci();
            int compilerInfopointBCI = currentFrame.getBCI();
            if (virtualFrameBCI != compilerInfopointBCI) {
                throw VMError.shouldNotReachHere("VirtualFrameInfo decoded from installed Ristretto code does not match the compiler infopoint." + "\nCompiler infopoint frame position was " +
                                currentFrame + "\nwhile decoded virtual frame had position " + virtualFrameBCI + "\nAnd the infopoint was " + infopoint);
            }
            currentFrame = currentFrame.getCaller();
            virtualFrame = virtualFrame.getCaller();
        }
        return true;
    }

    /**
     * Reconstructs a Crema interpreter frame from the matching compiled frame.
     */
    private static InterpreterFrame createInterpreterFrameFromCompiledFrame(InterpreterResolvedJavaMethod interpreterMethod, FrameInfoQueryResult compiledFrame, Deoptimizer deoptimizer) {
        /*
         * Reuse the deoptimizer state so materialized virtual objects stay shared across
         * reconstructed inlined frames and reserved-thread register reads resolve against the deopt
         * target thread.
         */
        DeoptState deoptState = deoptimizer.getDeoptState();

        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            logger().string("[buf/deopt] interpreterMethod.getMaxLocals=").signed(interpreterMethod.getMaxLocals()).newline();
            logger().string("[buf/deopt] interpreterMethod.getMaxStackSize=").signed(interpreterMethod.getMaxStackSize()).newline();
            logger().string("[buf/deopt] compiledFrame.getNumLocals=").signed(compiledFrame.getNumLocals()).newline();
            logger().string("[buf/deopt] compiledFrame.getNumLocks=").signed(compiledFrame.getNumLocks()).newline();
            logger().string("[buf/deopt] compiledFrame.getNumStack=").signed(compiledFrame.getNumStack()).newline();
        }

        VMError.guarantee(interpreterMethod.getMaxLocals() == compiledFrame.getNumLocals());
        Object[] relockedMonitorObjects = relockInterpreterObjects(compiledFrame, deoptState);
        InterpreterFrame interpreterFrame = EspressoFrame.allocate(interpreterMethod.getMaxLocals(), interpreterMethod.getMaxStackSize());

        final int numLocals = compiledFrame.getNumLocals();
        final int numStack = compiledFrame.getNumStack();

        int slotIdx = 0;
        for (int localIdx = 0; localIdx < numLocals; localIdx++) {
            JavaConstant value = deoptState.readLocalVariable(localIdx, compiledFrame);
            if (value.getJavaKind().equals(JavaKind.Illegal)) {
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    logger().string("[buf/deopt] slot=").signed(localIdx).string(" is illegal").newline();
                }
                continue;
            }
            switch (value.getJavaKind().getStackKind()) {
                case Int -> EspressoFrame.setLocalInt(interpreterFrame, localIdx, value.asInt());
                case Long -> EspressoFrame.setLocalLong(interpreterFrame, localIdx, value.asLong());
                case Float -> EspressoFrame.setLocalFloat(interpreterFrame, localIdx, value.asFloat());
                case Double -> EspressoFrame.setLocalDouble(interpreterFrame, localIdx, value.asDouble());
                case Object -> EspressoFrame.setLocalObject(interpreterFrame, localIdx, SubstrateObjectConstant.asObject(value));
                default -> VMError.shouldNotReachHere("createInterpreterFrameFromCompiledFrame: kind not implemented yet: " + value.getJavaKind());
            }
        }
        slotIdx += numLocals;

        for (int stackIdx = 0; stackIdx < numStack; stackIdx++) {
            int valIndex = slotIdx + stackIdx;
            // in crema locals and expression stack are in the same array, stack[0]<==>
            // array[maxLocals]
            int tos = interpreterMethod.getMaxLocals() + stackIdx;
            JavaConstant value = deoptState.readValue(valIndex, compiledFrame);
            if (value.getJavaKind().equals(JavaKind.Illegal)) {
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    logger().string("[buf/deopt] slot=").signed(valIndex).string(" is illegal").newline();
                }
                continue;
            }
            switch (value.getJavaKind().getStackKind()) {
                case Int -> EspressoFrame.putInt(interpreterFrame, tos, value.asInt());
                case Long -> EspressoFrame.putLong(interpreterFrame, tos, value.asLong());
                case Float -> EspressoFrame.putFloat(interpreterFrame, tos, value.asFloat());
                case Double -> EspressoFrame.putDouble(interpreterFrame, tos, value.asDouble());
                case Object -> EspressoFrame.putObject(interpreterFrame, tos, SubstrateObjectConstant.asObject(value));
                default -> VMError.shouldNotReachHere("createInterpreterFrameFromCompiledFrame: kind not implemented yet: " + value.getJavaKind());
            }
        }
        if (relockedMonitorObjects != null) {
            for (int lockIdx = 0; lockIdx < relockedMonitorObjects.length; lockIdx++) {
                Object lockObject = relockedMonitorObjects[lockIdx];
                if (lockObject == null) {
                    if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                        int lockSlotIndex = numLocals + numStack + lockIdx;
                        logger().string("[buf/deopt] slot=").signed(lockSlotIndex).string(" is illegal").newline();
                    }
                    continue;
                }
                InterpreterToVM.registerHeldMonitor(interpreterFrame, lockObject);
            }
        }

        return interpreterFrame;
    }

    /**
     * Acquires all monitors whose synchronization state is not reflected in the deoptimized frame.
     * During optimization, objects may be virtualized and later materialized again, and monitor
     * state may also be elided for objects that were never virtualized. When deoptimization
     * reconstructs execution, that missing monitor state must be replayed by relocking.
     */
    private static Object[] relockInterpreterObjects(FrameInfoQueryResult sourceFrame, DeoptState deoptState) {
        int numLocks = sourceFrame.getNumLocks();
        if (numLocks == 0) {
            return null;
        }

        int slotIdx = sourceFrame.getNumLocals() + sourceFrame.getNumStack();
        Object[] heldMonitorObjects = null;
        for (int lockIdx = 0; lockIdx < numLocks; lockIdx++) {
            int lockSlotIdx = slotIdx + lockIdx;
            JavaConstant value = deoptState.readValue(lockSlotIdx, sourceFrame);
            if (value.getJavaKind().equals(JavaKind.Illegal)) {
                continue;
            }

            Object lockObject = SubstrateObjectConstant.asObject(value);
            if (sourceFrame.getValueInfos()[lockSlotIdx].isEliminatedMonitor()) {
                /*
                 * Only eliminated monitors need an explicit relock here. Live synchronized
                 * method/block locks are still owned at the deopt point and are registered below so
                 * the interpreter can release them without double-counting the acquisition.
                 */
                DeoptimizedFrame.RelockObjectData relockObjectData = createRelockObjectData(value, sourceFrame);
                MonitorSupport.singleton().doRelockObject(relockObjectData.getObject(), relockObjectData.getLockData());
            }
            if (heldMonitorObjects == null) {
                heldMonitorObjects = new Object[numLocks];
            }
            heldMonitorObjects[lockIdx] = lockObject;
        }
        return heldMonitorObjects;
    }
}
