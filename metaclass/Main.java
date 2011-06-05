import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import jsr292.cookbook.metaclass.MetaClass;

import static java.lang.invoke.MethodType.*;

public class Main {
  public static String foo(String s) {
    return s.toUpperCase();
  }
  
  public static String reverse(String s) {
    return new StringBuilder(s).reverse().toString();
  }
  
  public static void main(String[] args) throws ReflectiveOperationException {
    for(int i=0; i<10; i++) {
      System.out.println(foo("Hello"));

      if (i == 3) {
        MetaClass.getMetaClass(String.class).redirect("toUpperCase", methodType(String.class),
            MethodHandles.lookup().findStatic(Main.class, "reverse", methodType(String.class, String.class)));
      }
      
      if (i == 7) {
        MetaClass.getMetaClass(Main.class).redirect("foo", methodType(String.class, String.class),
            MethodHandles.lookup().findVirtual(String.class, "toUpperCase", methodType(String.class)));
      }
    }
  }
}
