package jsr292.cookbook.mdispatch;

import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

class LatticeTest {
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
    
    Lattice lattice = new Lattice(Arrays.asList(new PositionInfo(1, false, null)));  // 0 -> PrintStream, 1 -> argument
    for(MethodHandle mh: mhs) {
      lattice.add(mh);
    }
    MethodHandle[] array = lattice.topologicalSort();
    System.out.println(Arrays.toString(array));
  }
}
