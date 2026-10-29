package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** SQL and HTTP payloads live in strings, so these guards scan sources instead of bytecode. */
class SecurityInvariantsTest {
    static final Path MAIN = Path.of("src/main");
    static final Path ISSUER_ALLOWLIST = Path.of("architecture/ticket-issuers-allowlist.txt");

    /** I-1 (ADR-0037 item 3): application code never names the legacy token tables (system_oauth2_*); token values never enter the new system, which never writes or validates them. */
    @Test
    void legacyTokenTablesAreNeverTouched() {
        assertThat(matches(MAIN, Pattern.compile("system_oauth2", Pattern.CASE_INSENSITIVE)))
                .as(GuardMessage.of("I-1", "code, SQL or config references the legacy token tables system_oauth2_* (ADR-0037 item 3)",
                        "remove the reference; token values never enter the new system and it never writes or validates them"))
                .isEmpty();
    }

    /** I-2: only reviewed issuers may sign one-time tickets; the legacy backend never may. */
    @Test
    void ticketIssuersAreReviewedAndNeverTheLegacyBackend() throws IOException {
        Set<String> allowed = Set.copyOf(Files.readAllLines(ISSUER_ALLOWLIST).stream()
                .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList());
        assertThat(allowed).as(GuardMessage.of("I-2", "the ticket issuers allowlist contains a legacy backend issuer (psa, legacy or yudao)",
                "remove that issuer from " + ISSUER_ALLOWLIST)).noneMatch(SecurityInvariantsTest::looksLegacy);
        Pattern insert = Pattern.compile("insert\\s+into\\s+\\S*ticket_issuers[^;]*?values\\s*(.*?);",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Pattern firstString = Pattern.compile("\\(\\s*'([^']+)'");
        List<String> problems = new ArrayList<>();
        for (Path sql : files(MAIN, ".sql")) {
            Matcher m = insert.matcher(read(sql));
            while (m.find()) {
                Matcher code = firstString.matcher(m.group(1));
                while (code.find()) {
                    String issuer = code.group(1);
                    if (!allowed.contains(issuer) || looksLegacy(issuer)) {
                        problems.add(sql + ": issuer '" + issuer + "' is not in " + ISSUER_ALLOWLIST + " (CODEOWNERS review)");
                    }
                }
            }
        }
        assertThat(problems).as(GuardMessage.of("I-2", "a migration inserts a ticket issuer that is not on the reviewed allowlist, or is a legacy backend",
                "do not insert that issuer; a new reviewed issuer is added to " + ISSUER_ALLOWLIST + " with CODEOWNERS review")).isEmpty();
    }

    /** I-6: WeCom messages go to touser only - never toparty / totag / @all (SEC-118). */
    @Test
    void wecomMessagesNeverBroadcast() {
        assertThat(matches(MAIN, Pattern.compile("toparty|totag|@all", Pattern.CASE_INSENSITIVE)))
                .as(GuardMessage.of("I-6", "a WeCom payload uses toparty, totag or @all (SEC-118)",
                        "address WeCom messages to touser only"))
                .isEmpty();
    }

    private static boolean looksLegacy(String issuer) {
        String s = issuer.toLowerCase(Locale.ROOT);
        return s.contains("psa") || s.contains("legacy") || s.contains("yudao");
    }

    private static List<String> matches(Path root, Pattern pattern) {
        List<String> hits = new ArrayList<>();
        for (Path f : files(root, ".java", ".sql", ".properties", ".yaml", ".yml", ".xml", ".json")) {
            String[] lines = read(f).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (pattern.matcher(lines[i]).find()) {
                    hits.add(f + ":" + (i + 1) + ": " + lines[i].trim());
                }
            }
        }
        return hits;
    }

    private static List<Path> files(Path root, String... suffixes) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> Stream.of(suffixes).anyMatch(s -> p.toString().endsWith(s))).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
