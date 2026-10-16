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
 * natural key and the interval; a column named *_version_id points at the primary key of a version row. Only the five tables of architecture/primary-key-exemptions.txt,
 * whose key a framework owns, are left out.
 */
class PrimaryKeyTypeGuardTest {
    static final Pattern GENERATED = Pattern.compile("(?i)\\bGENERATED\\b.*\\bAS IDENTITY\\b|\\b(big|small)?serial\\b|\\bDEFAULT\\b");

    static List<String> problems(Map<String, String> migrations, Set<String> exempt) {
        return problems(migrations, exempt.stream().collect(java.util.stream.Collectors.toMap(t -> t, t -> "framework", (a, b) -> a)));
    }

    /** exemptions: table to reason; a reason that names an ADR registers a partitioned table (ADR-0048), any other reason a framework-owned key. */
    static List<String> problems(Map<String, String> migrations, Map<String, String> exemptions) {
        Set<String> exempt = exemptions.keySet();
        List<String> problems = new ArrayList<>();
        Map<String, DdlNamingTest.Schema> model = DdlNamingTest.model(migrations);
        Set<String> tables = new TreeSet<>();
        model.forEach((schema, m) -> m.tables.forEach((table, t) -> {
            String qualified = schema + "." + table;
            tables.add(qualified);
            boolean registered = exempt.contains(qualified) && PrimaryKeyExemptions.isPartitionException(exemptions.get(qualified));
            if (t.partitionKey != null && !registered) {
                problems.add(qualified + ": a partitioned table (PARTITION BY) must be listed in architecture/primary-key-exemptions.txt with the ADR that approved it (ADR-0048 decision 6)");
            }
            if (registered) {
                partitionShape(qualified, t, problems);
                return;
            }
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
                if (exempt.contains(qualified) && !PrimaryKeyExemptions.isPartitionException(exemptions.get(qualified))) {
                    continue;
                }
                checkLines(qualified, table.group(3), model.get(table.group(1)).tables.get(table.group(2)), problems);
            }
        });
        return problems;
    }

    /** ADR-0048 decision 5: the key is exactly (id uuid, created_at timestamptz) and created_at is the range partition key. */
    private static void partitionShape(String table, DdlNamingTest.Table t, List<String> problems) {
        if (t.partitionKey == null) {
            problems.add(table + ": registered as a partitioned table in architecture/primary-key-exemptions.txt but is not PARTITION BY RANGE");
        }
        boolean keyOk = t.primaryKey.equals(List.of("id", "created_at"))
                && t.columns.get("id") != null && t.columns.get("id").type().equals("uuid")
                && t.columns.get("created_at") != null && t.columns.get("created_at").type().equals("timestamptz");
        if (!keyOk) {
            problems.add(table + ": the primary key of the partitioned table must be exactly (id uuid, created_at timestamptz)");
        }
        if (t.partitionKey != null && !t.partitionKey.equals("created_at")) {
            problems.add(table + ": created_at must be the range partition key, found " + t.partitionKey);
        }
    }

    private static void checkLines(String table, String body, DdlNamingTest.Table model, List<String> problems) {
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
            if (name.endsWith("_version_id") && !model.primaryKey.contains(name) && !line.toUpperCase(Locale.ROOT).contains("REFERENCES")
                    && !body.toUpperCase(Locale.ROOT).matches("(?s).*FOREIGN KEY \\(" + name.toUpperCase(Locale.ROOT) + "\\).*")) {
                problems.add(table + "." + name + ": a *_version_id column must reference the primary key of a version row");
            }
            from |= name.startsWith("effective_from_");
            to |= name.startsWith("effective_to_");
        }
        if (from && to && !exclusion) {
            problems.add(table + ": has an effective_from_ and an effective_to_ column but no exclusion constraint on its natural key and the interval");
        }
    }

    @Test
    @DisplayName("every table has one uuid primary key made by the application, intervals are excluded from overlapping, *_version_id points at a version row; only the five framework tables and the ADR-0048 partitioned table are exempt")
    void everyKeyIsOneUuidColumnTheApplicationMakes() throws IOException {
        Map<String, String> exemptions = PrimaryKeyExemptions.read();

        assertThat(exemptions.keySet()).as("ADR-0033 section 1 item 8 allows these five framework tables, ADR-0048 the partitioned audit_entries, and no other")
                .containsExactlyInAnyOrder("platform_identity.sessions", "platform_identity.sessions_attributes", "platform_integration.event_publication",
                        "platform_integration.event_publication_archive", "platform_scheduling.scheduler_locks", "platform_audit.audit_entries");
        assertThat(exemptions.get("platform_audit.audit_entries")).contains("ADR-0048");
        assertThat(problems(DdlNamingTest.readMigrations(), exemptions)).isEmpty();
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
        return problems(Map.of("V1__x.sql", sql), Set.of());
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
        assertThat(problems(Map.of("V1__x.sql", SAMPLE), Set.of("platform_sample.gone"))).anyMatch(p -> p.startsWith("platform_sample.gone: listed in"));
        assertThat(problems(Map.of("V1__x.sql", SAMPLE.replace("thing_id uuid NOT NULL", "thing_id text NOT NULL")), Set.of("platform_sample.things"))).isEmpty();
    }

    private static final String PARTITIONED = """
            CREATE TABLE platform_sample.entries (
                id uuid NOT NULL,
                created_at timestamptz NOT NULL,
                CONSTRAINT pk_entries PRIMARY KEY (id, created_at)
            )
            PARTITION BY RANGE (created_at);
            """;

    @Test
    @DisplayName("ADR-0048 guard 1 and 2: a partitioned table must be registered with an ADR and its key must be exactly (id uuid, created_at timestamptz) with created_at as range key")
    void thePartitionedTableIsRegisteredAndKeepsItsShape() {
        Map<String, String> registered = Map.of("platform_sample.entries", "partitioned; ADR-0048");
        assertThat(problems(Map.of("V1__x.sql", PARTITIONED), registered)).isEmpty();

        assertThat(problems(Map.of("V1__x.sql", PARTITIONED), Set.of())).anyMatch(p -> p.contains("must be listed in architecture/primary-key-exemptions.txt"));
        assertThat(problems(Map.of("V1__x.sql", PARTITIONED), Map.of("platform_sample.entries", "framework table"))).anyMatch(p -> p.contains("must be listed"));
        assertThat(problems(Map.of("V1__x.sql", PARTITIONED.replace("(id, created_at)", "(id)")), registered)).anyMatch(p -> p.contains("must be exactly (id uuid, created_at timestamptz)"));
        assertThat(problems(Map.of("V1__x.sql", PARTITIONED.replace("(id, created_at)", "(created_at, id)")), registered)).anyMatch(p -> p.contains("must be exactly"));
        assertThat(problems(Map.of("V1__x.sql", PARTITIONED.replace("id uuid", "id text")), registered)).anyMatch(p -> p.contains("must be exactly"));
        assertThat(problems(Map.of("V1__x.sql", PARTITIONED.replace("created_at timestamptz NOT NULL,", "created_at timestamptz NOT NULL,\n    other_at timestamptz,").replace("RANGE (created_at)", "RANGE (other_at)")), registered))
                .anyMatch(p -> p.contains("created_at must be the range partition key"));
        assertThat(problems(Map.of("V1__x.sql", PARTITIONED.replace("\nPARTITION BY RANGE (created_at)", "")), registered)).anyMatch(p -> p.contains("is not PARTITION BY RANGE"));
    }
}
