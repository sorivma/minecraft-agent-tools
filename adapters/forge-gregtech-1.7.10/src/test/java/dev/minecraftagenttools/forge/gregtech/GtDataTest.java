package dev.minecraftagenttools.forge.gregtech;

import static dev.minecraftagenttools.protocol.data.Data.map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.minecraft.item.*;
import net.minecraftforge.fluids.*;
import org.junit.Test;

/** Public API fixtures only. No game, GT static registration or MCP process is started. */
public class GtDataTest {
    @Test
    public void recipePreservesBaseEnergyTypedFluidsAndIndependentChanceGetters() {
        Recipe recipe = new Recipe();
        Map<String, Object> data = GtData.recipe(recipe);
        Map<?, ?> energy = (Map<?, ?>) data.get("energy");
        assertEquals("2147483647", energy.get("baseEUPerTick"));
        assertEquals("4611686014132420609", energy.get("baseEUtTimesTicks"));
        Map<?, ?> chances = (Map<?, ?>) data.get("chances");
        assertEquals(Arrays.asList(0), ((Map<?, ?>) chances.get("itemInputs")).get("entries"));
        assertEquals(Arrays.asList(2500), ((Map<?, ?>) chances.get("itemOutputs")).get("entries"));
        assertEquals(Arrays.asList(8000), ((Map<?, ?>) chances.get("fluidOutputs")).get("entries"));
        Map<?, ?> fluids = (Map<?, ?>) data.get("mFluidOutputs");
        Map<?, ?> fluid = (Map<?, ?>) ((List<?>) fluids.get("entries")).get(0);
        assertEquals("test_gt_fluid", fluid.get("fluid"));
        assertEquals("mB", fluid.get("unit"));
        assertEquals(144, fluid.get("amount"));
        assertEquals(12345, data.get("specialValue"));
    }

    @Test
    public void missingEnergyIsUnavailableAndLargeLongsRemainExact() {
        Map<?, ?> data = GtData.recipe(new Object());
        assertEquals(false, ((Map<?, ?>) data.get("energy")).get("available"));
        Map<?, ?> energy = (Map<?, ?>) GtReader.energy(new Energy());
        assertEquals("9007199254740993", energy.get("getStoredEU"));
        assertEquals(false, ((Map<?, ?>) energy.get("getStoredSteam")).get("available"));
    }

    @Test
    public void searchIncludesWildcardAndAlternativeInputsWithoutMatchingWrongDirection() {
        Recipe recipe = new Recipe();
        Item item = new Item();
        recipe.mOreDictAlt = new ItemStack[][] {{new ItemStack(item, 1, 32767)}};
        assertTrue(
                GtReader.matches(
                        recipe,
                        map("item", "test:gt_input", "meta", 7, "direction", "input"),
                        stack -> "test:gt_input"));
        assertFalse(
                GtReader.matches(
                        recipe,
                        map("item", "test:gt_input", "direction", "output"),
                        stack -> "test:gt_input"));
        recipe.mAltFluidInputs = new FluidStack[][] {{recipe.mFluidOutputs[0]}};
        assertTrue(GtReader.matches(recipe, map("fluid", "test_gt_fluid", "direction", "input")));
        assertFalse(GtReader.matches(recipe, map("fluid", "another_fluid", "direction", "either")));
    }

    public static class Recipe {
        public int mEUt = Integer.MAX_VALUE, mDuration = Integer.MAX_VALUE, mSpecialValue = 12345;
        public ItemStack[] mInputs = new ItemStack[] {null}, mOutputs = new ItemStack[] {null};
        public ItemStack[][] mOreDictAlt;
        public FluidStack[] mFluidInputs = new FluidStack[0], mFluidOutputs;
        public FluidStack[][] mAltFluidInputs;

        public Recipe() {
            Fluid fluid = new Fluid("test_gt_fluid");
            FluidStack stack = mock(FluidStack.class);
            when(stack.getFluid()).thenReturn(fluid);
            stack.amount = 144;
            mFluidOutputs = new FluidStack[] {stack};
        }

        public int getInputChance(int i) {
            return 0;
        }

        public int getOutputChance(int i) {
            return 2500;
        }

        public int getFluidInputChance(int i) {
            return 10000;
        }

        public int getFluidOutputChance(int i) {
            return 8000;
        }

        public String[] getNeiDesc() {
            return new String[] {"A special recipe"};
        }
    }

    public static class Energy {
        public long getStoredEU() {
            return 9007199254740993L;
        }
    }
}
