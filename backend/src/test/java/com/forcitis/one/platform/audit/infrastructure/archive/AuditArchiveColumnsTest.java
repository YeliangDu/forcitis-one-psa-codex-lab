package com.forcitis.one.platform.audit.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditArchiveColumnsTest {
    @Test
    @DisplayName("N9 the columns the job archives are exactly the ARCHIVE lines of architecture/archive-columns.txt")
    void jobAndListAgree() throws IOException {
        List<String> archived = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("architecture/archive-columns.txt"))) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] parts = line.split("\\s*\\|\\s*", 4);
                assertThat(parts).as(line).hasSize(4);
                assertThat(parts[1]).as(line).isIn("ARCHIVE", "EXCLUDE");
                if (parts[1].equals("ARCHIVE")) {
                    archived.add(parts[0]);
                }
            }
        }
        assertThat(AuditArchiveJob.COLUMNS).containsExactlyInAnyOrderElementsOf(archived);
    }
}
