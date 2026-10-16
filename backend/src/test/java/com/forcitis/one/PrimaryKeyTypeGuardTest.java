package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0033 guard 1: every table of ours has a one-column uuid primary key that the application makes (IdGenerator), never a sequence, an identity column
 * or a database default; a natural or composite key is a named unique constraint. A table with a validity interval has an exclusion constraint on its
 * natural key and the interval; a column named *_version_id points at the primary key of a version row, by foreign key inside its schema and by registration in architecture/cross-schema-version-references.txt across schemas. Only the five tables of architecture/primary-key-exemptions.txt,
 * whose key a framework owns, are left out.
 */
class PrimaryKeyTypeGuardTest {
    static final Pattern GENERATED = Pattern.compile("(?i)\\bGENERATED\\b.*\\bAS IDENTITY\\b|\\b(big|small)?serial\\b|\\bDEFAULT\\b");

    static List<String> problems(Map<String, String> migrations, Set<String> exempt, Map<String, String> crossSchema) {
        List<String> problems = new ArrayList<>();
        Map<String, DdlNamingTest.Schema> model = DdlNamingTest.model(migrations);
        Set<String> tables = new TreeSet<>();
        model.forEach((schema, m) -> m.tables.forEach((table, t) -> {
            String qualified = schema + "." + table;
            tables.add(qualified);
            if (exempt.contains(qualified)) {
                return;
            }
            if (t.primaryKey.isEmpty()) {
                problems.add(qualified + ": no primary key; add a uuid key made with IdGenerator");
                return;
            }
            DdlNamingTest.Column key = t.primaryKey.size() == 1 ? t.columns.get(t.primaryKey.getFirst()) : null;
            if (key == null || !key.type().equals("uuid")) {
                problems.add(qualified + ": the primary key is not one uuid column; add a uuid key made with IdGenerator and make the old key a named unique constraint");
            }
        }));
        exempt.stream().filter(e -> !tables.contains(e))
                .forEach(e -> problems.add(e + ": listed in architecture/primary-key-exemptions.txt but the table is gone; delete the line"));

        migrations.values().forEach(sql -> {
            Matcher table = DdlNamingTest.CREATE_TABLE.matcher(sql);
            while (table.find()) {
                String qualified = table.group(1) + "." + table.group(2);
                if (exempt.contains(qualified)) {
                    continue;
                }
                checkLines(qualified, table.group(3), model.get(table.group(1)).tables.get(table.group(2)), crossSchema, problems);
            }
        });
        registryProblems(model, crossSchema, problems);
        return problems;
    }

    private static void registryProblems(Map<String, DdlNamingTest.Schema> model, Map<String, String> crossSchema, List<String> problems) {
        String file = "architecture/cross-schema-version-references.txt";
        crossSchema.forEach((source, target) -> {
            String[] s = source.split("\\.");
            String[] t = target.split("\\.");
            if (s.length != 3 || t.length != 2) {
                problems.add(source + " -> " + target + ": expected <schema.table.column> -> <schema.table>");
                return;
            }
            if (!s[2].endsWith("_version_id")) {
                problems.add(source + ": listed in " + file + " but the column name does not end with _version_id; delete the line");
            }
            DdlNamingTest.Table sourceTable = model.containsKey(s[0]) ? model.get(s[0]).tables.get(s[1]) : null;
            if (sourceTable == null || !sourceTable.columns.containsKey(s[2])) {
                problems.add(source + ": listed in " + file + " but the table or column is gone; delete the line");
            }
            if (s[0].equals(t[0])) {
                problems.add(source + ": listed in " + file + " but the target is in the same schema; a same-schema *_version_id has a foreign key, delete the line");
            }
            DdlNamingTest.Table targetTable = model.containsKey(t[0]) ? model.get(t[0]).tables.get(t[1]) : null;
            DdlNamingTest.Column key = targetTable == null || targetTable.primaryKey.size() != 1 ? null : targetTable.columns.get(targetTable.primaryKey.getFirst());
            if (key == null || !key.type().equals("uuid")) {
                problems.add(source + ": the target " + target + " does not exist or its primary key is not one uuid column");
            }
        });
    }

    private static void checkLines(String table, String body, DdlNamingTest.Table model, Map<String, String> crossSchema, List<String> problems) {
        boolean from = false;
        boolean to = false;
        boolean exclusion = false;
        for (String raw : body.split(",\\s*\\n")) {
            String line = raw.trim().replaceAll("\\s+", " ");
            String name = line.split(" ")[0].toLowerCase(Locale.ROOT);
            if (DdlNamingTest.KEYWORDS.contains(name) || line.matches("(?i)(CONSTRAINT [a-z0-9_]+ )?(UNIQUE|EXCLUDE|CHECK|FOREIGN|PRIMARY)\\b.*")) {
                exclusion |= line.matches("(?i)(CONSTRAINT [a-z0-9_]+ )?EXCLUDE\\b.*");
                continue;
            }
            boolean isKey = model.primaryKey.contains(name) || name.equals("id") || name.endsWith("_id");
            if (isKey && GENERATED.matcher(line).find()) {
                problems.add(table + "." + name + ": a primary key or *_id column is made by a sequence, an identity or a default; the application makes it with IdGenerator");
            }
            if (name.endsWith("_version_id") && !model.primaryKey.contains(name)) {
                versionReference(table, name, line, body, crossSchema, problems);
            }
            from |= name.startsWith("effective_from_");
            to |= name.startsWith("effective_to_");
        }
        if (from && to && !exclusion) {
            problems.add(table + ": has an effective_from_ and an effective_to_ column but no exclusion constraint on its natural key and the interval");
        }
    }

    private static final String REFERENCES = "REFERENCES\\s+([a-z0-9_]+)(?:\\.([a-z0-9_]+))?";

    private static void versionReference(String table, String column, String line, String body, Map<String, String> crossSchema, List<String> problems) {
        String schema = table.substring(0, table.indexOf('.'));
        Matcher reference = Pattern.compile(REFERENCES, Pattern.CASE_INSENSITIVE).matcher(line);
        if (!reference.find()) {
            reference = Pattern.compile("FOREIGN KEY\\s*\\(" + column + "\\)\\s*" + REFERENCES, Pattern.CASE_INSENSITIVE).matcher(body);
            if (!reference.find()) {
                reference = null;
            }
        }
        boolean registered = crossSchema.containsKey(table + "." + column);
        if (reference == null) {
            if (!registered) {
                problems.add(table + "." + column + ": a *_version_id column must reference the primary key of a version row; add a foreign key in the same schema,"
                        + " or for another schema register it in architecture/cross-schema-version-references.txt");
            }
            return;
        }
        String targetSchema = reference.group(2) == null ? schema : reference.group(1).toLowerCase(Locale.ROOT);
        if (!targetSchema.equals(schema)) {
            problems.add(table + "." + column + ": a *_version_id across schemas must not have a foreign key; register it in architecture/cross-schema-version-references.txt instead");
        }
        if (registered) {
            problems.add(table + "." + column + ": registered in architecture/cross-schema-version-references.txt but has a foreign key; a cross-schema reference is by ID only (D2)");
        }
    }

    @Test
    @DisplayName("every table has one uuid primary key made by the application, intervals are excluded from overlapping, *_version_id points at a version row; only the five framework tables are exempt")
    void everyKeyIsOneUuidColumnTheApplicationMakes() throws IOException {
        Map<String, String> exemptions = PrimaryKeyExemptions.read();

        assertThat(exemptions.keySet()).as("ADR-0033 section 1 item 8 allows these five framework tables and no other")
                .containsExactlyInAnyOrder("platform_identity.sessions", "platform_identity.sessions_attributes", "platform_integration.event_publication",
                        "platform_integration.event_publication_archive", "platform_scheduling.scheduler_locks");
        assertThat(problems(DdlNamingTest.readMigrations(), exemptions.keySet(), CrossSchemaVersionReferences.read())).isEmpty();
    }

    private static final String SAMPLE = """
            CREATE TABLE platform_sample.things (
                thing_id uuid NOT NULL,
                owner_id uuid NOT NULL,
                effective_from_date date NOT NULL,
                effective_to_date date,
                CONSTRAINT pk_things PRIMARY KEY (thing_id),
                CONSTRAINT ex_things_overlap EXCLUDE USING gist (owner_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
            );
            """;

    private static List<String> sample(String sql) {
        return problems(Map.of("V1__x.sql", sql), Set.of(), Map.of());
    }

    @Test
    @DisplayName("injected: a table without a key, a composite key, a text key, an identity or default key, an interval without exclusion, a stale exemption and a lone *_version_id are caught; the sample is clean")
    void theGuardCatchesInjectedSamples() {
        assertThat(sample(SAMPLE)).isEmpty();

        assertThat(sample(SAMPLE.replace("    CONSTRAINT pk_things PRIMARY KEY (thing_id),\n", ""))).anyMatch(p -> p.contains("no primary key"));
        assertThat(sample(SAMPLE.replace("PRIMARY KEY (thing_id)", "PRIMARY KEY (thing_id, owner_id)"))).anyMatch(p -> p.contains("not one uuid column"));
        assertThat(sample(SAMPLE.replace("thing_id uuid NOT NULL", "thing_id text NOT NULL"))).anyMatch(p -> p.contains("not one uuid column"));
        assertThat(sample(SAMPLE.replace("thing_id uuid NOT NULL", "thing_id bigint GENERATED ALWAYS AS IDENTITY"))).anyMatch(p -> p.contains("thing_id: a primary key or *_id column is made by"));
        assertThat(sample(SAMPLE.replace("thing_id uuid NOT NULL", "thing_id uuid NOT NULL DEFAULT gen_random_uuid()"))).anyMatch(p -> p.contains("thing_id: a primary key or *_id column is made by"));
        assertThat(sample(SAMPLE.replace("owner_id uuid NOT NULL", "owner_id bigserial"))).anyMatch(p -> p.contains("owner_id: a primary key or *_id column is made by"));
        assertThat(sample(SAMPLE.replace(",\n    CONSTRAINT ex_things_overlap EXCLUDE USING gist (owner_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)", "")))
                .anyMatch(p -> p.contains("no exclusion constraint"));
        assertThat(sample(SAMPLE.replace("owner_id uuid NOT NULL,", "owner_id uuid NOT NULL,\n    thing_version_id uuid,")))
                .anyMatch(p -> p.contains("thing_version_id: a *_version_id column must reference"));
        assertThat(sample(SAMPLE.replace("owner_id uuid NOT NULL,", "owner_id uuid NOT NULL,\n    thing_version_id uuid REFERENCES platform_sample.thing_versions (thing_version_id),")))
                .noneMatch(p -> p.contains("_version_id"));
        assertThat(problems(Map.of("V1__x.sql", SAMPLE), Set.of("platform_sample.gone"), Map.of())).anyMatch(p -> p.startsWith("platform_sample.gone: listed in"));
        assertThat(problems(Map.of("V1__x.sql", SAMPLE.replace("thing_id uuid NOT NULL", "thing_id text NOT NULL")), Set.of("platform_sample.things"), Map.of())).isEmpty();
    }

    private static final String VERSIONS = """
            CREATE TABLE platform_other.thing_versions (
                thing_version_id uuid NOT NULL,
                CONSTRAINT pk_thing_versions PRIMARY KEY (thing_version_id)
            );
            """;
    private static final String CROSS = SAMPLE.replace("owner_id uuid NOT NULL,", "owner_id uuid NOT NULL,\n    thing_version_id uuid,");
    private static final String SOURCE = "platform_sample.things.thing_version_id";

    private static List<String> cross(String sample, Map<String, String> registry) {
        return problems(Map.of("V1__x.sql", sample, "V2__y.sql", VERSIONS), Set.of(), registry);
    }

    @Test
    @DisplayName("injected: a cross-schema *_version_id is caught when unregistered or given a foreign key, a registered one passes, a registry line with a missing target, the same schema or a gone column is caught")
    void theGuardCatchesCrossSchemaVersionReferenceSamples() {
        Map<String, String> registered = Map.of(SOURCE, "platform_other.thing_versions");

        assertThat(cross(CROSS, Map.of())).anyMatch(p -> p.contains("thing_version_id: a *_version_id column must reference")
                && p.contains("register it in architecture/cross-schema-version-references.txt"));
        assertThat(cross(CROSS, registered)).isEmpty();
        assertThat(cross(CROSS, Map.of(SOURCE, "platform_other.nothing"))).anyMatch(p -> p.contains("does not exist or its primary key is not one uuid column"));
        assertThat(cross(CROSS.replace("thing_version_id uuid,", "thing_version_id uuid REFERENCES platform_other.thing_versions (thing_version_id),"), Map.of()))
                .anyMatch(p -> p.contains("across schemas must not have a foreign key"));
        assertThat(cross(CROSS.replace("thing_version_id uuid,", "thing_version_id uuid REFERENCES platform_other.thing_versions (thing_version_id),"), registered))
                .anyMatch(p -> p.contains("registered in architecture/cross-schema-version-references.txt but has a foreign key"));
        assertThat(cross(CROSS, Map.of(SOURCE, "platform_sample.things"))).anyMatch(p -> p.contains("target is in the same schema"));
        assertThat(cross(SAMPLE, registered)).anyMatch(p -> p.contains("the table or column is gone; delete the line"));
        assertThat(cross(CROSS, Map.of("platform_sample.things.owner_id", "platform_other.thing_versions"))).anyMatch(p -> p.contains("does not end with _version_id"));
    }
}
