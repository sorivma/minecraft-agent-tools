package dev.minecraftagenttools.gregtech.contract;

import static dev.minecraftagenttools.protocol.data.Data.map;

import java.lang.reflect.*;
import java.util.*;

/** Internal allowlisted callers only. No class/field/method name is accepted from MCP arguments. */
public final class GtRead {
    private GtRead() {}

    public static Object field(Object target, String name) {
        try {
            return (target instanceof Class ? (Class<?>) target : target.getClass())
                    .getField(name)
                    .get(target instanceof Class ? null : target);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Unavailable GT field: " + name, e);
        }
    }

    public static Object call(Object target, String name) {
        return call(target, name, new Class<?>[0]);
    }

    public static Object call(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Class<?> owner = target instanceof Class ? (Class<?>) target : target.getClass();
            Method method = publicMethod(owner, name, types);
            return method.invoke(target instanceof Class ? null : target, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Unavailable GT getter: " + name, e);
        }
    }

    private static Method publicMethod(Class<?> owner, String name, Class<?>[] types)
            throws NoSuchMethodException {
        Method method = owner.getMethod(name, types);
        if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) return method;
        // Anonymous StructureLib elements implement public interfaces. Invoke their public
        // contract,
        // without opening private members or depending on implementation-class visibility.
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Class<?> api : current.getInterfaces()) {
                try {
                    return publicMethod(api, name, types);
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (current != owner && Modifier.isPublic(current.getModifiers())) {
                try {
                    return current.getMethod(name, types);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        throw new NoSuchMethodException("No accessible public contract for " + name);
    }

    public static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("Unavailable GT API: " + name, e);
        }
    }

    public static boolean is(Object value, String name) {
        if (value == null) return false;
        try {
            return type(name).isInstance(value);
        } catch (IllegalStateException absent) {
            return false;
        }
    }

    /** Unknowns remain explicit. Never invoke toString on an arbitrary mod object. */
    public static Object scalar(Object value) {
        if (value == null || value instanceof Boolean) return value;
        if (value instanceof Long
                || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal) return value.toString();
        if (value instanceof Number) {
            if (value instanceof Double && !Double.isFinite((Double) value)
                    || value instanceof Float && !Float.isFinite((Float) value))
                return map("unavailable", "non-finite number");
            return value;
        }
        if (value instanceof Enum) return ((Enum<?>) value).name();
        if (value instanceof String || value instanceof Character)
            return clip(String.valueOf(value), 4096);
        return map(
                "class",
                value.getClass().getName(),
                "unavailable",
                "no generic scalar representation");
    }

    public static Map<String, Object> scalars(Object target) {
        Map<String, Object> result = new TreeMap<String, Object>();
        int count = 0;
        for (Field field : target.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Class<?> type = field.getType();
            if (!(type.isPrimitive()
                    || type == String.class
                    || Number.class.isAssignableFrom(type)
                    || type.isEnum())) continue;
            if (count++ == 128) {
                result.put("_truncated", true);
                break;
            }
            try {
                result.put(field.getName(), scalar(field.get(target)));
            } catch (IllegalAccessException e) {
                result.put(field.getName(), map("unavailable", "inaccessible"));
            }
        }
        return result;
    }

    public static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
