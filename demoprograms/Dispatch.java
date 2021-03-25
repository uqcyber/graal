class Dispatch {

    static class A {
	public void print() {
	    System.out.print("A");
	}
    }
    static class B extends A {
	public void print() {
	    System.out.print("B");
	}
    }

    public static void main(String[] args) {
	A a = new A();
	B b = new B();
	A ba = b;
	a.print();
	b.print();
	ba.print();
    }
    
}