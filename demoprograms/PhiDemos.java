import java.util.Random;

public class PhiDemos {
    public static void main(String[] args) {
        Random r = new Random();
        for (int i =0; i < 1000000; i++){
            exec();
        }
    }

    public static int exec(){

        Random r = new Random();
        int a = r.nextInt(100);
        int b = r.nextInt(100);

        loop(a, b);
        nested_loop(a, b);
        nested_loop_complex(a, b);
        int out = nested_loop_very_complex(a, b);

        funCallRecursiveLoop(); // 155
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


    public static int funCallRecursiveLoop(){
        return recursiveLoop(5, 3);
    }

    public static int recursiveLoop(int a, int b){
        if (b<1){
            return 8;
        }

        int out = 0;
        for (int i=0; i < a; i++){
            out += a + recursiveLoop(i, b - 1);
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

    public static int nested_loop_complex(int a, int b){
        int out = 10;
        for (int i=0; i<4; i++){
            for (int j=0; j<3; j++){
                for (int k=2; k < i + 20; k+=i + 1){
                    out += i + j - k;
                }
            }
        }
        return out;
    }

    public static int nested_loop_very_complex(int a, int b){
        int out = 10;
        for (int i=0; i<4; i++){
            for (int j=0; j<3; j++){
                for (int k=2; k < i + 20; k+=i + 1){
                    if (k > 2*i){
                        out += j;
                    }
                    out += i + j - k;
                }
            }
        }
        return out;
    }
}
