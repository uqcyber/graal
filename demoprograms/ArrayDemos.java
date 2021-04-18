// import java.util.Arrays;
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
        funCallAverageArray();
        return 1;
    }

    public static void loop(){
        int a = 0;
        
        for (; a < 5 ; a++){
            ;
        }
    }

    // todo empty array tests, branch on null return tests

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
}