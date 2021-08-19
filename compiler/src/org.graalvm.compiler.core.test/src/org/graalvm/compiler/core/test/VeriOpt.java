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
package org.graalvm.compiler.core.test;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IllegalStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.VoidStamp;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeInterface;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FixedBinaryNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.FinalFieldBarrierNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class VeriOpt {
    public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("uq.debug", "false"));
    public static final boolean ENCODE_FLOAT_STAMPS = Boolean.parseBoolean(System.getProperty("uq.encode_float_stamps", "true"));
    public static final String IRNODES_FILES = System.getProperty("uq.irnodes", "");
    private static String irNodes = null;

    private static HashSet<String> binaryNodes;
    static {
        binaryNodes = new HashSet<>();
        // add just the binary nodes that we currently handle, with x,y fields only.
        binaryNodes.add("AddNode");
        binaryNodes.add("AndNode");
        binaryNodes.add("BinaryMathIntrinsicNode");
        binaryNodes.add("FloatNormalizeCompareNode");
        binaryNodes.add("IntegerBelowNode");
        binaryNodes.add("IntegerEqualsNode");
        binaryNodes.add("IntegerLessThanNode");
        binaryNodes.add("IntegerTestNode");
        binaryNodes.add("LeftShiftNode");
        binaryNodes.add("MulNode");
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
        unaryNodes.add("NarrowNode");
        unaryNodes.add("NegateNode");
        unaryNodes.add("NotNode");
        unaryNodes.add("SignExtendNode");
        unaryNodes.add("SqrtNode");
        unaryNodes.add("ReverseBytesNode");
        unaryNodes.add("UnaryMathIntrinsicNode");
        unaryNodes.add("ZeroExtendNode");
    }

    private StringBuilder stringBuilder = new StringBuilder();

    protected String id(Node node) {
        return node.toString(Verbosity.Id);
    }

    protected String optId(Node optional) {
        return optional == null ? "None" : "(Some " + id(optional) + ")";
    }

    protected String optIdAsNode(ValueNodeInterface optional) {
        return optional == null ? "None" : optId(optional.asNode());
    }

    protected <T extends Node> String idList(NodeIterable<T> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        Iterator<T> iter = nodes.iterator();
        while (iter.hasNext()) {
            T n = iter.next();
            if (n == null) {
                throw new IllegalArgumentException("null found in Node list");
            }
            sb.append(id(n));
            if (iter.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Translates an optional list of nodes.
     *
     * TODO: I think this should be "ID option list", rather than "ID list option"?
     *
     * @param nodes
     * @param <T>
     * @return A list of optional ids
     */
    protected <T extends Node> String optIdList(NodeIterable<T> nodes) {
        if (nodes.isEmpty()) {
            return "None";
        } else {
            return "(Some " + idList(nodes) + ")";
        }
    }

    private static String encodeStamp(Stamp stamp) {
        if (stamp instanceof IllegalStamp) {
            return "IllegalStamp";
        } else if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            return "IntegerStamp " + integerStamp.getBits() + " (" + integerStamp.lowerBound() + ") (" + integerStamp.upperBound() + ")";
        } else if (stamp instanceof FloatStamp && ENCODE_FLOAT_STAMPS) {
            FloatStamp floatStamp = (FloatStamp) stamp;
            return "FloatStamp " + floatStamp.getBits() + " (" + floatStamp.lowerBound() + ") (" + floatStamp.upperBound() + ")";
        } else if (stamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) stamp;
            String type = objectStamp.type() == null ? null : objectStamp.type().toClassName();
            return "ObjectStamp ''" + type + "'' " + bool(objectStamp.isExactType()) + " " + bool(objectStamp.nonNull()) + " " + bool(objectStamp.alwaysNull());
        } else if (stamp instanceof VoidStamp) {
            return "VoidStamp";
        } else {
            throw new IllegalArgumentException("unhandled stamp: " + stamp.getClass().getSimpleName() + ": " + stamp.toString());
        }
    }

    /**
     * Adds one (id, Node, Stamp) triple into the output Isabelle graph.
     *
     * @param node
     * @param args
     */
    protected void nodeDef(Node node, String... args) {
        String clazz = node.getClass().getSimpleName();
        stringBuilder.append("\n  (");
        stringBuilder.append(id(node));
        stringBuilder.append(", (");
        stringBuilder.append(clazz);
        for (String arg : args) {
            stringBuilder.append(" ");
            stringBuilder.append(arg);
        }
        stringBuilder.append("), ");
        if (node instanceof ValueNode) {
            Stamp stamp = ((ValueNode) node).stamp(NodeView.DEFAULT);
            stringBuilder.append(encodeStamp(stamp));
        } else {
            stringBuilder.append("IllegalStamp");
        }
        stringBuilder.append("),");
    }

    /**
     * Dump multiple IRGraphs as a single Program.
     *
     * @param graphs The graphs to dump
     * @return A definition of the graphs as a Program in isabelle syntax, with {name} representing
     *         the name of the graph
     */
    public String dumpProgram(Graph... graphs) {
        stringBuilder.setLength(0);
        stringBuilder.append("definition {name} :: Program where\n");
        stringBuilder.append("  \"{name} = Map.empty (\n");

        for (Graph graph : graphs) {
            String graphName = getGraphName(graph);
            stringBuilder.append("  ''" + graphName + "'' \\<mapsto> irgraph ");
            writeNodeArray(graph);
            stringBuilder.append(",\n");
        }
        stringBuilder.setLength(stringBuilder.length() - 2); // remove last comma

        stringBuilder.append("\n  )\"");
        return stringBuilder.toString();
    }

    /**
     * Get a reasonable name for a graph.
     *
     * @param graph The graph to get a name for
     * @return Either Graph.name, StructuredGraph.method().getName(), or null
     */
    public String getGraphName(Graph graph) {
        if (graph.name != null) {
            return graph.name;
        }

        if (graph instanceof StructuredGraph && ((StructuredGraph) graph).method() != null) {
            return formatMethod(((StructuredGraph) graph).method());
        }

        return null;
    }

    /**
     * Dump a single IRGraph.
     *
     * @param graph The graph to dump
     * @return A definition of the graph as an IRGraph in isabelle syntax, with {name} representing
     *         the name of the graph
     */
    public String dumpGraph(Graph graph) {
        stringBuilder.setLength(0);

        stringBuilder.append("definition {name} :: IRGraph where");
        stringBuilder.append("  \"{name} = irgraph ");
        writeNodeArray(graph);

        stringBuilder.append("\"");
        return stringBuilder.toString();
    }

    /**
     * Dump a single IRGraph with the specified name.
     *
     * @param graph The graph to dump
     * @param name The name to give the graph
     * @return A definition of the graph as an IRGraph in isabelle syntax
     */
    public String dumpGraph(Graph graph, String name) {
        return dumpGraph(graph).replace("{name}", name);
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

    /**
     * Returns the [node...] string.
     *
     * @param graph The graph to write
     */
    private void writeNodeArray(Graph graph) {
        stringBuilder.append("[");
        for (Node node : graph.getNodes()) {
            if (!isInIrNodes(node)) {
                throw new IllegalArgumentException("node type " + node + " (" + node.getClass().getSimpleName() + ") is not in -Duq.irnodes=file.");
            } else if (node instanceof ArrayLengthNode) {
                ArrayLengthNode n = (ArrayLengthNode) node;
                nodeDef(n, id(n.array()), id(n.next()));
            } else if (node instanceof BeginNode) {
                BeginNode n = (BeginNode) node;
                nodeDef(n, id(n.next()));
            } else if (node instanceof BoxNode) {
                BoxNode n = (BoxNode) node;
                nodeDef(n, id(n.getValue()), optIdAsNode(n.getLastLocationAccess()), id(n.next()));
            } else if (node instanceof BytecodeExceptionNode) {
                BytecodeExceptionNode n = (BytecodeExceptionNode) node;
                nodeDef(n, idList(n.getArguments()), optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof ConditionalNode) {
                ConditionalNode n = (ConditionalNode) node;
                nodeDef(n, id(n.condition()), id(n.trueValue()), id(n.falseValue()));
            } else if (node instanceof ConstantNode) {
                ConstantNode n = (ConstantNode) node;
                Constant c = n.getValue();
                if (c instanceof PrimitiveConstant) {
                    nodeDef(n, value(((PrimitiveConstant) c).asBoxedPrimitive()));
                } else {
                    throw new IllegalArgumentException("constant type " + c + " (" + c.getClass().getSimpleName() + ") not implemented yet.");
                }
            } else if (node instanceof ControlFlowAnchorNode) {
                ControlFlowAnchorNode n = (ControlFlowAnchorNode) node;
                nodeDef(n, id(n.next()));
            } else if (node instanceof DeoptimizeNode) {
                DeoptimizeNode n = (DeoptimizeNode) node;
                nodeDef(n, optId(n.stateBefore()));
            } else if (node instanceof DynamicNewArrayNode) {
                DynamicNewArrayNode n = (DynamicNewArrayNode) node;
                nodeDef(n, id(n.getElementType()), id(n.length()), optId(n.getVoidClass()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof EndNode) {
                EndNode n = (EndNode) node;
                nodeDef(n);
            } else if (node instanceof ExceptionObjectNode) {
                ExceptionObjectNode n = (ExceptionObjectNode) node;
                nodeDef(n, optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof FinalFieldBarrierNode) {
                FinalFieldBarrierNode n = (FinalFieldBarrierNode) node;
                nodeDef(n, optId(n.getValue()), id(n.next()));
            } else if (node instanceof FixedGuardNode) {
                FixedGuardNode n = (FixedGuardNode) node;
                nodeDef(n, id(n.condition()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof FrameState) {
                FrameState n = (FrameState) node;
                nodeDef(n, "[]", optId(n.outerFrameState()), "None", "None");
                // TODO:
                // option(n.values()) + n.index() + ")\n")
            } else if (node instanceof IfNode) {
                IfNode n = (IfNode) node;
                nodeDef(n, id(n.condition()), id(n.trueSuccessor()), id(n.falseSuccessor()));
            } else if (node instanceof InstanceOfNode) {
                InstanceOfNode n = (InstanceOfNode) node;
                nodeDef(n, optIdAsNode(n.getAnchor()), id(n.getValue()));
            } else if (node instanceof IntegerDivRemNode) {
                // SignedDivNode, SignedRemNode, UnsignedDivNode, UnsignedRemNode
                IntegerDivRemNode n = (IntegerDivRemNode) node;
                nodeDef(n, id(n), id(n.getX()), id(n.getY()), optIdAsNode(n.getZeroCheck()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof IntegerSwitchNode) {
                IntegerSwitchNode n = (IntegerSwitchNode) node;
                nodeDef(n, idList(n.successors()), id(n.value()));
            } else if (node instanceof InvokeNode) {
                InvokeNode n = (InvokeNode) node;
                nodeDef(n, id(n), id(n.callTarget()), optId(n.classInit()), optId(n.stateDuring()), optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode n = (InvokeWithExceptionNode) node;
                nodeDef(n, id(n), id(n.callTarget()), optId(n.classInit()), optId(n.stateDuring()), optId(n.stateAfter()), id(n.next()), id(n.exceptionEdge()));
            } else if (node instanceof IsNullNode) {
                IsNullNode n = (IsNullNode) node;
                nodeDef(n, id(n.getValue()));
            } else if (node instanceof KillingBeginNode) {
                KillingBeginNode n = (KillingBeginNode) node;
                nodeDef(n, id(n.next()));
            } else if (node instanceof LoadFieldNode) {
                LoadFieldNode n = (LoadFieldNode) node;
                nodeDef(n, id(n), fieldRef(n.field()), optId(n.object()), id(n.next()));
            } else if (node instanceof LogicConstantNode) {
                LogicConstantNode n = (LogicConstantNode) node;
                nodeDef(n, "(IntVal32 (" + (n.getValue() ? 1 : 0) + "))");
            } else if (node instanceof LogicNegationNode) {
                LogicNegationNode n = (LogicNegationNode) node;
                nodeDef(n, id(n.getValue()));
            } else if (node instanceof LoopBeginNode) {
                LoopBeginNode n = (LoopBeginNode) node;
                nodeDef(n, idList(n.cfgPredecessors()), optIdAsNode(n.getOverflowGuard()), optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof LoopEndNode) {
                LoopEndNode n = (LoopEndNode) node;
                nodeDef(n, id(n.loopBegin()));
            } else if (node instanceof LoopExitNode) {
                LoopExitNode n = (LoopExitNode) node;
                nodeDef(n, id(n.loopBegin()), optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof MergeNode) {
                MergeNode n = (MergeNode) node;
                nodeDef(n, idList(n.cfgPredecessors()), optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof MembarNode) {
                MembarNode n = (MembarNode) node;
                nodeDef(n, id(n.next()));
            } else if (node instanceof MethodCallTargetNode) {
                MethodCallTargetNode n = (MethodCallTargetNode) node;
                nodeDef(n, "''" + formatMethod(n.targetMethod()) + "''", idList(n.arguments()));
            } else if (node instanceof NewArrayNode) {
                NewArrayNode n = (NewArrayNode) node;
                nodeDef(n, id(n.length()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof NewMultiArrayNode) {
                NewMultiArrayNode n = (NewMultiArrayNode) node;
                nodeDef(n, id(n), typeRef(n.type()), idList(n.dimensions()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof NewInstanceNode) {
                NewInstanceNode n = (NewInstanceNode) node;
                nodeDef(n, id(n), typeRef(n.instanceClass()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof OpaqueNode) {
                OpaqueNode n = (OpaqueNode) node;
                nodeDef(n, id(n.getValue()));
            } else if (node instanceof ParameterNode) {
                ParameterNode n = (ParameterNode) node;
                nodeDef(n, Integer.toString(n.index()));
            } else if (node instanceof PiNode) {
                PiNode n = (PiNode) node;
                nodeDef(n, id(n.object()), optIdAsNode(n.getGuard()));
            } else if (node instanceof RawStoreNode) {
                RawStoreNode n = (RawStoreNode) node;
                nodeDef(n, id(n.value()), optId(n.stateAfter()), id(n.object()), id(n.offset()), id(n.next()));
            } else if (node instanceof ReturnNode) {
                ReturnNode n = (ReturnNode) node;
                nodeDef(n, optId(n.result()), optId(n.getMemoryMap()));
            } else if (node instanceof StartNode) {
                StartNode n = (StartNode) node;
                nodeDef(n, optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof StateSplitProxyNode) {
                StateSplitProxyNode n = (StateSplitProxyNode) node;
                nodeDef(n, optId(n.stateAfter()), optId(n.object()), id(n.next()));
            } else if (node instanceof StoreFieldNode) {
                StoreFieldNode n = (StoreFieldNode) node;
                nodeDef(n, id(n), fieldRef(n.field()), id(n.value()),
                                optId(n.stateAfter()), optId(n.object()), id(n.next()));
            } else if (node instanceof StoreIndexedNode) {
                StoreIndexedNode n = (StoreIndexedNode) node;
                nodeDef(n, optIdAsNode(n.getStoreCheck()), id(n.value()), optId(n.stateAfter()), id(n.index()), optIdAsNode(n.getBoundsCheck()), id(n.array()), id(n.next()));
            } else if (node instanceof UnboxNode) {
                UnboxNode n = (UnboxNode) node;
                nodeDef(n, id(n.getValue()), optIdAsNode(n.getLastLocationAccess()), id(n.next()));
            } else if (node instanceof UnwindNode) {
                UnwindNode n = (UnwindNode) node;
                nodeDef(n, id(n.exception()));
            } else if (node instanceof ValuePhiNode) {
                ValuePhiNode n = (ValuePhiNode) node;
                nodeDef(n, id(n), idList(n.values()), id(n.merge()));
            } else if (node instanceof ValueProxyNode) {
                ValueProxyNode n = (ValueProxyNode) node;
                nodeDef(n, id(n.value()), id(n.proxyPoint()));
            } else if (node instanceof BranchProbabilityNode) {
                // Do nothing, we don't need this node
            } else if (node instanceof BinaryNode && binaryNodes.contains(node.getClass().getSimpleName())) {
                BinaryNode n = (BinaryNode) node;
                nodeDef(n, id(n.getX()), id(n.getY()));
            } else if (node instanceof BinaryOpLogicNode && binaryNodes.contains(node.getClass().getSimpleName())) {
                BinaryOpLogicNode n = (BinaryOpLogicNode) node;
                nodeDef(n, id(n.getX()), id(n.getY()));
            } else if (node instanceof FixedBinaryNode && binaryNodes.contains(node.getClass().getSimpleName())) {
                FixedBinaryNode n = (FixedBinaryNode) node;
                nodeDef(n, id(n.getX()), id(n.getY()));
            } else if (node instanceof UnaryNode && unaryNodes.contains(node.getClass().getSimpleName())) {
                UnaryNode n = (UnaryNode) node;
                nodeDef(n, id(n.getValue()));
            } else {
                generateCode(node);
                throw new IllegalArgumentException("node type " + node + " (" + node.getClass().getSimpleName() + ") not implemented yet.");
            }
        }
        stringBuilder.setLength(stringBuilder.length() - 1); // remove last comma
        stringBuilder.append("\n  ]");
    }

    public String fieldRef(ResolvedJavaField field) {
        return "''" + field.getDeclaringClass().toClassName() + "::" + field.getName() + "''";
    }

    public String typeRef(ResolvedJavaType type) {
        return "''" + type.toClassName() + "''";
    }

    public String value(Object obj) {
        if (obj instanceof Double) {
            Double f = (Double) obj;
            return "(FloatVal 64 (" + f.toString() + "))";
        } else if (obj instanceof Float) {
            Float f = (Float) obj;
            return "(FloatVal 32 (" + f.toString() + "))";
        } else if (obj instanceof Long) {
            Long i = (Long) obj;
            return "(IntVal64 (" + i.toString() + "))";
        } else if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "(IntVal32 (" + i.toString() + "))";
        } else if (obj instanceof Short) {
            Short i = (Short) obj;
            return "(IntVal32 (" + i.toString() + "))";
        } else if (obj instanceof Byte) {
            Byte i = (Byte) obj;
            return "(IntVal32 (" + i.toString() + "))";
        } else if (obj instanceof Boolean) {
            Boolean b = (Boolean) obj;
            return "(IntVal32 (" + (b ? "1" : "0") + "))";
        } else if (obj instanceof String) {
            String s = (String) obj;
            return "(ObjStr ''" + s + "'')";
        } else if (obj == null) {
            throw new IllegalArgumentException("unsupported value type: " + obj);
        } else {
            throw new IllegalArgumentException("unsupported value type: " + obj + " (" + obj.getClass().getSimpleName() + ")");
        }
    }

    public String checkResult(Object obj, String id) {
        Map<String, String> fields = new HashMap<>();
        StringBuilder check = new StringBuilder();
        String sep = "";

        if (obj.getClass().isArray()) {
            throw new IllegalArgumentException("unsupported checkResult type: " + obj.getClass().getName());
        }

        getFieldsRecursively(obj, obj.getClass(), fields, "");

        for (Map.Entry<String, String> field : fields.entrySet()) {
            check.append(sep);
            check.append("h_load_field ''");
            check.append(field.getKey());
            check.append("'' x h = ");
            check.append(field.getValue());

            sep = " \\<and> ";
        }

        return String.format("fun check_result_%s :: \"Value \\<Rightarrow> FieldRefHeap \\<Rightarrow> bool\" where\n" + "  \"check_result_%s (ObjRef x) h = (%s)\" |\n" +
                        "  \"check_result_%s _ _ = False\"\n", id, id, check.toString(), id);
    }

    /**
     * Lists all public and private fields for a class and any super classes, and their values for
     * the specified object.
     *
     * @param object The object to retrieve the value for
     * @param clazz The class to retrieve the fields for
     */
    private void getFieldsRecursively(Object object, Class<?> clazz, Map<String, String> fields, String prefix) {
        // Add this class' fields
        for (Field field : clazz.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    field.setAccessible(true); // Let us get the value of private fields
                } catch (RuntimeException ignored) {
                }
                try {
                    Object value = field.get(object);
                    String name = clazz.getName() + "::" + field.getName();
                    if (value == null) {
                        fields.put(name, "None");
                    } else if (!(value instanceof Number) && !(value instanceof String) && !(value instanceof Boolean)) {
                        getFieldsRecursively(value, value.getClass(), fields, name + ".");
                    } else {
                        fields.put(name, value(value));
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        // Add the super class' fields
        if (clazz.getSuperclass() != null) {
            getFieldsRecursively(object, clazz.getSuperclass(), fields, prefix);
        }
    }

    public String valueList(Object[] args) {
        if (args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (Object obj : args) {
            sb.append(value(obj));
            sb.append(", ");
        }
        sb.setLength(sb.length() - 2); // remove last separator
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns the boolean in isabelle syntax.
     *
     * @param bool The boolean to convert to isabelle
     * @return Either True or False
     */
    private static String bool(boolean bool) {
        return bool ? "True" : "False";
    }

    public static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H.%n") + method.getSignature().toMethodDescriptor();
    }

    private static boolean isInIrNodes(Node node) {
        if (irNodes == null) {
            // Load the IRNodes for the first time
            if (IRNODES_FILES.isEmpty()) {
                // File not specified, leave empty
                irNodes = "";
            } else {
                try {
                    irNodes = new String(Files.readAllBytes(new File(IRNODES_FILES).toPath()), StandardCharsets.UTF_8);
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
}
