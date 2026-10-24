package dev.minecraftagenttools.forge.betterquesting;

import static dev.minecraftagenttools.protocol.data.Data.*;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.*;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import bq_standard.rewards.IRewardItemOutput;
import bq_standard.rewards.RewardChoice;
import bq_standard.tasks.ITaskItemInput;
import dev.minecraftagenttools.betterquesting.contract.BqTools;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** All access is confined to the client tick thread. Only detached JSON values leave call(). */
final class BqReader {
    private final Supplier<ClientState> client;
    private final Supplier<Map<UUID, IQuest>> quests;
    private final Supplier<List<Map.Entry<UUID, IQuestLine>>> lines;
    private final IntSupplier revision;
    private ClientState state;
    private String session;

    BqReader(IntSupplier revision) {
        this(
                BqReader::clientState,
                () -> QuestDatabase.INSTANCE,
                () -> QuestLineDatabase.INSTANCE.getOrderedEntries(),
                revision);
    }

    BqReader(
            Supplier<ClientState> client,
            Supplier<Map<UUID, IQuest>> quests,
            Supplier<List<Map.Entry<UUID, IQuestLine>>> lines,
            IntSupplier revision) {
        this.client = client;
        this.quests = quests;
        this.lines = lines;
        this.revision = revision;
    }

    private static ClientState clientState() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (mc.theWorld == null || player == null)
            return new ClientState(null, 0, null, null, null);
        QuestCache cache =
                (QuestCache) player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        return new ClientState(
                mc.theWorld,
                mc.theWorld.provider.dimensionId,
                player,
                QuestingAPI.getQuestingUUID(player),
                cache == null ? null : cache.getVisibleQuests());
    }

    void tick() {
        ClientState next = client.get();
        if (state == null
                || state.world != next.world
                || state.dimension != next.dimension
                || state.player != next.player
                || !Objects.equals(state.playerId, next.playerId))
            session =
                    next.world == null || next.playerId == null
                            ? null
                            : UUID.randomUUID().toString();
        state = next;
    }

    Map<String, Object> call(String tool, Map<String, Object> args) {
        tick(); // Revalidate queued requests at execution, including disconnect/reconnect.
        BqTools.validate(tool, args);
        int rev = revision.getAsInt();
        if (tool.equals("bq_status"))
            return map(
                    "session",
                    session,
                    "dimension",
                    session == null ? null : state.dimension,
                    "revision",
                    rev,
                    "ready",
                    session != null && !quests.get().isEmpty(),
                    "questCount",
                    session == null ? 0 : quests.get().size(),
                    "lineCount",
                    session == null ? 0 : lines.get().size(),
                    "playerId",
                    state.playerId == null ? null : state.playerId.toString(),
                    "apiBaseline",
                    "BetterQuesting GTNH 3.8.89 (UUID API, Java 8)",
                    "progressSource",
                    "client synchronized database",
                    "limitations",
                    Arrays.asList(
                            "Sync completion/freshness is not exposed by BQ; ready means a nonempty local database only.",
                            "Visibility can be unknown until the quest cache arrives. Hidden client-loaded quests are browsable.",
                            "No submissions, detection, claims, edits or GUI actions. Reward claimability is not probed."));
        if (session == null
                || !session.equals(string(args, "session"))
                || state.dimension
                        != integer(args, "dimension", 0, Integer.MIN_VALUE, Integer.MAX_VALUE))
            throw new IllegalArgumentException("Stale BetterQuesting scope; call bq_status");
        if (args.containsKey("revision")
                && rev != integer(args, "revision", 0, 0, Integer.MAX_VALUE))
            throw new IllegalArgumentException(
                    "BetterQuesting database changed; restart pagination using bq_status");
        Object result;
        switch (tool) {
            case "bq_list_lines":
                result = listLines(args);
                break;
            case "bq_get_line":
                result = line(args);
                break;
            case "bq_search_quests":
                result = search(args);
                break;
            case "bq_get_quest":
                result = quest(args);
                break;
            case "bq_get_task":
                result = task(args);
                break;
            case "bq_get_reward":
                result = reward(args);
                break;
            case "bq_get_relations":
                result = relations(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown BetterQuesting tool");
        }
        if (rev != revision.getAsInt())
            throw new IllegalStateException("BetterQuesting updated during read; retry");
        return map(
                "session",
                session,
                "dimension",
                state.dimension,
                "revision",
                rev,
                "playerId",
                state.playerId.toString(),
                "observedAtEpochMs",
                System.currentTimeMillis(),
                "result",
                result);
    }

    private Map<String, Object> listLines(Map<String, Object> args) {
        List<Map.Entry<UUID, IQuestLine>> all = lines.get();
        List<Object> rows = new ArrayList<Object>();
        String query = optional(args, "query", "");
        int i = Math.min(offset(args), all.size()), scanned = 0;
        while (i < all.size() && scanned++ < 128 && rows.size() < limit(args)) {
            Map.Entry<UUID, IQuestLine> e = all.get(i++);
            if (contains(QuestTranslation.translateQuestLineName(e), query)
                    || contains(QuestTranslation.translateQuestLineDescription(e), query))
                rows.add(lineSummary(e.getKey(), e.getValue(), i - 1));
        }
        return page("lines", rows, i, all.size());
    }

    private Map<String, Object> line(Map<String, Object> args) {
        UUID id = UUID.fromString(string(args, "lineId"));
        IQuestLine line = requireLine(id);
        Map<String, Object> result = lineSummary(id, line, lineOrder(id));
        description(result, QuestTranslation.translateQuestLineDescription(id, line), args);
        List<UUID> ids = sorted(line.keySet());
        List<Object> rows = new ArrayList<Object>();
        int end = end(args, ids.size());
        for (int i = Math.min(offset(args), ids.size()); i < end; i++) {
            UUID qid = ids.get(i);
            Map<String, Object> row = summary(qid, quests.get().get(qid));
            IQuestLineEntry layout = line.get(qid);
            row.put(
                    "position",
                    map(
                            "x",
                            layout.getPosX(),
                            "y",
                            layout.getPosY(),
                            "width",
                            layout.getSizeX(),
                            "height",
                            layout.getSizeY()));
            rows.add(row);
        }
        result.putAll(page("quests", rows, end, ids.size()));
        return result;
    }

    private Map<String, Object> search(Map<String, Object> args) {
        Map<UUID, IQuest> db = quests.get();
        List<UUID> ids =
                sorted(
                        args.containsKey("lineId")
                                ? requireLine(UUID.fromString(string(args, "lineId"))).keySet()
                                : db.keySet());
        List<Object> rows = new ArrayList<Object>(), errors = new ArrayList<Object>();
        int i = Math.min(offset(args), ids.size()), scanned = 0;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5);
        while (i < ids.size()
                && scanned < 128
                && rows.size() < limit(args)
                && (scanned == 0 || System.nanoTime() < deadline)) {
            UUID id = ids.get(i++);
            scanned++;
            IQuest q = db.get(id);
            if (q == null) continue;
            try {
                if (!matchesState(q, optional(args, "state", "all"))) continue;
                String visibility = optional(args, "visibility", "all");
                if (!visibility.equals("all")
                        && (state.visible == null
                                || state.visible.contains(id) != visibility.equals("visible")))
                    continue;
                String match = match(id, q, optional(args, "query", ""));
                if (match != null) {
                    Map<String, Object> row = summary(id, q);
                    row.put("matchedIn", match);
                    rows.add(row);
                }
            } catch (RuntimeException | LinkageError e) {
                errors.add(
                        map(
                                "questId",
                                id.toString(),
                                "error",
                                clip(String.valueOf(e.getMessage()), 512)));
            }
        }
        Map<String, Object> result = page("quests", rows, i, ids.size());
        result.put("errors", errors);
        result.put("scanned", scanned);
        result.put("visibilityKnown", state.visible != null);
        return result;
    }

    private boolean matchesState(IQuest q, String filter) {
        if (filter.equals("all")) return true;
        if (filter.equals("complete")) return q.isComplete(state.playerId);
        if (filter.equals("incomplete")) return !q.isComplete(state.playerId);
        return q.getState(state.player).name().equalsIgnoreCase(filter);
    }

    private String match(UUID id, IQuest q, String query) {
        if (query.isEmpty()) return "all";
        if (contains(id.toString(), query)) return "questId";
        if (contains(QuestTranslation.translateQuestName(id, q), query)) return "name";
        if (contains(QuestTranslation.translateQuestDescription(id, q), query))
            return "description";
        for (DBEntry<ITask> e : q.getTasks().getEntries()) {
            ITask task = e.getValue();
            if (contains(task.getFactoryID().toString(), query)
                    || contains(translate(task.getUnlocalisedName()), query)) return "taskType";
            List<String> texts = task.getTextsForSearch();
            if (texts != null)
                for (String text : texts) if (contains(text, query)) return "taskText";
            if (task instanceof ITaskItemInput
                    && matchesItems(((ITaskItemInput) task).getItemInputs(), query))
                return "taskItem";
        }
        for (DBEntry<IReward> e : q.getRewards().getEntries()) {
            IReward reward = e.getValue();
            if (contains(reward.getFactoryID().toString(), query)
                    || contains(translate(reward.getUnlocalisedName()), query)) return "rewardType";
            if (reward instanceof IRewardItemOutput
                    && matchesItems(((IRewardItemOutput) reward).getItemOutputs(), query))
                return "rewardItem";
        }
        return null;
    }

    private static boolean matchesItems(List<BigItemStack> items, String query) {
        if (items != null)
            for (BigItemStack item : items)
                if (item != null) {
                    ItemStack stack = item.getBaseStack();
                    if (contains(item.getOreDict(), query)
                            || contains(stack.getDisplayName(), query)
                            || contains(Item.itemRegistry.getNameForObject(stack.getItem()), query))
                        return true;
                }
        return false;
    }

    private Map<String, Object> quest(Map<String, Object> args) {
        UUID id = UUID.fromString(string(args, "questId"));
        IQuest q = requireQuest(id);
        Map<String, Object> result = summary(id, q);
        description(result, QuestTranslation.translateQuestDescription(id, q), args);
        result.put("questLogic", q.getProperty(NativeProps.LOGIC_QUEST).name());
        result.put("taskLogic", q.getProperty(NativeProps.LOGIC_TASK).name());
        result.put("visibilityRule", q.getProperty(NativeProps.VISIBILITY).name());
        result.put(
                "settings",
                map(
                        "repeatTimeTicks",
                        q.getProperty(NativeProps.REPEAT_TIME),
                        "repeatRelative",
                        q.getProperty(NativeProps.REPEAT_REL),
                        "autoClaim",
                        q.getProperty(NativeProps.AUTO_CLAIM),
                        "global",
                        q.getProperty(NativeProps.GLOBAL),
                        "globalShare",
                        q.getProperty(NativeProps.GLOBAL_SHARE),
                        "lockedProgress",
                        q.getProperty(NativeProps.LOCKED_PROGRESS),
                        "simultaneous",
                        q.getProperty(NativeProps.SIMULTANEOUS),
                        "silent",
                        q.getProperty(NativeProps.SILENT)));
        result.put("prerequisiteCount", q.getRequirements().size());
        NBTTagCompound nativeData = new NBTTagCompound();
        nativeData.setTag("config", q.writeToNBT(new NBTTagCompound()));
        nativeData.setTag(
                "progress",
                q.writeProgressToNBT(
                        new NBTTagCompound(), Collections.singletonList(state.playerId)));
        result.put("nbt", NbtView.page(nativeData, args));
        List<Object> memberships = new ArrayList<Object>();
        List<Map.Entry<UUID, IQuestLine>> chapters = lines.get();
        for (int i = 0; i < chapters.size(); i++) {
            Map.Entry<UUID, IQuestLine> line = chapters.get(i);
            if (line.getValue().containsKey(id))
                memberships.add(lineSummary(line.getKey(), line.getValue(), i));
        }
        int membershipEnd = end(args, memberships.size());
        result.put(
                "lines",
                page(
                        "entries",
                        new ArrayList<Object>(
                                memberships.subList(
                                        Math.min(offset(args), memberships.size()), membershipEnd)),
                        membershipEnd,
                        memberships.size()));
        List<DBEntry<ITask>> tasks = new ArrayList<DBEntry<ITask>>(q.getTasks().getEntries());
        Collections.sort(tasks);
        List<Object> taskRows = new ArrayList<Object>();
        int taskEnd = end(args, tasks.size());
        for (int i = Math.min(offset(args), tasks.size()); i < taskEnd; i++)
            taskRows.add(taskSummary(tasks.get(i).getID(), tasks.get(i).getValue()));
        result.put("tasks", page("entries", taskRows, taskEnd, tasks.size()));
        List<DBEntry<IReward>> rewards =
                new ArrayList<DBEntry<IReward>>(q.getRewards().getEntries());
        Collections.sort(rewards);
        List<Object> rewardRows = new ArrayList<Object>();
        int rewardEnd = end(args, rewards.size());
        for (int i = Math.min(offset(args), rewards.size()); i < rewardEnd; i++)
            rewardRows.add(rewardSummary(rewards.get(i).getID(), rewards.get(i).getValue()));
        result.put("rewards", page("entries", rewardRows, rewardEnd, rewards.size()));
        return result;
    }

    private Map<String, Object> task(Map<String, Object> args) {
        IQuest q = requireQuest(UUID.fromString(string(args, "questId")));
        int id = integer(args, "taskId", 0, 0, Integer.MAX_VALUE);
        ITask task = q.getTasks().getValue(id);
        if (task == null) throw new IllegalArgumentException("Unknown taskId in quest");
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("config", task.writeToNBT(new NBTTagCompound()));
        root.setTag(
                "progress",
                task.writeProgressToNBT(
                        new NBTTagCompound(), Collections.singletonList(state.playerId)));
        Map<String, Object> result = taskSummary(id, task);
        result.put("questId", string(args, "questId"));
        result.put("nbt", NbtView.page(root, args));
        if (task instanceof ITaskItemInput)
            result.put("items", items(((ITaskItemInput) task).getItemInputs(), args));
        return result;
    }

    private Map<String, Object> reward(Map<String, Object> args) {
        IQuest q = requireQuest(UUID.fromString(string(args, "questId")));
        int id = integer(args, "rewardId", 0, 0, Integer.MAX_VALUE);
        IReward reward = q.getRewards().getValue(id);
        if (reward == null) throw new IllegalArgumentException("Unknown rewardId in quest");
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("config", reward.writeToNBT(new NBTTagCompound()));
        Map<String, Object> result = rewardSummary(id, reward);
        result.put("questId", string(args, "questId"));
        if (reward instanceof RewardChoice) {
            int choice = ((RewardChoice) reward).getSelecton(state.playerId);
            result.put("selectedChoiceIndex", choice < 0 ? null : choice);
        }
        result.put("nbt", NbtView.page(root, args));
        if (reward instanceof IRewardItemOutput)
            result.put("items", items(((IRewardItemOutput) reward).getItemOutputs(), args));
        return result;
    }

    private static Map<String, Object> items(List<BigItemStack> items, Map<String, Object> args) {
        if (items == null) return map("available", false);
        List<Object> rows = new ArrayList<Object>();
        int end = end(args, items.size());
        for (int i = Math.min(offset(args), items.size()); i < end; i++) {
            BigItemStack item = items.get(i);
            if (item == null) {
                rows.add(map("index", i, "available", false));
                continue;
            }
            ItemStack stack = item.getBaseStack();
            rows.add(
                    map(
                            "index",
                            i,
                            "registryName",
                            Item.itemRegistry.getNameForObject(stack.getItem()),
                            "displayName",
                            clip(stack.getDisplayName(), 512),
                            "count",
                            item.stackSize,
                            "damage",
                            stack.getItemDamage(),
                            "oreDictionary",
                            item.getOreDict(),
                            "hasNbt",
                            stack.hasTagCompound()));
        }
        return page("entries", rows, end, items.size());
    }

    private Map<String, Object> relations(Map<String, Object> args) {
        UUID id = UUID.fromString(string(args, "questId"));
        IQuest q = requireQuest(id);
        boolean prerequisites = string(args, "direction").equals("prerequisites");
        List<UUID> ids = sorted(prerequisites ? q.getRequirements() : quests.get().keySet());
        List<Object> rows = new ArrayList<Object>();
        int i = Math.min(offset(args), ids.size()), scanned = 0;
        while (i < ids.size() && scanned++ < 128 && rows.size() < limit(args)) {
            UUID other = ids.get(i++);
            IQuest related = quests.get().get(other);
            if (!prerequisites && (related == null || !related.getRequirements().contains(id)))
                continue;
            Map<String, Object> row = summary(other, related);
            row.put(
                    "requirementType",
                    (prerequisites ? q.getRequirementType(other) : related.getRequirementType(id))
                            .name());
            row.put("dependentQuestId", prerequisites ? id.toString() : other.toString());
            row.put("prerequisiteQuestId", prerequisites ? other.toString() : id.toString());
            row.put(
                    "dependentQuestLogic",
                    (prerequisites ? q : related).getProperty(NativeProps.LOGIC_QUEST).name());
            rows.add(row);
        }
        Map<String, Object> result = page("relations", rows, i, ids.size());
        result.put("direction", string(args, "direction"));
        result.put("questId", id.toString());
        return result;
    }

    private Map<String, Object> summary(UUID id, IQuest q) {
        if (q == null) return map("questId", id.toString(), "missing", true);
        return map(
                "questId",
                id.toString(),
                "name",
                clip(QuestTranslation.translateQuestName(id, q), 1024),
                "state",
                q.getState(state.player).name().toLowerCase(Locale.ROOT),
                "complete",
                q.isComplete(state.playerId),
                "unlocked",
                q.isUnlocked(state.playerId),
                "claimed",
                q.hasClaimed(state.playerId),
                "visible",
                state.visible == null ? null : state.visible.contains(id),
                "taskCount",
                q.getTasks().size(),
                "rewardCount",
                q.getRewards().size());
    }

    private Map<String, Object> taskSummary(int id, ITask t) {
        return map(
                "taskId",
                id,
                "type",
                t.getFactoryID().toString(),
                "name",
                clip(translate(t.getUnlocalisedName()), 512),
                "complete",
                t.isComplete(state.playerId),
                "ignored",
                t.ignored(state.playerId));
    }

    private static Map<String, Object> rewardSummary(int id, IReward r) {
        return map(
                "rewardId",
                id,
                "type",
                r.getFactoryID().toString(),
                "name",
                clip(translate(r.getUnlocalisedName()), 512));
    }

    private static Map<String, Object> lineSummary(UUID id, IQuestLine line, int order) {
        return map(
                "lineId",
                id.toString(),
                "name",
                clip(QuestTranslation.translateQuestLineName(id, line), 1024),
                "order",
                order,
                "questCount",
                line.size());
    }

    private IQuest requireQuest(UUID id) {
        IQuest q = quests.get().get(id);
        if (q == null) throw new IllegalArgumentException("Unknown questId");
        return q;
    }

    private IQuestLine requireLine(UUID id) {
        for (Map.Entry<UUID, IQuestLine> line : lines.get())
            if (line.getKey().equals(id)) return line.getValue();
        throw new IllegalArgumentException("Unknown lineId");
    }

    private int lineOrder(UUID id) {
        List<Map.Entry<UUID, IQuestLine>> all = lines.get();
        for (int i = 0; i < all.size(); i++) if (all.get(i).getKey().equals(id)) return i;
        return -1;
    }

    private static void description(
            Map<String, Object> result, String text, Map<String, Object> args) {
        if (text == null) text = "";
        int offset = integer(args, "textOffset", 0, 0, Integer.MAX_VALUE),
                end = (int) Math.min(text.length(), (long) offset + 8192);
        String chunk = text.substring(Math.min(offset, text.length()), end);
        result.put("description", chunk);
        result.put("descriptionPlain", plain(chunk));
        result.put("descriptionLength", text.length());
        result.put("nextTextOffset", end < text.length() ? end : null);
    }

    private static List<UUID> sorted(Collection<UUID> ids) {
        List<UUID> all = new ArrayList<UUID>(ids);
        Collections.sort(all);
        return all;
    }

    private static String translate(String value) {
        return QuestTranslation.translate(value);
    }

    private static String optional(Map<String, Object> args, String key, String fallback) {
        return args.containsKey(key) ? string(args, key) : fallback;
    }

    private static int offset(Map<String, Object> args) {
        return integer(args, "offset", 0, 0, Integer.MAX_VALUE);
    }

    private static int limit(Map<String, Object> args) {
        return integer(args, "limit", 32, 1, 64);
    }

    private static int end(Map<String, Object> args, int size) {
        return (int) Math.min(size, (long) offset(args) + limit(args));
    }

    private static Map<String, Object> page(String key, List<Object> values, int next, int total) {
        return map(key, values, "totalCandidates", total, "nextOffset", next < total ? next : null);
    }

    private static String clip(String text, int max) {
        return text == null ? "" : text.substring(0, Math.min(max, text.length()));
    }

    private static String plain(String text) {
        return text == null ? "" : text.replaceAll("(?i)\u00a7[0-9A-FK-OR]", "");
    }

    private static boolean contains(String text, String query) {
        return plain(text).toLowerCase(Locale.ROOT).contains(plain(query).toLowerCase(Locale.ROOT));
    }

    static final class ClientState {
        final Object world;
        final int dimension;
        final EntityPlayer player;
        final UUID playerId;
        final Set<UUID> visible;

        ClientState(
                Object world,
                int dimension,
                EntityPlayer player,
                UUID playerId,
                Set<UUID> visible) {
            this.world = world;
            this.dimension = dimension;
            this.player = player;
            this.playerId = playerId;
            this.visible = visible;
        }
    }
}
