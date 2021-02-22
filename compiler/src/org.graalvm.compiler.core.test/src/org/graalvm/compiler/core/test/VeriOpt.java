package org.graalvm.compiler.core.test;

import jdk.vm.ci.meta.Constant;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;

import java.util.HashSet;
import java.util.Iterator;

public class VeriOpt {
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
    }

    private StringBuilder sb = new StringBuilder();

    protected String id(Node node) {
        return "" + node.getId();
    }

    protected String optId(Node optional) {
        return optional == null ? " None" : " (Some " + id(optional) + ")";
    }

    protected <T extends Node> String idList(NodeIterable<T> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append(" [");
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
     * @return
     */
    protected <T extends Node> String optIdList(NodeIterable<T> nodes) {
        if (nodes.isEmpty()) {
            return " None";
        } else {
            return " (Some" + idList(nodes) + ")";
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
        sb.append("\n  (");
        sb.append(id(node));
        sb.append(", (");
        sb.append(clazz);
        for (String arg : args) {
            sb.append(" ");
            sb.append(arg);
        }
        sb.append("), default_stamp),");
    }

    public String dumpGraph(StructuredGraph graph, String name) {
        sb.setLength(0);
        sb.append("definition " + name + " :: IRGraph where\n");
        sb.append("  \"" + name + " = irgraph [");
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
                nodeDef(n, "[]", optId(n.outerFrameState()), "None", "None"); // TODO: option(n.values()) +  n.index() + ")\n")
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
//            } else if (node instanceof LoopBeginNode) {
//                LoopBeginNode n = (LoopBeginNode) node;
//                nodeDef(n, id(n.loopEnds()), id(n.trueSuccessor()), id(n.falseSuccessor()));
            } else if (node instanceof BinaryNode
                    && binaryNodes.contains(node.getClass().getSimpleName())) {
                BinaryNode n = (BinaryNode) node;
                nodeDef(n, id(n.getX()), id(n.getY()));
            } else if (node instanceof BinaryOpLogicNode
                    && binaryNodes.contains(node.getClass().getSimpleName())) {
                BinaryOpLogicNode n = (BinaryOpLogicNode) node;
                nodeDef(n, id(n.getX()), id(n.getY()));
            } else if (node instanceof ReturnNode) {
                ReturnNode n = (ReturnNode) node;
                nodeDef(n, optId(n.result()), optId(n.getMemoryMap()));
            } else {
                throw new IllegalArgumentException("node type " + node + " not implemented yet.");
            }
        }
        sb.setLength(sb.length() - 1); // remove last comma
        sb.append("\n  ]\"");
        return sb.toString();
    }

    public String value(Object obj) {
        if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "(IntVal 32 (" + i.toString() + "))";
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
}
