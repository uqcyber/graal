import java.util.Random;

public class ControlFlow {

    static int a = 1;
    static int b = 4;
    static int c;
    static int d;

    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            loop();
            branch(1,2);
            branch2();
            exec();
        }
    }

    public static int exec(){
        Random r = new Random();
        funCallConstant();
        funCallParam();
        funCallTwoParam();
        funCallBranch();
        funCallNested();
        funCallMulti();
        funCallRecursive();
        funCallRecursiveComplex(); // returns 50
        funCallFib();
        return branch(r.nextInt(100), r.nextInt(100));
    }

    public static void loop(){
        int a = 0;
        
        for (; a < 5 ; a++){
            ;
        }
    }


    // Calls a function that returns a constant
    public static int funCallConstant(){
        int a = constantFun();
        return a;
    }

    // Calls a function that returns the supplied parameter
    public static int funCallParam(){
        int a = constantParam(5);
        return a;
    }

    // Calls a function that returns the supplied parameter
    public static int funCallTwoParam(){
        int a = addDoubleXtoY(5, 100);
        return a;
    }

    // Slightly more complicated function
    public static int funCallComplex(){
        int a = branch2() + 1;
        return a;
    }

    public static int funCallBranch(){
        return branch(3, 5); // returns 1 if a < b else 0
    }

    public static int funCallMulti(){
        int a = constantParam(3);
        int b = constantParam(10);
        int c = funCallNested();

        return add(c, add(a, b));
    }

    public static int funCallNested(){
        return add(add(100,200), 400);
    }

    public static int funCallRecursive(){
        return recursive(3);
    }

    public static int funCallRecursiveComplex(){
        return recursiveComplex(3, 10);
    }

    public static int recursive(int a){
        if (a < 0){
            return 5;
        } else {
            return recursive(a-1);
        }
    }

    public static int recursiveComplex(int a, int b){
        if (a < 0){
            return b;
        } else {
            return recursiveComplex(a-1, b) + b;
        }
    }

    public static int constantParam(int a){
        return a;
    }

    public static int constantFun(){
        return 4;
    }

    public static int add(int a, int b){
        return a + b;
    }

    public static int addDoubleXtoY(int a, int b){
        return 2*a + b;
    }

    public static int branch(int a, int b){

        int out = 0;
        if (a < b){
            out = 1;
        }
        return out;
    }
    public static int branch2(){
        a = 1;
        b = 2;
        int out = 0;

        if (a < b){
            out = 1;
        } else {
            out = 2;
        }

        return out;
    }

    public static int branch3(){
        a = 1;
        b = 2;
        int out = 0;

        if (a < b){
            out = 1;
        }

        return out;
    }

    public static int funCallFib(){
        return fibRecursion(10);
    }

    public static int fibRecursion(int n){
        if(n == 0){
            return 0;
        }
        if(n == 1 || n == 2){
                return 1;
            }
        return fibRecursion(n-2) + fibRecursion(n-1);
        }
}