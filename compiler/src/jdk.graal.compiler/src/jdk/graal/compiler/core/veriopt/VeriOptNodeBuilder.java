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

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueNodeInterface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A helper class for translating IR nodes into Isabelle syntax.
 *
 * These builder objects use method chaining to build up an Isabelle representation of a node.
 * The resulting Isabelle syntax can then be obtained via the <code>toString()</code> method.
 */
public class VeriOptNodeBuilder {
    private Node node;
    private String clazz;
    private String id;
    private List<String> args = new ArrayList<>();
    private String stamp = "IllegalStamp";

    protected VeriOptNodeBuilder(Node node, String clazzName) {
        this.node = node;
        clazz = clazzName;
        id = node.toString(Verbosity.Id);
        if (node instanceof ValueNode) {
            stamp = VeriOptStampEncoder.encodeStamp(((ValueNode) node).stamp(NodeView.DEFAULT));
        }
    }

    public VeriOptNodeBuilder(Node node) {
        this(node, node.getClass().getSimpleName());
    }

    /**
     * Append a raw argument to the node's arguments.
     *
     * @param argument The argument to be appended
     * @return This builder
     */
    public VeriOptNodeBuilder arg(String argument) {
        args.add(argument);
        return this;
    }

    /**
     * Append an id of a node to the node's arguments.
     *
     * @param node The node whose id to be appended
     * @return This builder
     */
    public VeriOptNodeBuilder id(Node node) {
        return arg(node.toString(Verbosity.Id));
    }

    /**
     * Append an optional id of a node to the node's arguments.
     *
     * @param optional The node whose optional id to be appended (Can be null)
     * @return This builder
     */
    public VeriOptNodeBuilder optId(Node optional) {
        return arg(optional == null ? "None" : "(Some " + optional.toString(Verbosity.Id) + ")");
    }

    /**
     * Append an optional id of a node to the node's arguments.
     *
     * @param optional The node whose optional id to be appended (Can be null)
     * @return This builder
     */
    public VeriOptNodeBuilder optIdAsNode(ValueNodeInterface optional) {
        if (optional == null) {
            return arg("None");
        } else {
            return optId(optional.asNode());
        }
    }

    /**
     * Append an list of node ids to the node's arguments.
     *
     * @param nodes The nodes whose id to be appended
     * @return This builder
     */
    public <T extends Node> VeriOptNodeBuilder idList(Iterable<T> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        Iterator<T> iter = nodes.iterator();
        while (iter.hasNext()) {
            T n = iter.next();
            if (n == null) {
                throw new IllegalArgumentException("null found in Node list");
            }
            sb.append(n.toString(Verbosity.Id));
            if (iter.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return arg(sb.toString());
    }

    /**
     * Translates an optional list of nodes.
     *
     * @param nodes The nodes whose optional id to be appended
     * @param <T> Type of nodes
     * @return A list of optional ids
     */
    public <T extends Node> VeriOptNodeBuilder optIdList(NodeIterable<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return arg("None");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("(Some [");
            Iterator<T> iter = nodes.iterator();
            while (iter.hasNext()) {
                T n = iter.next();
                if (n == null) {
                    sb.append("None");
                    throw new IllegalArgumentException("null found in optional Node list");
                } else {
                    sb.append("(Some ").append(n.toString(Verbosity.Id)).append(")");
                }
                if (iter.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("])");
            return arg(sb.toString());
        }
    }

    /**
     * Append the value of an object to the node's arguments.
     *
     * @param object The object whose value is to be appended
     * @return This builder
     */
    public VeriOptNodeBuilder value(Object object) {
        return arg(VeriOptValueEncoder.value(object, false, false));
    }

    /**
     * Append a field reference to the node's arguments.
     *
     * @param field The field reference to be appended
     * @return This builder
     */
    public VeriOptNodeBuilder fieldRef(ResolvedJavaField field) {
        return arg("''" + field.getDeclaringClass().toClassName() + "::" + field.getName() + "''");
    }

    /**
     * Append a type reference to the node's arguments.
     *
     * @param type The type reference to be appended
     * @return This builder
     */
    public VeriOptNodeBuilder typeRef(ResolvedJavaType type) {
        return arg("''" + type.toClassName() + "''");
    }

    /**
     * Append a method reference to the node's arguments.
     *
     * @param method The method reference to be appended
     * @return This builder
     */
    public VeriOptNodeBuilder methodRef(ResolvedJavaMethod method) {
        return arg("''" + method.format("%H.%n") + method.getSignature().toMethodDescriptor() + "''");
    }

    /**
     * Append a natural number to the node's arguments.
     *
     * @param number The natural number to be appended
     * @return This builder
     */
    public VeriOptNodeBuilder nat(int number) {
        return arg(Integer.toString(number));
    }

    /**
     * Append the InvokeKind of a MethodCallTargetNode to the node's arguments
     *
     * @param kind the InvokeKind of the MethodCallTargetNode.
     * @return This builder
     * */
    public VeriOptNodeBuilder invokeKind(CallTargetNode.InvokeKind kind) {
        switch (kind) {
            case Static:
                return arg("Static");
            case Special:
                return arg("Special");
            case Virtual:
                return arg("Virtual");
            case Interface:
                return arg("Interface");
        }
        return arg(""); // Shouldn't happen
    }

    /**
     * Generates and stores the information necessary to generate the Isabelle representation of this NodeBuilder's
     * {@link #node}, and returns this NodeBuilder. <br>
     *
     * Note that this function only currently handles {@link BytecodeExceptionNode} and {@link IntegerDivRemNode};
     * remaining node type translations are defined in {@link VeriOptGraphTranslator#writeNodeArray(Graph)}.
     *
     * @return this NodeBuilder, now storing the information necessary to represent this {@link #node} in Isabelle.
     * */
    public VeriOptNodeBuilder build() {
        if (node instanceof BytecodeExceptionNode n) {
            return idList(n.getArguments()).optId(n.stateAfter()).id(n.next());
        }
        if (node instanceof IntegerDivRemNode n) {
            // SignedDivNode, SignedRemNode, UnsignedDivNode, UnsignedRemNode
            return id(n).id(n.getX()).id(n.getY()).optIdAsNode(n.getZeroGuard()).optId(n.stateBefore()).id(n.next());
        }

        String message = "Trying to build a node %s whose type %s isn't handled yet";
        throw new RuntimeException(String.format(message, node, node.getClass()));
    }

    /**
     * Appends the class name for the {@link #node} stored by this NodeBuilder ({@link #clazz}) to the provided
     * {@code builder}. <br>
     *
     * Note that if the {@link #node} is a {@link jdk.graal.compiler.nodes.LogicConstantNode}, "ConstantNode" is instead
     * appended, as Isabelle represents them as such.
     *
     * @param builder the builder being extended.
     * */
    private void appendClassName(StringBuilder builder) {
        builder.append((!clazz.equals("LogicConstantNode")) ? clazz : "ConstantNode");
    }

    /**
     * Appends this NodeBuilders' {@link #args} to the given {@code builder}.
     *
     * @param builder the builder being extended.
     * */
    private void appendArguments(StringBuilder builder) {
        for (String arg : args) {
            builder.append(" ");
            builder.append(arg);
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n  (");
        stringBuilder.append(id);
        stringBuilder.append(", (");
        appendClassName(stringBuilder);
        appendArguments(stringBuilder);
        stringBuilder.append("), ");
        stringBuilder.append(stamp);
        stringBuilder.append("),");
        return stringBuilder.toString();
    }

    /**
     * Encodes and returns the {@link #node} and {@link #stamp} stored by this NodeBuilder into an Isabelle-friendly
     * syntax, as: {@code (NodeType arg0 arg1 ... args) (stamp)}.
     *
     * @return the {@link #node} and {@link #stamp} stored by this NodeBuilder in an Isabelle-friendly format.
     * */
    public String asAbstractProgramNode() {
        StringBuilder encodedNode = new StringBuilder();

        appendClassName(encodedNode);
        appendArguments(encodedNode);

        return String.format("(%s) (%s)", encodedNode, stamp);
    }
}
