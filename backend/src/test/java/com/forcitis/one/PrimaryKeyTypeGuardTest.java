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

/** B3-M8: a table with a single-column primary key uses a uuid made by the IdGenerator; the tables that were there before the rule are listed. */
class PrimaryKeyTypeGuardTest {
    static final Path BASELINE = Path.of("architecture/primary-key-type-baseline.txt");

    @Test
    @DisplayName("a table with a one-column primary key has a uuid key unless it is in the baseline of tables that predate the rule, and no baseline line is stale")
    void singleColumnKeysAreUuid() throws IOException {
        Set<String> listed = new TreeSet<>();
        for (String line : Files.readAllLines(BASELINE)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                listed.add(line.split("#", 2)[0].trim());
            }
        }
        Set<String> offenders = new TreeSet<>();
        DdlNamingTest.model(DdlNamingTest.readMigrations()).forEach((schema, model) -> model.tables.forEach((table, t) -> {
            String qualified = schema + "." + table;
            if (DdlNamingTest.FRAMEWORK_TABLES.contains(qualified) || t.primaryKey.size() != 1) {
                return;
            }
            DdlNamingTest.Column key = t.columns.get(t.primaryKey.getFirst());
            if (key != null && !key.type().equals("uuid")) {
                offenders.add(qualified);
            }
        }));

        List<String> problems = new ArrayList<>();
        offenders.stream().filter(o -> !listed.contains(o)).forEach(o -> problems.add(o + ": the primary key is not a uuid; make it uuid and generate it with IdGenerator"));
        listed.stream().filter(l -> !offenders.contains(l)).forEach(l -> problems.add(l + ": listed in " + BASELINE + " but its key is a uuid or the table is gone; delete the line"));
        assertThat(problems).isEmpty();
    }
}
