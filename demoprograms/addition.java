import java.util.Random;

class Addition{
    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            loop();
            adder(1,2);
            average(6, 8);
            basicAdd();
            basicAdd2();
            basicAdd3();
            basicAdd4();
            branch(1,2);
            branch2();
            exec();
        }
    }

    public static int exec(){

        Random r = new Random();
        return branch(r.nextInt(100), r.nextInt(100));
    }

    public static int average(int a, int b) {
        return (a + b) / 2;
    }

    public static int adder(int a, int b){
        return a + b;
    }

    public static void loop(){
        int a = 0;
        
        for (; a < 5 ; a++){
            ;
        }
    }

    static int a = 1;
    static int b = 4;
    static int c;
    static int d;

    public static int branch(int a, int b){

        int out = 0;
        if (a < b){
            out = 1;
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

    public static int branch2(){
        a = 1;
        b = 2;
        int out = 0;

        if (a < b){
            out = 1;
        } else {
            out = 3;
        }

        return out;
    }

    
    public static int basicAdd(){
        return 2 + 3;
    }

    public static int basicAdd2(){
        return a + b;
    }

    public static int basicAdd3(){
        c = 4;
        d = 5;
        return c + d;
    }

    public static int basicAdd4(){
        c = 12;
        d = 2;
        int e = c + d;
        return e + c;
    }
    
}
