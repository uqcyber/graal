package org.graalvm.compiler.core.test;

import org.junit.Test;

/**
 * Ensure that bounds are kept tight by the conditional elimination phase.
 */
public class ConditionalEliminationTest17 extends ConditionalEliminationTestBase {
    public static void referenceSnippet1(int a) {
        if (a < 11) {
            if (9 < a) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet1(int a) {
        if (a < 11) {
            if (9 < a) {
                if (a == 10) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test1() {
        testConditionalElimination("testSnippet1", "referenceSnippet1");
    }
}
