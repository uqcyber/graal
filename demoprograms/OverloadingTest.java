class OverloadingTest {

    static class A {}
    static class B extends A {}

    static private void print(A a) {
	System.out.print("A");
    }

    static private void print(B b) {
	System.out.print("B");
    }

    public static void main(String[] args) {
	A a = new A();
	B b = new B();
	A ba = b;
	print(a);
	print(b);
	print(ba);
    }
    
}