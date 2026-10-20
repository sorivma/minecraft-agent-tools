package dev.minecraftagenttools.protocol.transport;

import java.io.*;

public final class Frames {
    public static final int MAX_CHARS = 4 * 1024 * 1024;

    private Frames() {}

    public static String read(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') return line.toString();
            if (line.length() >= MAX_CHARS) throw new IOException("Frame exceeds limit");
            line.append((char) ch);
        }
        if (line.length() != 0) throw new EOFException("Incomplete frame");
        return null;
    }

    public static void write(Writer writer, String json) throws IOException {
        if (json.length() > MAX_CHARS)
            throw new IOException("Response exceeds frame limit; narrow query");
        writer.write(json);
        writer.write('\n');
        writer.flush();
    }
}
