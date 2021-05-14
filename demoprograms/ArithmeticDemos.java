import java.util.Random;

class ArithmeticDemos{

    static int a = 1;
    static int b = 4;
    static int c = 8;
    static int d;

    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            exec();
        }
    }

    public static void exec(){
        long s = 1235;

        Random r = new Random();
        r.setSeed(s);

        //Subtraction
        subFields();

        //Addition
        addConstants(); //5
        addParams(r.nextInt(100), r.nextInt(100)); //74
        addFields(); //5
        addFields2(); //9
        addFields3(); //26


        averageConstants(); // 3
        averageFields(); // 32
        averageFields2(); // 
        averageFields3(); // 1
        averageParams(r.nextInt(100), r.nextInt(100)); // 45

        // System.out.println(out);

        // Exact math tests
        longSubTest(); //causes ArithmeticException: long overflow
        longSubTest2(); //causes ArithmeticException: long overflow

    }

    // Throws exception
    public static long longSubTest(){
        try {
            return longSub(Long.MIN_VALUE, 2L);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    //Does not throw exception -> returns -2147483650
    public static long longSubTest2(){
        try {
            return longSub((long) Integer.MIN_VALUE, 2L);
        } catch (ArithmeticException e) {
            return 0;
        }
    }


    // From ExactMathTest.java
    public static long longSub(long a, long b) {
        return Math.subtractExact(a, b);
    }


    public static int subFields(){
        return a - b;
    }

    public static int addConstants(){
        return 2 + 3;
    }

    // Add parameters
    public static int addParams(int a, int b){
        return a + b;
    }

    // Adds Static Fields a, b
    public static int addFields(){
        return a + b;
    }

    // Initialises and adds Static Fields c,d
    public static int addFields2(){
        c = 4;
        d = 5;
        return c + d;
    }

    // Uses the return value of an add in a following add operation
    public static int addFields3(){
        c = 12;
        d = 2;
        int e = c + d;
        return e + c;
    }

    // Average (div nodes)
    
    public static int averageConstants() {
        return (5 + 2) / 2;
    }

    // Is simplified to RighShift node
    public static int averageFields() {
        a = 5;
        b = 60;
        return (a + b) / 2;
    }

    // Is Simplified to signed div node
    public static int averageFields2() {
        a = 3;
        b = 4;
        c = 5;
        return (a + b + c) / 3;
    }

    public static int averageFields3() {
        return (a + b + c) / c;
    }

    public static int averageParams(int a, int b) {
        return (a + b) / 2;
    }
}
