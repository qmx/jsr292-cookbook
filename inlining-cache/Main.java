
public class Main {
  public static void main(String[] args) {
    Object[] array = new Object[] { 19, "baz", 42.0, 'X' };
    for(Object value: array) {
      System.out.println(value.toString());
    }
  }
}
