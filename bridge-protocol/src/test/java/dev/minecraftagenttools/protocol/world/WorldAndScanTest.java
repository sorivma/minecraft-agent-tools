package dev.minecraftagenttools.protocol.world;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class WorldAndScanTest {
    @Test
    public void staleRequestCannotReadReplacedWorld() {
        WorldSessions<Object> worlds = new WorldSessions<Object>();
        Object old = new Object();
        worlds.refresh(Collections.singletonMap(0, old));
        String token = worlds.session(0);
        worlds.refresh(Collections.singletonMap(0, old));
        assertSame(old, worlds.require(0, token));
        worlds.refresh(Collections.singletonMap(0, new Object()));
        assertNotEquals(token, worlds.session(0));
        try {
            worlds.require(0, token);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void unloadingAndRestartInvalidateSessions() {
        WorldSessions<Object> worlds = new WorldSessions<Object>();
        Object world = new Object();
        worlds.refresh(Collections.singletonMap(-1, world));
        String token = worlds.session(-1);
        try {
            worlds.require(0, token);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        worlds.refresh(Collections.emptyMap());
        try {
            worlds.require(-1, token);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        worlds.refresh(Collections.singletonMap(-1, world));
        assertNotEquals(token, worlds.session(-1));
    }

    @Test
    public void scanHasNoHolesAcrossNegativeChunkBoundaryAndPages() {
        Cuboid scan = new Cuboid(-17, 0, -1, 0, 1, 1);
        Set<String> cells = new HashSet<String>();
        for (int page = 0; page < scan.volume; page += 7)
            for (int i = page; i < Math.min(scan.volume, page + 7); i++)
                assertTrue(cells.add(Arrays.toString(scan.at(i))));
        assertEquals(108, cells.size());
        assertArrayEquals(new int[] {-17, 0, -1}, scan.at(0));
        assertArrayEquals(new int[] {0, 1, 1}, scan.at(scan.volume - 1));
    }

    @Test
    public void scanRejectsOverflowReversedAndOversizedBounds() {
        for (int[] b :
                new int[][] {
                    {Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 255, 1},
                    {0, 0, 0, 32768, 0, 0},
                    {1, 0, 0, 0, 0, 0},
                    {0, -1, 0, 0, 0, 0},
                    {0, 0, 0, 0, 256, 0}
                }) {
            try {
                new Cuboid(b[0], b[1], b[2], b[3], b[4], b[5]);
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
        assertEquals(32768, new Cuboid(0, 0, 0, 31, 31, 31).volume);
    }
}
