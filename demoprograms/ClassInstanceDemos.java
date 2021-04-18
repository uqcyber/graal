import java.util.ArrayList;

public class ClassInstanceDemos {
    public static void main(String [] args){
        ArrayList<Integer> x = new ArrayList<>();
        x.add(4);
        x.add(1);
        x.add(4);

        System.out.println(x);
        addElem(x);
        System.out.println(x);
    }

    public static void addElem(ArrayList<Integer> input_list){
        Integer b = input_list.remove(0);
        input_list.add(b+1);
    }
}
