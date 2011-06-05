package jsr292.cookbook.mdispatch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

public class BitReducer {
  public static MethodHandle reducer(MethodHandle[] mhs, int bits) {
    return mhs[Integer.highestOneBit(bits)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2) {
    return mhs[Integer.highestOneBit(bits1 & bits2)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3, int bits4) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3 & bits4)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3, int bits4, int bits5) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3 & bits4 & bits5)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3, int bits4, int bits5, int bits6) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3 & bits4 & bits5 & bits6)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3, int bits4, int bits5, int bits6, int bits7) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3 & bits4 & bits5 & bits6 & bits7)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3, int bits4, int bits5, int bits6, int bits7, int bits8) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3 & bits4 & bits5 & bits6 & bits7 & bits8)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3, int bits4, int bits5, int bits6, int bits7, int bits8, int bits9) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3 & bits4 & bits5 & bits6 & bits7 & bits8 & bits9)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int bits1, int bits2, int bits3, int bits4, int bits5, int bits6, int bits7, int bits8, int bits9, int bits10) {
    return mhs[Integer.highestOneBit(bits1 & bits2 & bits3 & bits4 & bits5 & bits6 & bits7 & bits8 & bits9 & bits10)];
  }
  public static MethodHandle reducer(MethodHandle[] mhs, int[] bitsArray) {
    int bits = bitsArray[0];
    for(int i=1; i<bitsArray.length; i++) {
      bits &= bitsArray[i];
    }
    return mhs[Integer.highestOneBit(bits)];
  }
  
  public static MethodHandle getReducer(int parameterCount) {
    if (parameterCount <= 0) {
      throw new IllegalArgumentException("parameterCount <= 0 "+parameterCount);
    }
    
    if (CACHE[parameterCount] != null) {
      return CACHE[parameterCount];
    }
    
    if (parameterCount <= 10) {
      Class<?>[] parameterTypes = new Class<?>[parameterCount];
      Arrays.fill(parameterTypes, int.class);
      try {
        return CACHE[parameterCount] = MethodHandles.publicLookup().findStatic(BitReducer.class, "reducer",
            MethodType.methodType(MethodHandle.class, parameterTypes).insertParameterTypes(0, MethodHandle[].class));
      } catch (ReflectiveOperationException e) {
        throw (AssertionError)new AssertionError().initCause(e);
      }
    }
    return CACHE[parameterCount] = REDUCER_VARARGS.asCollector(int[].class, parameterCount);
  }
  
  private static final MethodHandle[] CACHE = new MethodHandle[256];
  private static final MethodHandle REDUCER_VARARGS;
  static {
    try {
      REDUCER_VARARGS = MethodHandles.publicLookup().findStatic(BitReducer.class, "reducer",
          MethodType.methodType(MethodHandle.class, MethodHandle[].class, int[].class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
