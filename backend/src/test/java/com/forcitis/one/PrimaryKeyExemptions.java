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

    /** A line whose reason names an ADR is a registered partitioned table (ADR-0048): its key shape is checked, it is not left out of the naming rules. */
    static boolean isPartitionException(String reason) {
        return reason.contains("ADR-");
    }

    /** The tables whose key and column names a framework fixes; the registered partitioned table is not among them. */
    static Set<String> tables() {
        Set<String> tables = new java.util.TreeSet<>();
        read().forEach((table, reason) -> {
            if (!isPartitionException(reason)) {
                tables.add(table);
            }
        });
        return Set.copyOf(tables);
    }
}
