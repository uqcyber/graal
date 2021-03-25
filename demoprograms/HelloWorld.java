public class HelloWorld {
       public static void main(String[] args) {
                for(int i=0; i < 1_000_000; i++) {
                        printInt(i);
                }
        }

        public static void printInt(int number) {
                System.err.println("Hello World" + number);
        }
}
