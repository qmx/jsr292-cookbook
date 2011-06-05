package jsr292.cookbook.mdispatch;

import java.lang.invoke.MethodHandle;

public class ClassMHMap {
  private Class<?>[] keys;
  private MethodHandle[] values;
  private int size;
  private final Object lock = new Object();
  
  ClassMHMap(Class<?>[] keys, MethodHandle[] values, int size) {
    this.keys = keys;
    this.values = values;
    this.size = size;
  }
  
  public int size() {
    synchronized (lock) {
      return size;
    }
  }
  
  private static int hash(Class<?> x, int length) {
    return System.identityHashCode(x) & (length - 1);
  }

  private static int next(int i, int len) {
    return (i + 1 ) & ( len - 1);
  }
  
  private static MethodHandle find(Class<?> k, int len, Class<?>[] ks, MethodHandle[] vs) {
    int index = hash(k, len);
    for(;;) {
      Class<?> key = ks[index];
      if (key == k) {
        return vs[index];
      }
      if (key == null) {
        return null;
      }
      index = next(index, len);
    }
  }

  public MethodHandle lookup(Class<?> k) {
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
  
  private MethodHandle update(Class<?> k, int index) {
    Class<?>[] ks = keys;
    int len = ks.length;
    MethodHandle[] vs = values;
    MethodHandle v = null;
    for(Class<?> zuper = k.getSuperclass(); zuper != null; zuper = zuper.getSuperclass()) {
      if ((v = find(zuper, len, ks, vs)) != null) {
        break;
      }
    }
    
    ks[index] = k;
    vs[index] = v;   // also store cache-miss
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

  /*
  public void put(Class<?> k, MethodHandle v) {
    synchronized(lock) {
      Class<?>[] ks = keys;
      int len = ks.length;
      int index = hash(k, len);

      while ( ks[index] != null) {
        index = next(index, len);
      }

      ks[index] = k;
      values[index] = v;
      int size = this.size;
      this.size = size + 1;
      
      if (size == (len>>1)) {
        resize();
      }
    }
  }*/
  
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
