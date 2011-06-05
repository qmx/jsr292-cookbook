import java.math.BigDecimal;

import jsr292.cookbook.visitor.AbstractVisitor;
import jsr292.cookbook.visitor.Visit;

public class Main {
  public static class MyVisitor extends AbstractVisitor {
    @Visit
    public void visitText(String text) {
      System.out.println("visit text "+text);
    }
    
    @Visit
    public void visitBigDecimal(BigDecimal decimal) {
      System.out.println("visit decimal "+decimal);
    }
    
    public void visit(Object o) {
      throw new UnsupportedOperationException("need to please the compiler");
    }
    public void visit(String s) {
      throw new UnsupportedOperationException("need to please the compiler");
    }
    public void visit(BigDecimal d) {
      throw new UnsupportedOperationException("need to please the compiler");
    }
  }
  
  public static void main(String[] args) {
    MyVisitor visitor = new MyVisitor();
    
    visitor.visit("foo");
    visitor.visit((Object)"bar");
    visitor.visit(new BigDecimal(3));
    visitor.visit((Object)new BigDecimal(7));
    
    Object[] array = new Object[] { "baz", new BigDecimal(13) };
    for(Object value: array) {
      visitor.visit(value);
    }
  }
}
