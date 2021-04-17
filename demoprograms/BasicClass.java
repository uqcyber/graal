import java.util.Arrays;
import java.util.Random;

public class BasicClass {
    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            Random r = new Random();
            exec();
        }
    }

    public static int[] exec(){
        // Integer m = 4;
        

        // Objects in java are either instances of classes or arrays
        int[] testArray = new int[50];

        // for (int i = 0; i < testArray.length; i++) {   
        //     testArray[i] = i;
        // }

        populateArray(testArray); // test that arrays are properly passed as params

        testArray[1] = 20;

        
        // System.out.println(Arrays.toString(testArray));
        return testArray;

        // funCallParam();
        // return funCallConstant();
    }

    public static void loop(){
        int a = 0;
        
        for (; a < 5 ; a++){
            ;
        }
    }

    public static void populateArray (int[] arr){
        for (int i = 0; i < arr.length; i++) {   
            arr[i] = i;
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