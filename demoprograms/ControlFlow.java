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
        return branch(r.nextInt(100), r.nextInt(100));
    }

    public static void loop(){
        int a = 0;
        
        for (; a < 5 ; a++){
            ;
        }
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

    public static int branch6(){
        a = 1;
        b = 2;
        int out = 0;

        if (a < b){
            out = 1;
        }

        return out;
    }
}