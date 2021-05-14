public class ExactMathTest {

    public static void main(String[] args){
        // System.out.println("Test Add:");
        // System.out.println(add(1,2));
        // System.out.println(add(Integer.MAX_VALUE, 2));
        // System.out.println(add(Integer.MIN_VALUE, -1));
        // System.out.println(add(-1,2));

        // System.out.println("testLongMulHighUnsigned");
        // // ExactMath.multiplyHighUnsigned(a, b)
        // System.out.println(Long.MAX_VALUE);
        // System.out.println(ExactMath.multiplyHighUnsigned(7L, 15L));
        // System.out.println(ExactMath.multiplyHighUnsigned(Long.MAX_VALUE, 15L));
        // System.out.println(ExactMath.multiplyHighUnsigned( Long.MIN_VALUE, 15L));


        System.out.println("testLongSub");
        System.out.println(longSub((long) Integer.MIN_VALUE, 2L));
        // System.out.println(longSub(Long.MIN_VALUE, 2L));
    }


    // public void testMul() {
    //     test("mul", 1, 2);
    //     test("mul", -1, 2);
    //     test("mul", Integer.MIN_VALUE, 1);
    //     test("mul", Integer.MIN_VALUE, 2);
    //     test("mul", Integer.MIN_VALUE, Integer.MIN_VALUE);
    //     test("mul", Integer.MAX_VALUE, 1);
    //     test("mul", Integer.MAX_VALUE, 2);
    //     test("mul", Integer.MAX_VALUE, Integer.MAX_VALUE);
    // }


    // public void testSub() {
    //     test("sub", 1, 2);
    //     test("sub", Integer.MIN_VALUE, 2);
    // }


    // public void testMulHigh() {
    //     test("mulHigh", 7, 15);
    //     test("mulHigh", Integer.MAX_VALUE, 15);
    //     test("mulHigh", Integer.MIN_VALUE, 15);
    // }


    // public void testMulHighUnsigned() {
    //     test("mulHighUnsigned", 7, 15);
    //     test("mulHighUnsigned", Integer.MAX_VALUE, 15);
    //     test("mulHighUnsigned", 15, Integer.MAX_VALUE);
    //     test("mulHighUnsigned", Integer.MAX_VALUE, Integer.MAX_VALUE);
    //     test("mulHighUnsigned", 15, Integer.MIN_VALUE);
    //     test("mulHighUnsigned", Integer.MIN_VALUE, 15);
    //     test("mulHighUnsigned", Integer.MIN_VALUE, Integer.MIN_VALUE);
    // }

    // public void testLongAdd() {
    //     test("longAdd", (long) Integer.MAX_VALUE, 2L);
    //     test("longAdd", Long.MAX_VALUE, 2L);
    // }

    // public void testLongMul() {
    //     test("longMul", (long) Integer.MAX_VALUE, 2L);
    //     test("longMul", (long) Integer.MIN_VALUE, 2L);
    //     test("longMul", Long.MAX_VALUE, 2L);
    //     test("longMul", Long.MAX_VALUE, 1L);
    //     test("longMul", Long.MAX_VALUE, Long.MAX_VALUE);
    //     test("longMul", Long.MIN_VALUE, Long.MIN_VALUE);
    //     test("longMul", Long.MIN_VALUE, Long.MAX_VALUE);
    // }


    // public void testLongSub() {
    //     test("longSub", (long) Integer.MIN_VALUE, 2L);
    //     test("longSub", Long.MIN_VALUE, 2L);
    // }


    // public void testLongMulHigh() {
    //     test("longMulHigh", 7L, 15L);
    //     test("longMulHigh", Long.MAX_VALUE, 15L);
    //     test("longMulHigh", 15L, Long.MAX_VALUE);
    //     test("longMulHigh", Long.MAX_VALUE, Long.MAX_VALUE);
    //     test("longMulHigh", Long.MIN_VALUE, 15L);
    //     test("longMulHigh", 15L, Long.MIN_VALUE);
    //     test("longMulHigh", Long.MIN_VALUE, Long.MIN_VALUE);
    // }


    // public void testLongMulHighUnsigned() {
    //     test("longMulHighUnsigned", 7L, 15L);
    //     test("longMulHighUnsigned", Long.MAX_VALUE, 15L);
    //     test("longMulHighUnsigned", Long.MIN_VALUE, 15L);
    // }

    public static int add(int a, int b) {
        return Math.addExact(a, b);
    }

    public static int mul(int a, int b) {
        return Math.multiplyExact(a, b);
    }

    public static int sub(int a, int b) {
        return Math.subtractExact(a, b);
    }

    public static int mulHigh(int a, int b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static int mulHighUnsigned(int a, int b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }

    public static long longAdd(long a, long b) {
        return Math.addExact(a, b);
    }

    public static long longMul(long a, long b) {
        return Math.multiplyExact(a, b);
    }

    public static long longSub(long a, long b) {
        return Math.subtractExact(a, b);
    }

    public static long longMulHigh(long a, long b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static long longMulHighUnsigned(long a, long b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }
}
