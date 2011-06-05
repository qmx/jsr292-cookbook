package jsr292.cookbook.metaclass;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;

public class RT {
 
  public static class InvokeStaticCallSite extends MutableCallSite {
    private final Lookup lookup;
    private final String name;
    private final Class<?> ownerType;
    private final MethodHandle fallback;

    public InvokeStaticCallSite(Lookup lookup, String name, MethodType type, Class<?> ownerType) {
      super(type);
      this.lookup = lookup;
      this.name = name;
      this.ownerType = ownerType;
      
      MethodHandle fallback = MethodHandles.insertArguments(STATIC_FALLBACK, 0, this);
      fallback = fallback.asCollector(Object[].class, type.parameterCount());
      fallback = fallback.asType(type);
      this.fallback = fallback;
    }
    
    MethodHandle staticSwitch() {
      MetaClass metaClass = MetaClass.getMetaClass(ownerType);
      MethodType type = type();
      MethodHandle mh;
      SwitchPoint switchPoint;
      synchronized(MetaClass.MUTATION_LOCK) {
        mh = metaClass.staticLookup(name, type);
        switchPoint = metaClass.switchPoint;
      }
      
      if (mh == null) {
        try {
          mh = lookup.findStatic(ownerType, name, type);
        } catch (ReflectiveOperationException e) {
          throw new BootstrapMethodError(e);
        }
      }
      mh = mh.asType(type());
      return switchPoint.guardWithTest(mh, fallback);
    }
  }
  
  
  public static CallSite invokestatic(Lookup lookup, String name, MethodType type, Class<?> ownerType) {
    InvokeStaticCallSite callsite = new InvokeStaticCallSite(lookup, name, type, ownerType);
    callsite.setTarget(callsite.staticSwitch());
    return callsite;
  }
  
  public static Object staticFallback(InvokeStaticCallSite callSite, Object[] args) throws Throwable {
    MethodHandle switchGuard = callSite.staticSwitch();
    callSite.setTarget(switchGuard);
    return switchGuard.invokeWithArguments(args);
  }
  
  
  public static class InvokeVirtualCallSite extends MutableCallSite {
    private final Lookup lookup;
    private final String name;
    final MethodHandle checkClass;
    final MethodHandle switchFallback;
    final MethodHandle dispatchFallback;

    public InvokeVirtualCallSite(Lookup lookup, String name, MethodType type) {
      super(type);
      this.lookup = lookup;
      this.name = name;
      
      MethodHandle checkClass = MethodHandles.dropArguments(CHECK_CLASS, 2, type.dropParameterTypes(0, 1).parameterList());
      checkClass = checkClass.asType(type().changeReturnType(boolean.class).insertParameterTypes(0, Class.class));
      this.checkClass = checkClass;
      
      MethodHandle switchFallback = MethodHandles.insertArguments(VIRTUAL_FALLBACK, 0, this, true);
      switchFallback = switchFallback.asCollector(Object[].class, type.parameterCount());
      switchFallback = switchFallback.asType(type);
      this.switchFallback = switchFallback;
      
      MethodHandle dispatchFallback = MethodHandles.insertArguments(VIRTUAL_FALLBACK, 0, this, false);
      dispatchFallback = dispatchFallback.asCollector(Object[].class, type.parameterCount());
      dispatchFallback = dispatchFallback.asType(type);
      this.dispatchFallback = dispatchFallback;
    }
    
    MethodHandle virtualSwitch(Class<?> receiverType) {
      MetaClass metaClass = MetaClass.getMetaClass(receiverType);
      MethodType type = type().dropParameterTypes(0, 1);
      MethodHandle mh;
      SwitchPoint switchPoint;
      synchronized(MetaClass.MUTATION_LOCK) {
        mh = metaClass.virtualLookup(name, type);
        switchPoint = metaClass.switchPoint;
      }
      
      if (mh == null) {
        try {
          mh = lookup.findVirtual(receiverType, name, type);
        } catch (ReflectiveOperationException e) {
          throw new BootstrapMethodError(e);
        }
      }
      mh = mh.asType(type());
      return switchPoint.guardWithTest(mh, switchFallback);
    }
  }
  
  public static CallSite invokevirtual(Lookup lookup, String name, MethodType type) {
    InvokeVirtualCallSite callSite = new InvokeVirtualCallSite(lookup, name, type);
    callSite.setTarget(callSite.dispatchFallback);
    return callSite;
  }
  
  public static boolean checkClass(Class<?> clazz, Object receiver) {
    return receiver.getClass() == clazz;
  }
  
  public static Object virtualFallback(InvokeVirtualCallSite callSite, boolean reset, Object[] args) throws Throwable {
    Class<?> receiverClass = args[0].getClass();
    MethodHandle virtualSwitch = callSite.virtualSwitch(receiverClass);
    
    MethodHandle fallback = (reset)? callSite.dispatchFallback: callSite.getTarget();
    
    MethodHandle guard = MethodHandles.guardWithTest(callSite.checkClass.bindTo(receiverClass),
        virtualSwitch,
        fallback);
      
    callSite.setTarget(guard);
    return virtualSwitch.invokeWithArguments(args);
  }
  
  
  
  
  static final MethodHandle STATIC_FALLBACK;
  static final MethodHandle VIRTUAL_FALLBACK;
  static final MethodHandle CHECK_CLASS;
  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      STATIC_FALLBACK = lookup.findStatic(RT.class, "staticFallback",
          MethodType.methodType(Object.class, InvokeStaticCallSite.class, Object[].class));
      VIRTUAL_FALLBACK = lookup.findStatic(RT.class, "virtualFallback",
          MethodType.methodType(Object.class, InvokeVirtualCallSite.class, boolean.class, Object[].class));
      CHECK_CLASS = lookup.findStatic(RT.class, "checkClass",
          MethodType.methodType(boolean.class, Class.class, Object.class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
