package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 decision 1 item 7 (迁移不再写角色权限：roles、role_versions 不由迁移写入，守护第 8 条扫描迁移文件) and guard rule 8 (迁移扫描);
 * ADR-0034-IMPL card row 0034-G8-03 (迁移文件写 roles、role_versions 数据即失败，注入一条 INSERT 被拦下) and card section 6 (迁移不写
 * roles、role_versions 数据). This is the acceptance side of the scan: every migration file of the application, comments and string
 * literals left out, has no statement that writes rows of either table. The implementation's own guard (card 3.3) may be wider.
 */
class MigrationRoleDataScanTest {

    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /** A data statement whose target is roles or role_versions, schema-qualified or not. */
    static final Pattern WRITES_ROLE_DATA = Pattern.compile(
            "\\b(?:insert\\s+into|update|delete\\s+from|merge\\s+into|copy|truncate(?:\\s+table)?(?:\\s+only)?)\\s+(?:platform_identity\\s*\\.\\s*)?(roles|role_versions)\\b(?!\\s*\\.)",
            Pattern.CASE_INSENSITIVE);

    static String withoutCommentsAndLiterals(String sql) {
        String noBlock = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        String noLine = noBlock.replaceAll("--[^\\n]*", " ");
        return noLine.replaceAll("'(?:[^']|'')*'", "''");
    }

    static List<String> roleDataWrites(String sql) {
        List<String> found = new ArrayList<>();
        Matcher matcher = WRITES_ROLE_DATA.matcher(withoutCommentsAndLiterals(sql));
        while (matcher.find()) {
            found.add(matcher.group().replaceAll("\\s+", " "));
        }
        return found;
    }

    @Test
    @DisplayName("0034-G8-03 迁移扫描：全部迁移文件没有写 roles 或 role_versions 数据的语句（INSERT、UPDATE、DELETE、MERGE、COPY、TRUNCATE）")
    void noMigrationWritesRoleData() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).sorted().toList()) {
                for (String statement : roleDataWrites(Files.readString(file))) {
                    problems.add(MIGRATIONS.relativize(file) + ": " + statement);
                }
            }
        }
        assertThat(problems).as("ADR-0034 decision 1 item 7: system roles come from the declaration and the start-up reconciliation, not from migrations").isEmpty();
    }

    @Test
    @DisplayName("0034-G8-03 注入样例：写 roles、role_versions 的 INSERT / UPDATE / DELETE 被拦下；注释、字符串、触发器与外键里的表名、其他表不被误报")
    void injectedWritesAreCaught() {
        assertThat(roleDataWrites("INSERT INTO platform_identity.roles (role_id, code) VALUES ('019a0000-0000-7000-8000-000000000107', 'x');")).hasSize(1);
        assertThat(roleDataWrites("insert into role_versions (role_version_id) values (gen_random_uuid());")).hasSize(1);
        assertThat(roleDataWrites("UPDATE platform_identity.roles SET kind = 'SYSTEM';")).hasSize(1);
        assertThat(roleDataWrites("delete from platform_identity.role_versions;")).hasSize(1);
        assertThat(roleDataWrites("INSERT INTO platform_identity.roles\n    (role_id) SELECT 1;")).hasSize(1);

        assertThat(roleDataWrites("-- INSERT INTO platform_identity.roles\n/* update roles set x = 1 */ select 1;")).isEmpty();
        assertThat(roleDataWrites("COMMENT ON TABLE platform_identity.roles IS 'insert into roles happens at start-up';")).isEmpty();
        assertThat(roleDataWrites("CREATE TRIGGER t BEFORE UPDATE ON platform_identity.roles FOR EACH ROW EXECUTE FUNCTION f();")).isEmpty();
        assertThat(roleDataWrites("CREATE TRIGGER t BEFORE UPDATE OR DELETE ON platform_identity.role_versions FOR EACH ROW EXECUTE FUNCTION f();")).isEmpty();
        assertThat(roleDataWrites("CREATE TRIGGER t BEFORE TRUNCATE ON platform_identity.role_versions FOR EACH STATEMENT EXECUTE FUNCTION f();")).isEmpty();
        assertThat(roleDataWrites("role_id uuid REFERENCES platform_identity.roles (role_id) ON DELETE RESTRICT")).isEmpty();
        assertThat(roleDataWrites("INSERT INTO platform_identity.navigation_items (item_id) VALUES (1);")).isEmpty();
        assertThat(roleDataWrites("INSERT INTO platform_identity.role_versions_archive (x) VALUES (1);")).isEmpty();
    }
}
