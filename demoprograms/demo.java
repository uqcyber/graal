class Demo {
  public static void main(String[] args) {

    int[] vals = new int[]{1,2,3,4};
    while (true) {
      workload(14, 2);
      average(vals);
    }
  }

  static int average(int[] values) {
    int sum = 0;
    for (int n = 0; n < values.length; n++) {
      sum += values[n];
    }
    return sum / values.length;
  }
  
  

  private static int workload(int a, int b) {
    
    return a + b;
  }
}
