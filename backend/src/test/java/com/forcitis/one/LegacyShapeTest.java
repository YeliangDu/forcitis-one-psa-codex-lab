package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

class LegacyShapeTest {
    static final Path EXCEPTIONS = Path.of("architecture/legacy-shape-exceptions.txt");

    static final List<String> APPROVED_AREAS = List.of(
            "src/main/java/com/forcitis/one/platform/integration/infrastructure/seeyon/",
            "src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/",
            "src/main/java/com/forcitis/one/platform/integration/infrastructure/legacy/",
            "src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/",
            "src/main/java/com/forcitis/one/platform/approval/infrastructure/replay/",
            "src/test/java/com/forcitis/one/platform/approval/infrastructure/replay/");

    static List<String> missingAreas(List<String> areas) {
        return areas.stream().filter(area -> !Files.isDirectory(Path.of(area))).toList();
    }

    @Test
    @DisplayName("every approved anti-corruption area is a directory that exists; a moved or deleted one is removed from APPROVED_AREAS")
    void approvedAreasExist() {
        assertThat(missingAreas(APPROVED_AREAS)).isEmpty();
    }

    @Test
    @DisplayName("injected: a directory that does not exist is caught by the check of approved areas, an existing one is not")
    void aMissingAreaIsCaught() {
        assertThat(missingAreas(List.of("src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/", "src/main/java/com/forcitis/one/platform/approval/infrastructure/replay/")))
                .containsExactly("src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/");
    }

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
        EXTERNAL_SYSTEM_NAME("external system name in a domain module (a8 / seeyon / u8c / fenbeitong / yudao / weixin / feishu / lexmis, or a Member class)"),
        OLD_FRONTEND_SHAPE("old front-end shape: Vuex / mixins / pageNo / pageSize / nickname / deptId / /bpm/ / /system/ / dictionaries / wrapped result"),
        OLD_DOCUMENT_FORMAT("old document number format PSAxx or TS{yyyyMMdd}"),
        INSTANCE_ID_LITERAL("an 18 to 19 digit literal: an id of one external instance, allowed only in platform_integration.properties and the Seeyon adapters");

        final String description;

        Rule(String description) {
            this.description = description;
        }
    }

    enum Kind { JAVA, SQL, FRONTEND, OTHER }

    record Hit(String file, Rule rule, int line, String excerpt) {
        @Override
        public String toString() {
            return file + ":" + line + " [" + rule + "] " + excerpt;
        }
    }

    record Allowed(String file, Set<Rule> rules, String reason) {
    }

    static final Pattern URL_PREFIX = Pattern.compile("/(admin|app|rpc)-api/");
    static final Pattern BASE_TYPE = Pattern.compile(
            "(?<![A-Za-z0-9_])(?:CommonResult|PageResult|PageParam|pageNo|BaseDO|TenantBaseDO|BaseMapperX|LambdaQueryWrapper|TenantContextHolder)(?![A-Za-z0-9_])|@TenantIgnore");
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
            "^\\s*(\\w*(?:status|state|type|kind|flag))\\s+(int|integer|smallint|tinyint|bigint|numeric|decimal)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    static final Pattern FRAMEWORK_NAME = Pattern.compile("yudao|ruoyi|iocoder", Pattern.CASE_INSENSITIVE);
    static final Pattern FIELD_NAME = Pattern.compile(
            "(?<![A-Za-z0-9_])(paramsId|dataStamp|scriptField|deliveryDeptId|deptId|arUserId|leaderUserId|startUserId|taskDefKey|manager_id"
                    + "|area_leader|leader_user_id|dept_id|post_ids|manHourType|manhour|man_hour|tsheet|hourType|hour_type|bizType|startUserNickname"
                    + "|INITIATOR_LEADER)(?![A-Za-z0-9_])");
    static final Pattern ROLE_CODE = Pattern.compile("BU-\\d{3}");
    static final Pattern RULE_TYPE_NUMBER = Pattern.compile("\\bcase\\s+(?:\\d+\\s*,\\s*)*(?:9|10|20|21|22|30|31|32|33|40|50|60|70)\\b\\s*(?:->|:|,)");
    static final Pattern EXTERNAL_NAME = Pattern.compile("a8|seeyon|u8c|fenbeitong|yudao|weixin|feishu|lexmis", Pattern.CASE_INSENSITIVE);
    static final Pattern EXTERNAL_CLASS_NAME = Pattern.compile("Member");
    static final Pattern FRONTEND_SHAPE = Pattern.compile("this\\.\\$store|mapGetters|mapActions|mixins\\s*:|\\bpageNo\\b|\\bpageSize\\b|\\bnickname\\b"
            + "|\\bdeptId\\b|/bpm/|/system/|getDicts|dict_type|res\\.data\\.data|code\\s*===\\s*0");
    static final Pattern DOCUMENT_FORMAT = Pattern.compile("PSA[A-Z]{2}|TS\\{yyyyMMdd\\}");
    static final Pattern INSTANCE_ID = Pattern.compile("-?\\d{18,19}L?");

    static final List<String> INSTANCE_ID_AREAS = List.of("src/main/resources/config/platform_integration.properties",
            "src/main/java/com/forcitis/one/platform/integration/infrastructure/seeyon/",
            "src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/");

    /** Guard tests spell what they forbid; they are no module code. A new guard test is added here by name. */
    static final Set<String> GUARD_TESTS = Set.of(
            "src/test/java/com/forcitis/one/ArchitectureRulesTest.java",
            "src/test/java/com/forcitis/one/DdlNamingTest.java",
            "src/test/java/com/forcitis/one/Glossary.java",
            "src/test/java/com/forcitis/one/GlossaryTest.java",
            "src/test/java/com/forcitis/one/LegacyShapeTest.java",
            "src/test/java/com/forcitis/one/ModularityTest.java",
            "src/test/java/com/forcitis/one/ModuleDependencyTest.java",
            "src/test/java/com/forcitis/one/NamingSurfaceTest.java",
            "src/test/java/com/forcitis/one/SecurityInvariantsTest.java");

    /** Files that carry an external name on purpose and permanently. */
    static final List<Allowed> STANDING = List.of(new Allowed("src/main/java/com/forcitis/one/kernel/WecomUserId.java",
            Set.of(Rule.EXTERNAL_SYSTEM_NAME), "企业微信 userid 是全系统共用的外部标识"));

    static final Pattern COMMENT_ON_LINE = Pattern.compile("(?im)^\\s*COMMENT\\s+ON\\b.*$");

    static final List<String> SCAN_ROOTS = List.of("src/main/java", "src/test/java", "src/main/resources/db/migration",
            "src/main/resources/processes", "src/main/resources/config", "src/test/resources");
    static final List<String> SCAN_FILES = List.of("src/main/resources/application.properties");
    static final String FRONTEND = "../frontend/";

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
        files.addAll(frontendFiles(project.resolve(FRONTEND)));
        files.sort(null);
        return files;
    }

    static List<Path> frontendFiles(Path frontend) {
        List<Path> files = new ArrayList<>();
        for (String group : List.of("apps", "packages")) {
            Path groupDir = frontend.resolve(group);
            if (!Files.isDirectory(groupDir)) {
                continue;
            }
            try (Stream<Path> children = Files.list(groupDir)) {
                for (Path source : children.map(child -> child.resolve("src")).filter(Files::isDirectory).toList()) {
                    try (Stream<Path> walk = Files.walk(source)) {
                        walk.filter(Files::isRegularFile).filter(f -> !f.startsWith(source.resolve("generated"))).forEach(files::add);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Path contract = frontend.resolve("packages/api/openapi.json");
        if (!Files.isRegularFile(contract)) {
            throw new IllegalStateException("缺少 " + contract + "；请在仓库根运行 bash scripts/codegen/generate_api.sh（需 Docker 和前端依赖）");
        }
        files.add(contract);
        return files;
    }

    static Kind kindOf(String file) {
        return file.startsWith(FRONTEND) ? Kind.FRONTEND : file.endsWith(".java") ? Kind.JAVA : file.endsWith(".sql") ? Kind.SQL : Kind.OTHER;
    }

    static String moduleOf(String file) {
        String javaRoot = "com/forcitis/one/";
        int at = file.indexOf(javaRoot);
        if (file.endsWith(".java") && at >= 0) {
            String relative = file.substring(at + javaRoot.length());
            String[] parts = (relative.startsWith("acceptance/") ? relative.substring("acceptance/".length()) : relative).split("/");
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
            if (GUARD_TESTS.contains(relative)) {
                continue;
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
        collect(hits, file, Rule.OLD_DOCUMENT_FORMAT, DOCUMENT_FORMAT, text);
        if (INSTANCE_ID_AREAS.stream().noneMatch(file::startsWith)) {
            collect(hits, file, Rule.INSTANCE_ID_LITERAL, INSTANCE_ID, text);
        }
        if (kind == Kind.FRONTEND) {
            collect(hits, file, Rule.OLD_FRONTEND_SHAPE, FRONTEND_SHAPE, text);
        }
        if (kind == Kind.JAVA) {
            collect(hits, file, Rule.OLD_RULE_TYPE_NUMBER, RULE_TYPE_NUMBER, text);
            Matcher classes = CLASS_DECLARATION.matcher(JavaCode.withoutComments(text));
            while (classes.find()) {
                String name = classes.group(1);
                if (CLASS_SUFFIX.matcher(name).find()) {
                    hits.add(new Hit(file, Rule.OLD_CLASS_SUFFIX, lineOf(text, name), name));
                }
                if (WORKFLOW_CLASS.matcher(name).find()) {
                    hits.add(new Hit(file, Rule.OLD_WORKFLOW_CLASS, lineOf(text, name), name));
                }
                if (!moduleOf(file).equals("platform.integration") && EXTERNAL_CLASS_NAME.matcher(name).find()) {
                    hits.add(new Hit(file, Rule.EXTERNAL_SYSTEM_NAME, lineOf(text, name), "class " + name));
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
        if (!moduleOf(file).equals("platform.integration") && kind != Kind.FRONTEND) {
            String named = kind == Kind.JAVA ? JavaCode.withoutComments(text) : text;
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
        Stream.of(exceptions, STANDING).flatMap(List::stream).forEach(e -> allowed.merge(e.file(), e.rules(), (a, b) -> {
            Set<Rule> merged = new LinkedHashSet<>(a);
            merged.addAll(b);
            return merged;
        }));
        return hits.stream().filter(h -> !allowed.getOrDefault(h.file(), Set.of()).contains(h.rule())).toList();
    }

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
        for (Allowed standing : STANDING) {
            assertThat(Files.isRegularFile(Path.of(standing.file()))).as("%s exists", standing.file()).isTrue();
            assertThat(standing.reason()).isNotBlank();
        }
        for (String guard : GUARD_TESTS) {
            assertThat(Files.isRegularFile(Path.of(guard))).as("%s exists (remove it from GUARD_TESTS when deleted)", guard).isTrue();
        }
        assertThat(exceptions.stream().map(Allowed::file).distinct().count()).as("one line per file").isEqualTo(exceptions.size());
        assertThat(exceptions.stream().flatMap(e -> e.rules().stream()).collect(Collectors.toSet()))
                .as("the platform.integration names rule never needs an exception").doesNotContain(Rule.EXTERNAL_SYSTEM_NAME);
    }

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
                "package com.forcitis.one.cost.timesheet.application;\nclass Sync { static final String Q = \"select * from a8_view\"; }\n",
                pkg + "StaffMember.java", "package com.forcitis.one.cost.timesheet.application;\nclass StaffMember { }\n"));
        samples.put(Rule.OLD_FRONTEND_SHAPE, Map.of("../frontend/apps/web/src/list.ts", "export const query = { pageNo: 1, pageSize: 20 };\n"));
        samples.put(Rule.OLD_DOCUMENT_FORMAT, Map.of(pkg + "Numbers.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass Numbers { static final String N = \"PSAQU2026\"; }\n"));
        samples.put(Rule.INSTANCE_ID_LITERAL, Map.of(pkg + "Ids.java",
                "package com.forcitis.one.cost.timesheet.application;\nclass Ids { static final long HEAD = -1726439953579618944L; }\n"));
        return samples;
    }

    static final Map<Rule, Map<String, String>> CLEAN_SAMPLES = cleanSamples();

    private static Map<Rule, Map<String, String>> cleanSamples() {
        Map<Rule, Map<String, String>> samples = new EnumMap<>(Rule.class);
        String pkg = "src/main/java/com/forcitis/one/cost/timesheet/application/";
        String head = "package com.forcitis.one.cost.timesheet.application;\n";
        String sql = "src/main/resources/db/migration/cost_timesheet/V20260101000000__cost_timesheet_create_rows.sql";
        samples.put(Rule.OLD_URL_PREFIX, Map.of(pkg + "Paths.java", head + "class Paths { static final String P = \"/api/v1/platform/users\"; }\n"));
        samples.put(Rule.OLD_BASE_TYPE, Map.of(pkg + "Reply.java", head + "class Reply { int pageIndex; String page; }\n"));
        samples.put(Rule.OLD_CLASS_SUFFIX, Map.of(pkg + "RowTranslator.java", head + "class RowTranslator { }\n"));
        samples.put(Rule.OLD_PACKAGE, Map.of(pkg + "Rows.java", head + "class Rows { }\n"));
        samples.put(Rule.OLD_WORKFLOW_CLASS, Map.of(pkg + "BpmnReader.java", head + "class BpmnReader { }\n"));
        samples.put(Rule.OLD_MESSAGING, Map.of(pkg + "Outbox.java", head + "class Outbox { String s = \"outbox\"; }\n"));
        samples.put(Rule.OLD_DDL_COLUMN, Map.of(sql, "CREATE TABLE cost_timesheet.rows (\n    id bigint PRIMARY KEY,\n    created_at timestamptz,\n    updated_at timestamptz\n);\n"));
        samples.put(Rule.OLD_TABLE_PREFIX, Map.of(sql, "CREATE TABLE cost_timesheet.timesheet_rows (\n    id bigint PRIMARY KEY\n);\n"));
        samples.put(Rule.NUMERIC_STATUS, Map.of(sql, "CREATE TABLE cost_timesheet.rows (\n    id bigint PRIMARY KEY,\n    status text NOT NULL,\n"
                + "    row_type text NOT NULL,\n    attempt_count integer NOT NULL\n);\n"));
        samples.put(Rule.OLD_FRAMEWORK_NAME, Map.of(pkg + "Imports.java", head + "import java.util.List;\nclass Imports { }\n"));
        samples.put(Rule.OLD_FIELD_NAME, Map.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml",
                "<process><userTask id=\"a\"><x field=\"projectManagerId\"/></userTask><y n=\"legacyHourTypes\"/></process>\n"));
        samples.put(Rule.OLD_ROLE_CODE, Map.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml",
                "<process><userTask id=\"a\"><x type=\"ROLE\" role=\"reviewer\"/></userTask></process>\n"));
        samples.put(Rule.OLD_RULE_TYPE_NUMBER, Map.of(pkg + "Types.java",
                head + "class Types { String f(int t) { return switch (t) { case 1 -> \"x\"; default -> \"y\"; }; } }\n"));
        samples.put(Rule.EXTERNAL_SYSTEM_NAME, Map.of(pkg + "Remember.java", head + "class Remember { String s = \"remember\"; }\n",
                "../frontend/apps/web/src/login.ts", "export const loginUrl = 'https://login.work.weixin.qq.com';\n",
                "src/main/java/com/forcitis/one/platform/integration/infrastructure/wecom/Endpoint.java",
                "package com.forcitis.one.platform.integration.infrastructure.wecom;\nclass Endpoint { String s = \"qyapi.weixin.qq.com\"; }\n"));
        samples.put(Rule.OLD_FRONTEND_SHAPE, Map.of("../frontend/apps/web/src/list.ts", "export const query = { page: 1, size: 20 };\n",
                "../frontend/packages/api/src/generated/client.ts", "export const old = { pageNo: 1 };\n"));
        samples.put(Rule.OLD_DOCUMENT_FORMAT, Map.of(pkg + "Numbers.java", head + "class Numbers { static final String N = \"{TYPE}-{LE}-{yyyyMM}-{seq:4}\"; String t = \"PSA team\"; }\n"));
        samples.put(Rule.INSTANCE_ID_LITERAL, Map.of(pkg + "Ids.java", head + "class Ids { static final long SHORT = 12345678901234567L; static final long SMALL = 42L; }\n",
                "src/main/java/com/forcitis/one/platform/integration/infrastructure/seeyon/Ids.java",
                "package com.forcitis.one.platform.integration.infrastructure.seeyon;\nclass Ids { static final long HEAD = -1726439953579618944L; }\n",
                "src/main/resources/config/platform_integration.properties", "forcitis.x.head-type-id=-1726439953579618944\n"));
        return samples;
    }

    static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative).normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    @DisplayName("every rule has an injection sample, and each sample fails its rule")
    void everyRuleFailsOnItsSample(@TempDir Path root) throws IOException {
        assertThat(SAMPLES.keySet()).as("a sample for every rule").containsExactlyInAnyOrder(Rule.values());
        for (Map.Entry<Rule, Map<String, String>> sample : SAMPLES.entrySet()) {
            Path project = backendIn(Files.createTempDirectory(root, sample.getKey().name().toLowerCase(Locale.ROOT)));
            for (Map.Entry<String, String> file : sample.getValue().entrySet()) {
                write(project, file.getKey(), file.getValue());
            }

            List<Hit> hits = scan(project);

            assertThat(hits).as("sample of %s", sample.getKey()).isNotEmpty();
            assertThat(hits.stream().map(Hit::rule).collect(Collectors.toSet())).as("rules hit by the sample of %s: %s", sample.getKey(), hits)
                    .contains(sample.getKey());
        }
    }

    static Path backendIn(Path root) throws IOException {
        Path project = Files.createDirectories(root.resolve("backend"));
        write(project, "../frontend/packages/api/openapi.json", "{\"openapi\":\"3.1.0\",\"paths\":{\"/api/v1/cost/timesheets\":{\"get\":{}}}}\n");
        return project;
    }

    @Test
    @DisplayName("GOV-1023-QUA-02：契约缺失不得静默漏扫，失败提示如何生成")
    void missingContractFailsWithGenerationCommand(@TempDir Path root) throws IOException {
        Path project = backendIn(root);
        Files.delete(root.resolve("frontend/packages/api/openapi.json"));
        assertThatThrownBy(() -> scan(project)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bash scripts/codegen/generate_api.sh");
    }

    @Test
    @DisplayName("every rule has a counter-example that must NOT hit it")
    void everyRuleLeavesItsCounterExampleAlone(@TempDir Path root) throws IOException {
        assertThat(CLEAN_SAMPLES.keySet()).as("a counter-example for every rule").containsExactlyInAnyOrder(Rule.values());
        for (Map.Entry<Rule, Map<String, String>> sample : CLEAN_SAMPLES.entrySet()) {
            Path project = backendIn(Files.createTempDirectory(root, sample.getKey().name().toLowerCase(Locale.ROOT)));
            for (Map.Entry<String, String> file : sample.getValue().entrySet()) {
                write(project, file.getKey(), file.getValue());
            }

            List<Hit> hits = scan(project);

            assertThat(hits.stream().map(Hit::rule)).as("counter-example of %s: %s", sample.getKey(), hits).doesNotContain(sample.getKey());
        }
    }

    @Test
    @DisplayName("the front end is scanned (apps and packages src, the API contract) except generated code")
    void frontEndScope(@TempDir Path root) throws IOException {
        Path project = backendIn(root);
        write(project, "../frontend/apps/web/src/a.ts", "const a = { pageNo: 1 };\n");
        write(project, "../frontend/apps/h5/src/b.ts", "const b = { deptId: 1 };\n");
        write(project, "../frontend/packages/ui/src/c.tsx", "const c = mapGetters;\n");
        write(project, "../frontend/packages/api/openapi.json", "{\"x\": \"/system/user\"}\n");
        write(project, "../frontend/packages/api/src/generated/d.ts", "const d = { pageNo: 1 };\n");
        write(project, "../frontend/apps/web/node_modules/e.ts", "const e = { pageNo: 1 };\n");

        assertThat(scan(project)).extracting(Hit::file).contains("../frontend/apps/web/src/a.ts", "../frontend/apps/h5/src/b.ts",
                        "../frontend/packages/ui/src/c.tsx", "../frontend/packages/api/openapi.json")
                .doesNotContain("../frontend/packages/api/src/generated/d.ts").doesNotContain("../frontend/apps/web/node_modules/e.ts");
    }

    @Test
    @DisplayName("an 18 to 19 digit id literal is allowed only in platform_integration.properties and the Seeyon adapters")
    void instanceIdsStayInTheirAreas(@TempDir Path root) throws IOException {
        Path project = backendIn(root);
        String literal = "-1726439953579618944";
        write(project, "src/main/resources/config/platform_integration.properties", "k=" + literal + "\n");
        write(project, "src/main/resources/config/platform_identity.properties", "k=" + literal + "\n");
        write(project, "src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/T.java",
                "package com.forcitis.one.platform.integration.infrastructure.seeyon;\nclass T { long a = " + literal + "L; }\n");
        write(project, "src/main/java/com/forcitis/one/platform/integration/infrastructure/legacy/L.java",
                "package com.forcitis.one.platform.integration.infrastructure.legacy;\nclass L { long a = " + literal + "L; }\n");

        assertThat(scan(project)).filteredOn(h -> h.rule() == Rule.INSTANCE_ID_LITERAL).extracting(Hit::file).containsExactlyInAnyOrder(
                "src/main/resources/config/platform_identity.properties",
                "src/main/java/com/forcitis/one/platform/integration/infrastructure/legacy/L.java");
    }

    @Test
    @DisplayName("a clean tree passes, COMMENT ON lines and Java comments are not matched by the names rule, platform.integration may name external systems")
    void cleanTreeAndAllowances(@TempDir Path root) throws IOException {
        Path project = backendIn(root);
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
    @DisplayName("moduleOf reads the module out of acceptance packages too: acceptance/platform/integration is platform.integration, other acceptance modules stay what they are")
    void moduleOfReadsAcceptancePackages(@TempDir Path root) throws IOException {
        String base = "src/test/java/com/forcitis/one/";
        assertThat(moduleOf(base + "acceptance/platform/integration/StalledExternalSourceIT.java")).isEqualTo("platform.integration");
        assertThat(moduleOf(base + "platform/integration/guard/SomeTest.java")).isEqualTo("platform.integration");
        assertThat(moduleOf(base + "acceptance/organization/workforce/SomeIT.java")).isEqualTo("organization.workforce");
        assertThat(moduleOf(base + "acceptance/kernel/time/SomeIT.java")).isEqualTo("kernel");
        assertThat(moduleOf(base + "LegacyShapeTest.java")).isEmpty();

        Path project = backendIn(root);
        String named = "package %s;\nclass Named { String s = \"seeyon\"; }\n";
        write(project, base + "acceptance/platform/integration/Named.java", named.formatted("com.forcitis.one.acceptance.platform.integration"));
        write(project, base + "acceptance/organization/workforce/Named.java", named.formatted("com.forcitis.one.acceptance.organization.workforce"));

        assertThat(scan(project)).filteredOn(h -> h.rule() == Rule.EXTERNAL_SYSTEM_NAME).extracting(Hit::file)
                .containsExactly(base + "acceptance/organization/workforce/Named.java");
    }

    @Test
    @DisplayName("only the named guard tests are not scanned (they spell what they forbid); other tests in com.forcitis.one and module tests are")
    void guardTestsAreNotScannedButModuleTestsAre(@TempDir Path root) throws IOException {
        Path project = backendIn(root);
        write(project, "src/test/java/com/forcitis/one/DdlNamingTest.java", "package com.forcitis.one;\nclass DdlNamingTest { String s = \"yudao\"; }\n");
        write(project, "src/test/java/com/forcitis/one/SomeNewTest.java", "package com.forcitis.one;\nclass SomeNewTest { String s = \"yudao\"; }\n");
        write(project, "src/test/java/com/forcitis/one/cost/timesheet/SomeTest.java",
                "package com.forcitis.one.cost.timesheet;\nclass SomeTest { String s = \"yudao\"; }\n");

        assertThat(scan(project)).extracting(Hit::file).containsOnly("src/test/java/com/forcitis/one/SomeNewTest.java",
                "src/test/java/com/forcitis/one/cost/timesheet/SomeTest.java");
    }

    @Test
    @DisplayName("an exception covers exactly one file and exactly its listed rules: other files and other rules still fail")
    void exceptionsAreNarrow(@TempDir Path root) throws IOException {
        Path project = backendIn(root);
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
}
