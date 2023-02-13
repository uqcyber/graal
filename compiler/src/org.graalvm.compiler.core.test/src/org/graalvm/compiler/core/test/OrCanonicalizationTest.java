package org.graalvm.compiler.core.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.NotNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.junit.Test;

public class OrCanonicalizationTest extends GraalCompilerTest {

    public static int xOrNotX(int x) {
        return (x | ~x);
    }

    public static int notXOrX(int x) {
        return (~x | x);
    }


    //  x | ~x |-> 111...111
    // ~x |  x |-> 111...111
    private void checkNodes(String methodName) {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(methodName));
        createCanonicalizerPhase().apply(graph, getProviders());

        // If there are no Or or Not nodes in the graph, then the optimisation occurred.
        // Test should fail on this line.
        assertTrue(graph.getNodes().filter(OrNode.class).count() == 0);
        assertTrue(graph.getNodes().filter(NotNode.class).count() == 0);
    }

    @Test
    public void testOr() {
        checkNodes("xOrNotX");
        checkNodes("notXOrX");
    }
}
