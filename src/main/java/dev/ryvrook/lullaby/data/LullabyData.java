package dev.ryvrook.lullaby.data;

import com.hypixel.hytale.logger.HytaleLogger;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The two small files Lullaby keeps next to its config. {@code ignored.json} lists the players who ran
 * {@code /lullaby ignore}, and {@code stats.json} counts skipped nights per world. Both live in memory and
 * are written whole, through a temporary file, on the Lullaby IO thread.
 */
public final class LullabyData {
    private static final JsonWriterSettings PRETTY = JsonWriterSettings.builder().indent(true).build();

    private final Path ignoredFile;
    private final Path statsFile;
    private final HytaleLogger logger;
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    /** World name as the engine spells it, to nights skipped. */
    private final Map<String, Integer> nights = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();

    public LullabyData(Path directory, HytaleLogger logger) {
        this.ignoredFile = directory.resolve("ignored.json");
        this.statsFile = directory.resolve("stats.json");
        this.logger = logger;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            logger.at(Level.SEVERE).withCause(e).log("Could not create the Lullaby data folder %s", directory);
        }
    }

    /** Reads both files. A missing file is an empty one, a broken file is kept aside and replaced. */
    public void load() {
        Document ignoredDoc = read(ignoredFile);
        if (ignoredDoc != null && ignoredDoc.get("Ignored") instanceof List<?> list) {
            for (Object o : list) {
                try {
                    ignored.add(UUID.fromString(String.valueOf(o)));
                } catch (IllegalArgumentException e) {
                    logger.at(Level.WARNING).log("Lullaby skipped a bad uuid %s in ignored.json", o);
                }
            }
        }
        Document statsDoc = read(statsFile);
        if (statsDoc != null && statsDoc.get("NightsSkipped") instanceof Document worlds) {
            for (Map.Entry<String, Object> e : worlds.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    nights.put(e.getKey(), Math.max(0, n.intValue()));
                }
            }
        }
    }

    public boolean isIgnored(UUID uuid) {
        return ignored.contains(uuid);
    }

    /** Flips the ignore flag and returns the new value. The caller saves. */
    public boolean toggleIgnored(UUID uuid) {
        if (ignored.remove(uuid)) {
            return false;
        }
        ignored.add(uuid);
        return true;
    }

    public Set<UUID> ignored() {
        return ignored;
    }

    public int nightsSkipped(String world) {
        return world == null ? 0 : nights.getOrDefault(world, 0);
    }

    /** Adds one skipped night to a world and returns the new total. The caller saves. */
    public int countSkip(String world) {
        return nights.merge(world, 1, Integer::sum);
    }

    /** Writes both files. Safe to call from any thread, runs one write at a time. */
    public void save() {
        synchronized (writeLock) {
            List<String> ids = new ArrayList<>();
            for (UUID uuid : ignored) {
                ids.add(uuid.toString());
            }
            ids.sort(null);
            write(ignoredFile, new Document("Ignored", ids));
            write(statsFile, new Document("NightsSkipped", new Document(new TreeMap<>(nights))));
        }
    }

    private Document read(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Document.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.at(Level.WARNING).withCause(e).log("Lullaby could not read %s, starting it fresh", file.getFileName());
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignoredMove) {
                // the next save overwrites it anyway
            }
            return null;
        }
    }

    private void write(Path file, Document doc) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, doc.toJson(PRETTY), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.at(Level.WARNING).withCause(e).log("Lullaby could not write %s", file.getFileName());
        }
    }
}
