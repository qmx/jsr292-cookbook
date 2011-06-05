package jsr292.cookbook.metaclass;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class MetaClass {
  static final Object MUTATION_LOCK = new Object();
  
  private static final ClassValue<MetaClass> metaClassValue =
    new ClassValue<MetaClass>() {
    @Override
    protected MetaClass computeValue(Class<?> type) {
      Class<?> superclass = type.getSuperclass();
      MetaClass parentMetaClass = (superclass == null)? null: getMetaClass(superclass);
      return new MetaClass(parentMetaClass);
    }
  };
  
  public static MetaClass getMetaClass(Class<?> clazz) {
    return metaClassValue.get(clazz);
  }

  private static class Selector {
    private final String name;
    private final MethodType methodType;

    Selector(String name, MethodType methodType) {
      this.name = name;
      this.methodType = methodType;
    }

    @Override
    public int hashCode() {
      return name.hashCode() ^ methodType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this)
        return true;
      if (!(obj instanceof Selector))
        return false;
      Selector selector = (Selector)obj;
      return methodType.equals(selector.methodType) &&
             name.equals(selector.name);
    }
  }  

  SwitchPoint switchPoint;
  private final MetaClass parent;
  private final LinkedList<WeakReference<MetaClass>> subMetaClasses =
    new LinkedList<WeakReference<MetaClass>>();
  private final HashMap<Selector, MethodHandle> vtable =
    new HashMap<Selector, MethodHandle>();

  MetaClass(MetaClass parent) {
    synchronized(MUTATION_LOCK) {
      switchPoint = new SwitchPoint();
      this.parent = parent;
      if (parent != null) {
        parent.subMetaClasses.add(new WeakReference<MetaClass>(this));
      }
    }
  }

  MethodHandle staticLookup(String name, MethodType methodType) {
    return staticLookup(new Selector(name, methodType));
  }
  
  private MethodHandle staticLookup(Selector selector) {
    assert Thread.holdsLock(MUTATION_LOCK);
    return vtable.get(selector);
  }
  
  MethodHandle virtualLookup(String name, MethodType methodType) {
    Selector selector = new Selector(name, methodType);
    for(MetaClass metaClass = this; metaClass != null; metaClass = metaClass.parent) {
      MethodHandle mh = metaClass.staticLookup(selector);
      if (mh != null) {
        return mh;
      }
    }
    return null;
  }
  
  public void redirect(String name, MethodType type, MethodHandle target) {
    synchronized(MUTATION_LOCK) {
      ArrayList<SwitchPoint> switchPoints = new ArrayList<SwitchPoint>();
      mutateSwitchPoints(this, switchPoints);
      SwitchPoint.invalidateAll(switchPoints.toArray(new SwitchPoint[switchPoints.size()]));
      
      vtable.put(new Selector(name, type), target);
    }
  }
  
  private static void mutateSwitchPoints(MetaClass metaClass, ArrayList<SwitchPoint> switchPoints) {
    assert Thread.holdsLock(MUTATION_LOCK);
    
    switchPoints.add(metaClass.switchPoint);
    metaClass.switchPoint = new SwitchPoint();  // new switch point
    
    for(Iterator<WeakReference<MetaClass>> it = metaClass.subMetaClasses.iterator(); it.hasNext();) {
      WeakReference<MetaClass> reference = it.next();
      MetaClass subMetaClass = reference.get();
      if (subMetaClass == null) {  // unloaded class
        it.remove();
        continue;
      }
      mutateSwitchPoints(subMetaClass, switchPoints);
    }
  }
}
