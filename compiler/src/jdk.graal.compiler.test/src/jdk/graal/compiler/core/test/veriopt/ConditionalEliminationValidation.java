package jdk.graal.compiler.core.test.veriopt;

import jdk.graal.compiler.core.test.ConditionalEliminationTestBase;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.veriopt.VeriOpt;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;

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

        DebugContext debug = graph.getDebug();
        CoreProviders context = test.verioptGetProviders();
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest.ReferenceGraph", graph)) {
            CanonicalizerPhase phase = CanonicalizerPhase.create();
            phase.apply(graph, context);
            run.begin(graph);
            new ConditionalEliminationPhase(phase, true).apply(graph, context);

            /* Old implementation prior to ConditionalEliminationPhase constructor changes
            CanonicalizerPhase.create().apply(graph, context);
            run.begin(graph);
            new ConditionalEliminationPhase(true).apply(graph, context);
            */
        } catch (Throwable t) {
            debug.handle(t);
        }

        if (VeriOpt.DUMP_OPTIMIZATIONS && !test.areEqual(run.getInitialGraph(), graph)) {
            run.end(graph);
            run.export();
        }
    }
}
