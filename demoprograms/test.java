

class Addition{
    public static void main(String[] args) {
        while(true){
            loop();
            adder(1,2);
            average(6, 8);
            basicAdd();
            basicAdd2();
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

    public static int branch(int a, int b){
        int out = 0;

        if (a < b){
            out = 1;
        }

        return out;
    }
    
    public static int basicAdd(){
        return 1 + 1;
    }

    static int a = 1;
    static int b = 1;

    public static int basicAdd2(){
        return a + b;
    }
    
}
