/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.test.veriopt.VeriOptTestUtil;
import org.graalvm.compiler.core.veriopt.VeriOpt;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.junit.Assert;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Collection of tests for {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase}
 * including those that triggered bugs in this phase.
 */
public class ConditionalEliminationTestBase extends GraalCompilerTest {
    protected static int sink0;
    protected static int sink1;
    protected static int sink2;

    /**
     * These tests assume all code paths in called routines are reachable so disable removal of dead
     * code based on method profiles.
     */
    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.RemoveNeverExecutedCode);
    }

    protected void testConditionalElimination(String snippet, String referenceSnippet) {
        testConditionalElimination(snippet, referenceSnippet, false, false);
        exportConditionalElimination(snippet, referenceSnippet);
    }

    @SuppressWarnings({"try", "unused"})
    protected void exportConditionalElimination(String snippet, String referenceSnippet) {
        TestRun run = new TestRun(this.getClass().getSimpleName() + "_" + snippet);
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        run.begin(graph);
        DebugContext debug = graph.getDebug();
        CoreProviders context = getProviders();
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest.ReferenceGraph", graph)) {
            new ConditionalEliminationPhase(true).apply(graph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }
        run.end(graph);
        if (VeriOpt.DUMP_OPTIMIZATIONS) {
            run.export();
        }
    }

    @SuppressWarnings("try")
    protected void testConditionalElimination(String snippet, String referenceSnippet, boolean applyConditionalEliminationOnReference, boolean applyLowering) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        CoreProviders context = getProviders();
        CanonicalizerPhase canonicalizer1 = createCanonicalizerPhase();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest", graph)) {
            prepareGraph(graph, canonicalizer1, context, applyLowering);
            new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
            canonicalizer.apply(graph, context);
            canonicalizer.apply(graph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }
        StructuredGraph referenceGraph = parseEager(referenceSnippet, AllowAssumptions.YES);
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest.ReferenceGraph", referenceGraph)) {
            prepareGraph(referenceGraph, canonicalizer, context, applyLowering);
            if (applyConditionalEliminationOnReference) {
                new ConditionalEliminationPhase(true).apply(referenceGraph, context);
            }
            canonicalizer.apply(referenceGraph, context);
            canonicalizer.apply(referenceGraph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }

        assertEquals(referenceGraph, graph);
    }

    /**
     * Collect a copy of the a graph initially and after canonicalization.
     *
     * Exports the initial and final graphs to Isabelle encoding for validation.
     */
    private static class TestRun {
        // name of the test being run
        private final String name;
        private StructuredGraph initialGraph;
        private StructuredGraph finalGraph;

        TestRun(String name) {
            this.name = name;
        }

        protected void begin(StructuredGraph graph) {
            initialGraph = (StructuredGraph) graph.copy((String) null, DebugContext.forCurrentThread());
        }

        protected void end(StructuredGraph graph) {
            finalGraph = (StructuredGraph) graph.copy((String) null, DebugContext.forCurrentThread());
        }

        /**
         * Export the copy of the initial graph and final graph to Isabelle.
         *
         * hint: run with `mx unittest --regex ConditionalElimination.*`
         */
        protected void export() {
            try {
                String encodedInitial = new VeriOptTestUtil().dumpGraph(initialGraph, name + "_initial");
                String encodedReference = new VeriOptTestUtil().dumpGraph(finalGraph, name + "_final");
                String outFile = "ce_" + name + ".test";
                try (PrintWriter out = new PrintWriter(outFile)) {
                    out.println("\n(* initial: " + name + "*)\n" + encodedInitial);
                    out.println("\n(* final: " + name + "*)\n" + encodedReference);
                } catch (IOException ex) {
                    System.err.println("Error writing " + outFile + ": " + ex);
                }
            } catch (IllegalArgumentException ex) {
                if (VeriOpt.DEBUG) {
                    System.out.println("skip conditional_elimination_test " + name + ": " + ex.getMessage());
                }
            }
        }
    }

    protected void prepareGraph(StructuredGraph graph, CanonicalizerPhase canonicalizer, CoreProviders context, boolean applyLowering) {
        if (applyLowering) {
            new ConvertDeoptimizeToGuardPhase().apply(graph, context);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            canonicalizer.apply(graph, context);
        }
        canonicalizer.apply(graph, context);
        new ConvertDeoptimizeToGuardPhase().apply(graph, context);
    }

    public void testProxies(String snippet, int expectedProxiesCreated) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        CoreProviders context = getProviders();
        CanonicalizerPhase canonicalizer1 = CanonicalizerPhase.createWithoutCFGSimplification();
        canonicalizer1.apply(graph, context);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);

        int baseProxyCount = graph.getNodes().filter(ProxyNode.class).count();
        new ConditionalEliminationPhase(true).apply(graph, context);
        canonicalizer.apply(graph, context);
        new SchedulePhase(graph.getOptions()).apply(graph, context);
        int actualProxiesCreated = graph.getNodes().filter(ProxyNode.class).count() - baseProxyCount;
        Assert.assertEquals(expectedProxiesCreated, actualProxiesCreated);
    }
}
