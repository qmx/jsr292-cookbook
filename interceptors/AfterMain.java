import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import jsr292.cookbook.tryfinally.Interceptors;

import static java.lang.invoke.MethodType.*;

public class AfterMain {
  public static void test1() {
    System.out.println("test1");
  }
  
  public static int test2(int value) {
    System.out.println("test2");
    return 42 + value;
  }
  
  public static void foo1() {
    System.out.println("foo1");
  }
  
  public static void foo2(int returnValue) {
    System.out.println("foo2 "+returnValue);
  }
  
  public static void main(String[] args) throws Throwable {
    Lookup lookup = MethodHandles.lookup();
    MethodHandle test1 = lookup.findStatic(AfterMain.class, "test1",
        methodType(void.class));
    MethodHandle test2 = lookup.findStatic(AfterMain.class, "test2",
        methodType(int.class, int.class));
    MethodHandle foo1 = lookup.findStatic(AfterMain.class, "foo1",
        methodType(void.class));
    MethodHandle foo2 = lookup.findStatic(AfterMain.class, "foo2",
        methodType(void.class, int.class));
    
    MethodHandle after1 = Interceptors.after(test1, foo1);
    after1.invokeExact();
    
    MethodHandle after2 = Interceptors.after(test2, foo2);
    System.out.println((int)after2.invokeExact(7));
  }
}
