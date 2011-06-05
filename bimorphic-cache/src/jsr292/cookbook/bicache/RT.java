package jsr292.cookbook.bicache;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

public class RT {
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
            MethodHandle target = lookup.findVirtual(receiverClass, name,
                type.dropParameterTypes(0, 1));
            return target.asType(type);
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
      MethodHandle target = lookup.findVirtual(receiverClass, name,
          type.dropParameterTypes(0, 1));
      target = target.asType(type);
      
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
  
  public static CallSite bootstrap(Lookup lookup, String name, MethodType type) {
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
