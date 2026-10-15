package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** B3-M8: every table has a one-column uuid primary key made by the IdGenerator (a natural or composite key is a unique constraint); the tables that are not yet converted are listed with where they go. Framework tables are exempt. */
class PrimaryKeyTypeGuardTest {
    static final Path BASELINE = Path.of("architecture/primary-key-type-baseline.txt");

    @Test
    @DisplayName("a table has a one-column uuid primary key unless it is in the baseline of tables still to convert, and no baseline line is stale")
    void everyKeyIsOneUuidColumnAreUuid() throws IOException {
        Set<String> listed = new TreeSet<>();
        for (String line : Files.readAllLines(BASELINE)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                listed.add(line.split("#", 2)[0].trim());
            }
        }
        Set<String> offenders = new TreeSet<>();
        DdlNamingTest.model(DdlNamingTest.readMigrations()).forEach((schema, model) -> model.tables.forEach((table, t) -> {
            String qualified = schema + "." + table;
            if (DdlNamingTest.FRAMEWORK_TABLES.contains(qualified) || t.primaryKey.isEmpty()) {
                return;
            }
            DdlNamingTest.Column key = t.primaryKey.size() == 1 ? t.columns.get(t.primaryKey.getFirst()) : null;
            if (key == null || !key.type().equals("uuid")) {
                offenders.add(qualified);
            }
        }));

        List<String> problems = new ArrayList<>();
        offenders.stream().filter(o -> !listed.contains(o)).forEach(o -> problems.add(o + ": the primary key is not one uuid column; add a uuid key generated with IdGenerator and make the old key a named unique constraint"));
        listed.stream().filter(l -> !offenders.contains(l)).forEach(l -> problems.add(l + ": listed in " + BASELINE + " but its key is a uuid or the table is gone; delete the line"));
        assertThat(problems).isEmpty();
    }
}
