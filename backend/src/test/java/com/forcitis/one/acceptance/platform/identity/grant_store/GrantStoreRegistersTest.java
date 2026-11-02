package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * #732 part A, 0034-G9-01 and G10-01 (registers) with DD sign-off section B: the registers and baselines under backend/architecture no longer
 * name account_roles, account_role_grants belongs to the UserAccount aggregate and has platform.identity as its writer, role_versions to Role,
 * and the multi-aggregate exceptions list has no line for the grant or role tables. Comment lines are history and are left out.
 */
class GrantStoreRegistersTest {

    static final Path ARCHITECTURE = Path.of("architecture");
    static final Pattern TABLE_NAME = Pattern.compile("\\baccount_roles\\b");

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> tables() throws IOException {
        try (InputStream in = Files.newInputStream(ARCHITECTURE.resolve("data-access-registry.yaml"))) {
            return (Map<String, Map<String, Object>>) new Yaml().<Map<String, Object>>load(in).get("tables");
        }
    }

    @Test
    @DisplayName("0034-G9-01 / DD 签发 B：登记表没有 account_roles；account_role_grants 属 UserAccount、写入方为 platform.identity；role_versions 属 Role")
    @SuppressWarnings("unchecked")
    void theRegistryFollowsTheGrantStore() throws IOException {
        Map<String, Map<String, Object>> tables = tables();

        assertThat(tables).doesNotContainKey("platform_identity.account_roles");
        assertThat(tables.get("platform_identity.account_role_grants")).containsEntry("aggregate", "UserAccount");
        assertThat((List<Object>) tables.get("platform_identity.account_role_grants").get("writers")).containsExactly("platform.identity");
        assertThat(tables.get("platform_identity.role_versions")).containsEntry("aggregate", "Role");
    }

    @Test
    @DisplayName("0034-G10-01 / DD 签发 B：backend/architecture 下除注释外没有任何一行再提 account_roles（写入登记、DDL 命名基线、数据访问登记）")
    void noRegisterNamesTheDroppedTable() throws IOException {
        List<String> found = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.walk(ARCHITECTURE)) {
            for (Path file : files.filter(Files::isRegularFile).filter(f -> f.toString().matches(".*\\.(txt|yaml|yml|csv)$")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (!line.stripLeading().startsWith("#") && TABLE_NAME.matcher(line).find()) {
                        found.add(file + ": " + line.strip());
                    }
                }
            }
        }
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("0034-G9-01：multi-aggregate-exceptions.txt 没有为授予或角色表加例外行（账号与授予同属 UserAccount，一个事务一个聚合）")
    void noMultiAggregateExceptionForGrants() throws IOException {
        List<String> lines = Files.readAllLines(ARCHITECTURE.resolve("multi-aggregate-exceptions.txt")).stream().filter(l -> !l.startsWith("#")).toList();

        assertThat(lines).noneMatch(line -> line.contains("platform.identity") || line.contains("account_role") || line.contains("role_versions"));
    }
}
