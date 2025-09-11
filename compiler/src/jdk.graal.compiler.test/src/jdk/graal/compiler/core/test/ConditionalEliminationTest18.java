package jdk.graal.compiler.core.test;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Enumerating the possible permutations of the set of {=, !=, <, >=}
 */
@Ignore("GraalVM currently does not support optimizing transitive conditions")
public class ConditionalEliminationTest18 extends ConditionalEliminationTestBase {
    /** x = y & u = x -> u = y */
    public static void referenceSnippet1(int x, int y, int u) {
        if (x == y) {
            if (u == x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet1(int x, int y, int u) {
        if (x == y) {
            if (u == x) {
                if (u == y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test1() {
        testConditionalElimination("testSnippet1", "referenceSnippet1");
    }

    /** x = y & u = y -> u = x */
    public static void referenceSnippet2(int x, int y, int u) {
        if (x == y) {
            if (u == y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet2(int x, int y, int u) {
        if (x == y) {
            if (u == y) {
                if (u == x) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test2() {
        testConditionalElimination("testSnippet2", "referenceSnippet2");
    }

    /** x = y & x != y -> false */
    public static void referenceSnippet3(int x, int y, int u) {
        if (x == y) {
            sink1 = 10;
        }
    }

    public static void testSnippet3(int x, int y, int u) {
        if (x == y) {
            if (x != y) {
                sink0 = 0;
            }
            sink1 = 10;
        }
    }

    @Test
    public void test3() {
        testConditionalElimination("testSnippet3", "referenceSnippet3");
    }

    /** x = y & u != x -> u != y */
    public static void referenceSnippet4(int x, int y, int u) {
        if (x == y) {
            if (u != x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet4(int x, int y, int u) {
        if (x == y) {
            if (u != x) {
                if (u != y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test4() {
        testConditionalElimination("testSnippet4", "referenceSnippet4");
    }

    /** x = y & u != y -> u != x */
    public static void referenceSnippet5(int x, int y, int u) {
        if (x == y) {
            if (u != y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet5(int x, int y, int u) {
        if (x == y) {
            if (u != y) {
                if (u != x) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test5() {
        testConditionalElimination("testSnippet5", "referenceSnippet5");
    }

    /** x = y & u < x -> u < y */
    public static void referenceSnippet6(int x, int y, int u) {
        if (x == y) {
            if (u < x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet6(int x, int y, int u) {
        if (x == y) {
            if (u < x) {
                if (u < y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test6() {
        testConditionalElimination("testSnippet6", "referenceSnippet6");
    }

    /** x = y & y < u -> x < u */
    public static void referenceSnippet7(int x, int y, int u) {
        if (x == y) {
            if (y < u) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet7(int x, int y, int u) {
        if (x == y) {
            if (y < u) {
                if (x < y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test7() {
        testConditionalElimination("testSnippet7", "referenceSnippet7");
    }

    /** x = y & u >= x -> u >= y */
    public static void referenceSnippet8(int x, int y, int u) {
        if (x == y) {
            if (u >= x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet8(int x, int y, int u) {
        if (x == y) {
            if (u >= x) {
                if (u >= y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test8() {
        testConditionalElimination("testSnippet8", "referenceSnippet8");
    }

    /** x = y & x >= u -> y >= u */
    public static void referenceSnippet9(int x, int y, int u) {
        if (x == y) {
            if (x >= u) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet9(int x, int y, int u) {
        if (x == y) {
            if (x >= u) {
                if (y >= u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test9() {
        testConditionalElimination("testSnippet9", "referenceSnippet9");
    }

    /** x = y & u >= y -> u >= x */
    public static void referenceSnippet10(int x, int y, int u) {
        if (x == y) {
            if (u >= y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet10(int x, int y, int u) {
        if (x == y) {
            if (u >= y) {
                if (u >= x) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test10() {
        testConditionalElimination("testSnippet10", "referenceSnippet10");
    }

    /** x = y & y >= u -> x >= u */
    public static void referenceSnippet11(int x, int y, int u) {
        if (x == y) {
            if (y >= u) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet11(int x, int y, int u) {
        if (x == y) {
            if (y >= u) {
                if (x >= u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test11() {
        testConditionalElimination("testSnippet11", "referenceSnippet11");
    }

    /** x != y & x = y -> false */
    public static void referenceSnippet12(int x, int y) {
        if (x != y) {
            sink1 = 10;
        }
    }

    public static void testSnippet12(int x, int y) {
        if (x != y) {
            if (x == y) {
                sink0 = 0;
            }
            sink1 = 10;
        }
    }

    @Test
    public void test12() {
        testConditionalElimination("testSnippet12", "referenceSnippet12");
    }

    /** x != y & x >= y -> x > y */
    public static void referenceSnippet13(int x, int y) {
        if (x != y) {
            if (x >= y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet13(int x, int y) {
        if (x != y) {
            if (x >= y) {
                if (x > y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test13() {
        testConditionalElimination("testSnippet13", "referenceSnippet13");
    }

    /** x != y & y >= x -> y > x */
    public static void referenceSnippet14(int x, int y) {
        if (x != y) {
            if (y >= x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet14(int x, int y) {
        if (x != y) {
            if (y >= x) {
                if (y > x) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test14() {
        testConditionalElimination("testSnippet14", "referenceSnippet14");
    }

    /** x < y & u = x -> u < y */
    public static void referenceSnippet15(int x, int y, int u) {
        if (x < y) {
            if (u == x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet15(int x, int y, int u) {
        if (x < y) {
            if (u == x) {
                if (u < y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test15() {
        testConditionalElimination("testSnippet15", "referenceSnippet15");
    }

    /** x < y & u = y -> x < u */
    public static void referenceSnippet16(int x, int y, int u) {
        if (x < y) {
            if (u == y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet16(int x, int y, int u) {
        if (x < y) {
            if (u == y) {
                if (x < u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test16() {
        testConditionalElimination("testSnippet16", "referenceSnippet16");
    }

    /** x < y & u < x -> u < y */
    public static void referenceSnippet17(int x, int y, int u) {
        if (x < y) {
            if (u < x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet17(int x, int y, int u) {
        if (x < y) {
            if (u < x) {
                if (u < y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test17() {
        testConditionalElimination("testSnippet17", "referenceSnippet17");
    }

    /** x < y & u >= y -> u > x */
    public static void referenceSnippet18(int x, int y, int u) {
        if (x < y) {
            if (u >= y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet18(int x, int y, int u) {
        if (x < y) {
            if (u >= y) {
                if (u > x) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test18() {
        testConditionalElimination("testSnippet18", "referenceSnippet18");
    }

    /** x < y & y < u -> x < u */
    public static void referenceSnippet19(int x, int y, int u) {
        if (x < y) {
            if (y < u) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet19(int x, int y, int u) {
        if (x < y) {
            if (y < u) {
                if (x < u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test19() {
        testConditionalElimination("testSnippet19", "referenceSnippet19");
    }

    /** x < y & x >= u -> y > u */
    public static void referenceSnippet20(int x, int y, int u) {
        if (x < y) {
            if (x >= u) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet20(int x, int y, int u) {
        if (x < y) {
            if (x >= u) {
                if (y > u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test20() {
        testConditionalElimination("testSnippet20", "referenceSnippet20");
    }

    /** x < y & x >= y -> false */
    public static void referenceSnippet21(int x, int y) {
        if (x < y) {
            sink1 = 10;
        }
    }

    public static void testSnippet21(int x, int y) {
        if (x < y) {
            if (x >= y) {
                sink0 = 0;
            }
            sink1 = 10;
        }
    }

    @Test
    public void test21() {
        testConditionalElimination("testSnippet21", "referenceSnippet21");
    }

    /** x >= y & u = x -> u >= y */
    public static void referenceSnippet22(int x, int y, int u) {
        if (x >= y) {
            if (u == x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet22(int x, int y, int u) {
        if (x >= y) {
            if (u == x) {
                if (u >= y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test22() {
        testConditionalElimination("testSnippet22", "referenceSnippet22");
    }

    /** x >= y & u = y -> x >= u */
    public static void referenceSnippet23(int x, int y, int u) {
        if (x >= y) {
            if (u == y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet23(int x, int y, int u) {
        if (x >= y) {
            if (u == y) {
                if (x >= u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test23() {
        testConditionalElimination("testSnippet23", "referenceSnippet23");
    }

    /** x >= y & x != y -> y < x */
    public static void referenceSnippet24(int x, int y) {
        if (x >= y) {
            if (x != y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet24(int x, int y) {
        if (x >= y) {
            if (x != y) {
                if (y < x) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test24() {
        testConditionalElimination("testSnippet24", "referenceSnippet24");
    }

    /** x >= y & u < y -> u < x */
    public static void referenceSnippet25(int x, int y, int u) {
        if (x >= y) {
            if (u < y) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet25(int x, int y, int u) {
        if (x >= y) {
            if (u < y) {
                if (u < x) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test25() {
        testConditionalElimination("testSnippet25", "referenceSnippet25");
    }

    /** x >= y & x < u -> y < u */
    public static void referenceSnippet26(int x, int y, int u) {
        if (x >= y) {
            if (x < u) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet26(int x, int y, int u) {
        if (x >= y) {
            if (x < u) {
                if (y < u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test26() {
        testConditionalElimination("testSnippet26", "referenceSnippet26");
    }

    /** x >= y & y >= u -> x >= u */
    public static void referenceSnippet27(int x, int y, int u) {
        if (x >= y) {
            if (y >= u) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet27(int x, int y, int u) {
        if (x >= y) {
            if (y >= u) {
                if (x >= u) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test27() {
        testConditionalElimination("testSnippet27", "referenceSnippet27");
    }

    /** x >= y & u >= x -> u >= y */
    public static void referenceSnippet28(int x, int y, int u) {
        if (x >= y) {
            if (u >= x) {
                sink0 = 0;
            }
        }
    }

    public static void testSnippet28(int x, int y, int u) {
        if (x >= y) {
            if (u >= x) {
                if (u >= y) {
                    sink0 = 0;
                }
            }
        }
    }

    @Test
    public void test28() {
        testConditionalElimination("testSnippet28", "referenceSnippet28");
    }
}
