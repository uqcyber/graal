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

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class VeriOptGraphCache {

    private static final Backend backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
    private static final Providers providers = backend.getProviders();

    private static HashMap<String, CacheEntry> cache = new HashMap<>();

    private static CacheEntry getCacheEntry(ResolvedJavaMethod method) {
        return cache.computeIfAbsent(VeriOpt.formatMethod(method), key -> new CacheEntry(buildGraph(method), key));
    }

    /**
     * Get a StructuredGraph from the cache. If it doesn't exist, it will be built.
     *
     * @param method The method to get the graph of
     * @return The StructuredGraph
     */
    public static StructuredGraph getGraph(ResolvedJavaMethod method) {
        return getCacheEntry(method).graph;
    }

    /**
     * Get an Isabelle node array for the specified method.
     *
     * @param method The method to get the node array of
     * @return The node array, or null if it can't be translated
     */
    public static String getNodeArray(ResolvedJavaMethod method) {
        return getCacheEntry(method).nodeArray;
    }

    /**
     * Get an Isabelle node array for the specified graph.
     *
     * @param graph The graph to get the node array of
     * @return The node array, or null if it can't be translated
     */
    public static String getNodeArray(StructuredGraph graph) {
        if (graph.method() == null) {
            // Can't cache a graph without a method
            return VeriOptGraphTranslator.writeNodeArray(graph);
        }
        return getCacheEntry(graph.method()).nodeArray;
    }

    /**
     * Get every graph being referenced by this method recursively.
     *
     * @param method The method to get the referenced graphs for
     * @return All graphs being referenced by this method and those graphs
     */
    public static List<StructuredGraph> getReferencedGraphs(ResolvedJavaMethod method) {
        // Breadth First Search
        HashSet<CacheEntry> referenceSet = new HashSet<>();
        Queue<CacheEntry> toSearch = new LinkedList<>();
        toSearch.add(getCacheEntry(method));

        while (!toSearch.isEmpty()) {
            CacheEntry entry = toSearch.poll();

            if (entry.referencedGraphs == null) {
                resolveReferences(entry);
            }

            for (CacheEntry referenced : entry.referencedGraphs) {
                if (referenceSet.add(referenced)) {
                    toSearch.add(referenced);
                }
            }
        }

        // Flatten the CacheEntries into StructuredGraphs
        List<StructuredGraph> referenceList = new ArrayList<>(referenceSet.size());
        for (CacheEntry entry : referenceSet) {
            referenceList.add(entry.graph);
        }

        return referenceList;
    }

    private static void resolveReferences(CacheEntry entry) {
        entry.referencedGraphs = new HashSet<>();

        // <clinit>
        ResolvedJavaMethod clinit = entry.graph.method().getDeclaringClass().getClassInitializer();
        if (!entry.graph.method().isClassInitializer() && clinit != null) {
            try {
                entry.referencedGraphs.add(getCacheEntry(clinit));
            } catch (AssertionError error) {
                System.out.println("Error while getting the implementation graph for " + VeriOpt.formatMethod(clinit));
            }
        }

        // Graphs referenced by nodes in the graph
        for (Node node : entry.graph.getNodes()) {
            ResolvedJavaMethod method = null;

            if (node instanceof NewInstanceNode) {
                VeriOptClassHierarchy.processClass(((NewInstanceNode) node).instanceClass());
            }

            if (node instanceof MethodCallTargetNode) {
                method = ((MethodCallTargetNode) node).targetMethod();
                VeriOptClassHierarchy.processClass(((MethodCallTargetNode) node).targetMethod().getDeclaringClass());
            }

            if (method != null && !method.isNative()) {
                // Find implementations of this method
                List<ResolvedJavaMethod> implementations = getImplementationsOf(method, entry.graph);
                for (ResolvedJavaMethod implementation : implementations) {
                    try {
                        entry.referencedGraphs.add(getCacheEntry(implementation));
                    } catch (AssertionError error) {
                        System.out.println("Error while getting the implementation graph for " + VeriOpt.formatMethod(implementation));
                    }
                }
                try {
                    entry.referencedGraphs.add(getCacheEntry(method));
                } catch (AssertionError error) {
                    if (implementations.isEmpty()) {
                        System.out.println("Error while finding the implementation graph for " + VeriOpt.formatMethod(method));
                    }
                }
            }
        }
    }

    private static List<ResolvedJavaMethod> getImplementationsOf(ResolvedJavaMethod definition, StructuredGraph graph) {
        List<ResolvedJavaMethod> implementations = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            ResolvedJavaType type = null;

            if (node instanceof NewInstanceNode) {
                NewInstanceNode newInstanceNode = (NewInstanceNode) node;
                type = newInstanceNode.instanceClass();
            }

            if (type != null) {
                ResolvedJavaMethod implmentation = type.findMethod(definition.getName(), definition.getSignature());
                if (implmentation != null && !implmentation.isNative()) {
                    implementations.add(implmentation);
                }
            }
        }
        return implementations;
    }

    /**
     * Build and optimize a StructuredGraph for the given method.
     *
     * @param method The method to build the StructuredGraph for
     * @return An optimized StructuredGraph for the given method.
     */
    private static StructuredGraph buildGraph(ResolvedJavaMethod method) {
        OptionValues options = Graal.getRequiredCapability(OptionValues.class);
        DebugContext debugContext = new DebugContext.Builder(options, Collections.emptyList()).build();
        StructuredGraph.Builder builder = new StructuredGraph.Builder(options, debugContext, StructuredGraph.AllowAssumptions.YES).method(method).compilationId(
                        backend.getCompilationIdentifier(method));
        StructuredGraph graph = builder.build();
        PhaseSuite<HighTierContext> graphBuilderSuite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        graphBuilderSuite.apply(graph, new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL));
        // Don't optimise the referenced graphs (This can introduce some complicated nodes that
        // could've been avoided)
        // GraalCompiler.emitFrontEnd(providers, backend, graph,
        // backend.getSuites().getDefaultGraphBuilderSuite().copy(), OptimisticOptimizations.ALL,
        // graph.getProfilingInfo(),
        // backend.getSuites().getDefaultSuites(graph.getOptions()).copy());
        return graph;
    }

    /**
     * An object that holds a StructuredGraph, while being able to cache the translated node array
     * and referenced graphs. This object can be used in HashSets with the method as the hash key.
     */
    private static final class CacheEntry {
        private HashSet<CacheEntry> referencedGraphs = null;
        private StructuredGraph graph;
        private String methodName;
        private String nodeArray;

        private CacheEntry(StructuredGraph graph, String methodName) {
            this.graph = graph;
            this.methodName = methodName;

            try {
                nodeArray = VeriOptGraphTranslator.writeNodeArray(graph);
            } catch (IllegalArgumentException e) {
                System.out.println("Skipping graph " + methodName + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        @Override
        public int hashCode() {
            return methodName.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof CacheEntry) {
                return methodName.equals(((CacheEntry) other).methodName);
            }
            return false;
        }
    }

}
