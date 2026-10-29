package com.valoris.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.valoris.valoris2;

/**
 * Creates a config file from its built-in default, and replaces it when the built-in default has a higher
 * "version" (the old file is kept next to it as .bak), so new mod updates are not hidden by an old config.
 */
public final class ConfigFiles {
    private ConfigFiles() {}

    private static int version(JsonObject root) {
        return root.has("version") ? root.get("version").getAsInt() : 1;
    }

    public static void ensure(Path path, String resource) throws IOException {
        Files.createDirectories(path.getParent());
        int builtIn;
        try (InputStream in = ConfigFiles.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Built-in default " + resource + " is missing");
            builtIn = version(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        }
        if (Files.exists(path)) {
            int current;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                current = version(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (RuntimeException e) {
                current = Integer.MAX_VALUE; // broken file: leave it alone, loading reports the error
            }
            if (current >= builtIn) return;
            Path backup = path.resolveSibling(path.getFileName() + ".bak");
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            valoris2.LOGGER.warn("{} was out of date (version {} < {}); replaced, old copy saved as {}",
                    path.getFileName(), current, builtIn, backup.getFileName());
        }
        try (InputStream in = ConfigFiles.class.getResourceAsStream(resource)) {
            Files.copy(in, path);
        }
    }
}
