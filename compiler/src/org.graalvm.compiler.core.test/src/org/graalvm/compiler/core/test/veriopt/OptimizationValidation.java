package org.graalvm.compiler.core.test.veriopt;

import org.graalvm.compiler.core.veriopt.VeriOpt;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Collect a copy of the graph before and after optimization.
 * Exports the initial and final graphs to an Isabelle encoding for validation.
 */
public abstract class OptimizationValidation {
    // name of the test being run
    private final String name;
    private StructuredGraph initialGraph;
    private StructuredGraph finalGraph;

    public OptimizationValidation(String name) {
        this.name = name;
    }

    public void begin(StructuredGraph graph) {
        initialGraph = (StructuredGraph) graph.copy((String) null, DebugContext.forCurrentThread());
    }

    public void end(StructuredGraph graph) {
        finalGraph = (StructuredGraph) graph.copy((String) null, DebugContext.forCurrentThread());
    }

    protected abstract void writeTest(PrintWriter writer, String beforeLabel, String afterLabel);

    protected abstract String testPrefix();

    /**
     * Export the copy of the initial graph and final graph to Isabelle.
     * <p>
     * hint: run with `mx unittest --regex ConditionalElimination.*`
     */
    public void export() {
        try {
            String encodedInitial = new VeriOptTestUtil().dumpGraph(initialGraph, name + "_initial");
            String encodedReference = new VeriOptTestUtil().dumpGraph(finalGraph, name + "_final");
            String outFile = VeriOpt.DUMP_OPTIMIZATIONS_PATH + "/" + testPrefix() + name + ".test";
            try (PrintWriter out = new PrintWriter(outFile)) {
                out.println("\n(* initial: " + name + " *)\n" + encodedInitial);
                out.println("\n(* final: " + name + " *)\n" + encodedReference);
                out.println();
                writeTest(out, name + "_initial", name + "_final");

            } catch (IOException ex) {
                System.err.println("Error writing " + outFile + ": " + ex);
            }
        } catch (IllegalArgumentException ex) {
            if (VeriOpt.DEBUG) {
                System.out.println("skip " + testPrefix() + name + ": " + ex.getMessage());
            }
        }
    }
}
