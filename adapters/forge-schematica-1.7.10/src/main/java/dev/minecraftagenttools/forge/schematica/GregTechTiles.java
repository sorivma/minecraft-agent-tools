package dev.minecraftagenttools.forge.schematica;

/** Finishes render-only GT initialization without running a machine tick. */
final class GregTechTiles {
    static void bind(Object tile) throws ReflectiveOperationException {
        Object meta = SchematicaApi.call(tile, "getMetaTileEntity", new Class<?>[0]);
        if (meta == null)
            throw new IllegalArgumentException("GregTech machine initialization failed");
        // BaseMetaTileEntity renders ERROR_RENDERING while its cached validity is
        // false. Its first tick normally rebinds the meta entity; ghost tiles never
        // tick, so use the same public binding callback after NBT/world validation.
        Class<?> baseType = meta.getClass().getMethod("getBaseMetaTileEntity").getReturnType();
        SchematicaApi.call(meta, "setBaseMetaTileEntity", new Class<?>[] {baseType}, tile);
        if (SchematicaApi.call(meta, "getBaseMetaTileEntity", new Class<?>[0]) != tile
                || !Boolean.TRUE.equals(SchematicaApi.call(tile, "isStillValid", new Class<?>[0])))
            throw new IllegalArgumentException("GregTech ghost machine binding is invalid");
    }
}
