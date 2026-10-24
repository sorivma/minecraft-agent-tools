package dev.minecraftagenttools.forge.schematica;

import java.lang.reflect.*;
import java.util.Arrays;

/** Preserves extended GTNH metadata behind the optional ISchematic interface. */
final class SchematicMetadata implements InvocationHandler {
    private final Object delegate;
    private final int width, height, length;
    private final int[] metadata;

    private SchematicMetadata(Object delegate, int width, int height, int length) {
        this.delegate = delegate;
        this.width = width;
        this.height = height;
        this.length = length;
        metadata = new int[width * height * length];
        Arrays.fill(metadata, -1);
    }

    static Object wrap(Class<?> api, Object delegate, int width, int height, int length) {
        return Proxy.newProxyInstance(
                api.getClassLoader(),
                new Class<?>[] {api},
                new SchematicMetadata(delegate, width, height, length));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        boolean get = name.equals("getBlockMetadata");
        boolean setMeta = name.equals("setBlockMetadata");
        boolean setBlock = name.equals("setBlock");
        int index = -1;
        if (get || setMeta || setBlock) {
            int x = (Integer) args[0], y = (Integer) args[1], z = (Integer) args[2];
            if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length)
                index = x + (y * length + z) * width;
        }
        if (get && index >= 0 && metadata[index] >= 0) return metadata[index];
        Object result;
        try {
            result = method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        if (index >= 0 && Boolean.TRUE.equals(result)) {
            if (setMeta) metadata[index] = (Integer) args[3];
            if (setBlock) metadata[index] = args.length == 5 ? (Integer) args[4] : 0;
        }
        return result;
    }
}
