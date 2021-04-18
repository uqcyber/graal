import java.util.ArrayList;
import java.util.Random;

public class ClassInstanceDemos {
    public class Point{
        private int x;
        private int y;

        public Point(int a, int b){
            this.x = a;
            this.y = b;
        }
        public int get_x(){
            return this.x;
        }
        public int get_y(){
            return this.y;
        }
    }

    public static void main(String[] args) {
        Random r = new Random();
        for (int i =0; i < 1000000; i++){
            exec();
        }
    }

    public static void exec(){
        Random r = new Random();
        int a = r.nextInt(100);
        int b = r.nextInt(100);

        ArrayList<Integer> x = createList();
        Integer c = createInteger();
        createAndAccessInteger();

        createBoolean();
        createAndAccessBoolean();

        // System.out.println(x);
        addElem(x);
        // System.out.println(x);
    }

    public static Integer createInteger(){
        Integer a = new Integer(4);
        return a;
    }


    public static Boolean createBoolean(){
        Boolean a = new Boolean(true);
        return a;
    }

    public static int createAndAccessInteger(){
        Integer a = createInteger();
        int b = a.intValue();
        return b + 3;
    }

    public static boolean createAndAccessBoolean(){
        Boolean a = createBoolean();
        boolean b = a.booleanValue();
        return b;
    }

    public Point create_point(){
        return new Point(1,2);
    }

    public static ArrayList<Integer> createList(){
        ArrayList<Integer> x = new ArrayList<>();
        x.add(4);
        x.add(1);
        x.add(4);
        return x;
    }

    public static void addElem(ArrayList<Integer> input_list){
        Integer b = input_list.remove(0);
        input_list.add(b+1);
    }
}
