package jsr292.cookbook.lazyinit;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

import sun.misc.BASE64Decoder;

public class RT {
  @SuppressWarnings("restriction")
  public static CallSite bootstrap(Lookup lookup, String name, MethodType type, String base64Array) throws IOException {
    BASE64Decoder decoder = new BASE64Decoder();
    byte[] bytes = decoder.decodeBuffer(base64Array);
    
    Class<?> returnType = type.returnType();
    Object value = convertAsArray(bytes, returnType);
    return new ConstantCallSite(MethodHandles.constant(returnType, value));
  }
  
  private static Object convertAsArray(byte[] bytes, Class<?> returnType) {
    if (returnType == byte[].class) {
      return bytes;
    } else
      if (returnType == char[].class) {
        char[] array = new char[bytes.length / 2];
        ByteBuffer.wrap(bytes).asCharBuffer().get(array);
        return array;
      } else
        if (returnType == short[].class) {
          return ByteBuffer.wrap(bytes).asShortBuffer().duplicate().array();
        } else
          if (returnType == int[].class) {
            return ByteBuffer.wrap(bytes).asIntBuffer().duplicate().array();
          } else
            if (returnType == long[].class) {
              return ByteBuffer.wrap(bytes).asLongBuffer().duplicate().array();
            } else
              if (returnType == float[].class) {
                return ByteBuffer.wrap(bytes).asFloatBuffer().duplicate().array();
              } else
                if (returnType == double[].class) {
                  return ByteBuffer.wrap(bytes).asDoubleBuffer().duplicate().array();
                } else {
                  throw new BootstrapMethodError("invalid constant array type "+returnType);
                }
  }
}
