package dev.minecraftagenttools.forge.betterquesting;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.resources.textures.OreDictTexture;
import betterquesting.client.gui2.GuiQuest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/**
 * Optional client UI seam. No Harness dependency and no socket access from the rendering thread.
 */
public final class BqClient {
    public static BqClient active;
    private final BqReader reader;
    private final Consumer<UUID> open;
    private final LongSupplier clock;
    private final Map<String, Page> pages = new LinkedHashMap<String, Page>(16, 0.75f, true);
    private final Map<UUID, Icon> icons = new LinkedHashMap<UUID, Icon>(16, .75f, true);
    private String cachedSession;
    private int cachedRevision;

    BqClient(BqReader reader) {
        this(
                reader,
                id -> {
                    Minecraft mc = Minecraft.getMinecraft();
                    mc.displayGuiScreen(new GuiQuest(mc.currentScreen, id));
                });
    }

    BqClient(BqReader reader, Consumer<UUID> open) {
        this(reader, open, System::nanoTime);
    }

    BqClient(BqReader reader, Consumer<UUID> open, LongSupplier clock) {
        this.reader = reader;
        this.open = open;
        this.clock = clock;
    }

    public Map<String, Object> snapshot(
            String session, int dimension, UUID questId, String section, int offset) {
        return cached(
                session,
                dimension,
                questId,
                section + ":" + offset,
                () -> reader.widgetSnapshot(session, dimension, questId, section, offset));
    }

    public Map<String, Object> taskSnapshot(
            String session, int dimension, UUID questId, int taskId, int offset) {
        return cached(
                session,
                dimension,
                questId,
                "task:" + taskId + ":" + offset,
                () -> reader.widgetTask(session, dimension, questId, taskId, offset));
    }

    /** Same native texture as the quest book, including NBT and cycling OreDictionary variants. */
    public boolean drawQuestIcon(
            String session, int dimension, UUID questId, int x, int y, int size) {
        reader.requireWidgetQuest(session, dimension, questId);
        BigItemStack icon = reader.widgetIcon(session, dimension, questId);
        if (icon == null || size <= 0) return false;
        Icon cached = icons.get(questId);
        if (cached == null || cached.stack != icon) {
            cached = new Icon(icon);
            icons.put(questId, cached);
            if (icons.size() > 64) icons.remove(icons.keySet().iterator().next());
        }
        cached.texture.drawTexture(x, y, size, size, 0, 0);
        return true;
    }

    private Map<String, Object> cached(
            String session,
            int dimension,
            UUID questId,
            String pageKey,
            Supplier<Map<String, Object>> read) {
        // Guard every render before consulting the cache, including deleted quests and old worlds.
        reader.requireWidgetQuest(session, dimension, questId);
        int revision = reader.widgetRevision();
        if (!Objects.equals(cachedSession, session) || cachedRevision != revision) {
            pages.clear();
            icons.clear();
            cachedSession = session;
            cachedRevision = revision;
        }
        String key = dimension + ":" + questId + ":" + pageKey;
        Page page = pages.get(key);
        long now = clock.getAsLong();
        if (page == null || now - page.time >= TimeUnit.MILLISECONDS.toNanos(250)) {
            page = new Page(now, read.get());
            pages.put(key, page);
            if (pages.size() > 64) pages.remove(pages.keySet().iterator().next());
        }
        return page.data;
    }

    /** Call only from an explicit user click. Revalidate even if the last render was current. */
    public void openQuest(String session, int dimension, UUID questId) {
        reader.requireWidgetQuest(session, dimension, questId);
        open.accept(questId);
    }

    private static final class Page {
        final long time;
        final Map<String, Object> data;

        Page(long time, Map<String, Object> data) {
            this.time = time;
            this.data = data;
        }
    }

    private static final class Icon {
        final BigItemStack stack;
        final OreDictTexture texture;

        Icon(BigItemStack stack) {
            this.stack = stack;
            texture = new OreDictTexture(1F, stack, false, true);
        }
    }
}
