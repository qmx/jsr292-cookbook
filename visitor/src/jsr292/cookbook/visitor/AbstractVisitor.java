package jsr292.cookbook.visitor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.HashMap;

public abstract class AbstractVisitor {
  private final ClassValue<HashMap<Class<?>, MethodHandle>> vtable =
    new ClassValue<HashMap<Class<?>,MethodHandle>>() {
      @Override
      protected HashMap<Class<?>, MethodHandle> computeValue(Class<?> type) {
        Lookup lookup = MethodHandles.lookup();
        HashMap<Class<?>, MethodHandle> handleMap =
          new HashMap<Class<?>, MethodHandle>();
        for(Method method: type.getMethods()) {
          if (!method.isAnnotationPresent(Visit.class)) {
            continue;
          }
          
          MethodHandle mh;
          try {
            mh = lookup.unreflect(method);
          } catch (IllegalAccessException e) {
            throw (IllegalAccessError)new IllegalAccessError().initCause(e);
          }
          
          Class<?>[] parameterTypes = method.getParameterTypes();
          if (parameterTypes.length == 0) {
            throw new IllegalStateException("the visit method "+method+" must have at least one parameter");
          }
          
          handleMap.put(parameterTypes[0], mh.asType(mh.type().changeParameterType(1, Object.class)));
        }
        return handleMap;
      }
    };
  
  protected AbstractVisitor() {
    // do nothing
  }
  
  MethodHandle findHandle(Class<?> receiverClass) {
    return vtable.get(getClass()).get(receiverClass);
  }
  
  public MethodHandle getHandle(Object receiver) {
    MethodHandle mh = findHandle(receiver.getClass());
    if (mh != null)
      return mh;
   throw new IllegalArgumentException("no visit for "+receiver);
  }
}
