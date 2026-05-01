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

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.shared.Uninterruptible;

public final class RistrettoVirtualInterpreterFrame extends VirtualFrame {
    private final InterpreterFrame frame;
    private final InterpreterResolvedJavaMethod method;
    private final int currentBci;
    private final int targetBci;
    private final int numStack;
    private final RistrettoVirtualInterpreterFrame callee;
    private RistrettoVirtualInterpreterFrame caller;

    RistrettoVirtualInterpreterFrame(FrameInfoQueryResult frameInfo, InterpreterFrame frame, InterpreterResolvedJavaMethod method,
                    int currentBci, int targetBci, int numStack, RistrettoVirtualInterpreterFrame callee) {
        super(frameInfo);
        this.frame = frame;
        this.method = method;
        this.currentBci = currentBci;
        this.targetBci = targetBci;
        this.callee = callee;
        this.numStack = numStack;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    boolean hasCallee() {
        return callee != null;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    RistrettoVirtualInterpreterFrame getCallee() {
        return callee;
    }

    public InterpreterFrame getFrame() {
        return frame;
    }

    public InterpreterResolvedJavaMethod getMethod() {
        return method;
    }

    public int getCurrentBci() {
        return currentBci;
    }

    public int getTargetBci() {
        return targetBci;
    }

    public void setCaller(RistrettoVirtualInterpreterFrame caller) {
        assert this.caller == null;
        this.caller = caller;
    }

    @Override
    public void setCaller(VirtualFrame caller) {
        assert caller instanceof RistrettoVirtualInterpreterFrame;
        setCaller((RistrettoVirtualInterpreterFrame) caller);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public VirtualFrame getCaller() {
        return this.caller;
    }

    public int getNumStack() {
        return numStack;
    }
}
