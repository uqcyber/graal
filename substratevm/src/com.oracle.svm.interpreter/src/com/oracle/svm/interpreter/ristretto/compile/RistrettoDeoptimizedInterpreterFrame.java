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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

/**
 * Deoptimized-frame implementation for transitions from Ristretto compiled code back to the Crema
 * interpreter.
 *
 * <p>
 * Normal returns are replayed through the reconstructed interpreter frame chain, so this frame only
 * needs to carry the source-frame metadata plus an optional pending exception payload across the
 * handoff into {@link InterpreterDeoptEntryPoints}.
 */
public class RistrettoDeoptimizedInterpreterFrame extends DeoptimizedFrame {
    private final long frameSize;
    private final PinnedObject pin;
    private final RistrettoVirtualInterpreterFrame bottomFrame;
    private final RistrettoInstalledCode rCode;

    /* Carries the pending exception object across the deopt handoff. */
    private Object pendingExceptionObject;

    private CodePointer interpEntryPoint;
    private boolean hasPendingException = false;
    private final CodePointer sourcePC;
    private final char[] completedMessage;

    @SuppressWarnings("this-escape")
    public RistrettoDeoptimizedInterpreterFrame(long frameSize, RistrettoVirtualInterpreterFrame bottomFrame, RistrettoInstalledCode rCode, CodePointer sourcePC, boolean pinFrame) {
        this.frameSize = frameSize;
        this.pin = pinFrame ? PinnedObject.create(this) : null;
        this.bottomFrame = bottomFrame;
        this.rCode = rCode;
        this.sourcePC = sourcePC;
        StringBuilderLog sbl = new StringBuilderLog();
        sbl.string("deoptStub: completed ").string(pinFrame ? "eagerly" : "lazily").string(" for DeoptimizedFrame at ").hex(Word.objectToUntrackedPointer(this)).newline();
        this.completedMessage = sbl.getResult().toCharArray();
    }

    @Override
    public SubstrateInstalledCode getSourceInstalledCode() {
        return rCode;
    }

    /**
     * Returns the {@link PinnedObject} that ensures that this {@link DeoptimizedFrame} is not moved
     * by the GC. The {@link DeoptimizedFrame} is accessed during GC when walking the stack.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public PinnedObject getPin() {
        return pin;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public VirtualFrame getTopFrame() {
        RistrettoVirtualInterpreterFrame top = bottomFrame;
        while (top.hasCallee()) {
            top = top.getCallee();
        }
        return top;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public DeoptTargetTier getTargetTier() {
        return DeoptTargetTier.Interpreter;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public long getSourceEncodedFrameSize() {
        /* For interpreter deopt frames, encoded and total frame sizes are equivalent. */
        return frameSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public long getSourceTotalFrameSize() {
        return frameSize;
    }

    @Override
    public CodePointer getSourcePC() {
        return sourcePC;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    public void takeException() {
        assert !hasPendingException;
        assert pendingExceptionObject == null;
        hasPendingException = true;
    }

    public boolean hasPendingException() {
        return hasPendingException;
    }

    public RistrettoVirtualInterpreterFrame getBottomFrame() {
        return bottomFrame;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public CodePointer getInterpreterEntry() {
        return interpEntryPoint;
    }

    public void setInterpreterEntry(CodePointer p) {
        this.interpEntryPoint = p;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setPendingExceptionObject(Object pendingExceptionObject) {
        assert hasPendingException;
        this.pendingExceptionObject = pendingExceptionObject;
    }

    public Object getPendingExceptionObject() {
        assert hasPendingException : "Must have a pending exception";
        assert pendingExceptionObject != null : "Pending exception must not be null";
        return pendingExceptionObject;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @Override
    protected char[] getCompletedMessage() {
        return completedMessage;
    }

    /**
     * Continues the active deoptimization by tail-jumping into the typed interpreter entry stub for
     * this reconstructed frame chain.
     */
    @Uninterruptible(reason = "Custom deopt-stub epilogue rewrites the active stack frame.")
    public UnsignedWord continueInterpreterDeoptimization(Pointer originalStackPointer, UnsignedWord gpResult, boolean hasException) {
        IsolateThread targetThread = CurrentIsolate.getCurrentThread();

        /* Stack pointer of deoptee's caller after the source frame is removed. */
        Pointer revertSp = originalStackPointer.add(WordFactory.unsigned(getSourceTotalFrameSize()));
        JavaFrameAnchors.verifyTopFrameAnchor(revertSp);
        CodePointer returnAddressOfDeoptedMethod = FrameAccess.singleton().readReturnAddress(targetThread, revertSp);
        Pointer basePointerOfDeoptedMethod = revertSp.readWord(-(FrameAccess.returnAddressSize() + Deoptimizer.savedBasePointerSize()));

        if (hasException) {
            assert hasPendingException;
            setPendingExceptionObject(Deoptimizer.isNonNullObjectValue(gpResult) ? ((Pointer) gpResult).toObject() : null);
        } else {
            assert !hasPendingException;
            /*
             * Normal returns are replayed from the recorded invoke/return boundary in the
             * interpreter frame chain, so the caller frame injects the value into its operand stack
             * instead of reading raw compiled return registers from this object.
             */
        }
        if (pin != null) {
            /*
             * After this point the frame stays strongly reachable through the Java argument passed
             * into the deopt-entry stub and the typed interpreter entry method that it tail-jumps
             * to.
             */
            pin.close();
        }
        InterpreterDeoptEntryPoints.jumpToInterpreterEntryPoint(this, revertSp, getInterpreterEntry(), returnAddressOfDeoptedMethod, basePointerOfDeoptedMethod);
        throw VMError.shouldNotReachHere("At this point, this frame should not be on the stack anymore");
    }

    @Override
    public void logTraceDeoptMessage(Log log, FrameInfoQueryResult sourceTopFrame, boolean printOnlyTopFrames) {

        FrameInfoQueryResult virtualSourceFrame = sourceTopFrame;
        RistrettoVirtualInterpreterFrame targetFrame = (RistrettoVirtualInterpreterFrame) getTopFrame();

        while (virtualSourceFrame != null) {
            log.string("        at ");
            log.string("[Interpreter]Method ").string(targetFrame.getMethod().format("%H.%n(%p)"));
            log.string(" bci ");
            FrameInfoDecoder.logReadableBci(log, virtualSourceFrame.getEncodedBci());

            log.string("  return address ");
            var ret = targetFrame.getReturnAddress();
            if (ret == null) {
                log.string("null <==> not bottom frame").newline();
            } else {
                log.zhex(ret.getReturnAddress()).newline();
            }

            if (Deoptimizer.Options.TraceDeoptimizationDetails.getValue()) {
                Deoptimizer.printVirtualFrame(log, targetFrame);
            }

            virtualSourceFrame = virtualSourceFrame.getCaller();
            targetFrame = (RistrettoVirtualInterpreterFrame) targetFrame.getCaller();
        }
    }
}
