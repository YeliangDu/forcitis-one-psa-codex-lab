package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.guard.ExternalReadRegister.Row;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0037 item 6.3: the A8 login's default schema is the view schema, so an unqualified base-table name would resolve to whatever object of
 * that name sits there. The SQL of the integration layer names every A8 base table as dbo.&lt;table&gt; or [dbo].[table].
 */
class QualifiedBaseTableGuardTest {

    static final Pattern QUALIFIER_BEFORE = Pattern.compile("(?i)(?:dbo|\\[dbo\\])[ \\t]*\\.[ \\t]*\\[?$");
    static final Pattern BARE_IDENTIFIER = Pattern.compile("\\[?\\w+\\]?");

    static Set<String> a8BaseTables(List<Row> rows) {
        return ExternalReadRegister.baseObjectNames(rows.stream().filter(r -> r.database().equals(ExternalReadRegister.A8)).toList());
    }

    /** One string literal per line; a literal that is only a name is a catalog key, not SQL. */
    static List<String> unqualified(String javaSource, Set<String> tables) {
        List<String> found = new ArrayList<>();
        for (String literal : CredentialColumnGuardTest.stringLiterals(javaSource).split("\n")) {
            if (BARE_IDENTIFIER.matcher(literal.trim()).matches()) {
                continue;
            }
            for (String table : tables) {
                Matcher m = Pattern.compile("(?<![\\w.])\\[?" + Pattern.quote(table) + "(?![\\w])", Pattern.CASE_INSENSITIVE).matcher(literal);
                while (m.find()) {
                    if (!QUALIFIER_BEFORE.matcher(literal.substring(Math.max(0, m.start() - 16), m.start())).find()) {
                        found.add(table + " in: " + literal.trim());
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("R-1b-0 集成层读 A8 基表一律写 dbo. 或 [dbo]. 限定名")
    void integrationSqlQualifiesA8BaseTables() throws IOException {
        Set<String> tables = a8BaseTables(ExternalReadRegister.rows());
        assertThat(tables).contains("ORG_UNIT", "ORG_MEMBER");
        List<String> violations = new ArrayList<>();
        for (Path file : CredentialColumnGuardTest.integrationLayer()) {
            unqualified(Files.readString(file), tables).forEach(v -> violations.add(file + ": " + v));
        }

        assertThat(violations).as("unqualified, the name resolves in the login's default schema forcitis_one first").isEmpty();
    }

    @Test
    @DisplayName("R-1b-0 限定名守护自检：不带 dbo 的基表名（含方括号写法）被报出；dbo.、[dbo].、视图名、列名与单独的表名常量不误报")
    void injectedUnqualifiedNamesAreFound() throws IOException {
        Set<String> tables = a8BaseTables(ExternalReadRegister.rows());
        String java = "class Probe {\n"
                + "    static final String A = \"SELECT B.TYPE FROM ORG_UNIT B\";\n"
                + "    static final String C = \"SELECT ID FROM [ORG_MEMBER]\";\n"
                + "    static final String D = \"SELECT B.TYPE FROM dbo.ORG_UNIT B JOIN [dbo].[ORG_MEMBER] M ON M.ORG_DEPARTMENT_ID = B.ID\";\n"
                + "    static final String E = \"SELECT ORG_UNIT_ID, ORG_UNIT_PATH FROM forcitis_one.v_org_unit\";\n"
                + "    static final String F = \"ORG_UNIT\";\n"
                + "    // SELECT TYPE FROM ORG_UNIT in a comment\n"
                + "}\n";

        List<String> found = unqualified(java, tables);

        assertThat(found).hasSize(2).anyMatch(f -> f.startsWith("ORG_UNIT in: SELECT B.TYPE FROM ORG_UNIT B"))
                .anyMatch(f -> f.startsWith("ORG_MEMBER in: SELECT ID FROM [ORG_MEMBER]"));
    }
}
