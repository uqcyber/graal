package org.graalvm.compiler.core.test.veriopt;

import org.graalvm.compiler.core.test.ConditionalEliminationTestBase;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.core.veriopt.VeriOpt;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;

import java.io.PrintWriter;

public class ConditionalEliminationValidation extends OptimizationValidation {

    ConditionalEliminationValidation(String name) {
        super(name);
    }

    @Override
    protected String testPrefix() {
        return "ce_";
    }

    @Override
    protected void writeTest(PrintWriter writer, String beforeLabel, String afterLabel) {
        writer.println("corollary \"(runConditionalElimination " + beforeLabel + ") \\<approx>\\<^sub>s " + afterLabel + "\"");
        writer.println("  by eval");
    }

    public static void exportConditionalElimination(GraalCompilerTest test, String className, String snippet) {
        OptimizationValidation run = new ConditionalEliminationValidation(className + "_" + snippet);
        StructuredGraph graph = test.parseEager(snippet, StructuredGraph.AllowAssumptions.YES);
        run.begin(graph);
        DebugContext debug = graph.getDebug();
        CoreProviders context = test.getProviders();
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
}
