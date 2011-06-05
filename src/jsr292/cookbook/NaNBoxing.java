package jsr292.cookbook;

public class NaNBoxing {
  
  public static long plus(long value1, long value2) {
    if ((value1 & 0xffffffff00000000L) == 0xffffffff00000000L) {
      if ((value2 & 0xffffffff00000000L) == 0xffffffff00000000L) {
        return value(((int)value1) + ((int)value2));
      }
      return value(((int)value1) + asDouble(value2));
    }
    return value(Double.longBitsToDouble(value1) + asDouble(value2));
  }
  
  public static long value(int i) {
    return 0xffffffff00000000L | i;
  }
  
  public static long value(double d) {
    return Double.doubleToLongBits(d);
  }
  
  public static double asDouble(long value) {
    if ((value & 0xffffffff00000000L) == 0xffffffff00000000L) {
      return (int)value;
    }
    return Double.longBitsToDouble(value);
  }
  
  public static String toString(long value) {
    if ((value & 0xffffffff00000000L) == 0xffffffff00000000L) {
      return Integer.toString((int)value);
    }
    return Double.toString(Double.longBitsToDouble(value));
  }
  
  public static void main(String[] args) {
    //double d = Double.longBitsToDouble(0xffffffffffffffffL);
    //double d = Double.longBitsToDouble(0xffff000000000000L);
    //System.out.println(d);
    
    //System.out.println(toString(value(1)));
    //System.out.println(toString(value(10.0)));
    
    System.out.println(toString(plus(value(1), value(2))));
    System.out.println(toString(plus(value(1.0), value(2))));
    System.out.println(toString(plus(value(1), value(2.0))));
    System.out.println(toString(plus(value(1.0), value(2.0))));
  }
}
