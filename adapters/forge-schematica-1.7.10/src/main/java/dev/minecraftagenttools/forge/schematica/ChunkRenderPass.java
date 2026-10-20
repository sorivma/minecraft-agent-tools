package dev.minecraftagenttools.forge.schematica;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.objectweb.asm.*;

/** Supplies Forge's render context around Schematica's public chunk callbacks. */
public final class ChunkRenderPass {
    private ChunkRenderPass() {}

    static Constructor<?> constructor(Class<?> chunk, Class<?> world) throws Exception {
        return constructor(
                chunk, world, Class.forName("net.minecraftforge.client.ForgeHooksClient"));
    }

    static Constructor<?> constructor(Class<?> chunk, Class<?> world, Class<?> hooks)
            throws Exception {
        // Schematica remains optional: generate a Java 8 subclass of its public API
        // after loading it, without shipping or modifying any upstream classes.
        String parent = Type.getInternalName(chunk);
        String name = "dev/minecraftagenttools/forge/schematica/ScopedSchematicChunk";
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        out.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, parent, null);
        String ctor = "(" + Type.getDescriptor(world) + "III)V";
        MethodVisitor init = out.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctor, null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        for (int i = 2; i <= 4; i++) init.visitVarInsn(Opcodes.ILOAD, i);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", ctor, false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        scoped(out, parent, hooks, "renderBlocks", 7);
        scoped(out, parent, hooks, "renderTileEntities", 1);
        out.visitEnd();
        class Loader extends ClassLoader {
            Loader() {
                super(chunk.getClassLoader());
            }

            Class<?> define(byte[] bytes) {
                return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
            }
        }
        return new Loader()
                .define(out.toByteArray())
                .getConstructor(world, int.class, int.class, int.class);
    }

    private static void scoped(
            ClassWriter out, String parent, Class<?> hooks, String method, int arguments) {
        String descriptor = "(" + new String(new char[arguments]).replace('\0', 'I') + ")V";
        String bridge = Type.getInternalName(ChunkRenderPass.class);
        MethodVisitor m = out.visitMethod(Opcodes.ACC_PUBLIC, method, descriptor, null, null);
        m.visitCode();
        m.visitLdcInsn(Type.getType(hooks));
        m.visitVarInsn(Opcodes.ILOAD, 1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, "enter", "(Ljava/lang/Class;I)[I", false);
        m.visitVarInsn(Opcodes.ASTORE, arguments + 1);
        Label start = new Label(), end = new Label(), failure = new Label();
        m.visitTryCatchBlock(start, end, failure, null);
        m.visitLabel(start);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        for (int i = 1; i <= arguments; i++) m.visitVarInsn(Opcodes.ILOAD, i);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, method, descriptor, false);
        m.visitLabel(end);
        restore(m, hooks, bridge, arguments);
        m.visitInsn(Opcodes.RETURN);
        m.visitLabel(failure);
        m.visitVarInsn(Opcodes.ASTORE, arguments + 2);
        restore(m, hooks, bridge, arguments);
        m.visitVarInsn(Opcodes.ALOAD, arguments + 2);
        m.visitInsn(Opcodes.ATHROW);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void restore(MethodVisitor m, Class<?> hooks, String bridge, int arguments) {
        m.visitLdcInsn(Type.getType(hooks));
        m.visitVarInsn(Opcodes.ALOAD, arguments + 1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, "exit", "(Ljava/lang/Class;[I)V", false);
    }

    private static Field field(Class<?> hooks, String name) throws ReflectiveOperationException {
        Field field = hooks.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public static int[] enter(Class<?> hooks, int pass) throws ReflectiveOperationException {
        Field world = field(hooks, "worldRenderPass"), tile = field(hooks, "renderPass");
        int[] old = {world.getInt(null), tile.getInt(null)};
        world.setInt(null, pass);
        tile.setInt(null, pass);
        return old;
    }

    public static void exit(Class<?> hooks, int[] old) throws ReflectiveOperationException {
        field(hooks, "worldRenderPass").setInt(null, old[0]);
        field(hooks, "renderPass").setInt(null, old[1]);
    }
}
