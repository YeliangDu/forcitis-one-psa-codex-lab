package com.forcitis.one.acceptance.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-0033 section 2 item 7 (DA-11), ADR-0010 item 5 and the #129 review of legacy numbers: the original number of a migrated document is
 * keyed by its source system, the table only grows, and every row names the migration batch that wrote it. Probed in the module's own
 * schema inside one transaction that is rolled back; the insert names only the columns the decision fixes and lets the primary key and
 * the audit timestamps be filled from the catalogue.
 */
class LegacyNumberSourceIT extends AcceptanceSupport {

    static final String ACCEPTED = "accepted";
    static final String UNIQUE_VIOLATION = "23505";
    static final String CHECK_VIOLATION = "23514";
    static final String NOT_NULL_VIOLATION = "23502";
    static final UUID BATCH = UUID.fromString("01900000-0000-7000-8000-0000000000b1");

    static final TestDatabase postgres = TestPostgres.database(LegacyNumberSourceIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private Connection numbering;
    private String primaryKey;
    private final List<String> timestamps = new ArrayList<>();

    @BeforeEach
    void openTheNumberingSchema() throws Exception {
        numbering = schemaConnection("platform_numbering");
        try (PreparedStatement key = numbering.prepareStatement("""
                select a.attname from pg_index i join pg_attribute a on a.attrelid = i.indrelid and a.attnum = any (i.indkey)
                 where i.indrelid = cast('legacy_numbers' as regclass) and i.indisprimary
                """); ResultSet rs = key.executeQuery()) {
            assertThat(rs.next()).as("legacy_numbers has a primary key").isTrue();
            primaryKey = rs.getString(1);
        }
        try (PreparedStatement columns = numbering.prepareStatement("""
                select column_name from information_schema.columns
                 where table_schema = current_schema() and table_name = 'legacy_numbers' and column_name in ('created_at', 'updated_at')
                """); ResultSet rs = columns.executeQuery()) {
            while (rs.next()) {
                timestamps.add(rs.getString(1));
            }
        }
        numbering.setAutoCommit(false);
    }

    @AfterEach
    void rollBack() throws Exception {
        if (numbering != null) {
            numbering.rollback();
            numbering.close();
        }
    }

    private Map<String, Object> entry(String sourceSystem, String legacyNumber, UUID batch) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source_system", sourceSystem);
        row.put("document_type_id", UUID.fromString("019a0000-0000-7000-8000-000000000401"));
        row.put("legacy_number", legacyNumber);
        row.put("document_id", UUID.randomUUID());
        row.put("migration_batch_id", batch);
        return row;
    }

    private String insert(Map<String, Object> row, boolean keep) throws SQLException {
        List<String> names = new ArrayList<>(row.keySet());
        StringBuilder sql = new StringBuilder("insert into legacy_numbers (").append(primaryKey);
        names.forEach(name -> sql.append(", ").append(name));
        timestamps.forEach(name -> sql.append(", ").append(name));
        sql.append(") values (uuidv7()");
        names.forEach(name -> sql.append(", ?"));
        timestamps.forEach(name -> sql.append(", now()"));
        sql.append(')');
        Savepoint before = numbering.setSavepoint();
        try (PreparedStatement insert = numbering.prepareStatement(sql.toString())) {
            for (int i = 0; i < names.size(); i++) {
                insert.setObject(i + 1, row.get(names.get(i)));
            }
            insert.executeUpdate();
            if (!keep) {
                numbering.rollback(before);
            }
            return ACCEPTED;
        } catch (SQLException refused) {
            numbering.rollback(before);
            return refused.getSQLState();
        }
    }

    private String change(String statement) throws SQLException {
        Savepoint before = numbering.setSavepoint();
        try (PreparedStatement change = numbering.prepareStatement(statement)) {
            change.executeUpdate();
            numbering.rollback(before);
            return ACCEPTED;
        } catch (SQLException refused) {
            numbering.rollback(before);
            return refused.getSQLState();
        }
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 7 条（DA-11）、#129 G-MG1：原编号为 uuid 主键，带来源系统列，取值由 CHECK 约束；迁入批次为必填的 uuid、不建外键；旧的导入批次列已去掉")
    void columns() throws Exception {
        Map<String, String> columns = new LinkedHashMap<>();
        try (PreparedStatement select = numbering.prepareStatement("""
                select column_name, data_type || ' ' || is_nullable from information_schema.columns
                 where table_schema = current_schema() and table_name = 'legacy_numbers'
                """); ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                columns.put(rs.getString(1), rs.getString(2));
            }
        }
        assertThat(columns.get(primaryKey)).as("G-MG1 a UUIDv7 primary key").isEqualTo("uuid NO");
        assertThat(columns).containsEntry("source_system", "text NO").containsEntry("migration_batch_id", "uuid NO")
                .containsEntry("document_id", "uuid NO").doesNotContainKey("import_batch");
        try (PreparedStatement foreignKeys = numbering.prepareStatement("""
                select count(*) from pg_constraint c join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any (c.conkey)
                 where c.conrelid = cast('legacy_numbers' as regclass) and c.contype = 'f' and a.attname = 'migration_batch_id'
                """); ResultSet rs = foreignKeys.executeQuery()) {
            rs.next();
            assertThat(rs.getLong(1)).as("the migration batch lives in another schema: referenced by id only").isZero();
        }

        assertThat(insert(entry("LEGACY_SYSTEM", "Q-2019-0001", BATCH), false)).isEqualTo(ACCEPTED);
        assertThat(insert(entry("BOGUS", "Q-2019-0002", BATCH), false)).isEqualTo(CHECK_VIOLATION);
        assertThat(insert(entry("LEGACY_SYSTEM", "Q-2019-0003", null), false)).as("a legacy number without its migration batch").isEqualTo(NOT_NULL_VIOLATION);
        Map<String, Object> withoutSource = entry("LEGACY_SYSTEM", "Q-2019-0004", BATCH);
        withoutSource.put("source_system", null);
        assertThat(insert(withoutSource, false)).isEqualTo(NOT_NULL_VIOLATION);
    }

    @Test
    @DisplayName("R-1b P3a ADR-0033 第 2 项第 7 条（DA-11）：同一 (来源系统, 单据类型, 原编号) 唯一，由具名唯一约束 uk_legacy_numbers_source_system_document_type_id_legacy_number 保证；不同来源系统可有相同原编号")
    void uniquePerSourceSystem() throws Exception {
        assertThat(insert(entry("LEGACY_SYSTEM", "Q-2020-0001", BATCH), true)).isEqualTo(ACCEPTED);

        assertThat(insert(entry("LEGACY_SYSTEM", "Q-2020-0001", BATCH), false)).isEqualTo(UNIQUE_VIOLATION);
        assertThat(insert(entry("ACCOUNTING_SYSTEM", "Q-2020-0001", BATCH), false)).as("the same number from another source system").isEqualTo(ACCEPTED);

        List<String> keyColumns = new ArrayList<>();
        try (PreparedStatement select = numbering.prepareStatement("""
                select a.attname from pg_constraint c cross join lateral unnest(c.conkey) with ordinality k(attnum, position)
                  join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum
                 where c.conrelid = cast('legacy_numbers' as regclass) and c.contype = 'u'
                   and c.conname = 'uk_legacy_numbers_source_system_document_type_id_legacy_number'
                 order by k.position
                """); ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                keyColumns.add(rs.getString(1));
            }
        }
        assertThat(keyColumns).containsExactly("source_system", "document_type_id", "legacy_number");
    }

    @Test
    @DisplayName("R-1b P3a ADR-0010 第 5 条、#129 G-MG1：原编号只追加——更新与删除都被数据库拒绝")
    void appendOnly() throws Exception {
        assertThat(insert(entry("LEGACY_SYSTEM", "Q-2021-0001", BATCH), true)).isEqualTo(ACCEPTED);

        assertThat(change("update legacy_numbers set legacy_number = 'Q-2021-0009' where legacy_number = 'Q-2021-0001'")).isNotEqualTo(ACCEPTED);
        assertThat(change("update legacy_numbers set migration_batch_id = cast('01900000-0000-7000-8000-0000000000b2' as uuid) where legacy_number = 'Q-2021-0001'"))
                .isNotEqualTo(ACCEPTED);
        assertThat(change("delete from legacy_numbers where legacy_number = 'Q-2021-0001'")).isNotEqualTo(ACCEPTED);
    }

    /** Hidden files and directories (a path segment below the root starting with a dot, such as macOS .DS_Store) are not source and need not be UTF-8. */
    private static boolean hidden(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> filesContaining(Path root, java.util.function.Predicate<String> found) throws Exception {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).filter(file -> !hidden(root, file)).filter(file -> {
                try {
                    return found.test(Files.readString(file));
                } catch (java.io.IOException unreadable) {
                    throw new java.io.UncheckedIOException(unreadable);
                }
            }).toList();
        }
    }

    @Test
    @DisplayName("R-1b P3a #129 G-MG6：迁入批次只有一种写法——提议术语 import_batch 已删除，代码、迁移与 OpenAPI 中不再出现导入批次")
    void theOldBatchTermIsGone() throws Exception {
        assertThat(filesContaining(Path.of("glossary"), text -> text.contains("id: import_batch") || text.contains("snake: import_batch")))
                .as("no glossary entry registers import_batch").isEmpty();
        java.util.regex.Pattern oldWording = java.util.regex.Pattern.compile("import_batch|importBatch|ImportBatch");
        List<Path> carrying = new ArrayList<>();
        carrying.addAll(filesContaining(Path.of("src/main"), text -> oldWording.matcher(text).find()));
        carrying.addAll(filesContaining(Path.of("../frontend/packages/api/openapi.json"), text -> oldWording.matcher(text).find()));
        assertThat(carrying).isEmpty();
    }
}
