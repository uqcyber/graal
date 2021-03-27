import java.util.Random;

class basic{
    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            //exec();
            average(5,4);
        }
    }

    static int comp = 0;

    public static void loop(int c){
        int a = 0;
        
        for (; a < 5 ; a++){
            comp = comp + c;
        }
    }

    public static int average(int a, int b) {
        return (a + b) / 2;
    }

    public static void add_field(int c){
        comp = comp + c;
    }

    public static void exec(){
        Random r = new Random();
        add_field(r.nextInt(100));
        loop(r.nextInt(100));
    }
}
