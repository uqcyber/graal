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
package org.graalvm.compiler.core.test.veriopt;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class VeriOpt {
    public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("uq.debug", "false"));
    public static final boolean ENCODE_FLOAT_STAMPS = Boolean.parseBoolean(System.getProperty("uq.encode_float_stamps", "true"));
    public static final String IRNODES_FILES = System.getProperty("uq.irnodes", "");
    public static final boolean USE_CLASS_HIERARCHY = Boolean.parseBoolean(System.getProperty("uq.use_class_hierarchy", "true"));

    private static final VeriOptGraphCache veriOptGraphCache = new VeriOptGraphCache(null);

    private StringBuilder stringBuilder = new StringBuilder();

    /**
     * Dump multiple IRGraphs as a single Program.
     *
     * @param graphs The graphs to dump
     * @return A definition of the graphs as a Program in isabelle syntax, with {name} representing
     *         the name of the graph
     */
    public String dumpProgram(StructuredGraph... graphs) {
        stringBuilder.setLength(0);
        stringBuilder.append("definition {name} :: Program where\n");
        stringBuilder.append("  \"{name} = Map.empty (\n");

        for (StructuredGraph graph : graphs) {
            String graphName = getGraphName(graph);

            String nodeArray = veriOptGraphCache.getNodeArray(graph);
            if (nodeArray != null) {
                stringBuilder.append("  ''").append(graphName).append("'' \\<mapsto> irgraph ");
                stringBuilder.append(nodeArray);
                stringBuilder.append(",\n");
            } else if (graphs[0] == graph) {
                // Error if we can't translate the first graph
                throw new IllegalArgumentException("Could not translate graph for " + VeriOpt.formatMethod(graph.method()));
            }
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
    public String dumpGraph(StructuredGraph graph) {
        stringBuilder.setLength(0);

        stringBuilder.append("definition {name} :: IRGraph where");
        stringBuilder.append("  \"{name} = irgraph ");

        String nodeArray = veriOptGraphCache.getNodeArray(graph);
        if (nodeArray == null) {
            throw new IllegalArgumentException("Could not translate graph for " + VeriOpt.formatMethod(graph.method()));
        }
        stringBuilder.append(nodeArray);

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
    public String dumpGraph(StructuredGraph graph, String name) {
        return dumpGraph(graph).replace("{name}", name);
    }

    public String checkResult(Object obj, String id) {
        Map<String, String> fields = new HashMap<>();
        StringBuilder check = new StringBuilder();
        String sep = "";

        if (obj.getClass().isArray()) {
            throw new IllegalArgumentException("unsupported checkResult type: " + obj.getClass().getName());
        }

        getFieldsRecursively(obj, obj.getClass(), fields);

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
    private void getFieldsRecursively(Object object, Class<?> clazz, Map<String, String> fields) {
        if (clazz.getName().equals("java.lang.Object") || clazz.getName().equals("java.lang.Class")) {
            // Ignore these classes (openjdk 8 doesn't like them)
            return;
        }

        System.out.println("Class: " + clazz.getName());
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
                        getFieldsRecursively(value, value.getClass(), fields);
                    } else {
                        fields.put(name, VeriOptValueEncoder.value(value));
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        // Add the super class' fields
        if (clazz.getSuperclass() != null) {
            getFieldsRecursively(object, clazz.getSuperclass(), fields);
        }
    }

    public String valueList(Object[] args) {
        if (args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (Object obj : args) {
            sb.append(VeriOptValueEncoder.value(obj));
            sb.append(", ");
        }
        sb.setLength(sb.length() - 2); // remove last separator
        sb.append("]");
        return sb.toString();
    }

    public static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H.%n") + method.getSignature().toMethodDescriptor();
    }

    /**
     * Create a graph with the sole purpose of invoking a method.
     *
     * @param method The method to be invoked
     * @return A graph that will invoke the given method
     */
    public StructuredGraph invokeGraph(ResolvedJavaMethod method, OptionValues initialOptions, DebugContext debugContext) {
        StructuredGraph graph = new StructuredGraph.Builder(initialOptions, debugContext).name("").build();

        StartNode startNode = graph.start();

        FrameState frameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        graph.add(frameState);
        startNode.setStateAfter(frameState);

        MethodCallTargetNode targetNode = new MethodCallTargetNode(CallTargetNode.InvokeKind.Static, method, new ValueNode[0], StampPair.createSingle(StampFactory.forVoid()), null);
        graph.add(targetNode);

        InvokeNode invokeNode = new InvokeNode(targetNode, BytecodeFrame.BEFORE_BCI);
        graph.add(invokeNode);
        startNode.setNext(invokeNode);

        FrameState invokeFrameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        graph.add(invokeFrameState);
        invokeNode.setStateAfter(invokeFrameState);

        ReturnNode returnNode = new ReturnNode(null);
        graph.add(returnNode);
        invokeNode.setNext(returnNode);

        return graph;
    }
}
