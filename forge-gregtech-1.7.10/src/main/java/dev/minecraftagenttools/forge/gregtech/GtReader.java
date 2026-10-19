package dev.minecraftagenttools.forge.gregtech;

import static dev.minecraftagenttools.protocol.data.Data.*;

import cpw.mods.fml.common.Loader;
import dev.minecraftagenttools.gregtech.contract.GtCatalogs;
import dev.minecraftagenttools.gregtech.contract.GtRead;
import dev.minecraftagenttools.gregtech.contract.GtTools;
import dev.minecraftagenttools.protocol.world.WorldSessions;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

/** Server-tick-owned registry and loaded-machine reads. */
final class GtReader {
    private final WorldSessions<WorldServer> worlds = new WorldSessions<WorldServer>();
    private final GtRegistry registry = new GtRegistry();
    private final GtCatalogs catalogs = new GtCatalogs(System::nanoTime);

    void tick() {
        Map<Integer, WorldServer> active = new HashMap<Integer, WorldServer>();
        for (WorldServer world : DimensionManager.getWorlds())
            active.put(world.provider.dimensionId, world);
        worlds.refresh(active);
        catalogs.refresh(worlds::session);
    }

    Map<String, Object> call(String tool, Map<String, Object> args) {
        GtTools.validate(tool, args);
        tick();
        if (tool.equals("gt_status")) return status();
        int dimension = integer(args, "dimension", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
        WorldServer world = worlds.require(dimension, string(args, "session"));
        Object result;
        switch (tool) {
            case "gt_list_recipe_maps":
                result =
                        page(
                                new ArrayList<Object>(
                                        new TreeMap<String, Object>(registry.maps()).values()),
                                args,
                                v ->
                                        map(
                                                "map",
                                                GtRead.field(v, "unlocalizedName"),
                                                "amperage",
                                                GtData.getter(v, "getAmperage"),
                                                "recipeCount",
                                                GtData.safe(
                                                        () ->
                                                                ((Collection<?>)
                                                                                GtRead.call(
                                                                                        v,
                                                                                        "getAllRecipes"))
                                                                        .size())),
                                v -> String.valueOf(GtRead.field(v, "unlocalizedName")));
                break;
            case "gt_search_recipes":
                result = searchRecipes(args, dimension);
                break;
            case "gt_get_recipe":
                {
                    GtCatalogs.Entry catalog = catalog(args, dimension);
                    int index = integer(args, "recipeIndex", 0, 0, Integer.MAX_VALUE);
                    if (index >= catalog.recipes.size())
                        throw new IllegalArgumentException("Recipe index outside catalog");
                    result =
                            map(
                                    "map",
                                    catalog.map,
                                    "catalogId",
                                    args.get("catalogId"),
                                    "recipeIndex",
                                    index,
                                    "mapProperties",
                                    mapProperties(registry.maps().get(catalog.map)),
                                    "recipe",
                                    GtData.recipe(catalog.recipes.get(index)));
                    break;
                }
            case "gt_list_machines":
                {
                    Object[] machines = registry.machines();
                    List<Object> ids = new ArrayList<Object>();
                    for (int i = 0; i < machines.length; i++) if (machines[i] != null) ids.add(i);
                    result =
                            page(
                                    ids,
                                    args,
                                    id ->
                                            machineSummary(
                                                    ((Number) id).intValue(),
                                                    machines[((Number) id).intValue()]),
                                    id -> machineName(machines[((Number) id).intValue()]));
                    break;
                }
            case "gt_get_machine":
                result = machineDefinition(integer(args, "machineId", 0, 0, 32767));
                break;
            case "gt_get_structure":
                {
                    Object machine = machine(integer(args, "machineId", 0, 0, 32767));
                    result =
                            GtData.safe(
                                    () ->
                                            GtStructures.read(
                                                    machine,
                                                    (String) args.get("piece"),
                                                    integer(
                                                            args,
                                                            "offset",
                                                            0,
                                                            0,
                                                            Integer.MAX_VALUE),
                                                    integer(args, "limit", 32, 1, 64)));
                    break;
                }
            case "gt_inspect_machine":
                result = inspect(world, args);
                break;
            case "gt_list_materials":
                result =
                        page(
                                new ArrayList<Object>(
                                        new TreeMap<String, Object>(registry.materials()).values()),
                                args,
                                v ->
                                        map(
                                                "material",
                                                GtRead.field(v, "mName"),
                                                "name",
                                                GtData.field(v, "mDefaultLocalName"),
                                                "formula",
                                                GtData.getter(v, "getChemicalFormula")),
                                v ->
                                        String.valueOf(GtRead.field(v, "mName"))
                                                + " "
                                                + GtRead.call(v, "getChemicalFormula"));
                break;
            case "gt_get_material":
                result = material(string(args, "material"));
                break;
            case "gt_list_ore_prefixes":
                result =
                        page(
                                Arrays.asList(registry.prefixes()),
                                args,
                                v ->
                                        map(
                                                "name",
                                                GtData.getter(v, "getName"),
                                                "properties",
                                                GtRead.scalars(v)),
                                v -> String.valueOf(GtRead.call(v, "getName")));
                break;
            case "gt_get_energy_tiers":
                result = tiers();
                break;
            default:
                throw new IllegalArgumentException("Unknown GregTech tool");
        }
        return map(
                "session",
                worlds.session(dimension),
                "dimension",
                dimension,
                "tick",
                world.getTotalWorldTime(),
                "observedAtEpochMs",
                System.currentTimeMillis(),
                "result",
                result);
    }

    private Map<String, Object> status() {
        List<Object> dimensions = new ArrayList<Object>();
        for (int id : worlds.dimensions())
            dimensions.add(map("dimension", id, "session", worlds.session(id)));
        Map<String, Object> probes =
                map(
                        "recipeMaps",
                        GtData.safe(() -> registry.maps().size()),
                        "machineSlots",
                        GtData.safe(() -> registry.machines().length),
                        "materials",
                        GtData.safe(() -> registry.materials().size()),
                        "orePrefixes",
                        GtData.safe(() -> registry.prefixes().length));
        return map(
                "gregtechVersion",
                Loader.instance().getIndexedModList().get("gregtech").getVersion(),
                "apiProfile",
                "GTNH GT5 5.09.54.183 / StructureLib 1.4.45",
                "dimensions",
                dimensions,
                "registryProbes",
                probes,
                "tools",
                GtTools.list(),
                "readOnly",
                true,
                "coverage",
                Arrays.asList(
                        "registered GTRecipe maps including fuels/addons",
                        "meta-tile definitions",
                        "loaded GT tile energy/inventory/fluid/state",
                        "available StructureLib pieces and requirement descriptions",
                        "GT materials and ore prefixes",
                        "voltage tiers"),
                "limitations",
                Arrays.asList(
                        "not every addon exposes every field",
                        "no forced structure validation or missing-block diagnosis",
                        "no universal overclock simulation",
                        "no complete worldgen/quest/cover/wireless-network introspection",
                        "non-GT energy APIs not included",
                        "machine crafting recipes remain available through NEI"));
    }

    private Object machine(int id) {
        Object[] machines = registry.machines();
        if (id >= machines.length || machines[id] == null)
            throw new IllegalArgumentException("Unknown registered machineId");
        return machines[id];
    }

    private String machineName(Object machine) {
        return String.valueOf(GtRead.call(machine, "getMetaName"))
                + " "
                + machine.getClass().getName();
    }

    private Map<String, Object> machineSummary(int id, Object machine) {
        return map(
                "machineId",
                id,
                "name",
                GtData.getter(machine, "getMetaName"),
                "class",
                machine.getClass().getName(),
                "multiblock",
                GtRead.is(
                        machine, "gregtech.api.metatileentity.implementations.MTEMultiBlockBase"));
    }

    private Object machineDefinition(int id) {
        Object machine = machine(id);
        Map<String, Object> result = machineSummary(id, machine);
        result.putAll(
                map(
                        "description",
                        GtData.getter(machine, "getDescription"),
                        "stack",
                        GtData.safe(
                                () ->
                                        GtData.value(
                                                GtRead.call(
                                                        machine,
                                                        "getStackForm",
                                                        new Class<?>[] {long.class},
                                                        1L),
                                                0)),
                        "publicScalars",
                        GtRead.scalars(machine),
                        "recipeMap",
                        recipeMap(machine),
                        "structureDescription",
                        GtData.safe(
                                () ->
                                        GtData.value(
                                                GtRead.call(
                                                        machine,
                                                        "getStructureDescription",
                                                        new Class<?>[] {ItemStack.class},
                                                        GtRead.call(
                                                                machine,
                                                                "getStackForm",
                                                                new Class<?>[] {long.class},
                                                                1L)),
                                                0))));
        Map<String, Object> limits = new LinkedHashMap<String, Object>();
        for (String name :
                Arrays.asList(
                        "isElectric",
                        "isSteampowered",
                        "isPneumatic",
                        "maxEUStore",
                        "maxEUInput",
                        "maxEUOutput",
                        "maxAmperesIn",
                        "maxAmperesOut",
                        "maxSteamStore",
                        "getInputTier",
                        "getOutputTier")) limits.put(name, GtData.getter(machine, name));
        result.put("definitionEnergyLimits", limits);
        return result;
    }

    private Object recipeMap(Object machine) {
        return GtData.safe(
                () -> {
                    Object map = GtRead.call(machine, "getRecipeMap");
                    return map == null ? null : GtRead.field(map, "unlocalizedName");
                });
    }

    private Object material(String name) {
        Object material = registry.materials().get(name);
        if (material == null) throw new IllegalArgumentException("Unknown material name");
        Map<String, Object> result =
                map(
                        "material",
                        name,
                        "properties",
                        GtRead.scalars(material),
                        "formula",
                        GtData.getter(material, "getChemicalFormula"));
        for (String field :
                Arrays.asList(
                        "mMaterialList",
                        "mOreByProducts",
                        "mOreReplacement",
                        "mSmeltInto",
                        "mMacerateInto",
                        "mArcSmeltInto",
                        "mDirectSmelting",
                        "mSolid",
                        "mFluid",
                        "mGas",
                        "mPlasma",
                        "mStandardMoltenFluid")) result.put(field, GtData.field(material, field));
        return result;
    }

    private Object tiers() {
        Map<String, Object> result =
                map(
                        "voltageUnit",
                        "EU/p",
                        "recipeThresholdMeaning",
                        "VP is recipe voltage threshold, not universal machine consumption");
        for (String field : Arrays.asList("V", "VP", "VN", "VOLTAGE_NAMES"))
            result.put(field, GtData.safe(() -> GtData.value(registry.tiers(field), 0)));
        return result;
    }

    private Object mapProperties(Object map) {
        return GtData.safe(
                () -> {
                    Object frontend = GtRead.call(map, "getFrontend");
                    return map(
                            "amperage",
                            GtData.getter(map, "getAmperage"),
                            "frontendClass",
                            frontend.getClass().getName(),
                            "uiProperties",
                            GtRead.scalars(GtRead.call(frontend, "getUIProperties")),
                            "neiProperties",
                            GtRead.scalars(GtRead.call(frontend, "getNEIProperties")));
                });
    }

    private Object inspect(WorldServer world, Map<String, Object> args) {
        int x = integer(args, "x", 0, -29999984, 29999983),
                y = integer(args, "y", 0, 0, 255),
                z = integer(args, "z", 0, -29999984, 29999983);
        if (!world.getChunkProvider().chunkExists(x >> 4, z >> 4))
            return map("availability", "chunk_unloaded");
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!GtRead.is(tile, "gregtech.api.interfaces.tileentity.IGregTechTileEntity"))
            return map("availability", "not_a_gregtech_tile");
        Object meta = GtRead.call(tile, "getMetaTileEntity");
        Map<String, Object> result =
                map(
                        "availability",
                        "loaded",
                        "x",
                        x,
                        "y",
                        y,
                        "z",
                        z,
                        "machineId",
                        GtData.getter(tile, "getMetaTileID"),
                        "tileClass",
                        tile.getClass().getName(),
                        "energy",
                        energy(tile));
        if (meta == null) {
            result.put("metaTile", null);
            return result;
        }
        result.putAll(
                map(
                        "metaClass",
                        meta.getClass().getName(),
                        "name",
                        GtData.getter(meta, "getMetaName"),
                        "recipeMap",
                        recipeMap(meta),
                        "publicState",
                        GtRead.scalars(meta),
                        "scannerText",
                        GtData.getter(meta, "getInfoData"),
                        "scannerTextAuthoritativeStructure",
                        false));
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        for (String method :
                Arrays.asList(
                        "isActive",
                        "isAllowedToWork",
                        "getProgress",
                        "getMaxProgress",
                        "getErrorDisplayID",
                        "getFrontFacing",
                        "wasShutdown")) state.put(method, GtData.getter(tile, method));
        result.put("state", state);
        if (GtRead.is(meta, "gregtech.api.metatileentity.implementations.MTEMultiBlockBase")) {
            Map<String, Object> multiblock =
                    map(
                            "formedCached",
                            GtData.field(meta, "mMachine"),
                            "formedSemantics",
                            "last state maintained by machine, not a fresh validation",
                            "rawSignedEUt",
                            GtData.getter(meta, "getEUtForDamageCalc"),
                            "rawEUtSemantics",
                            "GT multiblock: negative consumption / positive generation; actual efficiency losses may differ",
                            "repairStatus",
                            GtData.getter(meta, "getRepairStatus"));
            for (String field :
                    Arrays.asList(
                            "mInputHatches",
                            "mOutputHatches",
                            "mInputBusses",
                            "mOutputBusses",
                            "mEnergyHatches",
                            "mDynamoHatches",
                            "mMaintenanceHatches",
                            "mMufflerHatches"))
                multiblock.put(field, GtData.safe(() -> hatches(GtRead.field(meta, field))));
            multiblock.put(
                    "exoticEnergyHatches",
                    GtData.safe(() -> hatches(GtRead.call(meta, "getExoticEnergyHatches"))));
            multiblock.put(
                    "exoticDynamoHatches",
                    GtData.safe(() -> hatches(GtRead.call(meta, "getExoticDynamoHatches"))));
            result.put("multiblock", multiblock);
        }
        if (tile instanceof IInventory) {
            IInventory inventory = (IInventory) tile;
            List<Object> slots = new ArrayList<Object>();
            for (int i = 0; i < Math.min(inventory.getSizeInventory(), 64); i++)
                slots.add(map("slot", i, "stack", GtData.value(inventory.getStackInSlot(i), 0)));
            result.put(
                    "inventory",
                    map(
                            "slots",
                            slots,
                            "total",
                            inventory.getSizeInventory(),
                            "truncated",
                            inventory.getSizeInventory() > 64));
        }
        if (tile instanceof IFluidHandler) {
            Map<String, Object> sides = new LinkedHashMap<String, Object>();
            for (ForgeDirection side : ForgeDirection.values())
                sides.put(
                        side.name(),
                        GtData.safe(
                                () -> {
                                    FluidTankInfo[] tanks =
                                            ((IFluidHandler) tile).getTankInfo(side);
                                    List<Object> values = new ArrayList<Object>();
                                    int total = tanks == null ? 0 : tanks.length;
                                    for (int i = 0; i < Math.min(64, total); i++)
                                        values.add(
                                                tanks[i] == null
                                                        ? null
                                                        : map(
                                                                "capacity",
                                                                tanks[i].capacity,
                                                                "unit",
                                                                "mB",
                                                                "fluid",
                                                                GtData.value(tanks[i].fluid, 0)));
                                    return map(
                                            "tanks",
                                            values,
                                            "total",
                                            total,
                                            "truncated",
                                            total > 64);
                                }));
            result.put("tanksBySide", sides);
        }
        result.put(
                "nbt",
                GtData.safe(
                        () -> {
                            NBTTagCompound nbt = new NBTTagCompound();
                            tile.writeToNBT(nbt);
                            return GtData.value(nbt, 0);
                        }));
        return result;
    }

    static Object energy(Object tile) {
        Map<String, Object> result =
                map(
                        "source",
                        "GregTech IBasicEnergyContainer",
                        "steamUnit",
                        "EU-equivalent as reported by GregTech, not fluid mB",
                        "voltageUnit",
                        "EU/p",
                        "averageWindowTicks",
                        5);
        for (String method :
                Arrays.asList(
                        "getStoredEU",
                        "getEUCapacity",
                        "getStoredSteam",
                        "getSteamCapacity",
                        "getUniversalEnergyStored",
                        "getUniversalEnergyCapacity",
                        "getInputVoltage",
                        "getOutputVoltage",
                        "getInputAmperage",
                        "getOutputAmperage",
                        "getAverageElectricInput",
                        "getAverageElectricOutput"))
            result.put(method, GtData.getter(tile, method));
        result.put(
                "universalEnergyMeaning",
                "largest supported energy store in EU-equivalent; not a sum of energy forms");
        return result;
    }

    private Object hatches(Object source) {
        List<Object> result = new ArrayList<Object>();
        Iterator<?> iterator = ((Iterable<?>) source).iterator();
        while (result.size() < 64 && iterator.hasNext()) {
            Object hatch = iterator.next();
            Object base = GtRead.call(hatch, "getBaseMetaTileEntity");
            Map<String, Object> info =
                    map(
                            "class",
                            hatch.getClass().getName(),
                            "name",
                            GtData.getter(hatch, "getMetaName"));
            if (base instanceof TileEntity) {
                TileEntity tile = (TileEntity) base;
                info.putAll(
                        map(
                                "x",
                                tile.xCoord,
                                "y",
                                tile.yCoord,
                                "z",
                                tile.zCoord,
                                "valid",
                                !tile.isInvalid(),
                                "energy",
                                energy(base)));
            }
            result.add(info);
        }
        return map("entries", result, "truncated", iterator.hasNext());
    }

    private Object page(
            List<Object> source,
            Map<String, Object> args,
            Function<Object, Object> describe,
            Function<Object, String> name) {
        int index = Math.min(integer(args, "offset", 0, 0, Integer.MAX_VALUE), source.size()),
                limit = integer(args, "limit", 32, 1, 64),
                scanned = 0;
        String query = ((String) args.getOrDefault("query", "")).toLowerCase(Locale.ROOT);
        List<Object> values = new ArrayList<Object>();
        List<Object> errors = new ArrayList<Object>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4);
        while (index < source.size()
                && scanned < 512
                && values.size() < limit
                && (scanned == 0 || System.nanoTime() < deadline)) {
            int current = index++;
            scanned++;
            Object value = source.get(current);
            try {
                if (query.isEmpty() || name.apply(value).toLowerCase(Locale.ROOT).contains(query))
                    values.add(describe.apply(value));
            } catch (RuntimeException | LinkageError e) {
                if (errors.size() < 8)
                    errors.add(map("index", current, "error", GtRead.clip(e.toString(), 512)));
            }
        }
        return map(
                "entries",
                values,
                "total",
                source.size(),
                "scanned",
                scanned,
                "errors",
                errors,
                "nextOffset",
                index < source.size() ? index : null);
    }

    private GtCatalogs.Entry catalog(Map<String, Object> args, int dimension) {
        return catalogs.require(string(args, "catalogId"), dimension, string(args, "session"));
    }

    private Object searchRecipes(Map<String, Object> args, int dimension) {
        String id;
        GtCatalogs.Entry catalog;
        if (args.containsKey("catalogId")) {
            id = string(args, "catalogId");
            catalog = catalog(args, dimension);
            if (!catalog.map.equals(args.get("map")))
                throw new IllegalArgumentException("Recipe catalog belongs to another map");
        } else {
            if (integer(args, "offset", 0, 0, Integer.MAX_VALUE) != 0)
                throw new IllegalArgumentException(
                        "Use catalogId from first page for recipe offsets");
            Object map = registry.maps().get(string(args, "map"));
            if (map == null) throw new IllegalArgumentException("Unknown recipe map");
            Collection<?> recipes = (Collection<?>) GtRead.call(map, "getAllRecipes");
            id = catalogs.create(string(args, "map"), dimension, string(args, "session"), recipes);
            catalog = catalogs.require(id, dimension, string(args, "session"));
        }
        int
                index =
                        Math.min(
                                integer(args, "offset", 0, 0, Integer.MAX_VALUE),
                                catalog.recipes.size()),
                limit = integer(args, "limit", 32, 1, 64),
                scanned = 0;
        List<Object> found = new ArrayList<Object>();
        List<Object> errors = new ArrayList<Object>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4);
        while (index < catalog.recipes.size()
                && scanned < 512
                && found.size() < limit
                && (scanned == 0 || System.nanoTime() < deadline)) {
            int current = index++;
            scanned++;
            Object recipe = catalog.recipes.get(current);
            try {
                if (matches(recipe, args))
                    found.add(
                            map(
                                    "recipeIndex",
                                    current,
                                    "mEUt",
                                    GtData.field(recipe, "mEUt"),
                                    "mDuration",
                                    GtData.field(recipe, "mDuration"),
                                    "mHidden",
                                    GtData.field(recipe, "mHidden"),
                                    "mFakeRecipe",
                                    GtData.field(recipe, "mFakeRecipe")));
            } catch (RuntimeException | LinkageError e) {
                if (errors.size() < 8)
                    errors.add(
                            map("recipeIndex", current, "error", GtRead.clip(e.toString(), 512)));
            }
        }
        return map(
                "catalogId",
                id,
                "map",
                catalog.map,
                "recipes",
                found,
                "total",
                catalog.recipes.size(),
                "scanned",
                scanned,
                "nextOffset",
                index < catalog.recipes.size() ? index : null,
                "errors",
                errors,
                "expiresInSeconds",
                60);
    }

    static boolean matches(Object recipe, Map<String, Object> args) {
        return matches(
                recipe,
                args,
                stack -> (String) Item.itemRegistry.getNameForObject(stack.getItem()));
    }

    static boolean matches(
            Object recipe, Map<String, Object> args, Function<ItemStack, String> itemName) {
        String direction = (String) args.getOrDefault("direction", "either");
        boolean item = !args.containsKey("item"), fluid = !args.containsKey("fluid");
        for (String side :
                direction.equals("either")
                        ? Arrays.asList("Inputs", "Outputs")
                        : Collections.singletonList(
                                direction.equals("input") ? "Inputs" : "Outputs")) {
            if (!item) {
                item = arrayMatches(GtRead.field(recipe, "m" + side), args, false, itemName);
                if (!item && side.equals("Inputs")) {
                    try {
                        recipe.getClass().getField("mOreDictAlt");
                        item =
                                arrayMatches(
                                        GtRead.field(recipe, "mOreDictAlt"), args, false, itemName);
                    } catch (NoSuchFieldException ignored) {
                        /* Base recipes have no item alternatives. */
                    }
                }
            }
            if (!fluid) {
                fluid = arrayMatches(GtRead.field(recipe, "mFluid" + side), args, true, itemName);
                if (!fluid && side.equals("Inputs"))
                    fluid =
                            arrayMatches(
                                    GtRead.field(recipe, "mAltFluidInputs"), args, true, itemName);
            }
        }
        return item && fluid;
    }

    private static boolean arrayMatches(
            Object array,
            Map<String, Object> args,
            boolean fluid,
            Function<ItemStack, String> itemName) {
        if (array == null) return false;
        for (int i = 0; i < Array.getLength(array); i++) {
            Object value = Array.get(array, i);
            if (value != null
                    && value.getClass().isArray()
                    && arrayMatches(value, args, fluid, itemName)) return true;
            if (fluid
                    && value instanceof FluidStack
                    && ((FluidStack) value).getFluid() != null
                    && ((FluidStack) value).getFluid().getName().equals(args.get("fluid")))
                return true;
            if (!fluid && value instanceof ItemStack) {
                ItemStack stack = (ItemStack) value;
                if (Objects.equals(itemName.apply(stack), args.get("item"))
                        && (!args.containsKey("meta")
                                || stack.getItemDamage() == 32767
                                || stack.getItemDamage() == integer(args, "meta", 0, 0, 32767)))
                    return true;
            }
        }
        return false;
    }
}
