import java.util.function.Consumer;

public class FunctionalInterface {
    public static void main(String[] args) {
        Consumer<Integer> consumer = new MyConsumer()::accept;
        consumer.accept(42);
    }
}

class MyConsumer {
    void accept(Integer i) {
        System.out.println(i);
    }
}
