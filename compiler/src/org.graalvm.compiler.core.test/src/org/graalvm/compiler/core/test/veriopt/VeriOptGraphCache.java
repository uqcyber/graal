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
import org.graalvm.compiler.core.veriopt.VeriOpt;
import org.graalvm.compiler.core.veriopt.VeriOptGraphTranslator;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.StructuredGraph;
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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

public class VeriOptGraphCache {

    private static final Backend backend = Graal.getRuntime().getRequiredCapability(RuntimeProvider.class).getHostBackend();
    private static final Providers providers = backend.getProviders();

    private static HashMap<String, CacheEntry> cache = new HashMap<>();

    private final Function<ResolvedJavaMethod, StructuredGraph> graphBuilder;

    public VeriOptGraphCache(Function<ResolvedJavaMethod, StructuredGraph> graphBuilder) {
        this.graphBuilder = graphBuilder;
    }

    private CacheEntry getCacheEntry(ResolvedJavaMethod method) {
    //    return cache.computeIfAbsent(VeriOpt.formatMethod(method), key -> {  // TODO: turn caching back on?
        String key = VeriOpt.formatMethod(method);
            StructuredGraph graph = null;
            String nodeArray = null;
            RuntimeException exception = null;
//  TODO: turned off use of optimised graph temporarily, because then we miss called methods.
//            try {
//                if (graphBuilder != null) {
//                    // Optimised generation (if available)
//                    graph = graphBuilder.apply(method);
//                    nodeArray = VeriOptGraphTranslator.writeNodeArray(graph);
//                }
//            } catch (Exception e) {
//                // Optimised graph may cause problems, fall through to non-optimised generation
//            }

            try {
                if (nodeArray == null) {
                    // Unoptimised generation
                    graph = buildGraph(method);
                    nodeArray = VeriOptGraphTranslator.writeNodeArray(graph);
                }
            } catch (IllegalArgumentException ex) {
                // we wrap the exception with some context information about this graph
                // then throw it again so that the top-level method dump will fail.
                exception = ex;
            }
            if (VeriOpt.DEBUG) {
                System.out.printf("DEBUG:   key=%s for %s.%s %s graphlen=%d\n", key, method.getDeclaringClass().getName(), method.getName(), method.getSignature(), nodeArray.length());
            }
            return new CacheEntry(graph, key, nodeArray, exception);
    //    });
    }

    /**
     * Get a StructuredGraph from the cache. If it doesn't exist, it will be built.
     *
     * @param method The method to get the graph of
     * @return The StructuredGraph
     */
    public StructuredGraph getGraph(ResolvedJavaMethod method) {
        return getCacheEntry(method).graph;
    }

    /**
     * Get an Isabelle node array for the specified method.
     *
     * @param method The method to get the node array of
     * @return The node array, or null if it can't be translated
     */
    public String getNodeArray(ResolvedJavaMethod method) {
        return getCacheEntry(method).nodeArray;
    }

    /**
     * Get an Isabelle node array for the specified graph.
     *
     * @param graph The graph to get the node array of
     * @return The node array, or null if it can't be translated
     * @throws IllegalArgumentException if the graph could not be translated.
     */
    public String getNodeArray(StructuredGraph graph) {
        if (graph.method() == null) {
            // Can't cache a graph without a method
            if (VeriOpt.DEBUG) {
                System.out.println("DEBUG: getNodeArray for graph with no method! " + graph);
            }
            return VeriOptGraphTranslator.writeNodeArray(graph);
        }
        CacheEntry entry = getCacheEntry(graph.method());
        if (entry.exception != null) {
            throw entry.exception;
        }
        return entry.nodeArray;
    }

    /**
     * Get every graph being referenced by this method recursively.
     *
     * @param method The method to get the referenced graphs for
     * @return All graphs being referenced by this method and those graphs
     */
    public List<StructuredGraph> getReferencedGraphs(ResolvedJavaMethod method) {
        // Breadth First Search
        HashSet<CacheEntry> referenceSet = new LinkedHashSet<>();
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
            // if any of the referenced methods have errors, we cannot translate this method.
            if (entry.exception != null) {
                throw entry.exception;
            }
            referenceList.add(entry.graph);
        }

        return referenceList;
    }

    /**
     * Tries to find implementations of all method that might be called by the given CacheEntry method.
     * @param entry
     */
    private void resolveReferences(CacheEntry entry) {
        entry.referencedGraphs = new LinkedHashSet<>();

        if (entry.nodeArray == null || entry.exception != null) {
            // No point resolving references for a graph that doesn't translate
            return;
        }

        // <clinit>
        ResolvedJavaMethod clinit = entry.graph.method().getDeclaringClass().getClassInitializer();
        if (!entry.graph.method().isClassInitializer() && clinit != null) {
            CacheEntry cachedInit = getCacheEntry(clinit);
            entry.referencedGraphs.add(cachedInit);
        }

        // Graphs referenced by nodes in the graph
        for (Node node : entry.graph.getNodes()) {
            ResolvedJavaMethod method = null;

            if (node instanceof CallTargetNode) {
                method = ((CallTargetNode) node).targetMethod();
            }

            if (method != null && !method.isNative()) {
                // Find implementations of this method
                List<ResolvedJavaMethod> implementations = getImplementationsOf(method, entry.graph);
                for (ResolvedJavaMethod implementation : implementations) {
                    CacheEntry cached = getCacheEntry(implementation);
                    entry.referencedGraphs.add(cached);
                }
                CacheEntry cachedMethod = getCacheEntry(method);
                entry.referencedGraphs.add(cachedMethod);
                if (implementations.isEmpty()) {
                    cachedMethod.exception = new IllegalArgumentException("no implementations found for "
                     + VeriOpt.formatMethod(method));
                }
            }

            if (node instanceof NewInstanceNode) {
                VeriOptClassHierarchy.processClass(((NewInstanceNode) node).instanceClass());
            }

            if (node instanceof CallTargetNode) {
                VeriOptClassHierarchy.processClass(((CallTargetNode) node).targetMethod().getDeclaringClass());
                // TODO: should we search subclasses too?
            }
        }
    }

    // TODO: this seems naive?  It is only searching for class C where new C appears in this method.
    //  I think it needs to get the class from the method type???  Or the self parameter?
    // But resolveReferences above *does* look at methods from the method type.
    private static List<ResolvedJavaMethod> getImplementationsOf(ResolvedJavaMethod definition, StructuredGraph graph) {
        List<ResolvedJavaMethod> implementations = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            ResolvedJavaType type = null;

            if (node instanceof NewInstanceNode) {
                NewInstanceNode newInstanceNode = (NewInstanceNode) node;
                type = newInstanceNode.instanceClass();
            }

            if (type != null) {
                ResolvedJavaMethod impl = type.findMethod(definition.getName(), definition.getSignature());
                if (impl != null && !impl.isNative()) {
                    implementations.add(impl);
                }
            }
        }

        // also look for the method in the declared class.
        ResolvedJavaType type = definition.getDeclaringClass();
        ResolvedJavaMethod impl = type.findMethod(definition.getName(), definition.getSignature());
        if (impl != null && !impl.isNative()) {
            implementations.add(impl);
        }

        return implementations;
    }

    /**
     * Build an unoptimized StructuredGraph for the given method.
     *
     * @param method The method to build the StructuredGraph for
     * @return A StructuredGraph for the given method.
     */
    private static StructuredGraph buildGraph(ResolvedJavaMethod method) {
        OptionValues options = Graal.getRuntime().getRequiredCapability(OptionValues.class);
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
     *
     * The CacheEntry is valid if the nodeArray is non-null and the exception is null;
     *
     * TODO: change the hashcode and equals method to check the graph, not just the method name.
     */
    private static final class CacheEntry {
        private HashSet<CacheEntry> referencedGraphs = null;
        private StructuredGraph graph;
        private String methodName;
        private String nodeArray;
        private RuntimeException exception;  // non-null means this graph cannot be translated to Isabelle.

        private CacheEntry(StructuredGraph graph, String methodName, String nodeArray, RuntimeException exception) {
            this.graph = graph;
            this.methodName = methodName;
            this.nodeArray = nodeArray;
            this.exception = exception;
        }

        private CacheEntry(StructuredGraph graph, String methodName, String nodeArray) {
            this(graph, methodName, nodeArray, null);
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
