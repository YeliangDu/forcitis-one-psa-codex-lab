package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The new system does not carry the shape of the old one (SESSION_PLAYBOOK section 5, "old code is evidence only"). */
class LegacyShapeTest {

    static final Path EXCEPTIONS = Path.of("architecture/legacy-shape-exceptions.txt");

    /** Where an old identifier may be registered at all: the approved anti-corruption layers and nothing else. */
    static final List<String> APPROVED_AREAS = List.of(
            "src/main/java/com/forcitis/one/platform/integration/infrastructure/seeyon/",
            "src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/",
            "src/main/java/com/forcitis/one/platform/integration/infrastructure/legacy/",
            "src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/",
            "src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/",
            "src/test/java/com/forcitis/one/platform/approval/infrastructure/legacy/",
            "src/main/java/com/forcitis/one/platform/approval/infrastructure/replay/",
            "src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/");

    /** The rules. */
    enum Rule {
        OLD_URL_PREFIX("old URL prefix /admin-api/ /app-api/ /rpc-api/"),
        OLD_BASE_TYPE("old result / page / base-class / tenant type"),
        OLD_CLASS_SUFFIX("class name ends with ServiceImpl / Convert / DO / ReqVO / RespVO / VO / Mapper"),
        OLD_PACKAGE("old package layer dal / dataobject / convert / controller.admin / controller.app"),
        OLD_WORKFLOW_CLASS("class named Bpm* or *ActivityBehavior(Factory)"),
        OLD_MESSAGING("old messaging: rocketmq / MessageModel / consumerGroup"),
        OLD_DDL_COLUMN("old base columns tenant_id / deleted / creator / updater / create_time / update_time"),
        OLD_TABLE_PREFIX("old table prefix psa_ / system_ / bpm_ / infra_ / fst_"),
        NUMERIC_STATUS("status column of a numeric type"),
        OLD_FRAMEWORK_NAME("old framework or vendor name yudao / ruoyi / iocoder"),
        OLD_FIELD_NAME("old field or variable name"),
        OLD_ROLE_CODE("old role code BU-nnn"),
        OLD_RULE_TYPE_NUMBER("old assignment rule type number in a switch"),
        EXTERNAL_SYSTEM_NAME("external system name in a domain module (a8 / seeyon / u8c / fenbeitong / yudao)");

        final String description;

        Rule(String description) {
            this.description = description;
        }
    }

    enum Kind { JAVA, SQL, OTHER }

    record Hit(String file, Rule rule, int line, String excerpt) {
        @Override
        public String toString() {
            return file + ":" + line + " [" + rule + "] " + excerpt;
        }
    }

    record Allowed(String file, Set<Rule> rules, String reason) {
    }

    // ---------------------------------------------------------------- patterns

    static final Pattern URL_PREFIX = Pattern.compile("/(admin|app|rpc)-api/");
    static final Pattern BASE_TYPE = Pattern.compile(
            "CommonResult|PageResult|PageParam|pageNo|BaseDO|TenantBaseDO|BaseMapperX|LambdaQueryWrapper|TenantContextHolder|@TenantIgnore");
    static final Pattern CLASS_DECLARATION = Pattern.compile("\\b(?:class|record|interface|enum)\\s+([A-Z][A-Za-z0-9_]*)");
    static final Pattern CLASS_SUFFIX = Pattern.compile("(ServiceImpl|Convert|DO|ReqVO|RespVO|VO|Mapper)$");
    static final Pattern PACKAGE_LINE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    static final Pattern PACKAGE_LAYER = Pattern.compile("\\.(dal|dataobject|convert)(\\.|$)|controller\\.(admin|app)(\\.|$)");
    static final Pattern WORKFLOW_CLASS = Pattern.compile("^Bpm[A-Z]|ActivityBehavior(Factory)?$");
    static final Pattern MESSAGING = Pattern.compile("rocketmq|MessageModel|RocketMQMessageListener|consumerGroup", Pattern.CASE_INSENSITIVE);
    static final Pattern DDL_COLUMN = Pattern.compile("^\\s*(tenant_id|deleted|creator|updater|create_time|update_time)\\s+\\w+",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    static final Pattern TABLE_PREFIX = Pattern.compile("(?<![A-Za-z0-9_])(psa_|system_|bpm_|infra_|fst_)[a-z0-9_]+");
    static final Pattern NUMERIC_STATUS_COLUMN = Pattern.compile(
            "^\\s*(\\w*status)\\s+(int|integer|smallint|tinyint|bigint|numeric|decimal)\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    static final Pattern FRAMEWORK_NAME = Pattern.compile("yudao|ruoyi|iocoder", Pattern.CASE_INSENSITIVE);
    static final Pattern FIELD_NAME = Pattern.compile(
            "(?<![A-Za-z0-9_])(paramsId|dataStamp|scriptField|deliveryDeptId|deptId|arUserId|leaderUserId|startUserId|taskDefKey|manager_id)(?![A-Za-z0-9_])");
    static final Pattern ROLE_CODE = Pattern.compile("BU-\\d{3}");
    static final Pattern RULE_TYPE_NUMBER = Pattern.compile("\\bcase\\s+(?:\\d+\\s*,\\s*)*(?:9|10|20|21|22|30|31|32|33|40|50|60|70)\\b\\s*(?:->|:|,)");
    static final Pattern EXTERNAL_NAME = Pattern.compile("a8|seeyon|u8c|fenbeitong|yudao", Pattern.CASE_INSENSITIVE);

    /** The guard tests directly in com.forcitis.one spell what they forbid; they are no module code. */
    static final Pattern GUARD_TEST = Pattern.compile("src/test/java/com/forcitis/one/[^/]+\\.java");

    static final Pattern COMMENT_ON_LINE = Pattern.compile("(?im)^\\s*COMMENT\\s+ON\\b.*$");

    // ---------------------------------------------------------------- scan

    /** The directories that make up the code base, relative to the project (backend) root. */
    static final List<String> SCAN_ROOTS = List.of("src/main/java", "src/test/java", "src/main/resources/db/migration",
            "src/main/resources/processes", "src/main/resources/config", "src/test/resources");
    static final List<String> SCAN_FILES = List.of("src/main/resources/application.properties");

    static List<Path> filesOf(Path project) {
        List<Path> files = new ArrayList<>();
        for (String root : SCAN_ROOTS) {
            Path dir = project.resolve(root);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(Files::isRegularFile).forEach(files::add);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        for (String file : SCAN_FILES) {
            if (Files.isRegularFile(project.resolve(file))) {
                files.add(project.resolve(file));
            }
        }
        files.sort(null);
        return files;
    }

    static Kind kindOf(String file) {
        return file.endsWith(".java") ? Kind.JAVA : file.endsWith(".sql") ? Kind.SQL : Kind.OTHER;
    }

    /** The module a file belongs to (subdomain.bc), or "" when it belongs to none (guard tests, application.properties). */
    static String moduleOf(String file) {
        String javaRoot = "com/forcitis/one/";
        int at = file.indexOf(javaRoot);
        if (file.endsWith(".java") && at >= 0) {
            String[] parts = file.substring(at + javaRoot.length()).split("/");
            if (parts.length >= 3 && parts[0].equals("kernel")) {
                return "kernel";
            }
            return parts.length >= 3 ? parts[0] + "." + parts[1] : "";
        }
        Matcher migration = Pattern.compile("db/migration/([a-z]+)_([a-z]+)/").matcher(file);
        if (migration.find()) {
            return migration.group(1) + "." + migration.group(2);
        }
        Matcher config = Pattern.compile("config/([a-z]+)_([a-z]+)\\.properties$").matcher(file);
        if (config.find()) {
            return config.group(1) + "." + config.group(2);
        }
        return "";
    }

    static List<Hit> scan(Path project) {
        List<Hit> hits = new ArrayList<>();
        for (Path path : filesOf(project)) {
            String relative = project.relativize(path).toString().replace('\\', '/');
            if (relative.endsWith("/LegacyShapeTest.java") || GUARD_TEST.matcher(relative).matches()) {
                continue; // the guard tests spell the forbidden names they guard against
            }
            String text;
            try {
                text = Files.readString(path);
            } catch (IOException binary) {
                continue;
            }
            hits.addAll(scanFile(relative, text));
        }
        return hits;
    }

    static List<Hit> scanFile(String file, String raw) {
        List<Hit> hits = new ArrayList<>();
        Kind kind = kindOf(file);
        String text = COMMENT_ON_LINE.matcher(raw).replaceAll("");
        // rules that apply to every text, comments included
        collect(hits, file, Rule.OLD_URL_PREFIX, URL_PREFIX, text);
        collect(hits, file, Rule.OLD_BASE_TYPE, BASE_TYPE, text);
        collect(hits, file, Rule.OLD_MESSAGING, MESSAGING, text);
        collect(hits, file, Rule.OLD_TABLE_PREFIX, TABLE_PREFIX, text);
        collect(hits, file, Rule.OLD_FRAMEWORK_NAME, FRAMEWORK_NAME, text);
        collect(hits, file, Rule.OLD_FIELD_NAME, FIELD_NAME, text);
        collect(hits, file, Rule.OLD_ROLE_CODE, ROLE_CODE, text);
        if (kind == Kind.JAVA) {
            collect(hits, file, Rule.OLD_RULE_TYPE_NUMBER, RULE_TYPE_NUMBER, text);
            Matcher classes = CLASS_DECLARATION.matcher(code(text));
            while (classes.find()) {
                String name = classes.group(1);
                if (CLASS_SUFFIX.matcher(name).find()) {
                    hits.add(new Hit(file, Rule.OLD_CLASS_SUFFIX, lineOf(text, name), name));
                }
                if (WORKFLOW_CLASS.matcher(name).find()) {
                    hits.add(new Hit(file, Rule.OLD_WORKFLOW_CLASS, lineOf(text, name), name));
                }
            }
            String simple = file.substring(file.lastIndexOf('/') + 1).replace(".java", "");
            if (CLASS_SUFFIX.matcher(simple).find() && hits.stream().noneMatch(h -> h.rule() == Rule.OLD_CLASS_SUFFIX && h.file().equals(file))) {
                hits.add(new Hit(file, Rule.OLD_CLASS_SUFFIX, 1, simple));
            }
            Matcher pkg = PACKAGE_LINE.matcher(text);
            if (pkg.find() && PACKAGE_LAYER.matcher(pkg.group(1)).find()) {
                hits.add(new Hit(file, Rule.OLD_PACKAGE, lineOf(text, pkg.group(0).trim()), pkg.group(1)));
            }
        }
        if (kind == Kind.SQL) {
            collect(hits, file, Rule.OLD_DDL_COLUMN, DDL_COLUMN, text);
            collect(hits, file, Rule.NUMERIC_STATUS, NUMERIC_STATUS_COLUMN, text);
        }
        if (!moduleOf(file).equals("platform.integration")) {
            String named = kind == Kind.JAVA ? code(text) : text;
            collect(hits, file, Rule.EXTERNAL_SYSTEM_NAME, EXTERNAL_NAME, named);
            Matcher inPath = EXTERNAL_NAME.matcher(file);
            if (inPath.find()) {
                hits.add(new Hit(file, Rule.EXTERNAL_SYSTEM_NAME, 1, "path " + file));
            }
        }
        return hits;
    }

    private static void collect(List<Hit> hits, String file, Rule rule, Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            hits.add(new Hit(file, rule, lineAt(text, m.start()), excerpt(text, m.start())));
        }
    }

    private static int lineAt(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int lineOf(String text, String snippet) {
        int at = text.indexOf(snippet);
        return at < 0 ? 1 : lineAt(text, at);
    }

    private static String excerpt(String text, int offset) {
        int start = text.lastIndexOf('\n', offset) + 1;
        int end = text.indexOf('\n', offset);
        return text.substring(start, end < 0 ? text.length() : end).strip();
    }

    /** Java text without comments (string literals and text blocks stay, so identifiers and strings are both matched). */
    static String code(String java) {
        StringBuilder out = new StringBuilder(java.length());
        int i = 0;
        int n = java.length();
        while (i < n) {
            char c = java.charAt(i);
            if (java.startsWith("\"\"\"", i)) {
                int end = java.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                out.append(java, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && java.charAt(j) != c && java.charAt(j) != '\n') {
                    j += java.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(n, j + 1);
                out.append(java, i, j);
                i = j;
            } else if (java.startsWith("//", i)) {
                while (i < n && java.charAt(i) != '\n') {
                    i++;
                }
            } else if (java.startsWith("/*", i)) {
                int end = java.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) {
                    if (java.charAt(k) == '\n') {
                        out.append('\n');
                    }
                }
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- exceptions

    static List<Allowed> exceptions(Path file) {
        List<Allowed> list = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return list;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                assertThat(parts).as("path | RULE,RULE | reason: %s", line).hasSize(3);
                Set<Rule> rules = Arrays.stream(parts[1].split(",")).map(String::trim).map(Rule::valueOf)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                list.add(new Allowed(parts[0].trim(), rules, parts[2].trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return list;
    }

    static List<Hit> violations(List<Hit> hits, List<Allowed> exceptions) {
        Map<String, Set<Rule>> allowed = new LinkedHashMap<>();
        exceptions.forEach(e -> allowed.merge(e.file(), e.rules(), (a, b) -> {
            Set<Rule> merged = new LinkedHashSet<>(a);
            merged.addAll(b);
            return merged;
        }));
        return hits.stream().filter(h -> !allowed.getOrDefault(h.file(), Set.of()).contains(h.rule())).toList();
    }

    // ---------------------------------------------------------------- the real guard

    @Test
    @DisplayName("the code base carries no old-system shape: no hit outside the registered exceptions")
    void theCodeBaseHasNoOldSystemShape() {
        List<Hit> violations = violations(scan(Path.of(".")), exceptions(EXCEPTIONS));

        assertThat(violations).as("old-system identifiers (write the new thing from the spec, rule cards and glossary; an anti-corruption "
                + "layer registers its file in architecture/legacy-shape-exceptions.txt):\n  %s",
                violations.stream().map(Hit::toString).collect(Collectors.joining("\n  "))).isEmpty();
    }

    @Test
    @DisplayName("the exceptions are only for approved anti-corruption layers, name known rules and a reason, and none of them is stale")
    void exceptionsAreApprovedAndNeeded() {
        List<Allowed> exceptions = exceptions(EXCEPTIONS);
        List<Hit> hits = scan(Path.of("."));
        Map<String, Set<Rule>> hitRules = new TreeMap<>();
        hits.forEach(h -> hitRules.computeIfAbsent(h.file(), k -> new TreeSet<>()).add(h.rule()));

        for (Allowed e : exceptions) {
            assertThat(Files.isRegularFile(Path.of(e.file()))).as("%s exists", e.file()).isTrue();
            assertThat(APPROVED_AREAS.stream().anyMatch(e.file()::startsWith)).as("%s is in an approved anti-corruption layer", e.file()).isTrue();
            assertThat(e.reason()).as("reason of %s", e.file()).isNotBlank();
            assertThat(e.rules()).as("rules of %s", e.file()).isNotEmpty();
            for (Rule rule : e.rules()) {
                assertThat(hitRules.getOrDefault(e.file(), Set.of())).as("%s needs the exception for %s (stale entries are removed)", e.file(), rule)
                        .contains(rule);
            }
        }
        assertThat(exceptions.stream().map(Allowed::file).distinct().count()).as("one line per file").isEqualTo(exceptions.size());
        assertThat(exceptions.stream().flatMap(e -> e.rules().stream()).collect(Collectors.toSet()))
                .as("the platform.integration names rule never needs an exception").doesNotContain(Rule.EXTERNAL_SYSTEM_NAME);
    }

    // ---------------------------------------------------------------- injection: every rule fails

    /** One violating sample per rule: file (relative to the project) and content. */
    static final Map<Rule, Map<String, String>> SAMPLES = samples();

    private static Map<Rule, Map<String, String>> samples() {
        Map<Rule, Map<String, String>> samples = new EnumMap<>(Rule.class);
        String pkg = "src/main/java/com/forcitis/one/cost/timesheet/application/";
        samples.put(Rule.OLD_URL_PREFIX, Map.of(pkg + "Paths.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass Paths { static final String P = \"/admin-api/system/user\"; }\n"));
        samples.put(Rule.OLD_BASE_TYPE, Map.of(pkg + "Reply.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass Reply { CommonResult<String> r; }\n"));
        samples.put(Rule.OLD_CLASS_SUFFIX, Map.of(pkg + "TimesheetServiceImpl.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass TimesheetServiceImpl { }\n"));
        samples.put(Rule.OLD_PACKAGE, Map.of("src/main/java/com/forcitis/one/cost/timesheet/dal/Rows.java",
                "package com.forcitis.one.cost.timesheet.dal;\nclass Rows { }\n"));
        samples.put(Rule.OLD_WORKFLOW_CLASS, Map.of(pkg + "BpmTaskListener.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass BpmTaskListener { }\n"));
        samples.put(Rule.OLD_MESSAGING, Map.of(pkg + "Consumer.java",
                "package com.forcitis.one.cost.timesheet.application;\n@RocketMQMessageListener(consumerGroup = \"g\")\nclass Consumer { }\n"));
        samples.put(Rule.OLD_DDL_COLUMN, Map.of("src/main/resources/db/migration/cost_timesheet/V20260101000000__cost_timesheet_create_rows.sql",
                "CREATE TABLE cost_timesheet.rows (\n    id bigint PRIMARY KEY,\n    tenant_id bigint,\n    created_at timestamptz,\n    updated_at timestamptz\n);\n"));
        samples.put(Rule.OLD_TABLE_PREFIX, Map.of("src/main/resources/db/migration/cost_timesheet/V20260101000000__cost_timesheet_create_rows.sql",
                "CREATE TABLE cost_timesheet.psa_timesheet_row (\n    id bigint PRIMARY KEY\n);\n"));
        samples.put(Rule.NUMERIC_STATUS, Map.of("src/main/resources/db/migration/cost_timesheet/V20260101000000__cost_timesheet_create_rows.sql",
                "CREATE TABLE cost_timesheet.rows (\n    id bigint PRIMARY KEY,\n    status integer NOT NULL\n);\n"));
        samples.put(Rule.OLD_FRAMEWORK_NAME, Map.of(pkg + "Imports.java",
                "package com.forcitis.one.cost.timesheet.application;\nimport cn.iocoder.yudao.framework.X;\nclass Imports { }\n"));
        samples.put(Rule.OLD_FIELD_NAME, Map.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml",
                "<process><userTask id=\"a\"><x field=\"manager_id\"/></userTask></process>\n"));
        samples.put(Rule.OLD_ROLE_CODE, Map.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml",
                "<process><userTask id=\"a\"><x type=\"ROLE\" role=\"BU-002\"/></userTask></process>\n"));
        samples.put(Rule.OLD_RULE_TYPE_NUMBER, Map.of(pkg + "Types.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass Types { String f(int t) { return switch (t) { case 60 -> \"x\"; default -> \"y\"; }; } }\n"));
        samples.put(Rule.EXTERNAL_SYSTEM_NAME, Map.of(pkg + "Sync.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass Sync { static final String Q = \"select * from a8_view\"; }\n"));
        return samples;
    }

    static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    @DisplayName("every rule has an injection sample, and each sample fails its rule")
    void everyRuleFailsOnItsSample(@TempDir Path root) throws IOException {
        assertThat(SAMPLES.keySet()).as("a sample for every rule").containsExactlyInAnyOrder(Rule.values());
        for (Map.Entry<Rule, Map<String, String>> sample : SAMPLES.entrySet()) {
            Path project = Files.createTempDirectory(root, sample.getKey().name().toLowerCase(Locale.ROOT));
            for (Map.Entry<String, String> file : sample.getValue().entrySet()) {
                write(project, file.getKey(), file.getValue());
            }

            List<Hit> hits = scan(project);

            assertThat(hits).as("sample of %s", sample.getKey()).isNotEmpty();
            assertThat(hits.stream().map(Hit::rule).collect(Collectors.toSet())).as("rules hit by the sample of %s: %s", sample.getKey(), hits)
                    .contains(sample.getKey());
        }
    }

    @Test
    @DisplayName("a clean tree passes, COMMENT ON lines and Java comments are not matched by the names rule, platform.integration may name external systems")
    void cleanTreeAndAllowances(@TempDir Path project) throws IOException {
        write(project, "src/main/java/com/forcitis/one/cost/timesheet/application/Clean.java",
                "package com.forcitis.one.cost.timesheet.application;\n// reads the a8 views through the port (comment)\n/** seeyon in a comment */\nclass Clean { }\n");
        write(project, "src/main/resources/db/migration/cost_timesheet/V20260101000000__cost_timesheet_create_rows.sql",
                "CREATE TABLE cost_timesheet.rows (\n    id bigint PRIMARY KEY,\n    status text NOT NULL CHECK (status IN ('A'))\n);\n"
                        + "COMMENT ON TABLE cost_timesheet.rows IS 'rows copied from a8 and psa_x';\n");
        write(project, "src/main/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonThing.java",
                "package com.forcitis.one.platform.integration.infrastructure.seeyon;\nclass SeeyonThing { String s = \"a8 views\"; }\n");

        assertThat(scan(project)).isEmpty();
    }

    @Test
    @DisplayName("the guard tests directly in com.forcitis.one are not scanned (they spell what they forbid); module tests are")
    void guardTestsAreNotScannedButModuleTestsAre(@TempDir Path project) throws IOException {
        write(project, "src/test/java/com/forcitis/one/SomeGuardTest.java", "package com.forcitis.one;\nclass SomeGuardTest { String s = \"yudao\"; }\n");
        write(project, "src/test/java/com/forcitis/one/cost/timesheet/SomeTest.java",
                "package com.forcitis.one.cost.timesheet;\nclass SomeTest { String s = \"yudao\"; }\n");

        assertThat(scan(project)).extracting(Hit::file).containsOnly("src/test/java/com/forcitis/one/cost/timesheet/SomeTest.java");
    }

    @Test
    @DisplayName("an exception covers exactly one file and exactly its listed rules: other files and other rules still fail")
    void exceptionsAreNarrow(@TempDir Path project) throws IOException {
        String adapter = "src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/Reader.java";
        String other = "src/main/java/com/forcitis/one/platform/approval/application/Reader.java";
        String content = "package com.forcitis.one.platform.approval.%s;\nclass Reader { String a = \"manager_id\"; String b = \"BU-002\"; }\n";
        write(project, adapter, content.formatted("infrastructure.legacy"));
        write(project, other, content.formatted("application"));
        List<Hit> hits = scan(project);
        Allowed allowed = new Allowed(adapter, Set.of(Rule.OLD_FIELD_NAME), "test");

        List<Hit> left = violations(hits, List.of(allowed));

        assertThat(left).extracting(Hit::file, Hit::rule).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(adapter, Rule.OLD_ROLE_CODE),
                org.assertj.core.groups.Tuple.tuple(other, Rule.OLD_FIELD_NAME),
                org.assertj.core.groups.Tuple.tuple(other, Rule.OLD_ROLE_CODE));
    }

    @Test
    @DisplayName("the exceptions file format is path | RULE,RULE | reason; an unknown rule name is refused")
    void exceptionsFileFormat(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("exceptions.txt");
        Files.writeString(file, "# comment\n\nsrc/a/B.java | OLD_FIELD_NAME, OLD_ROLE_CODE | because\n");
        assertThat(exceptions(file)).singleElement().satisfies(e -> {
            assertThat(e.file()).isEqualTo("src/a/B.java");
            assertThat(e.rules()).containsExactly(Rule.OLD_FIELD_NAME, Rule.OLD_ROLE_CODE);
            assertThat(e.reason()).isEqualTo("because");
        });
        Files.writeString(file, "src/a/B.java | NO_SUCH_RULE | because\n");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> exceptions(file)).isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unused")
    private static Collection<Rule> allRules() {
        return List.of(Rule.values());
    }
}
