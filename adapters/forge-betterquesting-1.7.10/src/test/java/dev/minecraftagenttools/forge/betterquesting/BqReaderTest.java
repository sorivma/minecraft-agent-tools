package dev.minecraftagenttools.forge.betterquesting;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import betterquesting.api.enums.*;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.*;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.storage.*;
import betterquesting.api2.utils.QuestTranslation;
import cpw.mods.fml.common.Mod;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import org.junit.*;
import org.mockito.MockedStatic;

public class BqReaderTest {
    @BeforeClass
    public static void initializePropertiesWithoutGameRegistry() throws Exception {
        // BQ's property defaults initialize OreDictionary; no game or Forge launcher runs here.
        try (org.mockito.MockedConstruction<betterquesting.api2.utils.OreIngredient> ores =
                mockConstruction(betterquesting.api2.utils.OreIngredient.class)) {
            Class.forName("betterquesting.api.properties.NativeProps");
        }
    }

    private final UUID user = UUID.randomUUID();
    private final EntityPlayer player = mock(EntityPlayer.class);
    private final Map<UUID, IQuest> quests = new LinkedHashMap<UUID, IQuest>();
    private final List<Map.Entry<UUID, IQuestLine>> lines =
            new ArrayList<Map.Entry<UUID, IQuestLine>>();
    private final AtomicInteger revision = new AtomicInteger();
    private final AtomicReference<BqReader.ClientState> state =
            new AtomicReference<BqReader.ClientState>();
    private BqReader reader;
    private MockedStatic<QuestTranslation> translations;

    @Before
    public void setup() {
        translations = mockStatic(QuestTranslation.class);
        translations
                .when(() -> QuestTranslation.translateQuestName(any(UUID.class), any(IQuest.class)))
                .thenAnswer(a -> ((IQuest) a.getArgument(1)).getProperty(NativeProps.NAME));
        translations
                .when(
                        () ->
                                QuestTranslation.translateQuestDescription(
                                        any(UUID.class), any(IQuest.class)))
                .thenAnswer(a -> ((IQuest) a.getArgument(1)).getProperty(NativeProps.DESC));
        state.set(new BqReader.ClientState(new Object(), 0, player, user, null));
        reader = new BqReader(state::get, () -> quests, () -> lines, revision::get);
    }

    @After
    public void close() {
        translations.close();
    }

    @SuppressWarnings("unchecked")
    private IQuest add(int id, String name, EnumQuestState status, UUID... requirements) {
        IQuest quest = mock(IQuest.class);
        IDatabaseNBT<ITask, NBTTagList, NBTTagList> tasks = mock(IDatabaseNBT.class);
        IDatabaseNBT<IReward, NBTTagList, NBTTagList> rewards = mock(IDatabaseNBT.class);
        when(tasks.getEntries()).thenReturn(Collections.emptyList());
        when(rewards.getEntries()).thenReturn(Collections.emptyList());
        when(quest.getTasks()).thenReturn(tasks);
        when(quest.getRewards()).thenReturn(rewards);
        when(quest.getProperty(NativeProps.NAME)).thenReturn(name);
        when(quest.getProperty(NativeProps.DESC)).thenReturn("A long guide to steam");
        when(quest.getProperty(NativeProps.LOGIC_QUEST)).thenReturn(EnumLogic.OR);
        when(quest.getProperty(NativeProps.LOGIC_TASK)).thenReturn(EnumLogic.AND);
        when(quest.getProperty(NativeProps.VISIBILITY)).thenReturn(EnumQuestVisibility.NORMAL);
        when(quest.getState(player)).thenReturn(status);
        when(quest.isComplete(user))
                .thenReturn(
                        status == EnumQuestState.COMPLETED || status == EnumQuestState.UNCLAIMED);
        when(quest.getRequirements())
                .thenReturn(new LinkedHashSet<UUID>(Arrays.asList(requirements)));
        when(quest.getRequirementType(any(UUID.class))).thenReturn(IQuest.RequirementType.NORMAL);
        when(quest.writeToNBT(any(NBTTagCompound.class))).thenAnswer(a -> a.getArgument(0));
        when(quest.writeProgressToNBT(any(NBTTagCompound.class), anyList()))
                .thenAnswer(a -> a.getArgument(0));
        quests.put(id(id), quest);
        return quest;
    }

    private static UUID id(int id) {
        return new UUID(0, id);
    }

    private Map<String, Object> scope() {
        Map<String, Object> status = reader.call("bq_status", map());
        return map("session", status.get("session"), "dimension", status.get("dimension"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String tool, Map<String, Object> args) {
        return (Map<String, Object>) reader.call(tool, args).get("result");
    }

    @Test
    public void rejectsOldWorldDimensionPlayerAndRevisionAtExecution() {
        Map<String, Object> old = scope();
        state.set(new BqReader.ClientState(new Object(), 0, player, user, null));
        assertThrows(IllegalArgumentException.class, () -> reader.call("bq_search_quests", old));
        Map<String, Object> dimensionScope = scope();
        BqReader.ClientState current = state.get();
        state.set(new BqReader.ClientState(current.world, -1, player, user, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> reader.call("bq_search_quests", dimensionScope));
        Map<String, Object> playerScope = scope();
        state.set(new BqReader.ClientState(current.world, -1, player, UUID.randomUUID(), null));
        assertThrows(
                IllegalArgumentException.class, () -> reader.call("bq_search_quests", playerScope));
        Map<String, Object> rev = scope();
        rev.put("revision", 0);
        revision.incrementAndGet();
        assertThrows(IllegalArgumentException.class, () -> reader.call("bq_search_quests", rev));
        state.set(new BqReader.ClientState(null, 0, null, null, null));
        assertEquals(false, reader.call("bq_status", map()).get("ready"));
        assertThrows(
                IllegalArgumentException.class, () -> reader.call("bq_search_quests", scope()));
    }

    @Test
    public void searchPaginatesNonMatchesAndFiltersProgress() {
        for (int i = 0; i < 135; i++)
            add(
                    i,
                    i == 134 ? "Steam turbine" : "unrelated",
                    i == 134 ? EnumQuestState.UNCLAIMED : EnumQuestState.LOCKED);
        Map<String, Object> args = scope();
        args.put("query", "TURBINE");
        args.put("state", "unclaimed");
        int seen = 0;
        boolean found = false;
        do {
            Map<String, Object> page = call("bq_search_quests", args);
            List<?> rows = (List<?>) page.get("quests");
            found |= !rows.isEmpty();
            seen += ((Number) page.get("scanned")).intValue();
            if (page.get("nextOffset") == null) break;
            args.put("offset", page.get("nextOffset"));
        } while (seen < 140);
        assertTrue(found);
        assertEquals(135, seen);
    }

    @Test
    public void relationsPreserveOrLogicMissingReferencesAndCycles() {
        add(1, "Root", EnumQuestState.LOCKED, id(2), id(99));
        add(2, "Other", EnumQuestState.UNLOCKED, id(1));
        Map<String, Object> args = scope();
        args.put("questId", id(1).toString());
        args.put("direction", "prerequisites");
        List<?> rows = (List<?>) call("bq_get_relations", args).get("relations");
        assertEquals(2, rows.size());
        assertEquals(true, ((Map<?, ?>) rows.get(1)).get("missing"));
        assertEquals("OR", ((Map<?, ?>) rows.get(0)).get("dependentQuestLogic"));
        args.put("direction", "dependents");
        rows = (List<?>) call("bq_get_relations", args).get("relations");
        assertEquals(1, rows.size());
        assertEquals(id(2).toString(), ((Map<?, ?>) rows.get(0)).get("questId"));
    }

    @Test
    public void taskProgressIsRestrictedToCurrentQuestingUuidAndNeverDetected() {
        IQuest q = add(1, "Quest", EnumQuestState.UNLOCKED);
        ITask task = mock(ITask.class);
        when(q.getTasks().getValue(7)).thenReturn(task);
        when(task.getFactoryID()).thenReturn(new ResourceLocation("test", "custom"));
        when(task.writeToNBT(any(NBTTagCompound.class)))
                .thenAnswer(
                        a -> {
                            NBTTagCompound n = a.getArgument(0);
                            n.setString("command", "/op not-an-instruction");
                            return n;
                        });
        when(task.writeProgressToNBT(
                        any(NBTTagCompound.class), eq(Collections.singletonList(user))))
                .thenAnswer(a -> a.getArgument(0));
        Map<String, Object> args = scope();
        args.put("questId", id(1).toString());
        args.put("taskId", 7);
        args.put("path", "/config/command");
        Map<String, Object> result = call("bq_get_task", args);
        assertEquals("/op not-an-instruction", ((Map<?, ?>) result.get("nbt")).get("data"));
        verify(task)
                .writeProgressToNBT(any(NBTTagCompound.class), eq(Collections.singletonList(user)));
        verify(task, never()).detect(any(), any());
        verify(task, never()).setComplete(any());
        verify(q, never()).detect(any());
        verify(q, never()).claimReward(any(), anyBoolean());
    }

    @Test
    public void questDetailsReadLongDescriptionsAndUseStableComponentIds() {
        IQuest q = add(1, "Quest", EnumQuestState.LOCKED);
        char[] chars = new char[10000];
        Arrays.fill(chars, 'z');
        when(q.getProperty(NativeProps.DESC)).thenReturn(new String(chars));
        Map<String, Object> args = scope();
        args.put("questId", id(1).toString());
        Map<String, Object> result = call("bq_get_quest", args);
        assertEquals(8192, result.get("nextTextOffset"));
        assertEquals("OR", result.get("questLogic"));
        args.put("textOffset", 8192);
        result = call("bq_get_quest", args);
        assertEquals(1808, ((String) result.get("description")).length());
        assertNull(result.get("nextTextOffset"));
    }

    @Test
    public void declaresExactModIdAndBaseline() {
        assertEquals(
                "required-after:betterquesting@[3.8.89,)",
                BetterQuestingMod.class.getAnnotation(Mod.class).dependencies());
    }

    @Test
    public void widgetPagesCacheBrieflyButInvalidationAndScopeChecksStayImmediate() {
        IQuest quest = add(1, "First", EnumQuestState.UNLOCKED);
        AtomicLong clock = new AtomicLong();
        BqClient client =
                new BqClient(
                        reader,
                        id -> {
                            fail("No GUI from snapshot");
                        },
                        clock::get);
        String session = (String) scope().get("session");
        assertEquals("First", client.snapshot(session, 0, id(1), "info", 0).get("name"));
        when(quest.getProperty(NativeProps.NAME)).thenReturn("Changed");
        assertEquals("First", client.snapshot(session, 0, id(1), "info", 0).get("name"));
        clock.set(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(251));
        assertEquals("Changed", client.snapshot(session, 0, id(1), "info", 0).get("name"));
        when(quest.getProperty(NativeProps.NAME)).thenReturn("Synced");
        revision.incrementAndGet();
        assertEquals("Synced", client.snapshot(session, 0, id(1), "info", 0).get("name"));
        quests.clear();
        assertThrows(
                IllegalArgumentException.class,
                () -> client.snapshot(session, 0, id(1), "info", 0));
        add(1, "Other world", EnumQuestState.UNLOCKED);
        state.set(new BqReader.ClientState(new Object(), 0, player, user, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.snapshot(session, 0, id(1), "info", 0));
    }

    @Test
    public void widgetReadsSmallPagesWithoutSerializingQuestOrIssuingActions() {
        IQuest q = add(1, "Widget quest", EnumQuestState.UNLOCKED);
        ITask done = mock(ITask.class), ignored = mock(ITask.class);
        when(done.getFactoryID()).thenReturn(new ResourceLocation("test", "done"));
        when(ignored.getFactoryID()).thenReturn(new ResourceLocation("test", "ignored"));
        when(done.isComplete(user)).thenReturn(true);
        when(ignored.ignored(user)).thenReturn(true);
        when(q.getTasks().getEntries())
                .thenReturn(
                        Arrays.asList(new DBEntry<ITask>(8, ignored), new DBEntry<ITask>(2, done)));
        Map<String, Object> scope = scope();
        String session = (String) scope.get("session");
        Map<String, Object> snapshot = reader.widgetSnapshot(session, 0, id(1), "tasks", 0);
        assertEquals(1, snapshot.get("completedTasks"));
        assertEquals(1, snapshot.get("countedTasks"));
        assertEquals(1, snapshot.get("ignoredTasks"));
        assertEquals(2, ((Map<?, ?>) ((List<?>) snapshot.get("entries")).get(0)).get("taskId"));
        verify(q, never()).writeToNBT(any());
        verify(q, never()).writeProgressToNBT(any(), any());
        verify(q, never()).detect(any());
        verify(q, never()).claimReward(any(), anyBoolean());
    }

    @Test
    public void widgetKeepsNativeIconAndTaskDetailsBoundToTheOriginalScope() {
        IQuest quest = add(1, "Quest icon", EnumQuestState.UNLOCKED);
        betterquesting.api.utils.BigItemStack icon =
                mock(betterquesting.api.utils.BigItemStack.class);
        when(quest.getProperty(NativeProps.ICON)).thenReturn(icon);
        ITask task = mock(ITask.class);
        when(task.getFactoryID()).thenReturn(new ResourceLocation("test", "custom"));
        when(quest.getTasks().getValue(7)).thenReturn(task);
        String session = (String) scope().get("session");
        assertSame(icon, reader.widgetIcon(session, 0, id(1)));
        Map<String, Object> details = reader.widgetTask(session, 0, id(1), 7, 0);
        assertEquals(7, details.get("taskId"));
        assertEquals("Custom condition", details.get("action"));
        assertThrows(
                IllegalArgumentException.class, () -> reader.widgetTask(session, 0, id(1), 8, 0));
        state.set(new BqReader.ClientState(new Object(), 0, player, user, null));
        assertThrows(IllegalArgumentException.class, () -> reader.widgetIcon(session, 0, id(1)));
        assertThrows(
                IllegalArgumentException.class, () -> reader.widgetTask(session, 0, id(1), 7, 0));
        verify(task, never()).detect(any(), any());
        verify(task, never()).writeToNBT(any());
        verify(quest, never()).claimReward(any(), anyBoolean());
    }

    @Test
    public void nativeIconUsesTheBooksExactStackAndOreTextureWithoutSerializingNbt() {
        IQuest quest = add(1, "Native quest icon", EnumQuestState.UNLOCKED);
        betterquesting.api.utils.BigItemStack icon =
                mock(betterquesting.api.utils.BigItemStack.class);
        when(quest.getProperty(NativeProps.ICON)).thenReturn(icon);
        String session = (String) scope().get("session");
        BqClient client = new BqClient(reader, id -> fail("Drawing cannot open book"));
        try (org.mockito.MockedConstruction<
                        betterquesting.api2.client.gui.resources.textures.OreDictTexture>
                textures =
                        mockConstruction(
                                betterquesting.api2.client.gui.resources.textures.OreDictTexture
                                        .class,
                                (texture, context) -> {
                                    assertSame(icon, context.arguments().get(1));
                                    assertEquals(false, context.arguments().get(2));
                                    assertEquals(true, context.arguments().get(3));
                                })) {
            assertTrue(client.drawQuestIcon(session, 0, id(1), 10, 20, 28));
            assertTrue(client.drawQuestIcon(session, 0, id(1), 10, 20, 28));
            assertEquals(1, textures.constructed().size());
            verify(textures.constructed().get(0), times(2)).drawTexture(10, 20, 28, 28, 0, 0);
            state.set(new BqReader.ClientState(new Object(), 0, player, user, null));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> client.drawQuestIcon(session, 0, id(1), 10, 20, 28));
            assertEquals(1, textures.constructed().size());
        }
        verify(quest, never()).writeToNBT(any());
        verify(quest, never()).claimReward(any(), anyBoolean());
    }

    @Test
    public void explicitBookOpenRechecksWorldPlayerDimensionAndMissingQuest() {
        add(1, "Quest", EnumQuestState.UNLOCKED);
        List<UUID> opened = new ArrayList<UUID>();
        BqClient client = new BqClient(reader, opened::add);
        String session = (String) scope().get("session");
        client.snapshot(session, 0, id(1), "info", 0);
        assertTrue(opened.isEmpty());
        client.openQuest(session, 0, id(1));
        assertEquals(Collections.singletonList(id(1)), opened);
        quests.clear();
        assertThrows(IllegalArgumentException.class, () -> client.openQuest(session, 0, id(1)));
        add(1, "Same ID in another world", EnumQuestState.UNLOCKED);
        state.set(new BqReader.ClientState(new Object(), 0, player, user, null));
        assertThrows(IllegalArgumentException.class, () -> client.openQuest(session, 0, id(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> client.snapshot(session, 0, id(1), "info", 0));
        String current = (String) scope().get("session");
        assertThrows(IllegalArgumentException.class, () -> client.openQuest(current, -1, id(1)));
        BqReader.ClientState previousState = state.get();
        state.set(
                new BqReader.ClientState(previousState.world, 0, player, UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> client.openQuest(current, 0, id(1)));
        assertEquals(1, opened.size());
    }

    @Test
    public void chaptersKeepBookOrderAndReturnQuestCoordinates() {
        add(1, "Start", EnumQuestState.UNLOCKED);
        IQuestLine first = mock(IQuestLine.class), second = mock(IQuestLine.class);
        IQuestLineEntry position = mock(IQuestLineEntry.class);
        when(first.keySet()).thenReturn(Collections.singleton(id(1)));
        when(first.get(id(1))).thenReturn(position);
        when(first.size()).thenReturn(1);
        when(position.getPosX()).thenReturn(70);
        when(position.getPosY()).thenReturn(-20);
        lines.add(new AbstractMap.SimpleEntry<UUID, IQuestLine>(id(20), first));
        lines.add(new AbstractMap.SimpleEntry<UUID, IQuestLine>(id(10), second));
        Map<String, Object> args = scope();
        args.put("limit", 1);
        Map<String, Object> page = call("bq_list_lines", args);
        assertEquals(
                id(20).toString(),
                ((Map<?, ?>) ((List<?>) page.get("lines")).get(0)).get("lineId"));
        assertEquals(1, page.get("nextOffset"));
        args.put("offset", 1);
        page = call("bq_list_lines", args);
        assertEquals(
                id(10).toString(),
                ((Map<?, ?>) ((List<?>) page.get("lines")).get(0)).get("lineId"));
        args = scope();
        args.put("lineId", id(20).toString());
        page = call("bq_get_line", args);
        Map<?, ?> row = (Map<?, ?>) ((List<?>) page.get("quests")).get(0);
        assertEquals(70, ((Map<?, ?>) row.get("position")).get("x"));
        assertEquals(-20, ((Map<?, ?>) row.get("position")).get("y"));
        assertEquals(id(1).toString(), row.get("questId"));
    }

    @Test
    public void rewardItemSearchAndDetailsPreserveLargeAmountsWithoutClaiming() {
        IQuest q = add(1, "Quest", EnumQuestState.UNCLAIMED);
        bq_standard.rewards.RewardChoice reward = mock(bq_standard.rewards.RewardChoice.class);
        betterquesting.api.utils.BigItemStack item =
                mock(betterquesting.api.utils.BigItemStack.class);
        net.minecraft.item.ItemStack stack = mock(net.minecraft.item.ItemStack.class);
        item.stackSize = 100000;
        when(item.getBaseStack()).thenReturn(stack);
        when(item.getOreDict()).thenReturn("ingotSteel");
        when(stack.getDisplayName()).thenReturn("Steel ingot");
        when(stack.getItem()).thenReturn(mock(net.minecraft.item.Item.class));
        when(reward.getFactoryID()).thenReturn(new ResourceLocation("test", "choice"));
        when(reward.getItemOutputs()).thenReturn(Collections.singletonList(item));
        when(reward.getSelecton(user)).thenReturn(-1);
        when(reward.writeToNBT(any(NBTTagCompound.class))).thenAnswer(a -> a.getArgument(0));
        when(q.getRewards().getEntries())
                .thenReturn(Collections.singletonList(new DBEntry<IReward>(42, reward)));
        when(q.getRewards().getValue(42)).thenReturn(reward);
        Map<String, Object> args = scope();
        args.put("query", "ingotSteel");
        Map<String, Object> search = call("bq_search_quests", args);
        assertEquals(
                "rewardItem",
                ((Map<?, ?>) ((List<?>) search.get("quests")).get(0)).get("matchedIn"));
        args = scope();
        args.put("questId", id(1).toString());
        args.put("rewardId", 42);
        Map<String, Object> detail = call("bq_get_reward", args);
        Map<?, ?> row =
                (Map<?, ?>) ((List<?>) ((Map<?, ?>) detail.get("items")).get("entries")).get(0);
        assertEquals(100000, row.get("count"));
        assertNull(row.get("registryName"));
        assertNull(detail.get("selectedChoiceIndex"));
        verify(reward, never()).claimReward(any(), any());
        verify(reward, never()).canClaim(any(), any());
        verify(reward, never()).setSelection(any(), anyInt());
    }

    @Test
    public void brokenExtensionDoesNotHideOtherSearchResultsAndUnknownVisibilityIsExplicit() {
        IQuest broken = add(1, "Broken", EnumQuestState.LOCKED);
        add(2, "Working", EnumQuestState.UNLOCKED);
        when(broken.getState(player)).thenThrow(new IllegalStateException("broken addon"));
        Map<String, Object> args = scope();
        int errors = 0, results = 0;
        do {
            Map<String, Object> page = call("bq_search_quests", args);
            errors += ((List<?>) page.get("errors")).size();
            results += ((List<?>) page.get("quests")).size();
            if (page.get("nextOffset") == null) break;
            args.put("offset", page.get("nextOffset"));
        } while (true);
        assertEquals(1, errors);
        assertEquals(1, results);
        args = scope();
        args.put("visibility", "visible");
        Map<String, Object> page = call("bq_search_quests", args);
        assertEquals(false, page.get("visibilityKnown"));
        assertTrue(((List<?>) page.get("quests")).isEmpty());
    }
}
