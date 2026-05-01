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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.interpreter.EspressoFrame;
import com.oracle.svm.interpreter.Interpreter;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;

/**
 * Typed entry points that resume execution in the Crema interpreter after deoptimization of a
 * Ristretto frame has already been committed and the stub epilogue has restored the caller-visible
 * stack shape.
 *
 * <p>
 * The eager versus lazy split happens before control reaches this class. With eager deoptimization,
 * a {@link RistrettoDeoptimizedInterpreterFrame} is constructed and pinned ahead of time, installed
 * at {@code SP[0]} as the active {@link DeoptimizedFrame}, and then consumed by the eager deopt
 * stub. With lazy deoptimization, {@code SP[0]} still holds the original return address while the
 * lazy stub constructs that same interpreter-target frame immediately before the handoff. In both
 * cases, the stub eventually restores {@code revertSp}, reinstalls the original return edge, and
 * tail-jumps into one of the typed methods below with the prepared frame passed as a Java argument.
 *
 * <p>
 * By the time one of the typed methods starts, {@code SP[0]} from the deoptimized compiled frame is
 * no longer consulted. Stack walkers instead follow the restored AOT caller frames while the
 * {@link RistrettoDeoptimizedInterpreterFrame} stays strongly reachable through the Java argument
 * passed down the stub and entry-method call chain.
 */
public class InterpreterDeoptEntryPoints {

    @Fold
    public static Log logger() {
        return Log.log();
    }

    /**
     * Tail-jumps from the custom deopt stub into the typed interpreter entry point after restoring
     * the caller stack shape.
     *
     * <p>
     * The backend-specific epilogue restores {@code revertSp}, optionally restores the caller base
     * pointer, reinstalls {@code oldReturnAddress}, and then jumps to {@code interpEntryPoint}.
     */
    @Deoptimizer.DeoptStub(stubType = Deoptimizer.StubType.InterpreterDeoptEntryPointStub)
    @Uninterruptible(reason = "Custom deopt-stub epilogue rewrites the active stack frame.")
    @NeverInline("custom prologue and epilogue")
    @SuppressWarnings("unused")
    public static void jumpToInterpreterEntryPoint(RistrettoDeoptimizedInterpreterFrame frame, Pointer revertSp, CodePointer interpEntryPoint, CodePointer oldReturnAddress,
                    Pointer oldBasePointer) {
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException uncheckedThrow(Throwable e) throws T {
        throw (T) e;
    }

    @NeverInline("Trace logging is slow-path code.")
    private static void logCurrentCallerFrame(String logPrefix, Pointer baseSp, CodePointer ra) {
        logger().string(logPrefix).string(" current sp=         ").hex(baseSp).newline();
        logger().string(logPrefix).string(" current return-addr=").hex(ra).newline();
    }

    private static void traceCurrentCallerFrame(String logPrefix, Pointer baseSp) {
        IsolateThread targetThread = CurrentIsolate.getCurrentThread();
        CodePointer ra = FrameAccess.singleton().readReturnAddress(targetThread, baseSp);
        logCurrentCallerFrame(logPrefix, baseSp, ra);
    }

    private static Object executeEntry(RistrettoDeoptimizedInterpreterFrame deoptFrame, String tracePrefix) throws Throwable {
        assert deoptFrame != null;
        RistrettoVirtualInterpreterFrame bottomFrame = deoptFrame.getBottomFrame();
        VMError.guarantee(bottomFrame != null, "Deoptimized interpreter frame must keep a bottom frame");

        final boolean hasPendingException = deoptFrame.hasPendingException();
        Object pendingExceptionObject = null;
        if (hasPendingException) {
            pendingExceptionObject = deoptFrame.getPendingExceptionObject();
        }

        try {
            Object returnValue = executeInterpreterFrames(bottomFrame, pendingExceptionObject, hasPendingException);
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                logger().string(tracePrefix).string(" leaving").newline();
            }
            return returnValue;
        } catch (Throwable ex) {
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                logger().string(tracePrefix).string(" caught exception with class ").string(ex.getClass().getName()).string(" will rethrow now").newline();
            }
            throw uncheckedThrow(ex);
        }
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static void entryVoid(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryVoid]", baseSp);
        }
        executeEntry(deoptFrame, "[buf/entryVoid]");
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static int entryInt(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryInt]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryInt]");
        assert returnValue instanceof Integer;
        return (int) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static long entryLong(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryLong]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryLong]");
        assert returnValue instanceof Long;
        return (long) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static float entryFloat(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryFloat]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryFloat]");
        assert returnValue instanceof Float;
        return (float) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static double entryDouble(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryDouble]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryDouble]");
        assert returnValue instanceof Double;
        return (double) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static boolean entryBoolean(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryBoolean]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryBoolean]");
        assert returnValue instanceof Boolean;
        return (boolean) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static byte entryByte(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryByte]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryByte]");
        assert returnValue instanceof Byte;
        return (byte) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static short entryShort(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryShort]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryShort]");
        assert returnValue instanceof Short;
        return (short) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static char entryChar(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryChar]", baseSp);
        }
        Object returnValue = executeEntry(deoptFrame, "[buf/entryChar]");
        assert returnValue instanceof Character;
        return (char) returnValue;
    }

    @NeverInline("entry point for deopt to interpreter, access to stack pointer")
    public static Object entryObject(RistrettoDeoptimizedInterpreterFrame deoptFrame) throws Throwable {
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            Pointer baseSp = KnownIntrinsics.readCallerStackPointer();
            traceCurrentCallerFrame("[buf/entryObject]", baseSp);
        }
        return executeEntry(deoptFrame, "[buf/entryObject]");
    }

    /**
     * Recreates execution through the deoptimized interpreter-frame chain.
     *
     * This method walks the frame chain from callee to caller, executes each frame in interpreter
     * mode, propagates pending exceptions, and injects callee return values back into caller
     * frames.
     */
    public static Object executeInterpreterFrames(RistrettoVirtualInterpreterFrame current, Object pendingExceptionObject, boolean hasPendingException)
                    throws Throwable {
        Object returnValue = null;
        Throwable pendingException = null;
        boolean inject = false;

        if (hasPendingException) {
            VMError.guarantee(pendingExceptionObject instanceof Throwable, "Pending exception payload must be a Throwable");
            pendingException = (Throwable) pendingExceptionObject;
        }

        if (current.hasCallee()) {
            try {
                returnValue = executeInterpreterFrames(current.getCallee(), pendingExceptionObject, hasPendingException);
                /* if we return properly, the pending exception got handled */
                pendingException = null;
                inject = true;
            } catch (Throwable e) {
                /*
                 * otherwise we have to propagate it to the next frame and let the interpreter look
                 * for a handler
                 */
                if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                    Log.log().string("[InterpreterDeoptEntryPoints] Caught exception with message=").string(e.getMessage()).string(" and class=")
                                    .string(e.getClass().getName()).string(" when executing ").string(current.getMethod().getName()).string(" will set pending interpreter exception now")
                                    .newline();
                }

                pendingException = e;
            }
        }

        // in crema locals and expression stack are in the same array, stack[0]<==> array[maxLocals]
        int startTop = EspressoFrame.startingStackOffset(current.getMethod().getMaxLocals()) + current.getNumStack();
        int targetBci = current.getTargetBci();
        if (pendingException != null) {
            if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
                Log.log().string("[InterpreterDeoptEntryPoints] Handling pending exception with message=").string(pendingException.getMessage()).string(" and class=")
                                .string(pendingException.getClass().getName()).string(" when deopting frame portion ").string(current.getMethod().getName()).newline();
            }
            ExceptionHandler handler = Interpreter.resolveExceptionHandler(current.getMethod(),
                            current.getCurrentBci(), pendingException);
            if (handler == null) {
                /* unwind this frame */
                throw pendingException;
            } else {
                Interpreter.clearOperandStack(current.getFrame(), current.getMethod(), startTop);
                startTop = EspressoFrame.startingStackOffset(current.getMethod().getMaxLocals());
                EspressoFrame.putObject(current.getFrame(), startTop, pendingException);
                startTop++;
                targetBci = Interpreter.beforeJumpChecks(current.getFrame(), targetBci, handler.getHandlerBCI(),
                                startTop);
            }
        } else if (inject) {
            startTop += injectReturnValue(current, returnValue);
        }

        return Interpreter.execute(current.getMethod(), current.getFrame(), targetBci, startTop);
    }

    private static void logInjectedReturnValue(JavaKind returnKind, Object returnValue, int slot) {
        if (!Deoptimizer.Options.TraceDeoptimization.getValue()) {
            return;
        }
        logger().string("[interpEntry] Writing returnValue=");
        switch (returnKind) {
            case Long -> logger().hex((Long) returnValue);
            case Float -> logger().hex(Float.floatToRawIntBits((Float) returnValue));
            case Double -> logger().hex(Double.doubleToRawLongBits((Double) returnValue));
            case Boolean -> logger().hex(((Boolean) returnValue) ? 1 : 0);
            case Byte -> logger().hex(((Byte) returnValue).byteValue());
            case Short -> logger().hex(((Short) returnValue).shortValue());
            case Char -> logger().hex(((Character) returnValue).charValue());
            case Int -> logger().hex(((Integer) returnValue).intValue());
            default -> logger().string(String.valueOf(returnValue));
        }
        logger().string(" at slot=").signed(slot).newline();
    }

    /**
     * Injects a callee return value into the caller frame and returns how many stack slots were
     * injected (0 for void, 1 for single-slot kinds, 2 for long/double).
     */
    private static int injectReturnValue(RistrettoVirtualInterpreterFrame chainedFrame, Object returnValue) {
        InterpreterFrame interpreterFrame = chainedFrame.getFrame();
        InterpreterResolvedJavaMethod interpreterMethod = chainedFrame.getMethod();

        byte[] bytecode = interpreterMethod.getCode();
        int callsiteBci = chainedFrame.getCurrentBci();
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            logger().string("caller method: ").string(interpreterMethod.toString()).newline();
        }

        int opcode = BytecodeStream.opcode(bytecode, callsiteBci);
        VMError.guarantee(opcode == Bytecodes.INVOKEVIRTUAL ||
                        opcode == Bytecodes.INVOKESPECIAL ||
                        opcode == Bytecodes.INVOKESTATIC ||
                        opcode == Bytecodes.INVOKEINTERFACE, "entrypoint: unsupported invoke opcode");
        char cpi = BytecodeStream.readCPI2(bytecode, callsiteBci);
        InterpreterResolvedJavaMethod calleeMethod = Interpreter.resolveMethod(interpreterMethod, opcode, cpi);
        if (Deoptimizer.Options.TraceDeoptimization.getValue()) {
            logger().string("callee method: ").string(calleeMethod.toString()).newline();
        }

        JavaKind returnKind = calleeMethod.getSignature().getReturnKind().getStackKind();
        int calleeReturnValueSlot = EspressoFrame.startingStackOffset(interpreterMethod.getMaxLocals()) + chainedFrame.getNumStack();
        switch (returnKind) {
            case Void:
                /* nothing to do */
                return 0;
            case Int:
                assert returnValue instanceof Integer;
                int calleeIntReturnValue = (Integer) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putInt(interpreterFrame, calleeReturnValueSlot, calleeIntReturnValue);
                return 1;
            case Boolean:
                assert returnValue instanceof Boolean;
                int calleeBooleanReturnValue = ((Boolean) returnValue) ? 1 : 0;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putInt(interpreterFrame, calleeReturnValueSlot, calleeBooleanReturnValue);
                return 1;
            case Byte:
                assert returnValue instanceof Byte;
                int calleeByteReturnValue = (Byte) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putInt(interpreterFrame, calleeReturnValueSlot, calleeByteReturnValue);
                return 1;
            case Short:
                assert returnValue instanceof Short;
                int calleeShortReturnValue = (Short) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putInt(interpreterFrame, calleeReturnValueSlot, calleeShortReturnValue);
                return 1;
            case Char:
                assert returnValue instanceof Character;
                int calleeCharReturnValue = (Character) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putInt(interpreterFrame, calleeReturnValueSlot, calleeCharReturnValue);
                return 1;
            case Long:
                assert returnValue instanceof Long;
                long calleeLongReturnValue = (Long) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putLong(interpreterFrame, calleeReturnValueSlot, calleeLongReturnValue);
                return 2;
            case Float:
                assert returnValue instanceof Float;
                float calleeFloatReturnValue = (Float) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putFloat(interpreterFrame, calleeReturnValueSlot, calleeFloatReturnValue);
                return 1;
            case Double:
                assert returnValue instanceof Double;
                double calleeDoubleReturnValue = (Double) returnValue;
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putDouble(interpreterFrame, calleeReturnValueSlot, calleeDoubleReturnValue);
                return 2;
            case Object:
                logInjectedReturnValue(returnKind, returnValue, calleeReturnValueSlot);
                EspressoFrame.putObject(interpreterFrame, calleeReturnValueSlot, returnValue);
                return 1;
            default:
                throw VMError.shouldNotReachHere("entrypoint: unsupported returnKind: " + returnKind);
        }
    }

}
