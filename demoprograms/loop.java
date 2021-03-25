import java.util.Random;

public class loop {
    public static void main(String[] args) {
        Random r = new Random();
        for (int i =0; i < 1000000; i++){
            //exampleIfNeverTaken(false, r.nextInt(), r.nextInt());
            exec();
        }
    }

    public static int exec(){
        Random r = new Random();
        return looper(r.nextInt(100));
    }

    public static int looper(int a){
        int out = 10;
        for (int i=0; i<10; i++){
            out += a;
        }
        return out;
    }
}
