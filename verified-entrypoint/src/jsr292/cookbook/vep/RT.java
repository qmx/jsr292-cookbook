package jsr292.cookbook.vep;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.HashMap;

public class RT {
  private static final ClassValue<HashMap<String, MethodHandle>> entryPointClassValue =
    new ClassValue<HashMap<String,MethodHandle>>() { 
      @Override
      protected HashMap<String, MethodHandle> computeValue(Class<?> type) {
        return new HashMap<String, MethodHandle>();
      }
    };
 
  static class VEPCallsite extends MutableCallSite {
    final String name;
    final Lookup lookup;
    
    public VEPCallsite(Lookup lookup, String name, MethodType type) {
      super(type);
      this.lookup = lookup;
      this.name = name;
    }
  }
    
  public static CallSite bootstrap(Lookup lookup, String name, MethodType type) {
    VEPCallsite callsite = new VEPCallsite(lookup, name, type);
    
    MethodHandle target = INSTALL_VEP.bindTo(callsite);
    target = target.asCollector(Object[].class, type.parameterCount());
    target = target.asType(type);
    
    callsite.setTarget(target);
    return callsite;
  }
  
  public static Object installVEP(VEPCallsite callSite, Object[] args) throws Throwable {
    Object receiver = args[0];
    Class<?> receiverClass = receiver.getClass();
    MethodType type = callSite.type();
    if (receiverClass == type.parameterType(0)) { // no need to use a dynamic checkcast
      return fallback(callSite, args);
    }
    
    String name = callSite.name;
    String selector = name + type.toMethodDescriptorString();
    HashMap<String, MethodHandle> vtable = entryPointClassValue.get(receiverClass);
    MethodHandle mh = vtable.get(selector);
    if (mh == null) {
      //System.out.println("construct "+receiverClass.getName()+ " " + name + " " + type);
      
      MethodHandle target = callSite.lookup.findVirtual(receiverClass, name, type.dropParameterTypes(0, 1));
      target = target.asType(type.changeParameterType(0, Object.class));
      target = MethodHandles.dropArguments(target, 0, VEPCallsite.class);
      
      MethodHandle test = CHECK_CLASS.bindTo(receiverClass);
      test = MethodHandles.dropArguments(test, 0, VEPCallsite.class);
      
      MethodHandle fallback = FALLBACK.asCollector(Object[].class, type.parameterCount());
      fallback = fallback.asType(target.type());
      
      mh = MethodHandles.guardWithTest(test, target, fallback);
      vtable.put(selector, mh);
    }
    
    mh = mh.bindTo(callSite);
    mh = mh.asType(type);
    callSite.setTarget(mh);
    return mh.invokeWithArguments(args);
  }
  
  public static Object fallback(VEPCallsite callSite, Object[] args) throws Throwable {
    // install a vtable call
    //System.out.println("fallback " + callSite.name + " " + callSite.type());
    
    MethodType type = callSite.type();
    MethodHandle target = callSite.lookup.findVirtual(type.parameterType(0), callSite.name, type.dropParameterTypes(0, 1));
    callSite.setTarget(target);
    return target.invokeWithArguments(args);
  }
  
  public static boolean checkClass(Class<?> clazz, Object receiver) {
    return receiver.getClass() == clazz;
  }
  
  private static final MethodHandle INSTALL_VEP;
  private static final MethodHandle CHECK_CLASS;
  private static final MethodHandle FALLBACK;
  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      INSTALL_VEP = lookup.findStatic(RT.class, "installVEP",
          MethodType.methodType(Object.class, VEPCallsite.class, Object[].class));
      CHECK_CLASS = lookup.findStatic(RT.class, "checkClass",
          MethodType.methodType(boolean.class, Class.class, Object.class));
      FALLBACK = lookup.findStatic(RT.class, "fallback",
          MethodType.methodType(Object.class, VEPCallsite.class, Object[].class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
