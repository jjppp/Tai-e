public class ThreadEscape {

    static Object staticField;

    public static void main(String[] args) throws Exception {
        A a1 = new A(); // escapes through static fields
        ThreadEscape.staticField = a1;

        A a2 = new A(); // escapes through spawning threads
        Thread thread = new Thread(new Runnable() { // thread and runnable objects also escape
            @Override
            public void run() {
                System.out.println(a2.toString());
            }
        });
        thread.start();

        A a3 = new A(); // escapes through indirect field access
        a1.f = a3;

        A a4 = new A(); // escapes through indirect field access
        a2.f = a4;

        A a5 = new A(); // does not escape
        a5.f = a2;
    }

    static class A {
        public Object f;
    }
}
