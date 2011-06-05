package jsr292.cookbook.bicache;

import java.lang.invoke.MethodHandle;

public abstract class DispatchMap {
  private Class<?>[] keys;
  private MethodHandle[] values;
  private int size;
  private final Object lock = new Object();
  
  public DispatchMap() {
    keys = new Class<?>[8];
    values = new MethodHandle[8];
  }
  
  private static int hash(Class<?> x, int length) {
    return System.identityHashCode(x) & (length - 1);
  }

  private static int next(int i, int len) {
    return (i + 1 ) & ( len - 1);
  }
  
  void populate(Class<?> k1, MethodHandle v1,Class<?> k2, MethodHandle v2) {
    Class<?>[] ks = keys;
    MethodHandle[] vs = this.values;
    int len = ks.length;
    putNoResize(ks, vs, len, k1, v1);
    putNoResize(ks, vs, len, k2, v2);
    size+=2;
  }
  
  private static void putNoResize(Class<?>[] ks, MethodHandle[] vs, int len, Class<?> k, MethodHandle v) {
    int index = hash(k, len);
    while ( ks[index] != null) {
      index = next(index, len);
    }
    ks[index] = k;
    vs[index] = v;
  }
  
  public MethodHandle lookup(Class<?> k) throws Throwable {
    synchronized(lock) {
      Class<?>[] ks = keys;
      int len = ks.length;
      int i = hash(k, len);
      for(;;) {
        Class<?> key = ks[i];
        if (key == k) {
          return values[i];
        }
        if (key == null) {
          return update(k, i);
        }
        i = next(i, len);
      }
    }
  }
  
  protected abstract MethodHandle findMethodHandle(Class<?> k) throws Throwable;
  
  private MethodHandle update(Class<?> k, int index) throws Throwable {
    Class<?>[] ks = keys;
    int len = ks.length;
    ks[index] = k;
    MethodHandle v = findMethodHandle(k);
    values[index] = v;
    int size = this.size;
    this.size = size + 1;
    
    if (size == (len>>1)) {
      resize();
    }
    
    return v;
  }
  
  private void resize() {
    Class<?>[] ks = keys;
    int len = ks.length;
    MethodHandle[] vs = values;
    
    int newLength = len << 1;
    Class<?>[] newKs = new Class<?>[newLength];
    MethodHandle[] newVs = new MethodHandle[newLength];
    
    for(int i=0; i<len; i++) {
      Class<?> key = ks[i];
      if (key != null) {
        int index = hash(key, newLength);
        while ( newKs[index] != null) {
          index = next(index, newLength);
        }
        newKs[index] = key;
        newVs[index] = vs[index];
      }
    }
    
    keys = newKs;
    values = newVs;
  }
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append('[');
    synchronized(lock) {
      Class<?>[] ks = this.keys;
      MethodHandle[] vs = this.values;
      int length = ks.length;
      for(int i=0; i<length; i++) {
        Class<?> key = ks[i];
        if (key != null) {
          builder.append(key).append('=').append(vs[i]).append(", ");
        }
      }
    }
    if (builder.length() != 0) {
      builder.setLength(builder.length() - 2);
    }
    return builder.append(']').toString();
  }
}
