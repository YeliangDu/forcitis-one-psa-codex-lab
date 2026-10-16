package com.forcitis.one;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The *_version_id columns that point at a version table of another schema by ID only, read from architecture/cross-schema-version-references.txt (D2, B4). */
final class CrossSchemaVersionReferences {
    static final Path FILE = Path.of("architecture/cross-schema-version-references.txt");

    private CrossSchemaVersionReferences() {
    }

    /** Source column (schema.table.column) to target version table (schema.table); a line without a note is refused. */
    static Map<String, String> read() {
        Map<String, String> references = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(FILE)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int hash = trimmed.indexOf('#');
                String[] sides = hash <= 0 ? new String[0] : trimmed.substring(0, hash).split("->");
                if (sides.length != 2 || trimmed.substring(hash + 1).isBlank()) {
                    throw new IllegalStateException(FILE + ": '" + trimmed + "' needs <schema.table.column> -> <schema.table>  # note");
                }
                references.put(sides[0].strip(), sides[1].strip());
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return references;
    }
}
