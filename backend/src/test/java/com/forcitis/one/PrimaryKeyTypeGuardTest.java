package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
 * ADR-0033 guard 1: every table of ours has a one-column uuid primary key that the application makes (IdGenerator), never a sequence, an identity
 * column or a database default; a natural or composite key is a named unique constraint. A table with a validity interval has an exclusion
 * constraint on its natural key and the interval; a column named *_version_id points at the primary key of a version row, by foreign key inside
 * its schema and by registration in architecture/cross-schema-version-references.txt across schemas. Only the five tables of
 * architecture/primary-key-exemptions.txt, whose key a framework owns, are left out.
 */
class PrimaryKeyTypeGuardTest {
    static final Pattern GENERATED = Pattern.compile("(?i)\\bGENERATED\\b.*\\bAS IDENTITY\\b|\\b(big|small)?serial\\b|\\bDEFAULT\\b");

    static List<String> problems(Map<String, String> migrations, Set<String> exempt) {
        return problems(migrations, exempt, Map.of());
    }

    static List<String> problems(Map<String, String> migrations, Map<String, String> exemptions) {
        return problems(migrations, exemptions, Map.of());
    }

    static List<String> problems(Map<String, String> migrations, Set<String> exempt, Map<String, String> crossSchema) {
        return problems(migrations, exempt.stream().collect(java.util.stream.Collectors.toMap(t -> t, t -> "framework", (a, b) -> a)), crossSchema);
    }

    /** exemptions: table to reason; a reason that names an ADR registers a partitioned table (ADR-0048), any other reason a framework-owned key. */
    static List<String> problems(Map<String, String> migrations, Map<String, String> exemptions, Map<String, String> crossSchema) {
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

        Map<String, List<Reference>> foreignKeys = foreignKeys(migrations);
        migrations.values().forEach(sql -> {
            Matcher table = DdlNamingTest.CREATE_TABLE.matcher(sql);
            while (table.find()) {
                String qualified = table.group(1) + "." + table.group(2);
                if (exempt.contains(qualified) && !PrimaryKeyExemptions.isPartitionException(exemptions.get(qualified))) {
                    continue;
                }
                DdlNamingTest.Table kept = model.get(table.group(1)).tables.get(table.group(2));
                if (kept == null) {
                    continue; // a later migration dropped the table: only the schema as it stands is judged
                }
                checkLines(qualified, table.group(3), kept, model, foreignKeys, crossSchema, problems);
            }
        });
        registryProblems(model, crossSchema, problems);
        return problems;
    }

    /** ADR-0048 decision 5: the key is exactly (id uuid, created_at timestamptz) and created_at is the range partition key. */
    private static void partitionShape(String table, DdlNamingTest.Table t, List<String> problems) {
        if (t.partitionKey == null || !"RANGE".equals(t.partitionMethod)) {
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
            if (key != null && !s[2].equals(key.name()) && !s[2].endsWith("_" + key.name())) {
                problems.add(source + ": the column name does not end with the primary key " + key.name() + " of the target " + target + "; the column must carry the key name of the version row it points at");
            }
            if (model.containsKey(s[0])) {
                model.get(s[0]).tables.forEach((name, other) -> {
                    String own = other.primaryKey.size() == 1 ? other.primaryKey.getFirst() : "";
                    if (own.endsWith("_version_id") && (s[2].equals(own) || s[2].endsWith("_" + own))) {
                        problems.add(source + ": " + (name.equals(s[1]) ? "the column is the primary key of its own table" : "schema " + s[0] + " has the version table " + name
                                + " with key " + own + ", so the column needs a foreign key there instead") + "; delete the line");
                    }
                });
            }
        });
    }

    private static void checkLines(String table, String body, DdlNamingTest.Table model, Map<String, DdlNamingTest.Schema> schemas,
            Map<String, List<Reference>> foreignKeys, Map<String, String> crossSchema, List<String> problems) {
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
                versionReference(table, name, schemas, foreignKeys, crossSchema, problems);
            }
            from |= name.startsWith("effective_from_");
            to |= name.startsWith("effective_to_");
        }
        if (from && to && !exclusion) {
            problems.add(table + ": has an effective_from_ and an effective_to_ column but no exclusion constraint on its natural key and the interval");
        }
    }

    private static final String REFERENCES = "REFERENCES\\s+\"?([a-z0-9_]+)\"?(?:\\.\"?([a-z0-9_]+)\"?)?";
    private static final Pattern TABLE_FOREIGN_KEY = Pattern.compile("FOREIGN KEY\\s*\\(([^)]*)\\)\\s*" + REFERENCES, Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_REFERENCE = Pattern.compile(REFERENCES, Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_FOREIGN_KEY = Pattern.compile("ALTER TABLE\\s+(?:ONLY\\s+)?(?:IF EXISTS\\s+)?\"?([a-z0-9_]+)\"?\\.\"?([a-z0-9_]+)\"?\\s+ADD\\s+(?:CONSTRAINT\\s+\"?[a-z0-9_]+\"?\\s+)?"
            + TABLE_FOREIGN_KEY.pattern(), Pattern.CASE_INSENSITIVE);

    private record Reference(String schema, String table, boolean qualified) {
        static Reference of(String sourceSchema, String first, String second) {
            return second == null ? new Reference(sourceSchema, first.toLowerCase(Locale.ROOT), false)
                    : new Reference(first.toLowerCase(Locale.ROOT), second.toLowerCase(Locale.ROOT), true);
        }
    }

    private static void addReference(Map<String, List<Reference>> found, String table, String columns, Reference reference) {
        for (String column : columns.split(",")) {
            found.computeIfAbsent(table + "." + column.strip().replace("\"", "").toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(reference);
        }
    }

    /** Foreign keys per schema.table.column from inline REFERENCES, table-level FOREIGN KEY and ALTER TABLE ... ADD FOREIGN KEY, over the whole migration text. */
    private static Map<String, List<Reference>> foreignKeys(Map<String, String> migrations) {
        Map<String, List<Reference>> found = new HashMap<>();
        for (String sql : migrations.values()) {
            Matcher table = DdlNamingTest.CREATE_TABLE.matcher(sql);
            while (table.find()) {
                String qualified = (table.group(1) + "." + table.group(2)).toLowerCase(Locale.ROOT);
                String schema = table.group(1).toLowerCase(Locale.ROOT);
                Matcher foreignKey = TABLE_FOREIGN_KEY.matcher(table.group(3));
                while (foreignKey.find()) {
                    addReference(found, qualified, foreignKey.group(1), Reference.of(schema, foreignKey.group(2), foreignKey.group(3)));
                }
                for (String raw : table.group(3).split(",\\s*\\n")) {
                    String line = raw.trim().replaceAll("\\s+", " ");
                    Matcher inline = INLINE_REFERENCE.matcher(line);
                    if (!line.matches("(?i)(CONSTRAINT [a-z0-9_]+ )?FOREIGN\\b.*") && inline.find()) {
                        addReference(found, qualified, line.split(" ")[0], Reference.of(schema, inline.group(1), inline.group(2)));
                    }
                }
            }
            Matcher alter = ALTER_FOREIGN_KEY.matcher(sql);
            while (alter.find()) {
                String schema = alter.group(1).toLowerCase(Locale.ROOT);
                addReference(found, schema + "." + alter.group(2).toLowerCase(Locale.ROOT), alter.group(3), Reference.of(schema, alter.group(4), alter.group(5)));
            }
        }
        return found;
    }

    private static void versionReference(String table, String column, Map<String, DdlNamingTest.Schema> model, Map<String, List<Reference>> foreignKeys,
            Map<String, String> crossSchema, List<String> problems) {
        String schema = table.substring(0, table.indexOf('.'));
        List<Reference> references = foreignKeys.getOrDefault(table + "." + column, List.of());
        boolean registered = crossSchema.containsKey(table + "." + column);
        if (references.isEmpty()) {
            if (!registered) {
                problems.add(table + "." + column + ": a *_version_id column must reference the primary key of a version row; add a foreign key in the same schema,"
                        + " or for another schema register it in architecture/cross-schema-version-references.txt");
            }
            return;
        }
        for (Reference reference : references) {
            if (!reference.schema().equals(schema)) {
                problems.add(table + "." + column + ": a *_version_id across schemas must not have a foreign key; register it in architecture/cross-schema-version-references.txt instead");
            } else if (!reference.qualified() && !(model.containsKey(schema) && model.get(schema).tables.containsKey(reference.table()))) {
                problems.add(table + "." + column + ": its REFERENCES names no schema and the table " + reference.table() + " is not in schema " + schema);
            }
        }
        if (registered) {
            problems.add(table + "." + column + ": registered in architecture/cross-schema-version-references.txt but has a foreign key; a cross-schema reference is by ID only (D2)");
        }
    }

    @Test
    @DisplayName("every table has one uuid primary key made by the application, intervals are excluded from overlapping, *_version_id points at a version row or is in the cross-schema registry; only the five framework tables and the ADR-0048 partitioned table are exempt")
    void everyKeyIsOneUuidColumnTheApplicationMakes() throws IOException {
        Map<String, String> exemptions = PrimaryKeyExemptions.read();

        assertThat(exemptions.keySet()).as(GuardMessage.of("CS-3.4 / ADR-0033 section 1 item 8", "backend/architecture/primary-key-exemptions.txt lists a table other than the five framework tables and the ADR-0048 audit_entries",
                "give the table a uuid primary key made with IdGenerator instead of exempting it"))
                .containsExactlyInAnyOrder("platform_identity.sessions", "platform_identity.sessions_attributes", "platform_integration.event_publication",
                        "platform_integration.event_publication_archive", "platform_scheduling.scheduler_locks", "platform_audit.audit_entries");
        assertThat(exemptions.get("platform_audit.audit_entries")).contains("ADR-0048");
        assertThat(problems(DdlNamingTest.readMigrations(), exemptions, CrossSchemaVersionReferences.read()))
                .as(GuardMessage.of("CS-3.4 / ADR-0033 section 1", "a table breaks the primary key rules (one uuid key made by the application, overlap exclusion, *_version_id references, partitioned table key)",
                        "follow the fix named on each line; register an exemption only in backend/architecture/primary-key-exemptions.txt with an ADR, and a cross-schema version reference in backend/architecture/cross-schema-version-references.txt")).isEmpty();
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

    private static final String OWN_VERSIONS = """
            CREATE TABLE platform_sample.thing_versions (
                thing_version_id uuid NOT NULL,
                CONSTRAINT pk_thing_versions PRIMARY KEY (thing_version_id)
            );
            """;
    private static final String TAIL_FK = "thing_version_id uuid,\n    CONSTRAINT fk_things_version FOREIGN KEY (thing_version_id) REFERENCES %s (thing_version_id),";
    private static final String ALTER = "\nALTER TABLE platform_sample.things ADD CONSTRAINT fk_things_version FOREIGN KEY (thing_version_id) REFERENCES %s (thing_version_id);\n";

    private static List<String> same(String sample, String extra) {
        return problems(Map.of("V1__x.sql", sample, "V2__y.sql", OWN_VERSIONS + extra), Set.of(), Map.of());
    }

    @Test
    @DisplayName("injected: a table-level or ALTER foreign key to the same schema passes, a schemaless REFERENCES counts as the same schema and needs the table there, a cross-schema ALTER foreign key is caught")
    void theGuardReadsForeignKeysOutsideTheColumnLine() {
        assertThat(same(CROSS.replace("thing_version_id uuid,", TAIL_FK.formatted("platform_sample.thing_versions")), "")).noneMatch(p -> p.contains("_version_id"));
        assertThat(same(CROSS, ALTER.formatted("platform_sample.thing_versions"))).noneMatch(p -> p.contains("_version_id"));
        assertThat(same(CROSS.replace("thing_version_id uuid,", "thing_version_id uuid REFERENCES \"platform_sample\".\"thing_versions\" (thing_version_id),"), "")).noneMatch(p -> p.contains("_version_id"));
        assertThat(same(CROSS.replace("thing_version_id uuid,", TAIL_FK.formatted("thing_versions")), "")).noneMatch(p -> p.contains("_version_id"));
        assertThat(same(CROSS.replace("thing_version_id uuid,", TAIL_FK.formatted("thing_gone")), "")).anyMatch(p -> p.contains("names no schema and the table thing_gone"));
        assertThat(same(CROSS, ALTER.formatted("thing_gone"))).anyMatch(p -> p.contains("names no schema"));
        assertThat(cross(CROSS.replace("thing_version_id uuid,", TAIL_FK.formatted("platform_other.thing_versions")), Map.of()))
                .anyMatch(p -> p.contains("across schemas must not have a foreign key"));
        assertThat(cross(CROSS + ALTER.formatted("platform_other.thing_versions"), Map.of())).anyMatch(p -> p.contains("across schemas must not have a foreign key"));
        assertThat(cross(CROSS + ALTER.formatted("platform_other.thing_versions"), Map.of(SOURCE, "platform_other.thing_versions")))
                .anyMatch(p -> p.contains("registered in architecture/cross-schema-version-references.txt but has a foreign key"));
    }

    @Test
    @DisplayName("injected: a registry line whose column does not carry the target key name, or which is a version key or has a version table in its own schema, or whose target key is not uuid is caught")
    void theGuardCatchesMisleadingRegistryLines() {
        Map<String, String> registered = Map.of(SOURCE, "platform_other.thing_versions");

        assertThat(cross(CROSS.replace("thing_version_id", "owner_version_id"), Map.of("platform_sample.things.owner_version_id", "platform_other.thing_versions")))
                .anyMatch(p -> p.contains("does not end with the primary key thing_version_id"));
        assertThat(cross(CROSS.replace("thing_version_id", "source_thing_version_id"), Map.of("platform_sample.things.source_thing_version_id", "platform_other.thing_versions")))
                .isEmpty();
        assertThat(problems(Map.of("V1__x.sql", CROSS, "V2__y.sql", VERSIONS, "V3__z.sql", OWN_VERSIONS), Set.of(), registered))
                .anyMatch(p -> p.contains("has the version table thing_versions with key thing_version_id"));
        assertThat(cross(OWN_VERSIONS, Map.of("platform_sample.thing_versions.thing_version_id", "platform_other.thing_versions")))
                .anyMatch(p -> p.contains("the column is the primary key of its own table"));
        assertThat(problems(Map.of("V1__x.sql", CROSS, "V2__y.sql", VERSIONS.replace("thing_version_id uuid", "thing_version_id text")), Set.of(), registered))
                .anyMatch(p -> p.contains("primary key is not one uuid column"));
    }

    @Test
    @DisplayName("injected: the registry file refuses a repeated column, a line without a note, without -> or with two ->, and accepts a well-formed one")
    void theRegistryReaderRefusesMalformedLines() {
        String line = "platform_sample.things.thing_version_id -> platform_other.thing_versions  # note";

        assertThat(CrossSchemaVersionReferences.parse(List.of("# comment", "", line))).containsExactly(Map.entry(SOURCE, "platform_other.thing_versions"));
        assertThatThrownBy(() -> CrossSchemaVersionReferences.parse(List.of(line, line))).isInstanceOf(IllegalStateException.class).hasMessageContaining("listed twice");
        assertThatThrownBy(() -> CrossSchemaVersionReferences.parse(List.of("platform_sample.things.thing_version_id -> platform_other.thing_versions")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("needs <schema.table.column>");
        assertThatThrownBy(() -> CrossSchemaVersionReferences.parse(List.of("platform_sample.things.thing_version_id platform_other.thing_versions  # note")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CrossSchemaVersionReferences.parse(List.of("a.b.c_version_id -> d.e -> f.g  # note"))).isInstanceOf(IllegalStateException.class);
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
        String listed = PARTITIONED.replace("RANGE (created_at)", "LIST (created_at)");
        assertThat(problems(Map.of("V1__x.sql", listed), registered)).anyMatch(p -> p.contains("is not PARTITION BY RANGE"));
        assertThat(problems(Map.of("V1__x.sql", listed), Set.of())).as("a LIST table is seen as partitioned, so it is not waved through as an ordinary table").anyMatch(p -> p.contains("must be listed in architecture/primary-key-exemptions.txt"));
    }
}
