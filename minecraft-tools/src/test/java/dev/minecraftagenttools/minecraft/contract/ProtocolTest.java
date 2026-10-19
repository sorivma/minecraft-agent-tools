package dev.minecraftagenttools.minecraft.contract;

import static dev.minecraftagenttools.protocol.data.Data.*;
import static org.junit.Assert.*;

import dev.minecraftagenttools.protocol.transport.Frames;
import dev.minecraftagenttools.protocol.world.Snapshots;
import java.io.*;
import java.util.*;
import org.junit.Test;

public class ProtocolTest {
    @Test
    public void framesPreserveUnicodeAndSeparateMessages() throws Exception {
        StringWriter writer = new StringWriter();
        Frames.write(writer, "{\"name\":\"Сундук\"}");
        Frames.write(writer, "{}");
        StringReader reader = new StringReader(writer.toString());
        assertEquals("{\"name\":\"Сундук\"}", Frames.read(reader));
        assertEquals("{}", Frames.read(reader));
        assertNull(Frames.read(reader));
    }

    @Test(expected = EOFException.class)
    public void partialFrameIsRejected() throws Exception {
        Frames.read(new StringReader("{}"));
    }

    @Test(expected = IOException.class)
    public void oversizedFrameIsRejected() throws Exception {
        char[] chars = new char[Frames.MAX_CHARS + 1];
        Arrays.fill(chars, 'x');
        Frames.read(new CharArrayReader(chars));
    }

    @Test
    public void coordinateValidationDoesNotTruncateOrOverflow() {
        for (Object bad :
                Arrays.asList(1.5, Double.NaN, Double.POSITIVE_INFINITY, 2147483648L, "1")) {
            try {
                integer(map("x", bad), "x", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
                fail("Accepted " + bad);
            } catch (IllegalArgumentException expected) {
            }
        }
        assertEquals(-16, integer(map("x", -16.0), "x", 0, -16, 16));
    }

    @Test
    public void everyScopedToolRequiresSessionPlayerAndDimension() {
        for (Map<String, Object> tool : Tools.list()) {
            String name = (String) tool.get("name");
            if (name.equals("list_sessions")) {
                Tools.validate(name, map());
                continue;
            }
            try {
                Tools.validate(name, map());
                fail(name);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void unknownAndNullArgumentsRejected() {
        for (Map<String, Object> args :
                Arrays.asList(
                        map("surprise", 1),
                        map("session", "s", "player", "p", "dimension", null))) {
            try {
                Tools.validate("observe", args);
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void snapshotReportsChangesAndRemovalWithoutConsumingCursor() {
        Snapshots history = new Snapshots(4);
        String cursor = (String) history.compare("scope", null, map("a", 1, "b", 2)).get("cursor");
        Map<String, Object> result = history.compare("scope", cursor, map("a", 3, "c", 4));
        assertEquals(map("a", 3, "c", 4), result.get("changed"));
        assertEquals(Collections.singletonList("b"), result.get("removedFromObservation"));
        assertEquals(false, result.get("baseline"));
        assertEquals(
                Collections.emptyMap(),
                history.compare("scope", cursor, map("a", 1, "b", 2)).get("changed"));
    }

    @Test
    public void snapshotRejectsExpiredAndDifferentWorldScopes() {
        Snapshots history = new Snapshots(1);
        String cursor = (String) history.compare("world:player:radius", null, map()).get("cursor");
        try {
            history.compare("new-world:player:radius", cursor, map());
            fail();
        } catch (IllegalArgumentException expected) {
        }
        history.compare("world:player:radius", null, map());
        try {
            history.compare("world:player:radius", cursor, map());
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
