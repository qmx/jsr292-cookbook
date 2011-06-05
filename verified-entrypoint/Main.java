
public class Main {
  public static void main(String[] args) {
    
    Object o1 = "foo";
    System.out.println(o1.toString());
    
    Object o2 = "bar";
    System.out.println(o2.toString());
    
    Object[] array = new Object[] { 19, "baz" };
    for(Object value: array) {
      System.out.println(value.toString());
    }
  }
}
