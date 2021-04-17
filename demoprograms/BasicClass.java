import java.util.Random;

public class BasicClass {
    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            Random r = new Random();
            exec();
        }
    }

    public static int exec(){
        // Integer m = 4;
        

        // Objects in java are either instances of classes or arrays
        int[] testArray = new int[50];

        testArray[1] = 20;

        int b = testArray[1];

        return b + 1;

        // funCallParam();
        // return funCallConstant();
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

    public static int constantParam(int a){
        return a;
    }

    public static int constantFun(){
        return 4;
    }
}