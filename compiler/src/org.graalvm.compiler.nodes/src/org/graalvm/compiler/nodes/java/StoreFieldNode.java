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
package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.interpreter.value.InterpreterValue;
import org.graalvm.compiler.interpreter.value.InterpreterValueObject;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.InterpreterState;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * The {@code StoreFieldNode} represents a write to a static or instance field.
 */
@NodeInfo(nameTemplate = "StoreField#{p#field/s}")
public final class StoreFieldNode extends AccessFieldNode implements StateSplit, Virtualizable, Canonicalizable {
    public static final NodeClass<StoreFieldNode> TYPE = NodeClass.create(StoreFieldNode.class);

    @Input ValueNode value;
    @OptionalInput(InputType.State) FrameState stateAfter;

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value) {
        this(object, field, value, field.isVolatile());
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value, boolean volatileAccess) {
        super(TYPE, StampFactory.forVoid(), object, field, volatileAccess);
        this.value = value;
    }

    public StoreFieldNode(ValueNode object, ResolvedJavaField field, ValueNode value, FrameState stateAfter, boolean volatileAccess) {
        super(TYPE, StampFactory.forVoid(), object, field, volatileAccess);
        this.value = value;
        this.stateAfter = stateAfter;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualInstanceNode virtual = (VirtualInstanceNode) alias;
            int fieldIndex = virtual.fieldIndex(field());
            if (fieldIndex != -1) {
                tool.setVirtualEntry(virtual, fieldIndex, value());
                tool.delete();
            }
        }
    }

    public FrameState getState() {
        return stateAfter;
    }

    @Override
    public NodeCycles estimatedNodeCycles() {
        if (isVolatile()) {
            return CYCLES_8;
        }
        return super.estimatedNodeCycles();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!field.isStatic() && object.isNullConstant()) {
            return new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
        }
        return this;
    }

    @Override
    public FixedNode interpret(InterpreterState interpreter) {
        InterpreterValue val = interpreter.interpretExpr(value());

        if (isStatic()) {
            interpreter.storeStaticFieldValue(field(), val);
        } else {
            InterpreterValue objectVal = interpreter.interpretExpr(object());
            GraalError.guarantee(objectVal instanceof InterpreterValueObject, "StoreFieldNode input doesn't interpret to object");
            GraalError.guarantee(((InterpreterValueObject) objectVal).hasField(field()), "StoreFieldNode field doesn't exist on object");

            ((InterpreterValueObject) objectVal).setFieldValue(field(), val);
        }
        return next();
    }
}
