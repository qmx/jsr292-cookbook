
public class Main {
  public static int fib(int n) {
    if ( n < 2 )
      return 1;
    return fib(n - 1) + fib(n - 2);
  }
  
  public static void main(String[] args) {
    long start = System.nanoTime();
    System.out.println(fib(40));
    long end = System.nanoTime();
    System.out.println("time " + (end - start));
  }
}
