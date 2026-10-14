package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * §9a source scan (REIMAGINED_ARCHITECTURE §3.3): src/main and the Flyway scripts must never name A8 form tables,
 * the A8 database, the LEXMIS accounting-mapping tables or the U8C tables. Accrual-basis business facts are not
 * readable from A8 in the new system; the only A8 names allowed are the whitelisted view names.
 */
class SeeyonSourceScanTest {

    static final Path MAIN = Path.of("src/main");
    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    static final Path WHITELIST = Path.of("architecture/seeyon-view-columns.yaml");

    static final Pattern FORBIDDEN = Pattern.compile("formmain_|formson_|a8_db|LEXMIS|T_U8C_", Pattern.CASE_INSENSITIVE);

    /**
     * The environment variable names of the A8 connection (AI_NATIVE_SPEC §3.3 names ${A8_DB_URL}). They are
     * variable names, not database objects; everything else that spells a8_db stays forbidden.
     */
    static final List<String> ENVIRONMENT_VARIABLES = List.of("A8_DB_URL", "A8_DB_USERNAME", "A8_DB_PASSWORD");

    @SuppressWarnings("unchecked")
    static List<String> whitelistedViewNames() throws IOException {
        Map<String, Object> yaml = new Yaml().load(Files.readString(WHITELIST));
        String schema = (String) yaml.get("schema");
        List<String> names = new ArrayList<>(ENVIRONMENT_VARIABLES);
        ((Map<String, Object>) yaml.get("views")).keySet().forEach(v -> {
            names.add(schema + "." + v);
            names.add(v);
        });
        return names;
    }

    /** Every line of the files that contains a forbidden token once the whitelisted view names are taken out. */
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

    @Test
    @DisplayName("§9a src/main never names formmain_, formson_, a8_db, LEXMIS or T_U8C_ (whitelisted view names excepted)")
    void mainSourcesDoNotNameForbiddenA8Objects() throws IOException {
        assertThat(violations(MAIN, whitelistedViewNames())).as("§9a: only the whitelisted views may be named").isEmpty();
    }

    @Test
    @DisplayName("§9a Flyway scripts never name them either (no foreign tables, no cross-database views)")
    void migrationsDoNotNameForbiddenA8Objects() throws IOException {
        assertThat(Files.isDirectory(MIGRATIONS)).isTrue();
        assertThat(violations(MIGRATIONS, whitelistedViewNames())).isEmpty();
    }

    @Test
    @DisplayName("§9a the scan itself works: it flags planted tokens, and a whitelisted view name is not a hit")
    void theScannerIsNotVacuous(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Bad.java"), "String sql = \"select * from a8_db.dbo.formmain_0051\";\n// LEXMISCIF_DEPTAUX\nT_U8C_BD_CUBASDOC\nformson_1\n");
        Files.writeString(dir.resolve("Good.java"), "String sql = \"select ID from forcitis_one.v_org_unit\";\nurl=${A8_DB_URL:}\n");

        List<String> hits = violations(dir, List.of("forcitis_one.v_org_unit", "v_org_unit", "A8_DB_URL"));

        assertThat(hits).hasSize(4).allMatch(h -> h.contains("Bad.java"));
        assertThat(hits.stream().map(h -> h.toLowerCase(Locale.ROOT)).toList())
                .anyMatch(h -> h.contains("a8_db")).anyMatch(h -> h.contains("lexmis")).anyMatch(h -> h.contains("t_u8c_")).anyMatch(h -> h.contains("formson_"));
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
