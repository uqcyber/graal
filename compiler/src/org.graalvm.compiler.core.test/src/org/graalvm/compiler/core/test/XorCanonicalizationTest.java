package org.graalvm.compiler.core.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.NotNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.junit.Test;

public class XorCanonicalizationTest extends GraalCompilerTest {

    public static int xXorNotX(int x) {
        return (x ^ ~x);
    }

    public static int notXXorX(int x) {
        return (~x ^ x);
    }

    //  x ^ ~x |-> 111...111
    // ~x ^  x |-> 111...111
    private void checkNodes(String methodName) {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod(methodName));
        createCanonicalizerPhase().apply(graph, getProviders());

        // If there are no Xor or Not nodes in the graph, then the optimisation occurred.
        // Test should fail on this line.
        assertTrue(graph.getNodes().filter(XorNode.class).count() == 0);
        assertTrue(graph.getNodes().filter(NotNode.class).count() == 0);
    }

    @Test
    public void testXor() {
        checkNodes("xXorNotX");
        checkNodes("notXXorX");
    }
}
