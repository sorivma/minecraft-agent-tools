package dev.minecraftagenttools.forge.betterquesting;

import static dev.minecraftagenttools.protocol.data.Data.map;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.*;
import bq_standard.tasks.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Bounded, read-only projections of the standard task APIs. No detection or NBT serialization. */
final class TaskDetails {
    private TaskDetails() {}

    static Map<String, Object> page(ITask task, UUID user, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 6)
            throw new IllegalArgumentException("Invalid condition page");
        Map<String, Object> result = map("requirementLogic", "ALL", "supported", true);
        List<String> notes = new ArrayList<String>();
        List<Object> rows = new ArrayList<Object>();
        int total = 1;
        if (task instanceof TaskRetrieval) {
            TaskRetrieval t = (TaskRetrieval) task;
            result.put("action", t.consume ? "Submit items" : "Have items");
            result.put("requirementLogic", t.requireOnlyOneItem ? "ANY" : "ALL");
            notes.add(t.consume ? "Items are consumed" : "Items are kept");
            if (t.autoConsume && t.consume) notes.add("Automatic submission");
            if (t.groupDetect) notes.add("Group inventory detection");
            matching(notes, t.ignoreNBT, t.partialMatch);
            total = items(rows, t.requiredItems, t.getUsersProgress(user), offset, limit);
        } else if (task instanceof TaskCrafting) {
            TaskCrafting t = (TaskCrafting) task;
            result.put("action", "Craft items");
            List<String> methods = new ArrayList<String>();
            if (t.allowCraft) methods.add("crafting");
            if (t.allowSmelt) methods.add("smelting");
            if (t.allowAnvil) methods.add("anvil");
            if (t.allowCraftedFromStatistics) methods.add("crafting statistics");
            notes.add(
                    "Counts: "
                            + (methods.isEmpty()
                                    ? "no crafting method enabled"
                                    : String.join(", ", methods)));
            matching(notes, t.ignoreNBT, t.partialMatch);
            total = items(rows, t.requiredItems, t.getUsersProgress(user), offset, limit);
        } else if (task instanceof TaskFluid) {
            TaskFluid t = (TaskFluid) task;
            result.put("action", t.consume ? "Submit fluids" : "Have fluids");
            notes.add(t.consume ? "Fluids are consumed" : "Fluids are kept");
            if (t.consume && t.autoConsume) notes.add("Automatic submission");
            if (t.groupDetect) notes.add("Group detection");
            if (!t.ignoreNbt) notes.add("Fluid NBT must match");
            int[] progress = t.getUsersProgress(user);
            total = t.requiredFluids.size();
            int end = (int) Math.min((long) offset + limit, total);
            for (int i = Math.min(offset, total); i < end; i++) {
                FluidStack fluid = t.requiredFluids.get(i);
                rows.add(
                        fluid == null
                                ? unknown("Unavailable fluid")
                                : numeric(
                                        fluid.getLocalizedName(),
                                        at(progress, i),
                                        fluid.amount,
                                        "mB"));
            }
        } else if (task instanceof TaskHunt) {
            TaskHunt t = (TaskHunt) task;
            result.put("action", "Defeat enemies");
            if (!t.damageType.isEmpty()) notes.add("Damage type: " + t.damageType);
            if (!t.ignoreNBT) notes.add("Entity NBT must match");
            if (t.subtypes) notes.add("Entity subtypes accepted");
            rows.add(
                    numeric(
                            entity(t.idName),
                            value(t.getUsersProgress(user)),
                            t.required,
                            "kills"));
        } else if (task instanceof TaskXP) {
            TaskXP t = (TaskXP) task;
            result.put("action", t.consume ? "Submit experience" : "Have experience");
            notes.add(t.consume ? "Experience is consumed" : "Experience is kept");
            rows.add(
                    numeric(
                            t.levels ? "Experience for level " + t.amount : "Experience points",
                            value(t.getUsersProgress(user)),
                            t.levels ? XPHelper.getLevelXP(t.amount) : t.amount,
                            "XP"));
        } else if (task instanceof TaskLocation) {
            TaskLocation t = (TaskLocation) task;
            result.put("action", t.invert ? "Avoid target area" : "Visit location");
            if (t.hideInfo) {
                notes.add("Location details are hidden in the quest book");
                rows.add(check(text(t.name), task.isComplete(user)));
            } else {
                rows.add(check(text(t.name) + " | dimension " + t.dim, task.isComplete(user)));
                if (t.range > 0)
                    notes.add(
                            "Target "
                                    + t.x
                                    + ", "
                                    + t.y
                                    + ", "
                                    + t.z
                                    + "; radius "
                                    + t.range
                                    + " blocks");
                else notes.add("Anywhere in the target dimension");
                if (t.biome >= 0) notes.add("Biome ID " + t.biome);
                if (!t.structure.isEmpty()) notes.add("Structure: " + t.structure);
                if (t.visible && t.range > 0) notes.add("Line of sight required");
                if (t.taxiCab) notes.add("Manhattan distance");
            }
        } else if (task instanceof TaskBlockBreak) {
            TaskBlockBreak t = (TaskBlockBreak) task;
            result.put("action", "Break blocks");
            int[] progress = t.getUsersProgress(user);
            total = t.blockTypes.size();
            int end = (int) Math.min((long) offset + limit, total);
            for (int i = Math.min(offset, total); i < end; i++) {
                NbtBlockType block = t.blockTypes.get(i);
                Map<String, Object> row =
                        numeric(
                                block.b == null ? "Unspecified block" : block.b.getLocalizedName(),
                                at(progress, i),
                                block.n,
                                "blocks");
                row.put(
                        "match",
                        (block.m < 0 || block.m == 32767 ? "Any metadata" : "Metadata " + block.m)
                                + (block.oreDict.isEmpty() ? "" : " | ore: " + block.oreDict)
                                + (block.tags.hasNoTags() ? "" : " | NBT filter"));
                rows.add(row);
            }
        } else if (task instanceof TaskInteractEntity) {
            TaskInteractEntity t = (TaskInteractEntity) task;
            result.put("action", interaction(t.onInteract, t.onHit));
            rows.add(
                    numeric(
                            entity(t.entityID),
                            value(t.getUsersProgress(user)),
                            t.required,
                            "times"));
            if (t.targetItem != null)
                notes.add("Held item: " + text(t.targetItem.getBaseStack().getDisplayName()));
            if (t.targetItem != null) matching(notes, t.ignoreItemNBT, t.partialItemMatch);
            if (!t.ignoreEntityNBT) notes.add("Entity NBT must match");
        } else if (task instanceof TaskInteractItem) {
            TaskInteractItem t = (TaskInteractItem) task;
            result.put("action", interaction(t.onInteract, t.onHit));
            rows.add(
                    numeric(
                            t.targetItem == null
                                    ? "Any held item"
                                    : t.targetItem.getBaseStack().getDisplayName(),
                            value(t.getUsersProgress(user)),
                            t.required,
                            "times"));
            if (t.targetBlock.b != null)
                notes.add("On block: " + t.targetBlock.b.getLocalizedName());
            if (t.targetBlock.m >= 0) notes.add("Block metadata: " + t.targetBlock.m);
            if (!t.ignoreNBT) notes.add("Item NBT must match");
        } else if (task instanceof TaskMeeting) {
            TaskMeeting t = (TaskMeeting) task;
            result.put("action", "Find nearby entities");
            rows.add(
                    check(
                            t.amount + " x " + entity(t.idName) + " within " + t.range + " blocks",
                            task.isComplete(user)));
            if (!t.ignoreNBT) notes.add("Entity NBT must match");
        } else if (task instanceof TaskScoreboard) {
            TaskScoreboard t = (TaskScoreboard) task;
            result.put("action", "Reach score condition");
            int score = ScoreboardBQ.INSTANCE.getScore(user, t.scoreName);
            Map<String, Object> row =
                    check(
                            text(t.scoreDisp)
                                    + " "
                                    + t.operation.GetText()
                                    + " "
                                    + score(t.target, t),
                            t.operation.checkValues(score, t.target));
            row.put("detail", "Current score: " + score(score, t));
            rows.add(row);
        } else if (task instanceof TaskCheckbox) {
            result.put("action", "Confirm in quest book");
            notes.add("Manual confirmation in the quest book");
            rows.add(check("Player confirmation", task.isComplete(user)));
        } else if (task instanceof ITaskItemInput) {
            result.put("action", "Custom item condition");
            result.put("supported", false);
            result.put("requirementLogic", "CUSTOM");
            notes.add("Progress and task behavior: see quest book");
            total = items(rows, ((ITaskItemInput) task).getItemInputs(), null, offset, limit);
        } else {
            result.put("action", "Custom condition");
            result.put("supported", false);
            result.put("requirementLogic", "CUSTOM");
            notes.add("Full conditions available in quest book");
            rows.add(check("Task completion", task.isComplete(user)));
        }
        if (!(task instanceof TaskRetrieval
                        || task instanceof TaskCrafting
                        || task instanceof TaskFluid
                        || task instanceof TaskBlockBreak
                        || task instanceof ITaskItemInput)
                && offset > 0) rows.clear();
        result.put("conditions", rows);
        result.put("notes", notes);
        result.put("totalConditions", total);
        int next = (int) Math.min((long) offset + limit, total);
        result.put("nextConditionOffset", next < total ? next : null);
        return result;
    }

    private static int items(
            List<Object> rows, List<BigItemStack> items, int[] progress, int offset, int limit) {
        if (items == null) return 0;
        int end = (int) Math.min((long) offset + limit, items.size());
        for (int i = Math.min(offset, items.size()); i < end; i++) {
            BigItemStack item = items.get(i);
            if (item == null) {
                rows.add(unknown("Unavailable item"));
                continue;
            }
            ItemStack stack = item.getBaseStack();
            Map<String, Object> row =
                    numeric(
                            stack.getDisplayName(),
                            progress == null ? null : at(progress, i),
                            item.stackSize,
                            "items");
            row.put("registryName", Item.itemRegistry.getNameForObject(stack.getItem()));
            row.put("damage", stack.getItemDamage());
            row.put("hasNbt", stack.hasTagCompound());
            row.put(
                    "match",
                    !item.getOreDict().isEmpty()
                            ? "Any ore: " + item.getOreDict()
                            : stack.getItemDamage() == 32767
                                    ? "Any metadata"
                                    : "Metadata " + stack.getItemDamage());
            rows.add(row);
        }
        return items.size();
    }

    private static Map<String, Object> numeric(
            String name, Number current, long required, String unit) {
        return map(
                "name",
                text(name),
                "current",
                current,
                "required",
                required,
                "unit",
                unit,
                "remaining",
                current == null ? null : Math.max(0, required - current.longValue()),
                "satisfied",
                current == null ? null : current.longValue() >= required);
    }

    private static Map<String, Object> check(String name, boolean done) {
        return map("name", text(name), "satisfied", done);
    }

    private static Map<String, Object> unknown(String name) {
        return map("name", name);
    }

    private static int at(int[] progress, int index) {
        return progress != null && index < progress.length ? progress[index] : 0;
    }

    private static long value(Number number) {
        return number == null ? 0 : number.longValue();
    }

    private static void matching(List<String> notes, boolean ignoreNBT, boolean partial) {
        if (!ignoreNBT)
            notes.add(partial ? "Required NBT fields must match" : "Exact NBT required");
    }

    private static String entity(String id) {
        return text("entity." + id + ".name").equals("entity." + id + ".name")
                ? id
                : text("entity." + id + ".name");
    }

    private static String interaction(boolean interact, boolean hit) {
        return interact && hit
                ? "Interact or hit"
                : interact ? "Interact" : hit ? "Hit target" : "No interaction enabled";
    }

    private static String text(String value) {
        if (value == null) return "";
        String translated = QuestTranslation.translate(value);
        String result = translated == null ? value : translated;
        return result.length() > 512 ? result.substring(0, 512) : result;
    }

    private static String score(int score, TaskScoreboard task) {
        if (task.conversion == 0 || !Float.isFinite(task.conversion))
            return Integer.toString(score);
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT))
                        .format(score / (double) task.conversion)
                + task.suffix;
    }
}
