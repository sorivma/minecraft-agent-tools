package dev.minecraftagenttools.minecraft.provider;

import dev.minecraftagenttools.api.ToolContext;
import dev.minecraftagenttools.api.ToolProvider;
import dev.minecraftagenttools.minecraft.contract.Tools;
import java.util.List;
import java.util.Map;

/** Optional Minecraft tools; the MCP core has no dependency on this provider. */
public final class MinecraftToolProvider implements ToolProvider {
    public String id() {
        return "minecraft";
    }

    public List<Map<String, Object>> tools() {
        return Tools.list();
    }

    public void validate(String tool, Map<String, Object> arguments) {
        Tools.validate(tool, arguments);
    }

    public Map<String, Object> call(String tool, Map<String, Object> arguments, ToolContext context)
            throws Exception {
        return context.callBridge("minecraft", tool, arguments);
    }

    public String instructions() {
        return "Start with list_sessions; pass session, player UUID and dimension to Minecraft tools. "
                + "Use read_terrain to see the base/terrain around the player, including blocks, tile/container locations and entities. "
                + "Follow terrain nextOffset, tiles.nextOffset (tileOffset) and entities.nextOffset (entityOffset) when present; "
                + "reuse returned centerChunk as chunkX/chunkZ, chunkRadius and requestedBounds heights. "
                + "Use inspect_block for container contents and inspect_entity for mob details. Queries never load chunks.";
    }
}
