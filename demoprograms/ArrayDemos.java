// import java.util.Arrays;
import java.util.Arrays;
import java.util.Random;

public class ArrayDemos {
    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            Random r = new Random();
            exec();
        }
    }
    public static int exec(){
        funCallPopulateArray();
        funCallMergeSort();
        funCallBigMergeSort();
        funCallAverageArray();

        funCallCreate2dArray();
        indexOfConstant();
        /*
        [1, 4, 7, 10, 13]
        [2, 5, 8, 11, 14]
        [3, 6, 9, 12, 15]
        [4, 7, 10, 13, 16]
        [5, 8, 11, 14, 17]
        */

        funCallLoopPhiCanonicalizerTest();
        funCallDecrIncrTest();
        
        return 1;
    }

    public static void loop(){
        int a = 0;
        
        for (; a < 5 ; a++){
            ;
        }
    }

    static int indexOfConstant() {
        String foobar = "foobar";
        String bar = "bar";
        return foobar.indexOf(bar);
    }


    // todo empty array tests, branch on null return tests, multidimensional arrays, arrays with non primitives

    static int[][] funCallCreate2dArray(){
        int[][] array = create2dArray();
        populate2dArray(array);
        return array;
    }

    static int[][] create2dArray(){
        int[][] arrOfarrs = new int[5][5];
        return arrOfarrs;
    }

    static void populate2dArray(int[][] arr){
        int j = 0;
        for (int[] subArr : arr){
            j++;
            for(int i = 0; i < subArr.length; i++){
                subArr[i] = 3 * i + j;
            }
        }
    }


    static int funCallAverageArray(){
        int[] arr = {1,2,3,4,5,6};
        return averageArray(arr);
    }

    static int averageArray(int[] values) {
        int sum = 0;
        for (int n = 0; n < values.length; n++) {
          sum += values[n];
        }
        return sum / values.length;
      }
      

    public static int[] funCallPopulateArray(){
        int[] testArray = new int[10];
        populateArray(testArray); // test that arrays are properly passed as params
        testArray[1] = 20;
        return testArray;
    }

    public static void populateArray (int[] arr){
        for (int i = 0; i < arr.length; i++) {   
            arr[i] = 2 * i;
        }
    }


    public static int[] funCallMergeSort(){
        // Standard merge sort algo using only arrays from https://www.baeldung.com/java-merge-sort
        int[] arr = {2,4,1,5,3,9,7,6,10,8};
        mergeSort(arr, arr.length);
        return arr;
    }


    public static int[] funCallBigMergeSort(){
        // Improper handling of this can cause stack overflow error
        int[] arr = {
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10,
            2,4,1,5,3,9,7,6,10,8,2,4,1,5,3,9,7,6,10,8, 2,4,1,5,3,9,7,6,10
        };
        mergeSort(arr, arr.length);
        return arr;
    }

    public static void merge(int[] a, int[] l, int[] r, int left, int right) {
        int i = 0, j = 0, k = 0;
        while (i < left && j < right) {
            if (l[i] <= r[j]) {
                a[k++] = l[i++];
            }
            else {
                a[k++] = r[j++];
            }
        }
        while (i < left) {
            a[k++] = l[i++];
        }
        while (j < right) {
            a[k++] = r[j++];
        }
    }

    public static void mergeSort(int[] a, int n) {
        if (n < 2) {
            return;
        }
        int mid = n / 2;
        int[] l = new int[mid];
        int[] r = new int[n - mid];
    
        for (int i = 0; i < mid; i++) {
            l[i] = a[i];
        }
        for (int i = mid; i < n; i++) {
            r[i - mid] = a[i];
        }
        mergeSort(l, mid);
        mergeSort(r, n - mid);
    
        merge(a, l, r, mid, n - mid);
    }


    // Based on the LoopPhiCanonicalizer Test


    private static int[] array = new int[1000];

    public static int funCallDecrIncrTest(){
        int[] array = {42};
        int d = 0;

        int sum = 0;
        while (d < 1) {
            // Or sum = array[d++]
            sum = array[d];
            d = d + 1;
        }
        return sum;
    }

    public static long funCallLoopPhiCanonicalizerTest() { // 199800
        // Originally the "before" function/////
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
        ///////////////////////////////////////
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;

        long sum = 0;
        while (d < 1000) {
            sum += array[a++] + array[b++] + array[c++] + array[d++];
        }
        return sum;
    }

}