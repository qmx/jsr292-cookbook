import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import jsr292.cookbook.tryfinally.Interceptors;

import static java.lang.invoke.MethodType.*;

public class BeforeMain {
  public static void test1() {
    System.out.println("test1");
  }
  
  public static int test2(int value) {
    System.out.println("test2");
    return 42 + value;
  }
  
  public static void foo() {
    System.out.println("foo");
  }
  
  public static void main(String[] args) throws Throwable {
    Lookup lookup = MethodHandles.lookup();
    MethodHandle test1 = lookup.findStatic(BeforeMain.class, "test1",
        methodType(void.class));
    MethodHandle test2 = lookup.findStatic(BeforeMain.class, "test2",
        methodType(int.class, int.class));
    MethodHandle foo = lookup.findStatic(BeforeMain.class, "foo",
        methodType(void.class));
    
    MethodHandle before1 = Interceptors.before(test1, foo);
    before1.invokeExact();
    
    MethodHandle before2 = Interceptors.before(test2, foo);
    System.out.println((int)before2.invokeExact(7));
  }
}
