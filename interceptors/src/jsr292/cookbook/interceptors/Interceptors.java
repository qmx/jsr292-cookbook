package jsr292.cookbook.interceptors;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
//import java.util.Map;

public class Interceptors {
  
  public static MethodHandle before(MethodHandle target, MethodHandle before) {
    MethodType beforeType = before.type();
    if (beforeType.returnType() != void.class) {
      throw new IllegalArgumentException("before must return void "+beforeType);
    }
    MethodType targetType = target.type();
    if (beforeType.parameterCount() != targetType.parameterCount()) {
      if (beforeType.parameterCount() > targetType.parameterCount()) {
        throw new IllegalArgumentException("before has too much parameter compare to target "+beforeType+" "+targetType);
      }
      before = MethodHandles.dropArguments(before,
          beforeType.parameterCount(),
          targetType.parameterList().subList(beforeType.parameterCount(), targetType.parameterCount()));  
    }
    
    if (!before.type().equals(targetType.changeReturnType(void.class))) {
      throw new IllegalArgumentException("before parameter types are not compatible with target "+beforeType+" "+targetType);
    }
    
    return MethodHandles.foldArguments(target, before);
  }
  
  public static MethodHandle after(MethodHandle target, MethodHandle after) {
    //FIXME just use filterReturnValue instead !!, when bug fixed
    
    MethodType afterType = after.type();
    if (afterType.returnType() != void.class) {
      throw new IllegalArgumentException("after must return void "+afterType);
    }
    
    MethodType targetType = target.type();
    boolean voidReturnType = targetType.returnType() == void.class;
    MethodType idealAfterType = (voidReturnType)? targetType:
        MethodType.methodType(void.class, targetType.returnType()).
            appendParameterTypes(targetType.parameterList());
    if (afterType.parameterCount() != idealAfterType.parameterCount()) {
      if (afterType.parameterCount() > idealAfterType.parameterCount()) {
        throw new IllegalArgumentException("after has too much parameter compare to return value + target "+afterType+" "+idealAfterType);
      }
      
      after = MethodHandles.dropArguments(after,
          afterType.parameterCount(),
          idealAfterType.parameterList().subList(afterType.parameterCount(), idealAfterType.parameterCount()));  
    }
    
    if (!after.type().equals(idealAfterType)) {
      throw new IllegalArgumentException("after parameter types are not compatible with return value + target "+afterType+" "+idealAfterType);
    }
  
    if (!voidReturnType) {
      MethodHandle identity = MethodHandles.identity(targetType.returnType());
      identity = MethodHandles.dropArguments(identity, 1, target.type().parameterList());
      after = before(identity, after);
    }  
    
    return MethodHandles.foldArguments(after, target);
  }
  
  public static MethodHandle tryFinally(MethodHandle target, MethodHandle finallyMH) {
    MethodType finallyType = finallyMH.type();
    if (finallyType.returnType() != void.class) {
      throw new IllegalArgumentException("finally block must return void");
    }
    
    MethodType targetType = target.type();
    if (finallyType.parameterCount() != targetType.parameterCount()) {
      if (finallyType.parameterCount() > targetType.parameterCount()) {
        throw new IllegalArgumentException("finally has too much parameter compare to target "+finallyType+" "+targetType);
      }
      finallyMH = MethodHandles.dropArguments(finallyMH,
          finallyType.parameterCount(),
          targetType.parameterList().subList(finallyType.parameterCount(), targetType.parameterCount()));  
    }

    if (!finallyMH.type().equals(targetType.changeReturnType(void.class))) {
      throw new IllegalArgumentException("finally parameter types are not compatible with target "+finallyType+" "+targetType);
    }
    
    MethodHandle rethrow = MethodHandles.throwException(targetType.returnType(), Throwable.class);
    rethrow = MethodHandles.dropArguments(rethrow, 1, targetType.parameterArray());
    MethodHandle finallyInCatch = MethodHandles.dropArguments(finallyMH, 0, Throwable.class);
    MethodHandle catchMH = MethodHandles.foldArguments(rethrow, finallyInCatch);
    
    if (targetType.returnType() != void.class) {
      MethodHandle identity = MethodHandles.identity(targetType.returnType());
      identity = MethodHandles.dropArguments(identity, 1, target.type().parameterList());
      finallyMH = MethodHandles.foldArguments(identity, finallyMH);
    }
    
    MethodHandle tryCatch = MethodHandles.catchException(target, Throwable.class, catchMH);
    return MethodHandles.foldArguments(finallyMH, tryCatch);
  }
}
