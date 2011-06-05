
public class Main {
  public static void m(String s, int v) {
    System.out.println("string "+s);
  }
  
  public static void m(Integer i, int v) {
    System.out.println("integer "+i);
  }
  
  private static void m(Object o, int v) {
    // just here to please the compiler
  }
  
  public static void main(String[] args) {
    m("foo", 0);
    
    Object[] array = new Object[] { 19, "baz" };
    for(Object value: array) {
      m(value, 0);
    }
  }
}
