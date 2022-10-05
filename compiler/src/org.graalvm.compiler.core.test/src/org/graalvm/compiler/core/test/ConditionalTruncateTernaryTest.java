package org.graalvm.compiler.core.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.FloatLessThanNode;
import org.junit.Test;

public class ConditionalTruncateTernaryTest extends GraalCompilerTest {

    /** Tests for the TruncateTernary optimisations */
    public static double truncateTernary1(float x, float y) {
        return (y < 0.0 ? Math.ceil(x) : Math.floor(x));
    }

    public static double truncateTernary2(float x, float y) {
        return (0.0 < y ? Math.floor(x) : Math.ceil(x));
    }

    private void checkNodes(String methodName) {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(methodName));
        createCanonicalizerPhase().apply(graph, getProviders());

        // If there are no FloatLessThanNodes in the graph, then the optimisation ran, and the test should fail.
        assertFalse(graph.getNodes().filter(FloatLessThanNode.class).count() == 0);

        // Testing optimised value against unoptimised value
        float y = 200.7f;
        float x = -2.3f;
        test(methodName, x, y);
    }

    @Test
    public void testConditional() {
        checkNodes("truncateTernary1");
        checkNodes("truncateTernary2");
    }
}
