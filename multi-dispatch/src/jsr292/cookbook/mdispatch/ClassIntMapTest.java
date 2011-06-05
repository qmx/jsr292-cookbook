package jsr292.cookbook.mdispatch;

import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class ClassIntMapTest {
  public static void main(String[] args) throws IllegalAccessException {
    Lookup lookup = MethodHandles.publicLookup();
    ArrayList<MethodHandle> mhs = new ArrayList<MethodHandle>();
    for(Method method: PrintStream.class.getMethods()) {
      if (!method.getName().equals("println")) {
        continue;
      }
      if (method.getParameterTypes().length == 0) {
        continue;
      }
      
      MethodHandle mh = lookup.unreflect(method);
      mhs.add(mh);
    }
    
    ClassIntMap map = new ClassIntMap(mhs.size());
    for(int i=0; i<mhs.size(); i++) {
      map.putNoResize(mhs.get(i).type().parameterType(1), 1<<i);
    }
    
    System.out.println(map.lookup(Object.class));
    System.out.println(map.lookup(String.class));
    System.out.println(map.lookup(Integer.class));
  }
}
