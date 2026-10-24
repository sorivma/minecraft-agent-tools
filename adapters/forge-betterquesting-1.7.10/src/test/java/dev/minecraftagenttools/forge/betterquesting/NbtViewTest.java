package dev.minecraftagenttools.forge.betterquesting;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import java.util.*;
import net.minecraft.nbt.*;
import org.junit.Test;

public class NbtViewTest {
    @Test
    public void nestedListsAreRecoverableWithoutMutation() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < 80; i++) list.appendTag(new NBTTagInt(i));
        root.setTag("a/b~c", list);
        Map<String, Object> first = NbtView.page(root, map());
        assertTrue((Boolean) first.get("truncated"));
        assertEquals(Collections.singletonList("/a~1b~0c"), first.get("omittedPaths"));
        Map<String, Object> page =
                NbtView.page(root, map("path", "/a~1b~0c", "offset", 32, "limit", 32));
        assertEquals(64, page.get("nextOffset"));
        assertEquals(32, ((List<?>) page.get("data")).get(0));
        assertEquals(79, NbtView.page(root, map("path", "/a~1b~0c/79")).get("data"));
        assertEquals(80, list.tagCount());
    }

    @Test
    public void preservesLongsArraysAndLongText() {
        NBTTagCompound root = new NBTTagCompound();
        root.setLong("exact", Long.MAX_VALUE);
        root.setIntArray("numbers", new int[] {3, 4});
        char[] chars = new char[5000];
        Arrays.fill(chars, 'x');
        root.setString("text", new String(chars));
        assertEquals(
                Long.toString(Long.MAX_VALUE),
                NbtView.page(root, map("path", "/exact")).get("data"));
        assertEquals(4, NbtView.page(root, map("path", "/numbers/1")).get("data"));
        Map<String, Object> page = NbtView.page(root, map("path", "/text"));
        assertEquals(4096, page.get("nextOffset"));
        assertEquals(
                904,
                ((String) NbtView.page(root, map("path", "/text", "offset", 4096)).get("data"))
                        .length());
        assertThrows(
                IllegalArgumentException.class, () -> NbtView.page(root, map("path", "/missing")));
        assertThrows(
                IllegalArgumentException.class,
                () -> NbtView.page(root, map("path", "/numbers/2")));
    }

    @Test
    public void boundsWideDataAndHandlesHugeOffset() {
        NBTTagCompound root = new NBTTagCompound();
        for (int i = 0; i < 100; i++) root.setString("key" + i, "value");
        assertTrue((Boolean) NbtView.page(root, map("path", "/key0")).get("truncated") == false);
        Map<String, Object> empty = NbtView.page(root, map("offset", Integer.MAX_VALUE));
        assertNull(empty.get("nextOffset"));
        assertTrue(((Map<?, ?>) empty.get("data")).isEmpty());
        assertEquals(32, NbtView.page(root, map()).get("nextOffset"));
    }
}
