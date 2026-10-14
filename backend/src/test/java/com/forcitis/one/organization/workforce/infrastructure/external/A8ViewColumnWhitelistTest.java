package com.forcitis.one.organization.workforce.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * backend/architecture/a8-view-columns.yaml is the reviewed whitelist (CODEOWNERS); the start-up check compares the
 * database with the code constant A8ViewSql.VIEW_COLUMNS. This test keeps both equal, and keeps the DBA proposal
 * docs/modernization/phase1a/A8_VIEWS.sql in line with them.
 */
class A8ViewColumnWhitelistTest {

    static final Path YAML = Path.of("architecture/a8-view-columns.yaml");
    static final Path DBA_SCRIPT = Path.of("../docs/modernization/phase1a/A8_VIEWS.sql");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() throws IOException {
        try (InputStream in = Files.newInputStream(YAML)) {
            return new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, String>>> columnsByView() throws IOException {
        Map<String, List<Map<String, String>>> byView = new LinkedHashMap<>();
        ((Map<String, Map<String, Object>>) load().get("views")).forEach((view, body) -> byView.put(view, (List<Map<String, String>>) body.get("columns")));
        return byView;
    }

    @Test
    @DisplayName("§9a the reviewed whitelist file lists exactly the views and columns the code reads")
    void yamlEqualsTheCodeConstant() throws IOException {
        Map<String, List<Map<String, String>>> byView = columnsByView();

        assertThat(load().get("schema")).isEqualTo(A8ViewSql.SCHEMA);
        assertThat(byView.keySet()).containsExactlyInAnyOrderElementsOf(A8ViewSql.VIEW_COLUMNS.keySet());
        byView.forEach((view, columns) -> assertThat(columns.stream().map(c -> c.get("name")).toList())
                .as("columns of %s", view).containsExactlyElementsOf(A8ViewSql.VIEW_COLUMNS.get(view)));
    }

    @Test
    @DisplayName("§9a every whitelisted column names its source table.column and a category (组织 / 身份)")
    void everyColumnHasSourceAndCategory() throws IOException {
        List<String> sources = new ArrayList<>();
        for (List<Map<String, String>> columns : columnsByView().values()) {
            for (Map<String, String> column : columns) {
                assertThat(column.get("source")).as("source of %s", column.get("name")).isNotBlank().matches(".+\\..+");
                assertThat(column.get("category")).as("category of %s", column.get("name")).isIn("组织", "身份");
                sources.add(column.get("source"));
            }
        }
        assertThat(sources).anyMatch(s -> s.startsWith("ORG_PRINCIPAL.LOGIN_NAME"));
    }

    @Test
    @DisplayName("§9a only organisation / identity base tables are named as sources; the one OA form is limited to its 3 agreed columns")
    void sourcesAreOrganisationAndIdentityOnly() throws IOException {
        Set<String> allowedTables = Set.of("ORG_UNIT", "ORG_MEMBER", "ORG_POST", "ORG_PRINCIPAL", "ORG_RELATIONSHIP", "ADDRESSBOOK", "CTP_ENUM_ITEM", "formmain_0307");
        List<String> formColumns = new ArrayList<>();
        for (List<Map<String, String>> columns : columnsByView().values()) {
            for (Map<String, String> column : columns) {
                String table = column.get("source").split("[.（]")[0];
                assertThat(allowedTables).as("source of %s", column.get("name")).contains(table);
                if (table.equals("formmain_0307")) {
                    formColumns.add(column.get("source"));
                }
            }
        }
        assertThat(formColumns).containsExactlyInAnyOrder("formmain_0307.field0234", "formmain_0307.field0284", "formmain_0307.FIELD0244");
    }

    @Test
    @DisplayName("§9a the DBA proposal creates exactly the whitelisted views and columns, grants SELECT on the view schema only and denies the base tables")
    void dbaScriptMatchesTheWhitelist() throws IOException {
        String sql = Files.readString(DBA_SCRIPT);

        for (Map.Entry<String, List<String>> view : A8ViewSql.VIEW_COLUMNS.entrySet()) {
            assertThat(sql).contains("CREATE VIEW " + A8ViewSql.SCHEMA + "." + view.getKey() + " AS");
        }
        assertThat(sql).contains("GRANT SELECT ON SCHEMA::" + A8ViewSql.SCHEMA)
                .contains("DENY SELECT, INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES, VIEW DEFINITION ON SCHEMA::dbo")
                .contains("DENY INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES ON SCHEMA::" + A8ViewSql.SCHEMA);
        assertThat(sql).doesNotContainIgnoringCase("EXT_ATTR_21").as("no birthday in a view");
        assertThat(sql.lines().filter(l -> l.matches("(?i)\\s*(GRANT|ALTER ROLE|EXEC sp_addrolemember).*"))
                .filter(l -> l.matches("(?i).*(db_datareader|db_owner|db_datawriter|CONTROL|INSERT|UPDATE|DELETE).*") && !l.matches("(?i)\\s*DENY.*"))).isEmpty();
        assertThat(sql).as("no real password or login: placeholders only").doesNotContainPattern("(?i)PASSWORD\\s*=\\s*N'(?!<)")
                .contains("{{READER_LOGIN}}").contains("{{READER_USER}}");
    }
}
