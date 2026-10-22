package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** ADR-0069 decision 14: the registered audit subject kinds (every audit migration, any status) are the code constants (which only refer to them) and are owned by real bcs. */
class AuditSubjectKindGuardTest {

    static final Path MIGRATIONS = Path.of("src/main/resources/db/migration/platform_audit");
    static final Path MIGRATION = Path.of("src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql");
    static final Pattern SEED = Pattern.compile("\\('[0-9a-f-]{36}', '([A-Z][A-Z0-9_]+)', '([a-z.]+)', (true|false), '(?:ACTIVE|RETIRED)', DATE '[0-9-]+'\\)");

    record Seed(String owner, boolean hasObject) {
    }

    private static Map<String, Seed> seeds() throws IOException {
        StringBuilder all = new StringBuilder();
        try (java.util.stream.Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).sorted().toList()) {
                all.append(Files.readString(file)).append('\n');
            }
        }
        Matcher m = SEED.matcher(all);
        Map<String, Seed> seeds = new LinkedHashMap<>();
        while (m.find()) {
            seeds.put(m.group(1), new Seed(m.group(2), Boolean.parseBoolean(m.group(3))));
        }
        return seeds;
    }

    @Test
    @DisplayName("every code constant is a seeded kind with the same has_object, and every seeded kind has its constant")
    void constantsAndSeedsAgree() throws IOException {
        Map<String, Seed> seeds = seeds();

        assertThat(seeds.keySet()).containsExactlyInAnyOrderElementsOf(List.of(AuditSubjectKind.values()).stream().map(Enum::name).toList());
        for (AuditSubjectKind kind : AuditSubjectKind.values()) {
            assertThat(seeds.get(kind.name()).hasObject()).as(kind + " has_object").isEqualTo(kind.hasObject());
        }
    }

    @Test
    @DisplayName("the baseline no longer lists the kinds in a CHECK on audit_entries")
    void noEnumeratedCheckOnTheEntries() throws IOException {
        assertThat(Files.readString(MIGRATION)).doesNotContain("ck_audit_entries_subject_kind CHECK").contains("fk_audit_entries_subject_kind");
    }

    @Test
    @DisplayName("every owner is an entry of kind bc in glossary/bcs.yaml (a subdomain or an unknown id does not count)")
    @SuppressWarnings("unchecked")
    void ownersAreBcs() throws IOException {
        Set<String> bcs;
        try (InputStream in = Files.newInputStream(Path.of("../docs/modernization/glossary/bcs.yaml"))) {
            Map<String, Object> root = new Yaml().load(in);
            bcs = ((List<Map<String, Object>>) root.get("bcs")).stream().filter(e -> "bc".equals(e.get("kind"))).map(e -> String.valueOf(e.get("id")))
                    .collect(Collectors.toSet());
        }

        assertThat(seeds().entrySet().stream().filter(e -> !bcs.contains(e.getValue().owner())).map(e -> e.getKey() + " -> " + e.getValue().owner()).toList()).isEmpty();
    }
}
