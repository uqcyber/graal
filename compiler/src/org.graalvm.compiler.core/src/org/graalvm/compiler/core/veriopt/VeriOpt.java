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
package org.graalvm.compiler.core.veriopt;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;

public class VeriOpt {
    public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("uq.debug", "false"));
    public static final boolean ENCODE_FLOAT_STAMPS = Boolean.parseBoolean(System.getProperty("uq.encode_float_stamps", "true"));
    public static final String IRNODES_FILES = System.getProperty("uq.irnodes", "");
    public static final boolean USE_CLASS_HIERARCHY = Boolean.parseBoolean(System.getProperty("uq.use_class_hierarchy", "true"));
    public static final boolean DUMP_OPTIMIZATIONS = Boolean.parseBoolean(System.getProperty("uq.dump_optimizations", "false"));
    public static final String DUMP_OPTIMIZATIONS_PATH = System.getProperty("uq.dump_optimizations_path", "optimizations");

    public static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H.%n") + method.getSignature().toMethodDescriptor();
    }

    /**
     * Create a graph with the sole purpose of invoking a method.
     *
     * @param method The method to be invoked
     * @return A graph that will invoke the given method
     */
    public static StructuredGraph invokeGraph(ResolvedJavaMethod method, OptionValues initialOptions, DebugContext debugContext) {
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
                    System.out.println("Already dumped " + name + ", skipping");
                    return;
                }

                try {
                    String unoptimizedGraphArray = VeriOptGraphTranslator.writeNodeArray(unoptimizedGraph);
                    String optimizedGraphArray = VeriOptGraphTranslator.writeNodeArray(optimizedGraph);

                    String toDump = "definition " + name + "_unoptimized :: IRGraph where \"" + name + "_unoptimized = irgraph " + unoptimizedGraphArray + "\"\n" +
                                    "definition " + name + "_optimized :: IRGraph where \"" + name + "_optimized = irgraph " + optimizedGraphArray + "\"";

                    Files.write(optimizationFile.toPath(), toDump.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    System.out.println("Skipping dumping of " + name + ": " + e.getClass().getName() + ": " + e.getMessage());

                    if (e instanceof NullPointerException) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static String typesToNames(JavaType[] types, String seperator) {
        String sep = "";
        StringBuilder builder = new StringBuilder();
        for (JavaType type : types) {
            builder.append(sep);
            builder.append(type.getUnqualifiedName());
            sep = seperator;
        }
        return builder.toString();
    }
}
