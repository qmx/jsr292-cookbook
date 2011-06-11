import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import jsr292.cookbook.interceptors.Interceptors;

import static java.lang.invoke.MethodType.*;

public class TryFinallyMain {
  public static void test1() {
    System.out.println("test1");
  }
  
  public static int test2(int value) {
    System.out.println("test2");
    return 42 + value;
  }
  
  public static int test3(int value) {
    System.out.println("test3");
    if (value == 0)
      throw new RuntimeException();
    return value;
  }
  
  public static void foo() {
    System.out.println("finally");
  }
  
  public static void main(String[] args) throws Throwable {
    Lookup lookup = MethodHandles.lookup();
    MethodHandle test1 = lookup.findStatic(TryFinallyMain.class, "test1",
        methodType(void.class));
    MethodHandle test2 = lookup.findStatic(TryFinallyMain.class, "test2",
        methodType(int.class, int.class));
    MethodHandle test3 = lookup.findStatic(TryFinallyMain.class, "test3",
        methodType(int.class, int.class));
    MethodHandle foo = lookup.findStatic(TryFinallyMain.class, "foo",
        methodType(void.class));
    
    MethodHandle mh1 = Interceptors.tryFinally(test1, foo);
    mh1.invoke();
    
    MethodHandle mh2 = Interceptors.tryFinally(test2, foo);
    System.out.println((int)mh2.invoke(5));
    
    MethodHandle mh3 = Interceptors.tryFinally(test3, foo);
    System.out.println((int)mh3.invoke(7));
    try {
      System.out.println((int)mh3.invoke(0));
      throw new AssertionError();
    } catch(RuntimeException e) {
      // do nothing
    }
  }
}
