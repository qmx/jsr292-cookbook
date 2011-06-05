package jsr292.cookbook.mdispatch;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public class RT {
  private static final ClassValue<HashMap<Selector,SelectorMetadata>> SELECTOR_MAP_VALUE =
    new ClassValue<HashMap<Selector,SelectorMetadata>>() {
      @Override
      protected HashMap<Selector, SelectorMetadata> computeValue(Class<?> type) {
        Lookup lookup = MethodHandles.publicLookup();
        HashMap<Selector, ArrayList<MethodHandle>> map = new HashMap<Selector, ArrayList<MethodHandle>>();
        for(Method method: type.getMethods()) {
          if (method.isBridge()) {
            continue;  // skip bridge
          }
          
          Selector selector = new Selector(method.getName(), method.getParameterTypes().length +
              ((Modifier.isStatic(method.getModifiers()))?0: 1));
          ArrayList<MethodHandle> list = map.get(selector);
          if (list == null) {
            list = new ArrayList<MethodHandle>();
            map.put(selector, list);
          }
          try {
            method.setAccessible(true);
            list.add(lookup.unreflect(method));
          } catch (IllegalAccessException e) {
            throw (LinkageError)new LinkageError().initCause(e);
          }
        }
        
        HashMap<Selector, SelectorMetadata> selectorMap =
          new HashMap<RT.Selector, SelectorMetadata>();
        for(Entry<Selector, ArrayList<MethodHandle>> entry: map.entrySet()) {
          ArrayList<MethodHandle> mhs = entry.getValue();
          if (mhs.size() > 1) {  // no multi-dispatch if only one method
              //System.out.println("selector "+entry.getKey());
              selectorMap.put(entry.getKey(), SelectorMetadata.create(mhs));
          }
        }
        
        return selectorMap;
      }
    };
    
  static class Selector {
    private final String name;
    private final int parameterCount;
    
    public Selector(String name, int parameterCount) {
      this.name = name;
      this.parameterCount = parameterCount;
    }
    
    @Override
    public int hashCode() {
      return name.hashCode() + parameterCount;
    }
    
    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof Selector)) {
        return false;
      }
      Selector selector = (Selector)obj;
      return parameterCount == selector.parameterCount &&
             name.equals(selector.name);
    }
    
    @Override
    public String toString() {
      return name+'/'+parameterCount;
    }
  }
    
  static MethodHandle getMultiDispatchTarget(Lookup lookup, String name, MethodType type, Class<?> staticType) throws NoSuchMethodException, IllegalAccessException {
    try {
      Selector selector = new Selector(name, type.parameterCount());
      SelectorMetadata metadata = SELECTOR_MAP_VALUE.get(staticType).get(selector);
      if (metadata == null) {  // only one method, no multi-dispatch
        //System.out.println("static linking "+staticType.getName()+'.'+name+type+" in "+lookup.lookupClass().getName());
        return lookup.findStatic(staticType, name, type);
      }

      return metadata.createMethodHandle(type);
      
    } catch(RuntimeException e) {
      throw new BootstrapMethodError(
          "error while linking "+staticType.getName()+'.'+name+type+" in "+lookup.lookupClass().getName(),
          e);
    }
  }
  
  public static CallSite invokestatic(Lookup lookup, String name, MethodType type, Class<?> staticType) throws NoSuchMethodException, IllegalAccessException {
    try {
      return new ConstantCallSite(getMultiDispatchTarget(lookup, name, type, staticType));
    } catch(BootstrapMethodError e) {
      e.printStackTrace();
      // revert to static dispatch
      return new ConstantCallSite(lookup.findStatic(staticType, name, type));
    }
  }
  
  static class BimorphicCacheCallSite extends MutableCallSite {
    final Lookup lookup;
    final String name;
    
    private Class<?> class1;
    private MethodHandle mh1;
    private Class<?> class2;
    private MethodHandle mh2;

    BimorphicCacheCallSite(Lookup lookup, String name, MethodType type) {
      super(type);
      this.lookup = lookup;
      this.name = name;
    }
    
    public synchronized Object fallback(Object[] args) throws Throwable {
      final MethodType type = type();
      if (class1 != null && class2 != null) {
        // bimorphic cache defeated, use a dispatch table instead
        DispatchMap dispatchMap = new DispatchMap() {
          @Override
          protected MethodHandle findMethodHandle(Class<?> receiverClass) throws Throwable {
            try {
              return getMultiDispatchTarget(lookup, name, type, receiverClass);
            } catch(BootstrapMethodError e) {
              // revert to a virtual dispatch
              return lookup.findVirtual(receiverClass, name, type.dropParameterTypes(0, 1));
            }
          }
        };
        dispatchMap.populate(class1, mh1, class1, mh2);   // pre-populated with known couples
        class1 = class2 = null;
        mh1 = mh2 = null;
        
        MethodHandle lookupMH = MethodHandles.filterReturnValue(GET_CLASS, LOOKUP_MH.bindTo(dispatchMap));
        lookupMH = lookupMH.asType(MethodType.methodType(MethodHandle.class, type.parameterType(0)));
        MethodHandle target = MethodHandles.foldArguments(MethodHandles.exactInvoker(type), lookupMH);
        setTarget(target);
        return target.invokeWithArguments(args);
      }
      
      Object receiver = args[0];
      Class<?> receiverClass = receiver.getClass();
      MethodHandle target;
      try {
        target = getMultiDispatchTarget(lookup, name, type, receiverClass);
      } catch(BootstrapMethodError e) {
        // revert to a virtual dispatch
        target = lookup.findVirtual(receiverClass, name, type.dropParameterTypes(0, 1));
      }
      
      MethodHandle test = CHECK_CLASS.bindTo(receiverClass);
      test = test.asType(test.type().changeParameterType(0, type.parameterType(0)));
      
      MethodHandle guard = MethodHandles.guardWithTest(test, target, getTarget());
      if (class1 == null) {
        class1 = receiverClass;
        mh1 = target;
      } else {
        class2 = receiverClass;
        mh2 = target;
      }
      
      setTarget(guard);
      return target.invokeWithArguments(args);
    }
  }
  
  public static CallSite invokevirtual(Lookup lookup, String name, MethodType type) {
    BimorphicCacheCallSite callSite = new BimorphicCacheCallSite(lookup, name, type);
    
    MethodHandle fallback = FALLBACK.bindTo(callSite);
    fallback = fallback.asCollector(Object[].class, type.parameterCount());
    fallback = fallback.asType(type);
    
    callSite.setTarget(fallback);
    return callSite;
  }
  
  public static boolean checkClass(Class<?> clazz, Object receiver) {
    return receiver.getClass() == clazz;
  }
  
  static final MethodHandle CHECK_CLASS;
  private static final MethodHandle FALLBACK;
  static final MethodHandle GET_CLASS;
  static final MethodHandle LOOKUP_MH;
  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      CHECK_CLASS = lookup.findStatic(RT.class, "checkClass",
          MethodType.methodType(boolean.class, Class.class, Object.class));
      FALLBACK = lookup.findVirtual(BimorphicCacheCallSite.class, "fallback",
          MethodType.methodType(Object.class, Object[].class));
      GET_CLASS = lookup.findVirtual(Object.class, "getClass",
          MethodType.methodType(Class.class));
      LOOKUP_MH =   lookup.findVirtual(DispatchMap.class, "lookup",
          MethodType.methodType(MethodHandle.class, Class.class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
