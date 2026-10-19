package dev.minecraftagenttools.forge.nei;

import static dev.minecraftagenttools.protocol.data.Data.*;

import codechicken.nei.ItemList;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.*;
import dev.minecraftagenttools.nei.contract.NeiScope;
import dev.minecraftagenttools.nei.contract.NeiTools;
import dev.minecraftagenttools.nei.contract.ScopedCache;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** All methods run on the client tick thread. No render, tooltip, overlay or crafting calls. */
final class NeiReader {
    private final NeiScope scope = new NeiScope();
    private final ScopedCache<ItemStack> items =
            new ScopedCache<ItemStack>(2048, TimeUnit.MINUTES.toNanos(5), System::nanoTime);
    private final ScopedCache<IRecipeHandler> queries =
            new ScopedCache<IRecipeHandler>(16, TimeUnit.SECONDS.toNanos(60), System::nanoTime);
    private List<ItemStack> catalog = Collections.emptyList();
    private boolean ready;
    private final Supplier<ClientState> client;
    private final Supplier<Map<String, Handler>> registry;

    NeiReader() {
        this(NeiReader::clientState, NeiReader::registeredHandlers);
    }

    NeiReader(Supplier<ClientState> client, Supplier<Map<String, Handler>> registry) {
        this.client = client;
        this.registry = registry;
    }

    private static ClientState clientState() {
        Minecraft mc = Minecraft.getMinecraft();
        Object world = mc.thePlayer == null ? null : mc.theWorld;
        int dimension = world == null ? 0 : mc.theWorld.provider.dimensionId;
        return new ClientState(
                world, dimension, ItemList.items, world != null && ItemList.loadFinished);
    }

    void tick() {
        ClientState state = client.get();
        catalog = state.catalog;
        ready = state.ready;
        if (scope.update(state.world, state.dimension, catalog, ready)) {
            items.clear();
            queries.clear();
        }
    }

    Map<String, Object> call(String tool, Map<String, Object> args) {
        // Recheck identity immediately before execution, including jobs queued before a transition.
        tick();
        NeiTools.validate(tool, args);
        if (tool.equals("nei_status"))
            return map(
                    "session",
                    scope.session(),
                    "dimension",
                    scope.session() == null ? null : scope.dimension(),
                    "ready",
                    ready,
                    "catalogSize",
                    ready ? catalog.size() : 0,
                    "apiBaseline",
                    "NEI GTNH 2.6.44 (Java 8)",
                    "capabilities",
                    Arrays.asList("items", "recipes", "usages", "stackAlternatives"),
                    "unavailableGenericFields",
                    Arrays.asList(
                            "energy", "duration", "chance", "machineConditions", "typedFluids"));
        scope.require(
                string(args, "session"),
                integer(args, "dimension", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
        Object result;
        switch (tool) {
            case "nei_list_handlers":
                result = listHandlers(args);
                break;
            case "nei_search_items":
                result = search(args);
                break;
            case "nei_find_recipes":
                result = find(args);
                break;
            case "nei_get_recipe":
                result = recipe(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown NEI tool");
        }
        return map(
                "session",
                scope.session(),
                "dimension",
                scope.dimension(),
                "observedAtEpochMs",
                System.currentTimeMillis(),
                "result",
                result);
    }

    private static Map<String, Handler> registeredHandlers() {
        Map<String, Handler> all = new LinkedHashMap<String, Handler>();
        List<IRecipeHandler> registered = new ArrayList<IRecipeHandler>();
        // NEI deliberately separates serial handlers from parallel ones. Both are queried serially
        // here.
        synchronized (GuiCraftingRecipe.craftinghandlers) {
            registered.addAll(GuiCraftingRecipe.craftinghandlers);
        }
        synchronized (GuiCraftingRecipe.serialCraftingHandlers) {
            registered.addAll(GuiCraftingRecipe.serialCraftingHandlers);
        }
        for (IRecipeHandler h : registered) {
            Handler entry = all.computeIfAbsent(h.getHandlerId(), k -> new Handler());
            entry.crafting = (ICraftingHandler) h;
        }
        registered.clear();
        synchronized (GuiUsageRecipe.usagehandlers) {
            registered.addAll(GuiUsageRecipe.usagehandlers);
        }
        synchronized (GuiUsageRecipe.serialUsageHandlers) {
            registered.addAll(GuiUsageRecipe.serialUsageHandlers);
        }
        for (IRecipeHandler h : registered) {
            Handler entry = all.computeIfAbsent(h.getHandlerId(), k -> new Handler());
            entry.usage = (IUsageHandler) h;
        }
        return all;
    }

    private Map<String, Object> listHandlers(Map<String, Object> args) {
        List<Map.Entry<String, Handler>> all =
                new ArrayList<Map.Entry<String, Handler>>(registry.get().entrySet());
        int offset = integer(args, "offset", 0, 0, Integer.MAX_VALUE),
                limit = integer(args, "limit", 32, 1, 64);
        int end = (int) Math.min(all.size(), (long) offset + limit);
        List<Object> result = new ArrayList<Object>();
        for (int i = offset; i < end; i++) {
            Map.Entry<String, Handler> entry = all.get(i);
            Handler h = entry.getValue();
            IRecipeHandler base = h.crafting == null ? h.usage : h.crafting;
            result.add(
                    map(
                            "handler",
                            entry.getKey(),
                            "name",
                            clip(base.getRecipeName(), 512),
                            "class",
                            base.getClass().getName(),
                            "recipes",
                            h.crafting != null,
                            "usages",
                            h.usage != null));
        }
        return map(
                "handlers",
                result,
                "total",
                all.size(),
                "nextOffset",
                end < all.size() ? end : null);
    }

    private Map<String, Object> search(Map<String, Object> args) {
        String query = string(args, "query").toLowerCase(Locale.ROOT);
        int offset = integer(args, "offset", 0, 0, Integer.MAX_VALUE),
                limit = integer(args, "limit", 32, 1, 64);
        List<Object> result = new ArrayList<Object>();
        List<Object> errors = new ArrayList<Object>();
        int index = Math.min(offset, catalog.size()), scanned = 0;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4);
        while (index < catalog.size()
                && scanned < 512
                && result.size() < limit
                && (scanned == 0 || System.nanoTime() < deadline)) {
            int current = index++;
            scanned++;
            try {
                ItemStack stack = catalog.get(current);
                if (stack == null || stack.getItem() == null) continue;
                String name = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));
                String display = stack.getDisplayName();
                if (name.toLowerCase(Locale.ROOT).contains(query)
                        || display.toLowerCase(Locale.ROOT).contains(query))
                    result.add(stack(stack));
            } catch (RuntimeException | LinkageError e) {
                if (errors.size() < 8)
                    errors.add(map("catalogIndex", current, "error", clip(e.toString(), 512)));
            }
        }
        return map(
                "items",
                result,
                "scanned",
                scanned,
                "catalogSize",
                catalog.size(),
                "errors",
                errors,
                "nextOffset",
                index < catalog.size() ? index : null);
    }

    private Map<String, Object> find(Map<String, Object> args) {
        Handler handler = registry.get().get(string(args, "handler"));
        if (handler == null)
            throw new IllegalArgumentException("Unknown handler; call nei_list_handlers");
        ItemStack input = items.get(string(args, "itemId")).copy();
        IRecipeHandler result;
        if (string(args, "mode").equals("recipes")) {
            if (handler.crafting == null)
                throw new IllegalArgumentException("Handler does not support recipes");
            result = handler.crafting.getRecipeHandler("item", input);
        } else {
            if (handler.usage == null)
                throw new IllegalArgumentException("Handler does not support usages");
            result = handler.usage.getUsageHandler("item", input);
        }
        int count = result == null ? 0 : result.numRecipes();
        if (count < 0) throw new IllegalStateException("Handler returned negative recipe count");
        return map(
                "queryId",
                count == 0 ? null : queries.put(result),
                "recipeCount",
                count,
                "handler",
                args.get("handler"),
                "mode",
                args.get("mode"),
                "expiresInSeconds",
                60);
    }

    private Map<String, Object> recipe(Map<String, Object> args) {
        IRecipeHandler handler = queries.get(string(args, "queryId"));
        int index = integer(args, "recipeIndex", 0, 0, Integer.MAX_VALUE);
        if (index >= handler.numRecipes())
            throw new IllegalArgumentException("Recipe index out of bounds");
        return map(
                "recipeIndex",
                index,
                "ingredients",
                slots(handler.getIngredientStacks(index)),
                "result",
                positioned(handler.getResultStack(index)),
                "otherStacks",
                slots(handler.getOtherStacks(index)),
                "otherStacksRole",
                "unspecified",
                "machinePropertiesAvailable",
                false);
    }

    private Map<String, Object> slots(List<PositionedStack> stacks) {
        List<Object> result = new ArrayList<Object>();
        int total = stacks == null ? 0 : stacks.size();
        for (int i = 0; i < Math.min(64, total); i++) result.add(positioned(stacks.get(i)));
        return map("slots", result, "total", total, "truncated", total > 64);
    }

    private Object positioned(PositionedStack positioned) {
        if (positioned == null) return null;
        ItemStack[] alternatives = positioned.items;
        if (alternatives == null || alternatives.length == 0)
            alternatives =
                    positioned.item == null ? new ItemStack[0] : new ItemStack[] {positioned.item};
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < Math.min(32, alternatives.length); i++)
            values.add(stack(alternatives[i]));
        return map(
                "x",
                positioned.relx,
                "y",
                positioned.rely,
                "alternatives",
                values,
                "totalAlternatives",
                alternatives.length,
                "truncated",
                alternatives.length > 32);
    }

    private Object stack(ItemStack original) {
        if (original == null || original.getItem() == null) return null;
        ItemStack copy = original.copy();
        String nbt = copy.hasTagCompound() ? copy.getTagCompound().toString() : null;
        return map(
                "itemId",
                items.put(copy),
                "registryName",
                Item.itemRegistry.getNameForObject(copy.getItem()),
                "meta",
                copy.getItemDamage(),
                "count",
                copy.stackSize,
                "displayName",
                clip(copy.getDisplayName(), 512),
                "nbt",
                clip(nbt, 8192),
                "nbtTruncated",
                nbt != null && nbt.length() > 8192);
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    static final class Handler {
        ICraftingHandler crafting;
        IUsageHandler usage;
    }

    static final class ClientState {
        final Object world;
        final int dimension;
        final List<ItemStack> catalog;
        final boolean ready;

        ClientState(Object world, int dimension, List<ItemStack> catalog, boolean ready) {
            this.world = world;
            this.dimension = dimension;
            this.catalog = catalog;
            this.ready = ready;
        }
    }
}
