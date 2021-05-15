import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class ClassInstanceDemos {
    public static class Point{
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
        createIfInteger(new Integer(0));
        listFromArray();
        populateList();
        createPoint();

        populateCustomList();

        funCallStringLength();
        funCallIndexOfString();

        // System.out.println(x);
        // addElem(x);
        // System.out.println(x);
    }

    public static Integer createInteger(){
        Integer a = new Integer(4);
        return a;
    }

    public static String createString(){
        return "FooBar";
    }

    public static int funCallStringLength(){
        return stringLength(createString());
    }

    public static int funCallIndexOfString(){
        String a = createString();
        return a.indexOf("B");
    }


    public static int stringLength(String testString){
        return testString.length();
    }


    public static Integer funCallcreateInteger(){
        return createIfInteger(new Integer(3));
    }

    public static Integer createIfInteger(Integer input){
        Integer a;
        if (input.intValue() == 0){
            a = input;
        
        } else {
            a = null;
        }

        return a.intValue();
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

    public static Point createPoint(){
        Point a =  new Point(1,2);
        return a;
    }

    public static ArrayList<Integer> createList(){
        ArrayList<Integer> x = new ArrayList<>();

        int a = x.size();

        return x;
    }

    public static ArrayList<Integer> populateList(){
        ArrayList<Integer> x = new ArrayList<>();
        x.add(7879);
        // x.add(1);
        // x.add(4);
        return x;
    }

    public static ArrayList<Integer> listFromArray(){
        Integer[] a = {new Integer(1), new Integer(2)};
        ArrayList<Integer> listA = new ArrayList<Integer>(Arrays.asList(a));

        return listA;
    }

    public static void addElem(ArrayList<Integer> input_list){
        Integer b = input_list.remove(0);
        input_list.add(b+1);
    }

    public static CustomList createCustomList(){
        return new CustomList();
    }

    public static CustomList populateCustomList(){
        CustomList a = createCustomList();

        a.add_elem(1);
        a.add_elem(2);

        return a;
    }
}
