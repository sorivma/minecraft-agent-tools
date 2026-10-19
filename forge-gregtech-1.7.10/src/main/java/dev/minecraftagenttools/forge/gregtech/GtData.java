package dev.minecraftagenttools.forge.gregtech;

import static dev.minecraftagenttools.protocol.data.Data.map;

import dev.minecraftagenttools.gregtech.contract.GtRead;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;

/** Detached bounded values. No object graph or reflective method names cross the wire. */
final class GtData {
    private GtData() {}

    static Object safe(Supplier<Object> read) {
        try {
            return read.get();
        } catch (RuntimeException | LinkageError e) {
            return map("available", false, "error", GtRead.clip(e.toString(), 512));
        }
    }

    static Object getter(Object target, String method) {
        return safe(() -> value(GtRead.call(target, method), 0));
    }

    static Object field(Object target, String name) {
        return safe(() -> value(GtRead.field(target, name), 0));
    }

    static Object value(Object v, int depth) {
        if (v == null
                || v instanceof Number
                || v instanceof Boolean
                || v instanceof String
                || v instanceof Character
                || v instanceof Enum) return GtRead.scalar(v);
        if (v instanceof ItemStack) {
            ItemStack s = ((ItemStack) v).copy();
            String nbt = s.hasTagCompound() ? s.getTagCompound().toString() : null;
            return map(
                    "item",
                    Item.itemRegistry.getNameForObject(s.getItem()),
                    "meta",
                    s.getItemDamage(),
                    "count",
                    s.stackSize,
                    "name",
                    safe(() -> GtRead.clip(s.getDisplayName(), 512)),
                    "nbt",
                    GtRead.clip(nbt, 8192),
                    "nbtTruncated",
                    nbt != null && nbt.length() > 8192);
        }
        if (v instanceof FluidStack) {
            FluidStack s = (FluidStack) v;
            String nbt = s.tag == null ? null : s.tag.toString();
            return map(
                    "fluid",
                    s.getFluid() == null ? null : s.getFluid().getName(),
                    "amount",
                    s.amount,
                    "unit",
                    "mB",
                    "nbt",
                    GtRead.clip(nbt, 8192),
                    "nbtTruncated",
                    nbt != null && nbt.length() > 8192);
        }
        if (v instanceof Fluid) return map("fluid", ((Fluid) v).getName());
        if (v instanceof NBTTagCompound) {
            String s = v.toString();
            return map("snbt", GtRead.clip(s, 8192), "truncated", s.length() > 8192);
        }
        if (depth >= 3) return map("class", v.getClass().getName(), "truncated", true);
        if (v.getClass().isArray()) {
            int size = Array.getLength(v);
            List<Object> entries = new ArrayList<Object>();
            for (int i = 0; i < Math.min(size, 64); i++)
                entries.add(value(Array.get(v, i), depth + 1));
            return map("entries", entries, "total", size, "truncated", size > 64);
        }
        if (v instanceof Iterable) {
            List<Object> entries = new ArrayList<Object>();
            Iterator<?> iterator = ((Iterable<?>) v).iterator();
            while (entries.size() < 64 && iterator.hasNext())
                entries.add(value(iterator.next(), depth + 1));
            return map("entries", entries, "truncated", iterator.hasNext());
        }
        if (v instanceof Map) {
            List<Object> entries = new ArrayList<Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (entries.size() == 64) break;
                entries.add(
                        map(
                                "key",
                                value(e.getKey(), depth + 1),
                                "value",
                                value(e.getValue(), depth + 1)));
            }
            return map(
                    "entries",
                    entries,
                    "total",
                    ((Map<?, ?>) v).size(),
                    "truncated",
                    ((Map<?, ?>) v).size() > 64);
        }
        Map<String, Object> fields = GtRead.scalars(v);
        if (GtRead.is(v, "gregtech.api.objects.MaterialStack"))
            fields.put("material", field(v, "mMaterial"));
        return map("class", v.getClass().getName(), "publicScalars", fields, "complete", false);
    }

    static Map<String, Object> recipe(Object recipe) {
        Map<String, Object> data =
                map(
                        "source",
                        "GTRecipe",
                        "class",
                        recipe.getClass().getName(),
                        "publicScalars",
                        GtRead.scalars(recipe));
        for (String field :
                Arrays.asList(
                        "mInputs",
                        "mOutputs",
                        "mFluidInputs",
                        "mFluidOutputs",
                        "mAltFluidInputs",
                        "mOreDictAlt",
                        "mOreDictIds",
                        "mSpecialItems")) data.put(field, field(recipe, field));
        data.put(
                "chances",
                map(
                        "itemInputs",
                        chances(recipe, "mInputs", "getInputChance"),
                        "itemOutputs",
                        chances(recipe, "mOutputs", "getOutputChance"),
                        "fluidInputs",
                        chances(recipe, "mFluidInputs", "getFluidInputChance"),
                        "fluidOutputs",
                        chances(recipe, "mFluidOutputs", "getFluidOutputChance"),
                        "scale",
                        10000));
        data.put(
                "energy",
                safe(
                        () -> {
                            long eut = ((Number) GtRead.field(recipe, "mEUt")).longValue();
                            int ticks = ((Number) GtRead.field(recipe, "mDuration")).intValue();
                            return map(
                                    "baseEUPerTick",
                                    Long.toString(eut),
                                    "durationTicks",
                                    ticks,
                                    "durationSecondsAt20TPS",
                                    ticks / 20.0,
                                    "baseEUtTimesTicks",
                                    java.math.BigInteger.valueOf(eut)
                                            .multiply(java.math.BigInteger.valueOf(ticks))
                                            .toString(),
                                    "semantics",
                                    "raw recipe parameters, before machine overclock/parallel/efficiency; fuel maps may use specialValue instead");
                        }));
        data.put(
                "metadata",
                safe(
                        () -> {
                            Object storage = GtRead.call(recipe, "getMetadataStorage");
                            Object entries = GtRead.call(storage, "getEntries");
                            List<Object> result = new ArrayList<Object>();
                            Iterator<?> iterator = ((Iterable<?>) entries).iterator();
                            while (result.size() < 64 && iterator.hasNext()) {
                                Map.Entry<?, ?> e = (Map.Entry<?, ?>) iterator.next();
                                // RecipeMetadataKey.toString is the API's public identifier
                                // representation.
                                result.add(
                                        map(
                                                "key",
                                                GtRead.clip(String.valueOf(e.getKey()), 512),
                                                "value",
                                                value(e.getValue(), 0)));
                            }
                            return map("entries", result, "truncated", iterator.hasNext());
                        }));
        data.put("specialValue", field(recipe, "mSpecialValue"));
        data.put("description", getter(recipe, "getNeiDesc"));
        data.put("specialValueSemantics", "recipe-map-specific; not a universal energy value");
        data.put("complete", false);
        return data;
    }

    private static Object chances(Object recipe, String field, String getter) {
        return safe(
                () -> {
                    Object array = GtRead.field(recipe, field);
                    int length = array == null ? 0 : Array.getLength(array);
                    List<Object> values = new ArrayList<Object>();
                    for (int i = 0; i < Math.min(length, 64); i++)
                        values.add(GtRead.call(recipe, getter, new Class<?>[] {int.class}, i));
                    return map("entries", values, "total", length, "truncated", length > 64);
                });
    }
}
