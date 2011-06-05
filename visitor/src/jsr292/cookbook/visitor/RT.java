package jsr292.cookbook.visitor;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

public class RT {
  public static CallSite bootstrap(Lookup lookup, String name, MethodType type) {
    MutableCallSite callSite = new MutableCallSite(type);
    MethodHandle boot = BOOT.bindTo(callSite);
    boot = boot.asCollector(Object[].class, type.parameterCount());
    boot = boot.asType(type);
    callSite.setTarget(boot);
    return callSite;
    
    //return new ConstantCallSite(dispatcher(type));
  }
  
  private static MethodHandle dispatcher(MethodType type) {
    MethodHandle combiner = MethodHandles.dropArguments(GET_HANDLE, 2, type.parameterList().subList(2, type.parameterCount()));
    combiner = combiner.asType(combiner.type().changeParameterType(0, type.parameterType(0)));
    
    MethodHandle invoker = MethodHandles.exactInvoker(type.changeParameterType(1, Object.class));
    
    MethodHandle target = MethodHandles.foldArguments(invoker, combiner);
    target = target.asType(type);
    return target;
  }
  
  public static Object boot(MutableCallSite callSite, Object[] args) throws Throwable {
    AbstractVisitor visitor = (AbstractVisitor)args[0];
    MethodType type = callSite.type();
    MethodHandle visit = visitor.findHandle(type.parameterType(1));
    if (visit == null) {
      /* no static visit, fallback to dispatcher
      MethodHandle target = dispatcher(type);
      callSite.setTarget(target);
      return target.invokeWithArguments(args);*/
            
      MutableCallSite inliningCache = new MutableCallSite(type);
      MethodHandle fallback = FALLBACK.bindTo(inliningCache);
      fallback = fallback.asCollector(Object[].class, type.parameterCount());
      fallback = fallback.asType(type);
      inliningCache.setTarget(fallback);
      visit = inliningCache.dynamicInvoker();
    }
    
    Class<?> visitorClass = visitor.getClass();
    MethodHandle test = CHECK_CLASS.bindTo(visitorClass);
    test = test.asType(MethodType.methodType(boolean.class, type.parameterType(0)));
    
    MethodHandle deopt = DEOPT.bindTo(callSite);
    deopt = deopt.asCollector(Object[].class, type.parameterCount());
    deopt = deopt.asType(type);
    
    visit = visit.asType(type);
    
    MethodHandle guard = MethodHandles.guardWithTest(test, visit, deopt);
    callSite.setTarget(guard);
    return guard.invokeWithArguments(args);
  }
  
  public static boolean checkClass(Class<?> clazz, Object receiver) {
    return receiver.getClass() == clazz;
  }
  
  public static Object deopt(MutableCallSite callSite, Object[] args) throws Throwable {
    // fallback to dispatcher 
    MethodHandle target = dispatcher(callSite.type());
    callSite.setTarget(target);
    return target.invokeWithArguments(args);
  }
  
  public static Object fallback(MutableCallSite callSite, Object[] args) throws Throwable {
    AbstractVisitor visitor = (AbstractVisitor) args[0];
    Object value = args[1];
    
    MethodType type = callSite.type();
    MethodHandle target = visitor.getHandle(value);
    target = target.asType(type);
    
    MethodHandle test = CHECK_CLASS.bindTo(value.getClass());
    test = MethodHandles.dropArguments(test, 0, type.parameterType(0));
    test = test.asType(test.type().changeParameterType(1, type.parameterType(1)));
    
    MethodHandle guard = MethodHandles.guardWithTest(test, target, callSite.getTarget());
    callSite.setTarget(guard);
    return target.invokeWithArguments(args);
  }
  
  private static final MethodHandle GET_HANDLE;
  private static final MethodHandle BOOT;
  private static final MethodHandle CHECK_CLASS;
  private static final MethodHandle DEOPT;
  private static final MethodHandle FALLBACK;
  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      GET_HANDLE = lookup.findVirtual(AbstractVisitor.class, "getHandle",
          MethodType.methodType(MethodHandle.class, Object.class));
      BOOT = lookup.findStatic(RT.class, "boot",
          MethodType.methodType(Object.class, MutableCallSite.class, Object[].class));
      CHECK_CLASS = lookup.findStatic(RT.class, "checkClass",
          MethodType.methodType(boolean.class, Class.class, Object.class));
      DEOPT = lookup.findStatic(RT.class, "deopt",
          MethodType.methodType(Object.class, MutableCallSite.class, Object[].class));
      FALLBACK = lookup.findStatic(RT.class, "fallback",
          MethodType.methodType(Object.class, MutableCallSite.class, Object[].class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
