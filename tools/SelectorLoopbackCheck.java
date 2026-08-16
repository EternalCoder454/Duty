// Minimal reproducer for the "Unable to establish loopback connection" failure that
// stops Gradle (and any Java NIO application) from starting on this machine.
// Run with:  java tools/SelectorLoopbackCheck.java

import java.nio.channels.*;
public class SelectorLoopbackCheck {
    public static void main(String[] a) {
        try (Selector s = Selector.open()) {
            System.out.println("Selector.open() OK -> " + s.getClass().getName());
        } catch (Throwable t) {
            System.out.println("Selector.open() FAILED: " + t);
            Throwable c = t.getCause();
            while (c != null) { System.out.println("  caused by: " + c); c = c.getCause(); }
        }
        try {
            Pipe p = Pipe.open();
            System.out.println("Pipe.open() OK");
            p.sink().close(); p.source().close();
        } catch (Throwable t) {
            System.out.println("Pipe.open() FAILED: " + t);
        }
    }
}
