package jsr292.cookbook.icache;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

public class RT {
  static class InliningCacheCallSite extends MutableCallSite {
    private static final int MAX_DEPTH = 3;
    
    final Lookup lookup;
    final String name;
    int depth;

    InliningCacheCallSite(Lookup lookup, String name, MethodType type) {
      super(type);
      this.lookup = lookup;
      this.name = name;
    }
  }
  
  public static CallSite bootstrap(Lookup lookup, String name, MethodType type) {
    InliningCacheCallSite callSite = new InliningCacheCallSite(lookup, name, type);
    
    MethodHandle fallback = FALLBACK.bindTo(callSite);
    fallback = fallback.asCollector(Object[].class, type.parameterCount());
    fallback = fallback.asType(type);
    
    callSite.setTarget(fallback);
    return callSite;
  }
  
  public static boolean checkClass(Class<?> clazz, Object receiver) {
    return receiver.getClass() == clazz;
  }
  
  public static Object fallback(InliningCacheCallSite callSite, Object[] args) throws Throwable {
    MethodType type = callSite.type();
    if (callSite.depth >= InliningCacheCallSite.MAX_DEPTH) {
      // revert to a vtable call
      MethodHandle target = callSite.lookup.findVirtual(type.parameterType(0), callSite.name,
          type.dropParameterTypes(0, 1));
      callSite.setTarget(target);
      return target.invokeWithArguments(args);
    }
    
    Object receiver = args[0];
    Class<?> receiverClass = receiver.getClass();
    MethodHandle target = callSite.lookup.findVirtual(receiverClass, callSite.name,
        type.dropParameterTypes(0, 1));
    target = target.asType(type);
    
    MethodHandle test = CHECK_CLASS.bindTo(receiverClass);
    test = test.asType(test.type().changeParameterType(0, type.parameterType(0)));
    
    MethodHandle guard = MethodHandles.guardWithTest(test, target, callSite.getTarget());
    callSite.depth++;
    
    callSite.setTarget(guard);
    return target.invokeWithArguments(args);
  }
  
  private static final MethodHandle CHECK_CLASS;
  private static final MethodHandle FALLBACK;
  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      CHECK_CLASS = lookup.findStatic(RT.class, "checkClass",
          MethodType.methodType(boolean.class, Class.class, Object.class));
      FALLBACK = lookup.findStatic(RT.class, "fallback",
          MethodType.methodType(Object.class, InliningCacheCallSite.class, Object[].class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
