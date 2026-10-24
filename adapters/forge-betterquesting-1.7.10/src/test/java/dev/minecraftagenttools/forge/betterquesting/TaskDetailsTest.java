package dev.minecraftagenttools.forge.betterquesting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.tasks.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import org.junit.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class TaskDetailsTest {
    private final UUID user = UUID.randomUUID();
    private MockedStatic<QuestTranslation> translations;

    @Before
    public void translations() {
        translations = mockStatic(QuestTranslation.class);
        translations
                .when(() -> QuestTranslation.translate(anyString()))
                .thenAnswer(a -> a.getArgument(0));
    }

    @After
    public void closeTranslations() {
        translations.close();
    }

    @BeforeClass
    public static void initializeItemApiWithoutMinecraft() throws Exception {
        try (MockedConstruction<betterquesting.api2.utils.OreIngredient> ignored =
                mockConstruction(betterquesting.api2.utils.OreIngredient.class)) {
            Class.forName("betterquesting.api.utils.BigItemStack");
        }
    }

    private static BigItemStack item(String name, int count) {
        BigItemStack item = mock(BigItemStack.class);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getDisplayName()).thenReturn(name);
        when(stack.hasTagCompound()).thenReturn(true);
        when(item.getBaseStack()).thenReturn(stack);
        when(item.getOreDict()).thenReturn("ingotIron");
        item.stackSize = count;
        return item;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> conditions(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("conditions");
    }

    @Test
    public void retrievalShowsSynchronizedCountsAnyLogicConsumptionAndMatchingWithoutMutation() {
        TaskRetrieval task = spy(new TaskRetrieval());
        for (int i = 0; i < 7; i++) task.requiredItems.add(item("Item " + i, 64));
        int[] progress = {12, 64, 3, 4, 5, 6, 7};
        doReturn(progress).when(task).getUsersProgress(user);
        task.consume = true;
        task.autoConsume = true;
        task.requireOnlyOneItem = true;
        task.ignoreNBT = false;
        task.partialMatch = false;
        Map<String, Object> first = TaskDetails.page(task, user, 0, 4);
        assertEquals("Submit items", first.get("action"));
        assertEquals("ANY", first.get("requirementLogic"));
        assertEquals(7, first.get("totalConditions"));
        assertEquals(4, first.get("nextConditionOffset"));
        assertEquals(4, conditions(first).size());
        assertEquals(12, conditions(first).get(0).get("current"));
        assertEquals(64L, conditions(first).get(0).get("required"));
        assertEquals(52L, conditions(first).get(0).get("remaining"));
        assertEquals(true, conditions(first).get(1).get("satisfied"));
        assertEquals("Any ore: ingotIron", conditions(first).get(0).get("match"));
        assertTrue(((List<?>) first.get("notes")).contains("Exact NBT required"));
        Map<String, Object> last = TaskDetails.page(task, user, 4, 4);
        assertEquals(3, conditions(last).size());
        assertEquals("Item 4", conditions(last).get(0).get("name"));
        assertNull(last.get("nextConditionOffset"));
        assertArrayEquals(new int[] {12, 64, 3, 4, 5, 6, 7}, progress);
        verify(task, never()).detect(any(), any());
        verify(task, never()).setComplete(any());
        verify(task, never()).writeToNBT(any());
        verify(task, never()).writeProgressToNBT(any(), anyList());
    }

    @Test
    public void fluidProgressUsesMilliBucketsAndCraftingReportsOnlyEnabledMethods() {
        TaskFluid fluid = spy(new TaskFluid());
        FluidStack water = mock(FluidStack.class);
        water.amount = 16000;
        when(water.getLocalizedName()).thenReturn("Water");
        fluid.requiredFluids.add(water);
        doReturn(new int[] {4000}).when(fluid).getUsersProgress(user);
        Map<String, Object> result = TaskDetails.page(fluid, user, 0, 4);
        assertEquals("Submit fluids", result.get("action"));
        assertEquals("mB", conditions(result).get(0).get("unit"));
        assertEquals(12000L, conditions(result).get(0).get("remaining"));
        TaskCrafting crafting = spy(new TaskCrafting());
        crafting.requiredItems.add(item("Plate", 8));
        crafting.allowCraft = false;
        crafting.allowSmelt = true;
        crafting.allowAnvil = false;
        doReturn(new int[] {2}).when(crafting).getUsersProgress(user);
        Map<String, Object> crafted = TaskDetails.page(crafting, user, 0, 4);
        assertEquals("Counts: smelting", ((List<?>) crafted.get("notes")).get(0));
        assertEquals(6L, conditions(crafted).get(0).get("remaining"));
    }

    @Test
    public void xpUsesQuestBooksRawXpThresholdAndHiddenLocationsStayHidden() {
        TaskXP xp = spy(new TaskXP());
        xp.levels = true;
        xp.amount = 30;
        doReturn(100L).when(xp).getUsersProgress(user);
        Map<String, Object> data = TaskDetails.page(xp, user, 0, 4);
        assertEquals(bq_standard.XPHelper.getLevelXP(30), conditions(data).get(0).get("required"));
        assertEquals(100L, conditions(data).get(0).get("current"));
        TaskLocation location = new TaskLocation();
        location.hideInfo = true;
        location.name = "Secret location";
        location.x = 1234567;
        location.dim = 987654;
        Map<String, Object> hidden = TaskDetails.page(location, user, 0, 4);
        assertFalse(hidden.toString().contains("1234567"));
        assertFalse(hidden.toString().contains("987654"));
        assertTrue(hidden.toString().contains("hidden"));
    }

    @Test
    public void customConditionsDoNotInventProgressAndCheckboxStillRequiresTheBook() {
        ITask custom = mock(ITask.class);
        Map<String, Object> data = TaskDetails.page(custom, user, 0, 4);
        assertEquals(false, data.get("supported"));
        assertFalse(conditions(data).get(0).containsKey("current"));
        verify(custom, never()).writeToNBT(any(NBTTagCompound.class));
        verify(custom, never()).detect(any(), any());
        TaskCheckbox checkbox = new TaskCheckbox();
        Map<String, Object> manual = TaskDetails.page(checkbox, user, 0, 4);
        assertEquals("Confirm in quest book", manual.get("action"));
        assertFalse(checkbox.isComplete(user));
        assertTrue(conditions(TaskDetails.page(checkbox, user, 1, 4)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TaskDetails.page(custom, user, -1, 4));
    }
}
