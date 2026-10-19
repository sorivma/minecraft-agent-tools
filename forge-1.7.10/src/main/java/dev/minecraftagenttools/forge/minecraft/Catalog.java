package dev.minecraftagenttools.forge.minecraft;

import static dev.minecraftagenttools.protocol.data.Data.*;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

final class Catalog {
    Object registry(Map<String, Object> args) {
        String kind = string(args, "kind");
        if (!kind.equals("items") && !kind.equals("blocks"))
            throw new IllegalArgumentException("kind must be items or blocks");
        List<String> names = new ArrayList<String>();
        Set keys =
                kind.equals("items") ? Item.itemRegistry.getKeys() : Block.blockRegistry.getKeys();
        for (Object key : keys) names.add(key.toString());
        Collections.sort(names);
        int offset = integer(args, "offset", 0, 0, Integer.MAX_VALUE),
                limit = integer(args, "limit", 64, 1, 256);
        List<Object> entries = new ArrayList<Object>();
        for (int i = Math.min(offset, names.size());
                i < names.size() && entries.size() < limit;
                i++) {
            String name = names.get(i);
            if (kind.equals("items")) {
                Item item = (Item) Item.itemRegistry.getObject(name);
                entries.add(
                        map(
                                "id",
                                name,
                                "numericId",
                                Item.getIdFromItem(item),
                                "translationKey",
                                item.getUnlocalizedName(),
                                "maxStackSize",
                                item.getItemStackLimit(),
                                "maxDamage",
                                item.getMaxDamage(),
                                "hasSubtypes",
                                item.getHasSubtypes()));
            } else {
                Block b = (Block) Block.blockRegistry.getObject(name);
                entries.add(
                        map(
                                "id",
                                name,
                                "numericId",
                                Block.getIdFromBlock(b),
                                "translationKey",
                                b.getUnlocalizedName(),
                                "opaque",
                                b.isOpaqueCube(),
                                "normalRender",
                                b.renderAsNormalBlock(),
                                "liquid",
                                b.getMaterial().isLiquid(),
                                "solid",
                                b.getMaterial().isSolid(),
                                "metadataSemantics",
                                "version-specific; inspect placed blocks for actual state"));
            }
        }
        return page(entries, offset, names.size());
    }

    private Map<String, Object> page(List<Object> entries, int offset, int total) {
        return map(
                "entries",
                entries,
                "total",
                total,
                "offset",
                offset,
                "nextOffset",
                (long) offset + entries.size() < total ? offset + entries.size() : null,
                "truncated",
                (long) offset + entries.size() < total,
                "consistency",
                "registry order within this server session");
    }
}
