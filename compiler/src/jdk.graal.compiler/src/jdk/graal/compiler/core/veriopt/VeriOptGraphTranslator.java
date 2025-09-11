/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.veriopt;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FixedBinaryNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.OpaqueNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.FinalFieldBarrierNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Defines how each IR node should be translated into Isabelle node syntax.
 */
public class VeriOptGraphTranslator {
    private static String irNodes = null;

    // Stores the names of the classes to be included in the Isabelle JVMClass definition.
    private static Set<String> classesToEncode = new HashSet<>();

    // Stores the methods belonging to classes being encoded.
    private static Set<ResolvedJavaMethod> classMethods = new HashSet<>();

    private static HashSet<String> binaryNodes;
    static {
        binaryNodes = new HashSet<>();
        // add just the binary nodes that we currently handle, with x,y fields only.
        binaryNodes.add("AddNode");
        binaryNodes.add("AndNode");
        binaryNodes.add("BinaryMathIntrinsicNode");
        binaryNodes.add("FloatEqualsNode");
        binaryNodes.add("FloatLessThanNode");
        binaryNodes.add("FloatNormalizeCompareNode");
        binaryNodes.add("IntegerBelowNode");
        binaryNodes.add("IntegerEqualsNode");
        binaryNodes.add("IntegerLessThanNode");
        binaryNodes.add("IntegerMulHighNode");
        binaryNodes.add("IntegerNormalizeCompareNode");
        binaryNodes.add("IntegerTestNode");
        binaryNodes.add("LeftShiftNode");
        binaryNodes.add("MulNode");
        binaryNodes.add("ObjectEqualsNode");
        binaryNodes.add("OrNode");
        binaryNodes.add("RightShiftNode");
        binaryNodes.add("ShortCircuitOrNode");
        binaryNodes.add("SubNode");
        binaryNodes.add("UnsignedMulHighNode");
        binaryNodes.add("UnsignedRightShiftNode");
        binaryNodes.add("XorNode");
    }

    private static HashSet<String> unaryNodes;
    static {
        unaryNodes = new HashSet<>();
        // add just the unary nodes that we currently handle, with value fields only.
        unaryNodes.add("AbsNode");
        unaryNodes.add("BitCountNode");
        unaryNodes.add("FloatConvertNode");
        unaryNodes.add("HotSpotCompressionNode");
        unaryNodes.add("NarrowNode");
        unaryNodes.add("NegateNode");
        unaryNodes.add("NotNode");
        unaryNodes.add("SignExtendNode");
        unaryNodes.add("SqrtNode");
        unaryNodes.add("ReverseBytesNode");
        unaryNodes.add("UnaryMathIntrinsicNode");
        unaryNodes.add("ZeroExtendNode");
    }

    /**
     * These nodes will be dynamically generated at runtime with VeriOptDynamicNodeTranslator.
     */
    private static HashSet<String> dynamicNodes;
    static {
        dynamicNodes = new HashSet<>();
        // add just the nodes that we currently handle
        dynamicNodes.add("ArrayCopyNode");
        dynamicNodes.add("AssertionNode");
        dynamicNodes.add("WriteNode");
    }

    private static HashSet<Class<? extends Node>> nodesGeneratedCodeFor = new HashSet<>();

    /**
     * Generate code that could be used to translate a node.
     *
     * @param node The node to generate translation code for
     */
    private static void generateCode(Node node) {
        if (nodesGeneratedCodeFor.add(node.getClass())) {
            System.out.printf("} else if (node instanceof %s) {\n", node.getClass().getSimpleName());
            System.out.printf("    %s n = (%s) node;\n", node.getClass().getSimpleName(), node.getClass().getSimpleName());
            System.out.print("    nodeDef(n");

            Class<?> clazz = node.getClass();
            while (Node.class.isAssignableFrom(clazz)) {
                for (Field field : clazz.getDeclaredFields()) {
                    // boolean isNode = Node.class.isAssignableFrom(field.getType());
                    boolean isNodeList = NodeIterable.class.isAssignableFrom(field.getType());
                    String method = null;
                    if (field.getAnnotation(Node.Input.class) != null || field.getAnnotation(Node.Successor.class) != null) {
                        if (isNodeList) {
                            method = "idList";
                        } else {
                            method = "id";
                        }
                    } else if (field.getAnnotation(Node.OptionalInput.class) != null) {
                        if (isNodeList) {
                            method = "optIdList";
                        } else {
                            method = "optId";
                        }
                    }
                    if (method != null) {
                        System.out.printf(", %s(n.%s())", method, field.getName());
                    }
                }

                clazz = clazz.getSuperclass();
            }

            System.out.println(");");
        }
    }

    private static boolean isInIrNodes(Node node) {
        if (irNodes == null) {
            // Load the IRNodes for the first time
            if (VeriOpt.IRNODES_FILES.isEmpty()) {
                // File not specified, leave empty
                irNodes = "";
            } else {
                try {
                    irNodes = new String(Files.readAllBytes(new File(VeriOpt.IRNODES_FILES).toPath()), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    // Not a file, leave empty
                    irNodes = "";
                }
            }
        }

        if (irNodes.isEmpty()) {
            // No IRNodes specified, skip this step
            return true;
        }

        String name = node.getClass().getSimpleName();

        // Simply check if the name is mentioned in the file (case-sensitive)
        return irNodes.contains(name);
    }

    /**
     * Returns the list of all methods & constructors which could be called (i.e., belong to classes being encoded).
     * */
    public static Set<ResolvedJavaMethod> getCallableMethods() {
        return classMethods;
    }

    /**
     * Clears the list of methods to generate additional IRGraphs for. Ensures that method calls from prior test runs
     * are not encoded in all subsequent tests.
     * */
    public static void clearCallableMethods() {
        classMethods.clear();
    }


    /**
     * Returns the list of classes which will be encoded into JVMClass format.
     * */
    public static Set<String> getClassesToEncode() {
        return classesToEncode;
    }

    /**
     * Clears the list of classes to encode in the Isabelle definition. Ensures that nodes from prior test runs are
     * not encoded in all subsequent tests.
     * */
    public static void clearClasses() {
        classesToEncode.clear();
    }

    /**
     * Returns the [node...] string.
     *
     * @param graph The graph to write
     */
    public static String writeNodeArray(Graph graph) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[");
        for (Node node : graph.getNodes()) {
            VeriOptNodeBuilder builder = new VeriOptNodeBuilder(node);
            if (!isInIrNodes(node)) {
                throw new IllegalArgumentException("node type " + node + " (" + node.getClass().getSimpleName() + ") is not in -Duq.irnodes=file.");
            } else if (node instanceof ArrayLengthNode) {
                ArrayLengthNode n = (ArrayLengthNode) node;
                builder.id(n.array()).id(n.next());
            } else if (node instanceof BeginNode) {
                BeginNode n = (BeginNode) node;
                builder.id(n.next());
            } else if (node instanceof BoxNode) {
                BoxNode n = (BoxNode) node;
                builder.id(n.getValue()).optIdAsNode(n.getLastLocationAccess()).id(n.next());
            } else if (node instanceof BytecodeExceptionNode) {
                BytecodeExceptionNode n = (BytecodeExceptionNode) node;
                builder.idList(n.getArguments()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof CallTargetNode) {
                CallTargetNode n = (CallTargetNode) node;
                builder.methodRef(n.targetMethod()).idList(n.arguments()).invokeKind(n.invokeKind());
            } else if (node instanceof ClassIsArrayNode) {
                ClassIsArrayNode n = (ClassIsArrayNode) node;
                builder.id(n.getValue());
            } else if (node instanceof ConditionalNode) {
                ConditionalNode n = (ConditionalNode) node;
                builder.id(n.condition()).id(n.trueValue()).id(n.falseValue());
            } else if (node instanceof ConstantNode) {
                ConstantNode n = (ConstantNode) node;
                Constant c = n.getValue();
                if (c instanceof PrimitiveConstant) {
                    builder.value(((PrimitiveConstant) c).asBoxedPrimitive());
                } else if (c instanceof JavaConstant && ((JavaConstant) c).isNull()) {
                    builder.value(null);
                } else {
                    // builder.arg("(constant type " + c + " (" + c.getClass().getName() + ") not
                    // implemented yet)");
                    throw new IllegalArgumentException("constant type " + c + " (" + c.getClass().getName() + ") not implemented yet.");
                }
            } else if (node instanceof ControlFlowAnchorNode) {
                ControlFlowAnchorNode n = (ControlFlowAnchorNode) node;
                builder.id(n.next());
            } else if (node instanceof DeoptimizeNode) {
                DeoptimizeNode n = (DeoptimizeNode) node;
                builder.optId(n.stateBefore());
            } else if (node instanceof DynamicNewArrayNode) {
                DynamicNewArrayNode n = (DynamicNewArrayNode) node;
                builder.id(n.getElementType()).id(n.length()).optId(n.getVoidClass()).optId(n.stateBefore()).id(n.next());
            } else if (node instanceof ExceptionObjectNode) {
                ExceptionObjectNode n = (ExceptionObjectNode) node;
                builder.optId(n.stateAfter()).id(n.next());
            } else if (node instanceof FinalFieldBarrierNode) {
                FinalFieldBarrierNode n = (FinalFieldBarrierNode) node;
                builder.optId(n.getValue()).id(n.next());
            } else if (node instanceof FixedGuardNode) {
                FixedGuardNode n = (FixedGuardNode) node;
                builder.id(n.condition()).optId(n.stateBefore()).id(n.next());
            } else if (node instanceof FrameState) {
                FrameState n = (FrameState) node;
                if (n.monitorIds() == null) {
                    builder.arg("[]");
                } else {
                    builder.idList(n.monitorIds());
                }
                builder.optId(n.outerFrameState()).optIdList(null).optIdList(n.virtualObjectMappings());
            } else if (node instanceof GetClassNode) {
                GetClassNode n = (GetClassNode) node;
                builder.id(n.getObject());
            } else if (node instanceof IfNode) {
                IfNode n = (IfNode) node;
                builder.id(n.condition()).id(n.trueSuccessor()).id(n.falseSuccessor());
            } else if (node instanceof InstanceOfNode) {
                InstanceOfNode n = (InstanceOfNode) node;
                builder.typeRef(n.getCheckedStamp().type()).id(n.getValue());
            } else if (node instanceof IntegerDivRemNode) {
                // SignedDivNode, SignedRemNode, UnsignedDivNode, UnsignedRemNode
                IntegerDivRemNode n = (IntegerDivRemNode) node;
                builder.id(n).id(n.getX()).id(n.getY()).optIdAsNode(n.getZeroGuard()).optId(n.stateBefore()).id(n.next());
            } else if (node instanceof IntegerSwitchNode) {
                IntegerSwitchNode n = (IntegerSwitchNode) node;
                builder.idList(n.successors()).id(n.value());
            } else if (node instanceof InvokeNode) {
                InvokeNode n = (InvokeNode) node;
                builder.id(n).id(n.callTarget()).optId(n.classInit()).optId(n.stateDuring()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode n = (InvokeWithExceptionNode) node;
                builder.id(n).id(n.callTarget()).optId(n.classInit()).optId(n.stateDuring()).optId(n.stateAfter()).id(n.next()).id(n.exceptionEdge());
            } else if (node instanceof IsNullNode) {
                IsNullNode n = (IsNullNode) node;
                builder.id(n.getValue());
            } else if (node instanceof LoadFieldNode) {
                LoadFieldNode n = (LoadFieldNode) node;
                builder.id(n).fieldRef(n.field()).optId(n.object()).id(n.next());
            } else if (node instanceof LoadIndexedNode) {
                LoadIndexedNode n = (LoadIndexedNode) node;
                builder.id(n.index()).optIdAsNode(n.getBoundsCheck()).id(n.array()).id(n.next());
            } else if (node instanceof LogicConstantNode) {
                LogicConstantNode n = (LogicConstantNode) node;
                builder.value(n.getValue());
            } else if (node instanceof LogicNegationNode) {
                LogicNegationNode n = (LogicNegationNode) node;
                builder.id(n.getValue());
            } else if (node instanceof LoopBeginNode) {
                LoopBeginNode n = (LoopBeginNode) node;
                ArrayList<Node> endNodes = new ArrayList<>();
                n.cfgPredecessors().forEach(endNodes::add);
                n.loopEnds().forEach(endNodes::add);
                builder.idList(endNodes).optIdAsNode(n.getOverflowGuard()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof LoopEndNode) {
                LoopEndNode n = (LoopEndNode) node;
                builder.id(n.loopBegin());
            } else if (node instanceof LoopExitNode) {
                LoopExitNode n = (LoopExitNode) node;
                builder.id(n.loopBegin()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof MergeNode) {
                MergeNode n = (MergeNode) node;
                builder.idList(n.cfgPredecessors()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof MembarNode) {
                MembarNode n = (MembarNode) node;
                builder.id(n.next());
            } else if (node instanceof MonitorEnterNode) {
                MonitorEnterNode n = (MonitorEnterNode) node;
                builder.optId(n.stateBefore()).id(n.object()).id(n.getMonitorId()).optId(n.getObjectData()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof MonitorExitNode) {
                MonitorExitNode n = (MonitorExitNode) node;
                builder.optId(n.stateBefore()).id(n.object()).id(n.getMonitorId()).optId(n.getObjectData()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof NewArrayNode) {
                NewArrayNode n = (NewArrayNode) node;
                builder.id(n.length()).optId(n.stateBefore()).id(n.next());
            } else if (node instanceof NewMultiArrayNode) {
                NewMultiArrayNode n = (NewMultiArrayNode) node;
                builder.id(n).typeRef(n.type()).idList(n.dimensions()).optId(n.stateBefore()).id(n.next());
            } else if (node instanceof NewInstanceNode) {
                NewInstanceNode n = (NewInstanceNode) node;
                builder.id(n).typeRef(n.instanceClass()).optId(n.stateBefore()).id(n.next());

                // Add the class to the list of classes to translate.
                classesToEncode.add(n.instanceClass().toClassName());

                // Add the classes' methods & constructors to the list of methods which could be called.
                classMethods.addAll(List.of(n.instanceClass().getDeclaredMethods()));
                classMethods.addAll(List.of(n.instanceClass().getDeclaredConstructors()));
            } else if (node instanceof OpaqueNode) {
                OpaqueNode n = (OpaqueNode) node;
                builder.id(n.getValue());
            } else if (node instanceof ParameterNode) {
                ParameterNode n = (ParameterNode) node;
                builder.nat(n.index());
            } else if (node instanceof PiNode) {
                PiNode n = (PiNode) node;
                builder.id(n.object()).optIdAsNode(n.getGuard());
            } else if (node instanceof RawLoadNode) {
                RawLoadNode n = (RawLoadNode) node;
                builder.id(n.object()).id(n.offset()).id(n.next());
            } else if (node instanceof RawStoreNode) {
                RawStoreNode n = (RawStoreNode) node;
                builder.id(n.value()).optId(n.stateAfter()).id(n.object()).id(n.offset()).id(n.next());
            } else if (node instanceof ReturnNode) {
                ReturnNode n = (ReturnNode) node;
                builder.optId(n.result()).optId(n.getMemoryMap());
            } else if (node instanceof StartNode) {
                StartNode n = (StartNode) node;
                if (n.next() == null) {
                    throw new IllegalArgumentException("StartNode.next is null. Has this graph been built?");
                }
                builder.optId(n.stateAfter()).id(n.next());
            } else if (node instanceof StateSplitProxyNode) {
                StateSplitProxyNode n = (StateSplitProxyNode) node;
                builder.optId(n.stateAfter()).optId(n.object()).id(n.next());
            } else if (node instanceof StoreFieldNode) {
                StoreFieldNode n = (StoreFieldNode) node;
                builder.id(n).fieldRef(n.field()).id(n.value()).optId(n.stateAfter()).optId(n.object()).id(n.next());
            } else if (node instanceof StoreIndexedNode) {
                StoreIndexedNode n = (StoreIndexedNode) node;
                builder.optIdAsNode(n.getStoreCheck()).id(n.value()).optId(n.stateAfter()).id(n.index()).optIdAsNode(n.getBoundsCheck()).id(n.array()).id(n.next());
            } else if (node instanceof UnboxNode) {
                UnboxNode n = (UnboxNode) node;
                builder.id(n.getValue()).optIdAsNode(n.getLastLocationAccess()).id(n.next());
            } else if (node instanceof UnsafeCompareAndSwapNode) {
                UnsafeCompareAndSwapNode n = (UnsafeCompareAndSwapNode) node;
                builder.id(n.object()).id(n.offset()).id(n.expected()).id(n.newValue()).optId(n.stateAfter()).id(n.next());
            } else if (node instanceof UnwindNode) {
                UnwindNode n = (UnwindNode) node;
                builder.id(n.exception());
            } else if (node instanceof ValuePhiNode) {
                ValuePhiNode n = (ValuePhiNode) node;
                builder.id(n).idList(n.values()).id(n.merge());
            } else if (node instanceof ValueProxyNode) {
                ValueProxyNode n = (ValueProxyNode) node;
                // WAS: builder.id(n.value()).id(n.proxyPoint());
                // we map these to RefNodes, to simplify reasoning.
                builder = new VeriOptNodeBuilder(node, "RefNode");
                builder.id(n.value());
            } else if (node instanceof BranchProbabilityNode) {
                // Skip, we don't need this node
                continue;
            } else if (node instanceof BinaryNode && binaryNodes.contains(node.getClass().getSimpleName())) {
                BinaryNode n = (BinaryNode) node;
                builder.id(n.getX()).id(n.getY());
            } else if (node instanceof BinaryOpLogicNode && binaryNodes.contains(node.getClass().getSimpleName())) {
                BinaryOpLogicNode n = (BinaryOpLogicNode) node;
                builder.id(n.getX()).id(n.getY());
            } else if (node instanceof FixedBinaryNode && binaryNodes.contains(node.getClass().getSimpleName())) {
                FixedBinaryNode n = (FixedBinaryNode) node;
                builder.id(n.getX()).id(n.getY());
            } else if (node instanceof UnaryNode && unaryNodes.contains(node.getClass().getSimpleName())) {
                if (node instanceof IntegerConvertNode) {
                    // SignExtendNode, NarrowNode, ZeroExtendNode
                    IntegerConvertNode<?> integerConvertNode = (IntegerConvertNode<?>) node;
                    builder.nat(integerConvertNode.getInputBits()).nat(integerConvertNode.getResultBits());
                }
                UnaryNode n = (UnaryNode) node;
                builder.id(n.getValue());
            } else if (VeriOpt.DYNAMICALLY_TRANSLATE_ALL_NODES || dynamicNodes.contains(node.getClass().getSimpleName())) {
                // Dynamically produce this node
                VeriOptDynamicNodeTranslator.generateNode(node, builder);
            } else if (!(node instanceof MonitorIdNode) && !(node instanceof EndNode)) {
                generateCode(node);  // why is this called?  It prints directly to System.out
                throw new IllegalArgumentException("node type " + node.getClass().getSimpleName() + " not implemented yet.");
            }
            stringBuilder.append(builder);
        }
        stringBuilder.setLength(stringBuilder.length() - 1); // remove last comma
        stringBuilder.append("\n  ]");
        return stringBuilder.toString();
    }

}
