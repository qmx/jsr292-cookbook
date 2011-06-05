package jsr292.cookbook.memoize;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.HashMap;

public class RT {
  private static ClassValue<HashMap<String, HashMap<Object,Object>>> cacheTables =
    new ClassValue<HashMap<String,HashMap<Object,Object>>>() {
      @Override
      protected HashMap<String, HashMap<Object, Object>> computeValue(Class<?> type) {
        return new HashMap<String, HashMap<Object,Object>>();
      }
    };
  
  public static CallSite bootstrap(Lookup lookup, String name, MethodType type, Class<?> staticType) throws ReflectiveOperationException {
    MethodHandle target = lookup.findStatic(staticType, name, type);
    
    HashMap<String, HashMap<Object, Object>> cacheTable = cacheTables.get(staticType);
    String selector = name + type.toMethodDescriptorString();
    HashMap<Object, Object> cache = cacheTable.get(selector);
    if (cache == null) {
      cache = new HashMap<Object, Object>();
      cacheTable.put(selector, cache);
    }
    
    MethodHandle identity = MethodHandles.identity(type.returnType());
    identity = identity.asType(identity.type().changeParameterType(0, Object.class));
    identity = MethodHandles.dropArguments(identity, 1, type.parameterType(0));
    
    /*FIXME fold doesn't work if combiner returns void !
    MethodHandle cachePut = MAP_PUT.bindTo(cache);
    cachePut = MethodHandles.permuteArguments(cachePut,
        MethodType.methodType(void.class, Object.class, Object.class),
        1, 0);
    cachePut = cachePut.asType(MethodType.methodType(void.class, type.parameterType(0), type.returnType()));
    
    MethodHandle identity2 = MethodHandles.dropArguments(identity, 1, type.parameterType(0));
    
    MethodHandle update = MethodHandles.foldArguments(identity2, cachePut);
    update = update.asType(type.insertParameterTypes(0, type.returnType()));
    */
    
    MethodHandle update = UPDATE.bindTo(cache);
    update = update.asType(type.insertParameterTypes(0, type.returnType()));
    
    MethodHandle fallback = MethodHandles.foldArguments(update, target);
    fallback = MethodHandles.dropArguments(fallback, 0, Object.class);
    
    MethodHandle combiner = MethodHandles.guardWithTest(NOT_NULL, identity, fallback);  // (Object,int)int
    
    MethodHandle cacheQuerier = MAP_GET.bindTo(cache);
    cacheQuerier = cacheQuerier.asType(MethodType.methodType(Object.class, type.parameterType(0)));
    
    MethodHandle memoize = MethodHandles.foldArguments(combiner, cacheQuerier);
    return new ConstantCallSite(memoize);
  }
  
  public static boolean notNull(Object receiver) {
    return receiver != null;
  }
  
  public static Object update(HashMap<Object, Object> cache, Object result, Object arg) {
    cache.put(arg, result);
    return result;
  }
  
  private static final MethodHandle NOT_NULL;
  private static final MethodHandle MAP_GET;
  //private static final MethodHandle MAP_PUT;
  private static final MethodHandle UPDATE;
  static {
    Lookup lookup = MethodHandles.lookup();
    try {
      NOT_NULL = lookup.findStatic(RT.class, "notNull",
          MethodType.methodType(boolean.class, Object.class));
      MAP_GET = lookup.findVirtual(HashMap.class, "get",
          MethodType.methodType(Object.class, Object.class));
      /*MAP_PUT = lookup.findVirtual(HashMap.class, "put",
          MethodType.methodType(Object.class, Object.class, Object.class)).
          asType(MethodType.methodType(void.class, HashMap.class, Object.class, Object.class));*/
      UPDATE = lookup.findStatic(RT.class, "update",
          MethodType.methodType(Object.class, HashMap.class, Object.class, Object.class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }
}
