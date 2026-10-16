package com.forcitis.one;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The tables whose primary key a framework owns, read from architecture/primary-key-exemptions.txt (ADR-0033 section 1 item 8). */
final class PrimaryKeyExemptions {
    static final Path FILE = Path.of("architecture/primary-key-exemptions.txt");

    private PrimaryKeyExemptions() {
    }

    /** Table to reason; a line without a reason is refused. */
    static Map<String, String> read() {
        Map<String, String> exemptions = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(FILE)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int hash = trimmed.indexOf('#');
                if (hash <= 0 || trimmed.substring(hash + 1).isBlank()) {
                    throw new IllegalStateException(FILE + ": '" + trimmed + "' needs <schema.table>  # reason");
                }
                exemptions.put(trimmed.substring(0, hash).strip(), trimmed.substring(hash + 1).strip());
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return exemptions;
    }

    static Set<String> tables() {
        return Set.copyOf(read().keySet());
    }
}
