package dev.minecraftagenttools.forge.nei;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.*;
import codechicken.nei.recipe.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.*;
import org.junit.Test;

/**
 * Tests the real reader with an isolated item and third-party API handler, without starting
 * Minecraft.
 */
public class NeiReaderTest {
    @Test
    @SuppressWarnings("unchecked")
    public void discoversArbitraryPluginQueriesBothModesAndRejectsPreviousWorld() {
        Item item =
                new Item() {
                    @Override
                    public String getItemStackDisplayName(ItemStack stack) {
                        return "Plugin ingredient";
                    }
                };
        List<ItemStack> catalog = Collections.singletonList(new ItemStack(item, 3, 7));
        AtomicReference<NeiReader.ClientState> client =
                new AtomicReference<NeiReader.ClientState>(
                        new NeiReader.ClientState(new Object(), 0, catalog, true));
        FakeHandler plugin = new FakeHandler();
        NeiReader.Handler registration = new NeiReader.Handler();
        registration.crafting = plugin;
        registration.usage = plugin;
        NeiReader reader =
                new NeiReader(
                        client::get,
                        () -> Collections.singletonMap("third.party.machine", registration));
        String session = (String) reader.call("nei_status", map()).get("session");
        Map<String, Object> scope = map("session", session, "dimension", 0);
        Map<String, Object> search = new LinkedHashMap<String, Object>(scope);
        search.put("query", "ingredient");
        Map<?, ?> page = (Map<?, ?>) reader.call("nei_search_items", search).get("result");
        Map<?, ?> found = (Map<?, ?>) ((List<?>) page.get("items")).get(0);
        assertEquals(7, found.get("meta"));
        assertEquals(3, found.get("count"));
        Map<String, Object> find = new LinkedHashMap<String, Object>(scope);
        find.putAll(
                map(
                        "handler",
                        "third.party.machine",
                        "itemId",
                        found.get("itemId"),
                        "mode",
                        "recipes"));
        Map<?, ?> result = (Map<?, ?>) reader.call("nei_find_recipes", find).get("result");
        assertEquals(1, result.get("recipeCount"));
        assertEquals("recipes", plugin.lastMode);
        assertEquals(7, plugin.input.getItemDamage());
        assertNotSame(catalog.get(0), plugin.input);
        Map<String, Object> detail = new LinkedHashMap<String, Object>(scope);
        detail.putAll(map("queryId", result.get("queryId"), "recipeIndex", 0));
        Map<?, ?> recipe = (Map<?, ?>) reader.call("nei_get_recipe", detail).get("result");
        assertEquals("unspecified", recipe.get("otherStacksRole"));
        assertEquals(false, recipe.get("machinePropertiesAvailable"));
        Map<?, ?> ingredients = (Map<?, ?>) recipe.get("ingredients");
        Map<?, ?> slot = (Map<?, ?>) ((List<?>) ingredients.get("slots")).get(0);
        assertEquals(2, slot.get("totalAlternatives"));
        List<?> variants = (List<?>) slot.get("alternatives");
        assertEquals(7, ((Map<?, ?>) variants.get(0)).get("meta"));
        assertEquals(9, ((Map<?, ?>) variants.get(1)).get("meta"));
        find.put("mode", "usages");
        reader.call("nei_find_recipes", find);
        assertEquals("usages", plugin.lastMode);
        detail.put("recipeIndex", 1);
        try {
            reader.call("nei_get_recipe", detail);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        client.set(new NeiReader.ClientState(new Object(), 0, catalog, true));
        try {
            reader.call("nei_find_recipes", find);
            fail("Old-world query executed");
        } catch (IllegalArgumentException expected) {
        }
        String next = (String) reader.call("nei_status", map()).get("session");
        find.put("session", next);
        try {
            reader.call("nei_find_recipes", find);
            fail("Old-world item handle survived");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static final class FakeHandler implements ICraftingHandler, IUsageHandler {
        String lastMode;
        ItemStack input;

        public String getRecipeName() {
            return "Third party machine";
        }

        public ICraftingHandler getRecipeHandler(String id, Object... values) {
            assertEquals("item", id);
            lastMode = "recipes";
            input = (ItemStack) values[0];
            return this;
        }

        public IUsageHandler getUsageHandler(String id, Object... values) {
            assertEquals("item", id);
            lastMode = "usages";
            input = (ItemStack) values[0];
            return this;
        }

        public int numRecipes() {
            return 1;
        }

        public List<PositionedStack> getIngredientStacks(int index) {
            ItemStack second = input.copy();
            second.setItemDamage(9);
            return Collections.singletonList(
                    new PositionedStack(new ItemStack[] {input, second}, 4, 8, false));
        }

        public PositionedStack getResultStack(int index) {
            return null;
        }

        public List<PositionedStack> getOtherStacks(int index) {
            return Collections.emptyList();
        }

        public void drawBackground(int index) {
            throw new AssertionError("Rendering must not run");
        }

        public void drawForeground(int index) {
            throw new AssertionError("Rendering must not run");
        }

        public void onUpdate() {
            throw new AssertionError("GUI updates must not run");
        }

        public boolean hasOverlay(GuiContainer gui, Container container, int recipe) {
            throw new AssertionError();
        }

        public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int recipe) {
            throw new AssertionError();
        }

        public IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe) {
            throw new AssertionError();
        }

        public int recipiesPerPage() {
            return 2;
        }

        public List<String> handleTooltip(GuiRecipe<?> gui, List<String> tip, int recipe) {
            throw new AssertionError();
        }

        public List<String> handleItemTooltip(
                GuiRecipe<?> gui, ItemStack stack, List<String> tip, int recipe) {
            throw new AssertionError();
        }

        public boolean keyTyped(GuiRecipe<?> gui, char key, int code, int recipe) {
            throw new AssertionError();
        }

        public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipe) {
            throw new AssertionError();
        }
    }
}
