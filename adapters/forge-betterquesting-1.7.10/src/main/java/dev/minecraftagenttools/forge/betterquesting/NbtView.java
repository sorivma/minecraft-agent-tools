package dev.minecraftagenttools.forge.betterquesting;

import static dev.minecraftagenttools.protocol.data.Data.*;

import betterquesting.api.utils.NBTConverter;
import java.util.*;
import net.minecraft.nbt.*;

/** Bounded, navigable NBT projection. No SNBT parsing, no game objects escape to socket workers. */
final class NbtView {
    private final List<String> omitted = new ArrayList<String>();
    private int budget = 4096;
    private int textBudget = 131072;

    static Map<String, Object> page(NBTBase root, Map<String, Object> args) {
        String path = args.containsKey("path") ? string(args, "path") : "";
        Object selected = root;
        if (!path.isEmpty())
            for (String segment : path.substring(1).split("/", -1)) {
                String key = segment.replace("~1", "/").replace("~0", "~");
                if (selected instanceof NBTTagCompound) {
                    NBTTagCompound tag = (NBTTagCompound) selected;
                    if (!tag.hasKey(key))
                        throw new IllegalArgumentException("Unknown NBT path: " + path);
                    selected = tag.getTag(key);
                } else {
                    int index;
                    try {
                        index = Integer.parseInt(key);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Expected array index in path");
                    }
                    if (index < 0 || index >= size(selected))
                        throw new IllegalArgumentException("NBT index out of range");
                    selected = child(selected, index);
                }
            }
        NbtView view = new NbtView();
        int offset = integer(args, "offset", 0, 0, Integer.MAX_VALUE),
                limit = integer(args, "limit", 32, 1, 64);
        int total = size(selected), end = (int) Math.min(total, (long) offset + limit);
        Object data;
        if (selected instanceof NBTTagCompound) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            List<String> keys = keys((NBTTagCompound) selected);
            for (int i = Math.min(offset, total); i < end; i++) {
                String key = keys.get(i);
                values.put(
                        key,
                        view.convert(
                                ((NBTTagCompound) selected).getTag(key),
                                path + "/" + escape(key),
                                0));
            }
            data = values;
        } else if (isArray(selected)) {
            List<Object> values = new ArrayList<Object>();
            for (int i = Math.min(offset, total); i < end; i++)
                values.add(view.convert(child(selected, i), path + "/" + i, 0));
            data = values;
        } else if (selected instanceof NBTTagString) {
            String value = ((NBTTagString) selected).func_150285_a_();
            total = value.length();
            end = (int) Math.min(total, (long) offset + 4096);
            data = value.substring(Math.min(offset, total), end);
        } else {
            total = 1;
            end = 1;
            data = view.convert(selected, path, 0);
        }
        return map(
                "path",
                path,
                "nbtType",
                selected instanceof NBTBase
                        ? ((NBTBase) selected).getId()
                        : selected instanceof Byte ? 1 : 3,
                "data",
                data,
                "total",
                total,
                "nextOffset",
                end < total ? end : null,
                "omittedPaths",
                view.omitted,
                "truncated",
                !view.omitted.isEmpty());
    }

    private Object convert(Object value, String path, int depth) {
        if (--budget < 0 || depth > 6) {
            omitted.add(path);
            return null;
        }
        if (value instanceof NBTTagCompound) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            List<String> keys = keys((NBTTagCompound) value);
            int count = Math.min(32, keys.size());
            for (int i = 0; i < count; i++) {
                String key = keys.get(i);
                data.put(
                        key,
                        convert(
                                ((NBTTagCompound) value).getTag(key),
                                path + "/" + escape(key),
                                depth + 1));
            }
            if (count < keys.size()) omitted.add(path);
            return data;
        }
        if (isArray(value)) {
            List<Object> data = new ArrayList<Object>();
            int count = Math.min(32, size(value));
            for (int i = 0; i < count; i++)
                data.add(convert(child(value, i), path + "/" + i, depth + 1));
            if (count < size(value)) omitted.add(path);
            return data;
        }
        if (value instanceof NBTTagString) {
            String text = ((NBTTagString) value).func_150285_a_();
            int length = Math.min(text.length(), Math.min(2048, textBudget));
            textBudget -= length;
            if (length < text.length()) {
                omitted.add(path);
                return text.substring(0, length);
            }
            return text;
        }
        if (value instanceof NBTBase.NBTPrimitive) {
            NBTBase.NBTPrimitive n = (NBTBase.NBTPrimitive) value;
            if (n.getId() == 4)
                return Long.toString(n.func_150291_c()); // Exact 64-bit values for JSON clients.
            if (n.getId() == 5 || n.getId() == 6) {
                double d = n.func_150286_g();
                return Double.isFinite(d) ? d : Double.toString(d);
            }
            return n.func_150287_d();
        }
        return value instanceof Number ? value : null;
    }

    private static boolean isArray(Object v) {
        return v instanceof NBTTagList
                || v instanceof NBTTagByteArray
                || v instanceof NBTTagIntArray;
    }

    private static int size(Object v) {
        if (v instanceof NBTTagCompound) return ((NBTTagCompound) v).func_150296_c().size();
        if (v instanceof NBTTagList) return ((NBTTagList) v).tagCount();
        if (v instanceof NBTTagByteArray) return ((NBTTagByteArray) v).func_150292_c().length;
        if (v instanceof NBTTagIntArray) return ((NBTTagIntArray) v).func_150302_c().length;
        return 0;
    }

    private static Object child(Object v, int i) {
        if (v instanceof NBTTagList) return NBTConverter.getTagList((NBTTagList) v).get(i);
        if (v instanceof NBTTagByteArray) return ((NBTTagByteArray) v).func_150292_c()[i];
        if (v instanceof NBTTagIntArray) return ((NBTTagIntArray) v).func_150302_c()[i];
        throw new IllegalArgumentException("NBT path is not a container");
    }

    @SuppressWarnings("unchecked")
    private static List<String> keys(NBTTagCompound v) {
        List<String> keys = new ArrayList<String>(v.func_150296_c());
        Collections.sort(keys);
        return keys;
    }

    private static String escape(String v) {
        return v.replace("~", "~0").replace("/", "~1");
    }
}
