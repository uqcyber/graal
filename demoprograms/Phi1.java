import java.util.Random;

public class Phi1 {
    public static void main(String[] args) {
        Random r = new Random();
        for (int i =0; i < 1000000; i++){
            //exampleIfNeverTaken(false, r.nextInt(), r.nextInt());
            exec();
        }
    }

    public static int exec(){

        Random r = new Random();
        int a = r.nextInt(100);
        int b = r.nextInt(100);

        loop(a, b);
        nested_loop(a, b);
        return branch(a,b);
    }

    public static int branch(int a, int b){

        int out = 10;
        if (a < b){
            out = 20;
        }
        return out;
    }

    public static int loop(int a, int b){
        int out = 10;
        for (int i=0; i<10; i++){
            out += i;
        }
        return out;
    }

    public static int nested_loop(int a, int b){
        int out = 10;
        for (int i=0; i<2; i++){
            for (int j=0; j<2; j++){
                out += i;
            }
        }
        return out;
    }

    private static volatile int intField;

    private static int exampleIfNeverTaken(boolean condition, int x, int y) {
        final int a;
        if (condition) {
            intField = x;
            a = x;
        } else {
            intField = y;
            a = y;
        }
        return a;
    }
}
