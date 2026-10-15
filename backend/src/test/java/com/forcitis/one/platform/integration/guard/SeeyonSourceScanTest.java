package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.guard.ExternalReadRegister.Row;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * src/main and the Flyway scripts never name A8 form tables, the A8 database, LEXMIS or U8C tables; registered dbo objects are named only by the
 * integration layer, and every dbo object and view the integration layer's SQL names is in the external read register (ADR-0037 item 6.1).
 */
class SeeyonSourceScanTest {

    static final Path MAIN = Path.of("src/main");
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    static final Pattern FORBIDDEN = Pattern.compile("formmain_|formson_|a8_db|LEXMIS|T_U8C_", Pattern.CASE_INSENSITIVE);

    /** Variable names, not database objects; everything else that spells a8_db stays forbidden. */
    static final List<String> ENVIRONMENT_VARIABLES = List.of("A8_DB_URL", "A8_DB_USERNAME", "A8_DB_PASSWORD");

    /** Adapter configuration: the view name is a setting of the adapter it configures (outside the Java of the integration layer). */
    static final Set<Path> ADAPTER_CONFIGURATION = Set.of(Path.of("src/main/resources/config/platform_integration.properties"),
            Path.of("src/main/resources/config/platform_approval.properties"));

    static final Pattern DBO_OBJECT = Pattern.compile("(?<![\\w\\]])(?:\\[dbo\\]|dbo)[ \\t]*\\.[ \\t]*\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);
    static final Pattern VIEW_SCHEMA_OBJECT = Pattern.compile("(?<![\\w\\]])\\[?forcitis_one\\]?[ \\t]*\\.[ \\t]*\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);
    static final Pattern BARE_VIEW_LITERAL = Pattern.compile("^v_\\w+$");

    static List<String> registeredNames() throws IOException {
        List<String> names = new ArrayList<>(ENVIRONMENT_VARIABLES);
        for (String object : ExternalReadRegister.objects(ExternalReadRegister.rows())) {
            names.add(object);
            names.add(object.substring(object.indexOf('.') + 1));
        }
        return names;
    }

    static List<String> violations(Path root, List<String> allowedNames) {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String[] lines;
                try {
                    lines = Files.readString(file).split("\n", -1);
                } catch (IOException binary) {
                    continue;
                }
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    for (String allowed : allowedNames.stream().sorted((a, b) -> b.length() - a.length()).toList()) {
                        line = line.replace(allowed, "");
                    }
                    if (FORBIDDEN.matcher(line).find()) {
                        hits.add(file + ":" + (i + 1) + ": " + lines[i].trim());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hits;
    }

    /** Lines outside the integration layer that name dbo.&lt;a registered base object&gt;. */
    static List<String> registeredBaseObjectsOutside(Path root, Set<Path> layer, Set<String> baseObjects) throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                if (layer.contains(file) || ADAPTER_CONFIGURATION.contains(file)) {
                    continue;
                }
                String[] lines;
                try {
                    lines = Files.readString(file).split("\n", -1);
                } catch (IOException binary) {
                    continue;
                }
                for (int i = 0; i < lines.length; i++) {
                    Matcher m = DBO_OBJECT.matcher(lines[i]);
                    while (m.find()) {
                        if (baseObjects.contains(m.group(1))) {
                            hits.add(file + ":" + (i + 1) + ": dbo." + m.group(1));
                        }
                    }
                }
            }
        }
        return hits;
    }

    /** dbo objects and view-schema objects named in SQL text, plus string literals that are a bare view name. */
    static Set<String> namedObjects(String javaSource) {
        Set<String> named = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String text = CredentialColumnGuardTest.stringLiterals(javaSource);
        Matcher dbo = DBO_OBJECT.matcher(text);
        while (dbo.find()) {
            named.add(ExternalReadRegister.BASE_SCHEMA + "." + dbo.group(1));
        }
        Matcher view = VIEW_SCHEMA_OBJECT.matcher(text);
        while (view.find()) {
            named.add(ExternalReadRegister.VIEW_SCHEMA + "." + view.group(1));
        }
        for (String literal : text.split("\n")) {
            if (BARE_VIEW_LITERAL.matcher(literal.trim()).matches()) {
                named.add(ExternalReadRegister.VIEW_SCHEMA + "." + literal.trim());
            }
        }
        return named;
    }

    static List<String> unregisteredObjects(List<Path> layer, Set<String> registered) throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : layer) {
            for (String object : namedObjects(Files.readString(file))) {
                if (!registered.contains(object)) {
                    hits.add(file + " names " + object);
                }
            }
        }
        return hits;
    }

    @Test
    @DisplayName("§9a src/main never names formmain_, formson_, a8_db, LEXMIS or T_U8C_ (registered object names excepted)")
    void mainSourcesDoNotNameForbiddenA8Objects() throws IOException {
        assertThat(violations(MAIN, registeredNames())).as("§9a: only registered objects may be named").isEmpty();
    }

    @Test
    @DisplayName("§9a Flyway scripts never name them either (no foreign tables, no cross-database views)")
    void migrationsDoNotNameForbiddenA8Objects() throws IOException {
        assertThat(Files.isDirectory(MIGRATIONS)).isTrue();
        assertThat(violations(MIGRATIONS, registeredNames())).isEmpty();
    }

    @Test
    @DisplayName("§9a the scan itself works: it flags planted tokens, and a registered view name is not a hit")
    void theScannerIsNotVacuous(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Bad.java"), "String sql = \"select * from a8_db.dbo.formmain_0051\";\n// LEXMISCIF_DEPTAUX\nT_U8C_BD_CUBASDOC\nformson_1\n");
        Files.writeString(dir.resolve("Good.java"), "String sql = \"select ID from forcitis_one.v_org_unit\";\nurl=${A8_DB_URL:}\n");

        List<String> hits = violations(dir, List.of("forcitis_one.v_org_unit", "v_org_unit", "A8_DB_URL"));

        assertThat(hits).hasSize(4).allMatch(h -> h.contains("Bad.java"));
        assertThat(hits.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList())
                .anyMatch(h -> h.contains("a8_db")).anyMatch(h -> h.contains("lexmis")).anyMatch(h -> h.contains("t_u8c_")).anyMatch(h -> h.contains("formson_"));
    }

    @Test
    @DisplayName("R-1b-0 集成层以外（Java、资源、Flyway）不出现登记的 dbo 对象")
    void registeredBaseObjectsStayInTheIntegrationLayer() throws IOException {
        List<Row> rows = ExternalReadRegister.rows();
        Set<Path> layer = Set.copyOf(CredentialColumnGuardTest.integrationLayer());

        assertThat(registeredBaseObjectsOutside(MAIN, layer, ExternalReadRegister.baseObjectNames(rows))).isEmpty();
    }

    @Test
    @DisplayName("R-1b-0 集成层 SQL 引用的每个 dbo 对象与 forcitis_one 视图都取自外部读取登记")
    void integrationSqlNamesOnlyRegisteredObjects() throws IOException {
        List<Path> layer = CredentialColumnGuardTest.integrationLayer();
        Set<String> named = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Path file : layer) {
            named.addAll(namedObjects(Files.readString(file)));
        }

        assertThat(named).as("the scan sees the SQL of both external databases")
                .contains("dbo.ORG_UNIT", "forcitis_one.v_org_member", "dbo.v_psa_one_legacy_todo", "dbo.v_one_legacy_org_short_name");
        assertThat(unregisteredObjects(layer, ExternalReadRegister.objects(ExternalReadRegister.rows()))).isEmpty();
    }

    @Test
    @DisplayName("R-1b-0 登记扫描自检：集成层外的 dbo.登记表名、集成层 SQL 中未登记的 dbo 表与视图（含方括号写法）都被报出，未登记的普通文字不误报")
    void injectedReferencesAreFound(@TempDir Path dir) throws IOException {
        Path outside = dir.resolve("Outside.java");
        Path inside = dir.resolve("Inside.java");
        Files.writeString(outside, "String sql = \"select ID from dbo.ORG_UNIT\";\n// [dbo].[org_member] in a comment is a hit too\nString ok = \"dbo.SOMETHING_ELSE\";\n");
        Files.writeString(inside, "String sql = \"SELECT V.ID FROM forcitis_one.v_org_unit V JOIN [dbo].[ORG_SECRET] S ON S.ID = V.ID\";\n"
                + "String view = \"v_org_hidden\";\nString other = \"SELECT ID FROM forcitis_one.v_org_post\";\n");

        List<String> outsideHits = registeredBaseObjectsOutside(dir, Set.of(inside), ExternalReadRegister.baseObjectNames(ExternalReadRegister.rows()));
        List<String> insideHits = unregisteredObjects(List.of(inside), ExternalReadRegister.objects(ExternalReadRegister.rows()));

        assertThat(outsideHits).hasSize(2).allMatch(h -> h.contains("Outside.java")).anyMatch(h -> h.contains("dbo.ORG_UNIT"))
                .anyMatch(h -> h.contains("dbo.org_member"));
        assertThat(insideHits).hasSize(2).anyMatch(h -> h.endsWith("dbo.ORG_SECRET")).anyMatch(h -> h.endsWith("forcitis_one.v_org_hidden"));
    }

    @Test
    @DisplayName("§9a the workforce schema and A8 access carry no cross-database or foreign-data-wrapper DDL")
    void noForeignDataDdl() throws IOException {
        Pattern foreign = Pattern.compile("CREATE\\s+EXTENSION\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(tds_fdw|postgres_fdw|dblink)|CREATE\\s+SERVER|IMPORT\\s+FOREIGN\\s+SCHEMA",
                Pattern.CASE_INSENSITIVE);
        try (Stream<Path> files = Files.walk(MIGRATIONS.resolve("organization_workforce"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertThat(foreign.matcher(Files.readString(file)).find()).as(file.toString()).isFalse();
            }
        }
    }
}
