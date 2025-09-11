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

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;

/**
 * Settings class for the VeriOpt translation of IR nodes to Isabelle graph definitions.
 *
 * This class controls how IR graphs are translated, which subset of IR nodes is allowed,
 * and whether graphs are automatically generated before and after each optimization.
 */
public class VeriOpt {
    /**
     * True means print debug messages about why methods cannot be translated to Isabelle.
     */
    public static final boolean DEBUG = Boolean.parseBoolean(Services.getSavedProperties().
            getOrDefault("uq.debug", "false"));

    /**
     * True means translate unit tests into Isabelle syntax, in *.test files.
     */
    public static final boolean DUMP_TESTS = Boolean.parseBoolean(Services.getSavedProperties().
            getOrDefault("uq.dump_tests", "false"));
    /**
     * True means nodes with floating point stamps will be translated.
     * False means methods that use float will not be translated.
     */
    public static final boolean ENCODE_FLOAT_STAMPS = Boolean.parseBoolean(Services.getSavedProperties().
            getOrDefault("uq.encode_float_stamps", "false"));
    /**
     * True means up and down masks will be included in integer stamps.
     */
    public static final boolean ENCODE_INT_MASKS = Boolean.parseBoolean(Services.getSavedProperties().
            getOrDefault("uq.encode_int_masks", "false"));

    /** The path to the config.yml file that lists all nodes that should be translated. */
    public static final String IRNODES_FILES = Services.getSavedProperties().
            getOrDefault("uq.irnodes", "");

    /** True means generate methods for all common library classes, whether used or not. */
    public static final boolean USE_CLASS_HIERARCHY = Boolean.parseBoolean(Services.getSavedProperties().
            getOrDefault("uq.use_class_hierarchy", "false"));

    /** True means dump the before and after graphs of each optimization phase. */
    public static final boolean DUMP_OPTIMIZATIONS = Boolean.parseBoolean(Services.getSavedProperties().
            getOrDefault("uq.dump_optimizations", "false"));

    /** The path to the folder where before-after optimization graphs will be dumped. */
    public static final String DUMP_OPTIMIZATIONS_PATH = Services.getSavedProperties().
            getOrDefault("uq.dump_optimizations_path", "optimizations");

    /** True means try to use reflection to automatically translate all unknown nodes. */
    public static final boolean DYNAMICALLY_TRANSLATE_ALL_NODES = Boolean.parseBoolean(Services.getSavedProperties().
            getOrDefault("uq.dynamically_translate_all_nodes", "false"));


    /** @return a unique string name for the given method signature. */
    public static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H.%n") + method.getSignature().toMethodDescriptor();
    }

    /**
     * Create a graph segment to extend a pre-existing graph and invoke a method.
     *
     * @param method The method to be invoked.
     * @param graph the graph being extended.
     * @param startNode the node which this graph segment will begin from (i.e., the final node of the {@code graph}
     *                  given.)
     * @return A graph segment that will invoke the given {@code method}.
     */
    public static StructuredGraph invokeGraph(ResolvedJavaMethod method, StructuredGraph graph, Node startNode) {
        // Nodes for the method call
        MethodCallTargetNode targetNode = new MethodCallTargetNode(CallTargetNode.InvokeKind.Static, method,
                ValueNode.EMPTY_ARRAY, StampPair.createSingle(StampFactory.forVoid()), null);
        graph.add(targetNode);

        InvokeNode invokeNode = new InvokeNode(targetNode, BytecodeFrame.BEFORE_BCI);
        graph.add(invokeNode);

        // The type of the startNode depends on where the given graph ended.
        if (startNode instanceof StartNode) {
            ((StartNode) startNode).setNext(invokeNode);
        } else if (startNode instanceof StoreFieldNode) {
            ((StoreFieldNode) startNode).setNext(invokeNode);
        }

        FrameState invokeFrameState = new FrameState(BytecodeFrame.BEFORE_BCI);
        graph.add(invokeFrameState);
        invokeNode.setStateAfter(invokeFrameState);

        ReturnNode returnNode = new ReturnNode(null);
        graph.add(returnNode);
        invokeNode.setNext(returnNode);

        return graph;
    }

    private static final HashSet<String> alreadyDumped = new HashSet<>();

    public static void onOptimize(StructuredGraph unoptimizedGraph, StructuredGraph optimizedGraph) {
        if (DUMP_OPTIMIZATIONS && optimizedGraph.method() != null) {
            File optimizationsDirectory = new File(DUMP_OPTIMIZATIONS_PATH);
            if (optimizationsDirectory.isDirectory() || optimizationsDirectory.mkdirs()) {
                String name = String.format(
                                "%s_%s_%s",
                                optimizedGraph.method().getDeclaringClass().getUnqualifiedName().replace(".", "_"),
                                optimizedGraph.method().getName(),
                                typesToNames(optimizedGraph.method().toParameterTypes(), "_"));
                File optimizationFile = new File(optimizationsDirectory, name + ".thy");

                if (!alreadyDumped.add(name)) {
                    if (DEBUG) {
                        System.out.println("Already dumped " + name + ", skipping");
                    }
                    return;
                }

                try {
                    String unoptimizedGraphArray = VeriOptGraphTranslator.writeNodeArray(unoptimizedGraph);
                    String optimizedGraphArray = VeriOptGraphTranslator.writeNodeArray(optimizedGraph);

                    String toDump = "definition " + name + "_unoptimized :: IRGraph where \"" + name + "_unoptimized = irgraph " + unoptimizedGraphArray + "\"\n" +
                                    "definition " + name + "_optimized :: IRGraph where \"" + name + "_optimized = irgraph " + optimizedGraphArray + "\"";

                    Files.write(optimizationFile.toPath(), toDump.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    if (DEBUG) {
                        System.out.println("Skipping dumping of " + name + ": " + e.getClass().getName() + ": " + e.getMessage());
                    }
                    if (e instanceof NullPointerException) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static String typesToNames(JavaType[] types, String separator) {
        String sep = "";
        StringBuilder builder = new StringBuilder();
        for (JavaType type : types) {
            builder.append(sep);
            builder.append(type.getUnqualifiedName());
            sep = separator;
        }
        return builder.toString();
    }

}
