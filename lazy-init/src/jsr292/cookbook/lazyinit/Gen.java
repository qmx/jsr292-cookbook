package jsr292.cookbook.lazyinit;

import sun.misc.BASE64Encoder;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import jsr292.cookbook.metaclass.RT;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodHandle;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/*
public class Main {
  public static void main(String[] args) {
    System.out.println(java.util.Arrays.toString(  {'h', 'e', 'l', 'l', 'o'}  ));
  } 
}
*/
public class Gen {
  private static final MethodHandle BSM = 
    new MethodHandle(MH_INVOKESTATIC,
        RT.class.getName().replace('.', '/'),
        "bootstrap",
        MethodType.methodType(
            CallSite.class, Lookup.class, String.class, MethodType.class, String.class
            ).toMethodDescriptorString());
  
  @SuppressWarnings("restriction")
  public static void main(String[] args) throws IOException {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(V1_7, ACC_PUBLIC | ACC_SUPER, "Main", null, "java/lang/Object", null);

    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

    ByteBuffer byteBuffer = ByteBuffer.allocate(10);
    byteBuffer.asCharBuffer().put("hello".toCharArray());
    BASE64Encoder base64Encoder = new BASE64Encoder();
    String base64 = base64Encoder.encode(byteBuffer);
    mv.visitInvokeDynamicInsn("const", "()[C", BSM, base64);

    mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "toString", "([C)Ljava/lang/String;");
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();

    Files.write(Paths.get("lazy-init/Main.class"), cw.toByteArray());
  }
}
