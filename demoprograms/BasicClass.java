public class BasicClass {
    public static void main(String[] args) {
        for (int i =0; i < 1000000; i++){
            constantFun();
            loop();
            exec();
        }
    }

    public static int exec(){
        funCallConstant();
        loop();
        return constantFun();
    }

    // Calls a function that returns a constant
    public static int funCallConstant(){
        int a = constantFun();
        return a;
    }

    public static int constantFun(){
        return 42;
    }

    public static void loop(){
        int a = 0;
        
        for (; a < 5 ; a++){
            ;
        }
    }
}