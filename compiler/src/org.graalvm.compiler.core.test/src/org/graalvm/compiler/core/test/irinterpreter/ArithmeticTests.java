// White box unit tests based on design outlined in InvokeHintsTest

package org.graalvm.compiler.core.test.irinterpreter;

import org.graalvm.compiler.core.GraalInterpreter;
import org.graalvm.compiler.core.runtimetypes.RTInteger;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.RuntimeType;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;


public class ArithmeticTests extends GraalCompilerTest{
    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    /**
     * Dummy method to avoid javac dead code elimination.
     */
    private static void test() {
    }

    public RuntimeType genericTest(String targetSnippet){
        // todo replace null with high tier context
        GraalInterpreter interpreter = new GraalInterpreter(null, false);
        StructuredGraph graph = parseEager(targetSnippet, StructuredGraph.AllowAssumptions.NO);
        return interpreter.executeGraph(graph);
    }

    @Test
    public void testAddConstants() {
        RTInteger output = (RTInteger) genericTest("testAddConstantsSnippet");
        RTInteger expected = new RTInteger(5);
        Assert.assertEquals(output.getValue(), expected.getValue());
    }
    public static int testAddConstantsSnippet(){
        return 2 + 3;
    }


    @Test //todo
    public void testPHI() {
        RuntimeType output = genericTest("testPHISnippet");
    }
    public static int testPHISnippet(int a) {
        if (a > 1) {
            test();
        }
        return a;
    }

    // Example tests
//    public static int const1() {
//        return 1;
//    }
//
//    public static int const7() {
//        return 7;
//    }
//    @Test
//    public void test2() {
//        test("test2Snippet");
//    }
//    @SuppressWarnings("all")
//    public static int test2Snippet() {
//        return const1() + const1() + const1() + const1() + const1() + const1() + const1();
//    }
//    private void test(String snippet) {
//        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO);
//        Map<Invoke, Double> hints = new HashMap<>();
//        for (Invoke invoke : graph.getInvokes()) {
//            hints.put(invoke, 1000d);
//        }
//
//        HighTierContext context = getDefaultHighTierContext();
//        createInliningPhase(hints, createCanonicalizerPhase()).apply(graph, context);
//        createCanonicalizerPhase().apply(graph, context);
//        new DeadCodeEliminationPhase().apply(graph);
//        StructuredGraph referenceGraph = parseEager(REFERENCE_SNIPPET, StructuredGraph.AllowAssumptions.NO);
//        assertEquals(referenceGraph, graph);
//    }
}
