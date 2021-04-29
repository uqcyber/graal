// A basic implementation of a list

public class CustomList{
    private Object[] objects;
    int size;

    public CustomList(){
        objects = new Object[0];
        size = 0;
    }

    public void add_elem(Object elem){
        if (objects.length == size){
            // copy into doubly sized array
            Object[] new_array = new Object[(size+1) * 2];
            System.arraycopy(objects, 0, new_array, 0, objects.length);
            objects = new_array;
        }
        objects[size] = elem;
        size++;
    }

    public Object get_elem(int index){
        if (0 <= index && index < size){
            return objects[index];
        }
        return null;
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("CustomList(");
        for (int i= 0; i < size; i++){
            sb.append(objects[i]).append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    public static void main(String[] args){
        CustomList a = new CustomList();
        CustomList b = new CustomList();
        System.out.println(a.toString());
        a.add_elem(5);
        System.out.println(a.toString());
        a.add_elem(20);
        System.out.println(a.toString());

        System.out.println(a.getClass());
        System.out.println(a.getClass() == b.getClass());
    }
}