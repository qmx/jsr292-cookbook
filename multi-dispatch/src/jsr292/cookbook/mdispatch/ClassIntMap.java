package jsr292.cookbook.mdispatch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public class ClassIntMap {
  private Class<?>[] keys;
  private int[] values;
  private int size;
  private final Object lock = new Object();
  
  public ClassIntMap(int initialCapacity) {
    int capacity = 4;
    while (capacity < initialCapacity) {  // must be a power of 2
        capacity <<= 1;
    }
    
    capacity <<= 1; // be sure to be half empty
    keys = new Class<?>[capacity];
    values = new int[capacity];
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

  public int lookup(Class<?> k) {
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
  
  private static int find(Class<?> k, int len, Class<?>[] ks, int[] vs) {
    int index = hash(k, len);
    for(;;) {
      Class<?> key = ks[index];
      if (key == k) {
        return vs[index];
      }
      if (key == null) {
        return 0;
      }
      index = next(index, len);
    }
  }

  private int update(Class<?> k, int index) {
    Class<?>[] ks = keys;
    int len = ks.length;
    int[] vs = values;
    int v = 0;
    for(Class<?> zuper = k.getSuperclass(); zuper != null; zuper = zuper.getSuperclass()) {
      if ((v = find(zuper, len, ks, vs)) != 0) {
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
    int[] vs = values;
    
    int newLength = len << 1;
    Class<?>[] newKs = new Class<?>[newLength];
    int[] newVs = new int[newLength];
    
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

  public void putNoResize(Class<?> k, int v) {
    synchronized(lock) {
      Class<?>[] ks = keys;
      int len = ks.length;
      int index = hash(k, len);

      while ( ks[index] != null) {
        index = next(index, len);
      }

      ks[index] = k;
      values[index] = v;
      size++;
    }
  }
  
  public ClassMHMap transfer(int position, MethodHandle[] mhs, MethodType type) {
    synchronized(lock) {
      Class<?>[] ks = this.keys;
      int[] vs = this.values;
      int length = ks.length;
      MethodHandle[] mhValues = new MethodHandle[length];
      for(int i=0; i<length; i++) {
        if (ks[i] != null) {
          MethodHandle mh = mhs[Integer.highestOneBit(vs[i])];
          mhValues[i] = mh.asType(mh.type().changeParameterType(position, ks[i])).asType(type);
        }
      }
      return new ClassMHMap(ks.clone(), mhValues, size);
    }
  }
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append('[');
    synchronized (lock) {
      Class<?>[] ks = this.keys;
      int[] vs = this.values;
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
