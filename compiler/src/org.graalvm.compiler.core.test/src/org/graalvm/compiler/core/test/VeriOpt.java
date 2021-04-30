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
import jdk.vm.ci.meta.ResolvedJavaField;
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
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FixedBinaryNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.debug.ControlFlowAnchorNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.StateSplitProxyNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;

import java.util.HashSet;
import java.util.Iterator;

public class VeriOpt {
    public static final boolean DEBUG = false;

    private static HashSet<String> binaryNodes;
    static {
        binaryNodes = new HashSet<>();
        // add just the binary nodes that we currently handle, with x,y fields only.
        binaryNodes.add("AddNode");
        binaryNodes.add("AndNode");
        binaryNodes.add("IntegerEqualsNode");
        binaryNodes.add("IntegerLessThanNode");
        binaryNodes.add("MulNode");
        binaryNodes.add("OrNode");
        binaryNodes.add("ShortCircuitOrNode");
        binaryNodes.add("SubNode");
        binaryNodes.add("XorNode");
        binaryNodes.add("SignedRemNode");
        binaryNodes.add("SignedDivNode");
        binaryNodes.add("IntegerBelowNode");
    }

    private StringBuilder stringBuilder = new StringBuilder();

    protected String id(Node node) {
        return node.toString(Verbosity.Id);
    }

    protected String optId(Node optional) {
        return optional == null ? "None" : "(Some " + id(optional) + ")";
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
        } else if (stamp instanceof FloatStamp) {
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
     * @param name Name of the program
     * @param graphs The graphs to dump
     * @return A definition of the graphs as a Program in isabelle syntax
     */
    public String dumpProgram(String name, Graph... graphs) {
        stringBuilder.setLength(0);
        stringBuilder.append("definition " + name + " :: Program where\n");
        stringBuilder.append("  \"" + name + " = Map.empty (\n");

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
            return ((StructuredGraph) graph).method().getName();
        }

        return null;
    }

    /**
     * Dump a single IRGraph.
     *
     * @param graph The graph to dump
     * @param name Name of the graph
     * @return A definition of the graph as an IRGraph in isabelle syntax
     */
    public String dumpGraph(Graph graph, String name) {
        stringBuilder.setLength(0);

        stringBuilder.append("definition " + name + " :: IRGraph where");
        stringBuilder.append("  \"" + name + " = irgraph ");
        writeNodeArray(graph);

        stringBuilder.append("\"");
        return stringBuilder.toString();
    }

    /**
     * Returns the [node...] string.
     *
     * @param graph The graph to write
     */
    private void writeNodeArray(Graph graph) {
        stringBuilder.append("[");
        for (Node node : graph.getNodes()) {
            if (node instanceof StartNode) {
                StartNode n = (StartNode) node;
                nodeDef(n, optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof ParameterNode) {
                ParameterNode n = (ParameterNode) node;
                nodeDef(n, Integer.toString(n.index()));
            } else if (node instanceof ConstantNode) {
                ConstantNode n = (ConstantNode) node;
                Constant c = n.getValue();
                // TODO: check type of c to make sure it is int and 32 bits
                nodeDef(n, "(IntVal 32 (" + c.toValueString() + "))");
            } else if (node instanceof FrameState) {
                FrameState n = (FrameState) node;
                nodeDef(n, "[]", optId(n.outerFrameState()), "None", "None"); // TODO:
                                                                              // option(n.values())
                                                                              // + n.index() +
                                                                              // ")\n")
            } else if (node instanceof BeginNode) {
                BeginNode n = (BeginNode) node;
                nodeDef(n, id(n.next()));
            } else if (node instanceof EndNode) {
                EndNode n = (EndNode) node;
                nodeDef(n);
            } else if (node instanceof ReturnNode) {
                ReturnNode n = (ReturnNode) node;
                nodeDef(n, optId(n.result()), optId(n.getMemoryMap()));
            } else if (node instanceof ConditionalNode) {
                ConditionalNode n = (ConditionalNode) node;
                nodeDef(n, id(n.condition()), id(n.trueValue()), id(n.falseValue()));
            } else if (node instanceof ValuePhiNode) {
                ValuePhiNode n = (ValuePhiNode) node;
                nodeDef(n, id(n), idList(n.values()), id(n.merge()));
            } else if (node instanceof IfNode) {
                IfNode n = (IfNode) node;
                nodeDef(n, id(n.condition()), id(n.trueSuccessor()), id(n.falseSuccessor()));
            } else if (node instanceof MergeNode) {
                MergeNode n = (MergeNode) node;
                nodeDef(n, idList(n.cfgPredecessors()), optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof LoopBeginNode) {
                LoopBeginNode n = (LoopBeginNode) node;
                GuardingNode overflow = n.getOverflowGuard();
                String overflowStr = (overflow == null) ? "None" : id(overflow.asNode());
                nodeDef(n, idList(n.cfgPredecessors()), overflowStr, optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof LoopEndNode) {
                LoopEndNode n = (LoopEndNode) node;
                nodeDef(n, id(n.loopBegin()));
            } else if (node instanceof LoopExitNode) {
                LoopExitNode n = (LoopExitNode) node;
                nodeDef(n, id(n.loopBegin()), optId(n.stateAfter()), id(n.next()));
            } else if (node instanceof ValueProxyNode) {
                ValueProxyNode n = (ValueProxyNode) node;
                nodeDef(n, id(n.value()), id(n.proxyPoint()));
            } else if (node instanceof LoadFieldNode) {
                LoadFieldNode n = (LoadFieldNode) node;
                nodeDef(n, id(n), fieldRef(n.field()), optId(n.object()), id(n.next()));
            } else if (node instanceof StateSplitProxyNode) {
                StateSplitProxyNode n = (StateSplitProxyNode) node;
                nodeDef(n, optId(n.stateAfter()), optId(n.object()), id(n.next()));
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
            } else if (node instanceof StoreFieldNode) {
                StoreFieldNode n = (StoreFieldNode) node;
                nodeDef(n, id(n), fieldRef(n.field()), id(n.value()),
                                optId(n.stateAfter()), optId(n.object()), id(n.next()));
            } else if (node instanceof NewInstanceNode) {
                NewInstanceNode n = (NewInstanceNode) node;
                nodeDef(n, id(n), typeRef(n.instanceClass()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof NewMultiArrayNode) {
                NewMultiArrayNode n = (NewMultiArrayNode) node;
                nodeDef(n, id(n), typeRef(n.type()), idList(n.dimensions()), optId(n.stateBefore()), id(n.next()));
            } else if (node instanceof UnaryNode) {
                UnaryNode n = (UnaryNode) node;
                nodeDef(n, id(n), id(n.getValue()));
            } else if (node instanceof ControlFlowAnchorNode) {
                ControlFlowAnchorNode n = (ControlFlowAnchorNode) node;
                nodeDef(n, id(n.next()));
            } else if (node instanceof LogicConstantNode) {
                LogicConstantNode n = (LogicConstantNode) node;
                nodeDef(n, "(IntVal 32 (" + (n.getValue() ? 1 : 0) + "))");
            } else if (node instanceof MethodCallTargetNode) {
                MethodCallTargetNode n = (MethodCallTargetNode) node;
                nodeDef(n, "''" + n.targetMethod().format("%H.%n") + n.targetMethod().getSignature().toMethodDescriptor() + "''", idList(n.arguments()));
            } else {
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
        if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "(IntVal 32 (" + i.toString() + "))";
        } else if (obj instanceof Boolean) {
            boolean b = (Boolean) obj;
            return "(IntVal 1 (" + (b ? "1" : "0") + "))";
        } else {
            throw new IllegalArgumentException("unsupported value type: " + obj);
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
}
