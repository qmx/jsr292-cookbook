package jsr292.cookbook.vep;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodHandle;
import org.objectweb.asm.MethodVisitor;

public class Rewriter {
  static final MethodHandle BSM = 
    new MethodHandle(MH_INVOKESTATIC,
        RT.class.getName().replace('.', '/'),
        "bootstrap",
        MethodType.methodType(
            CallSite.class, Lookup.class, String.class, MethodType.class
            ).toMethodDescriptorString());
  
  public static void main(String[] args) throws IOException {
    Path path = Paths.get(args[0]);
    InputStream input = Files.newInputStream(path);
    ClassReader reader = new ClassReader(input);
    ClassWriter writer = new ClassWriter(reader, 0);
    reader.accept(new ClassAdapter(writer) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new MethodAdapter(mv) {
          @Override
          public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (opcode == INVOKESPECIAL || opcode == INVOKESTATIC) {
              super.visitMethodInsn(opcode, owner, name, desc);
              return;
            }
            // INVOKEINTERFACE & INVOKEVIRTUAL
            super.visitInvokeDynamicInsn(name, "(L" + owner +';' + desc.substring(1), BSM);
          }
        };
      }
    } , 0);
    input.close();
    Files.write(path, writer.toByteArray());
  }
}
