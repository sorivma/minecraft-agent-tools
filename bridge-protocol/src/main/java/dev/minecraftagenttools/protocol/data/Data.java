package dev.minecraftagenttools.protocol.data;

import java.util.*;

/** JSON-compatible values only: no game objects may cross the thread boundary. */
public final class Data {
    private Data() {}

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    public static String string(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty())
            throw new IllegalArgumentException("Required string: " + key);
        return (String) value;
    }

    public static int integer(
            Map<String, Object> args, String key, int fallback, int min, int max) {
        Object value = args.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number))
            throw new IllegalArgumentException("Expected integer: " + key);
        double n = ((Number) value).doubleValue();
        if (!Double.isFinite(n) || n != Math.rint(n) || n < min || n > max)
            throw new IllegalArgumentException(
                    "Out of range integer: " + key + " [" + min + ", " + max + "]");
        return (int) n;
    }
}
