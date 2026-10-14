package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.kernel.DocumentType;
import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Derived from the Modulith module structure and glossary/terms.yaml; no hand-written lists of allowed names. */
class NamingSurfaceTest {
    static final Path MAIN_JAVA = Path.of("src/main/java");
    static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    static final Path MIGRATIONS = MAIN_RESOURCES.resolve("db/migration");

    static final Map<String, String> MODULES = new TreeMap<>();

    static {
        for (ApplicationModule m : ApplicationModules.of(OneApplication.class)) {
            MODULES.put(m.getIdentifier().toString(), m.getBasePackage().getName());
        }
    }

    static final Pattern CAMEL = Pattern.compile("[a-z][a-zA-Z0-9]*");
    static final Pattern PASCAL = Pattern.compile("[A-Z][a-zA-Z0-9]*");
    static final Pattern KEBAB = Pattern.compile("[a-z]+(-[a-z]+)*");
    static final Pattern UPPER_SNAKE = Pattern.compile("[A-Z]+(_[A-Z]+)*");
    static final Pattern DOTTED = Pattern.compile("([a-z]+)\\.([a-z]+)(\\.[a-z][a-z0-9]*)+");

    static final Pattern HTTP_PATH = Pattern.compile(
            "/api/v1/(?<slug>[^/]+)/(?<resource>[^/:{}]+)(?:/\\{(?<id>[^}]+)})?(?::(?<action>[^/]+))?");

    @Test
    void httpPathsAreApiVersionSubdomainSlugPluralGlossaryResourceOptionalIdAndAction() {
        List<String> problems = new ArrayList<>();
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
            if (!c.isAnnotatedWith(RestController.class)) {
                continue;
            }
            Class<?> type = c.reflect();
            String module = moduleOf(type).orElse("?");
            String subdomain = module.substring(0, module.indexOf('.') < 0 ? module.length() : module.indexOf('.'));
            RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            List<String> bases = onClass == null || onClass.path().length == 0 ? List.of("") : List.of(onClass.path());
            for (Method m : type.getDeclaredMethods()) {
                RequestMapping onMethod = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (onMethod == null) {
                    continue;
                }
                String where = type.getSimpleName() + "." + m.getName() + "()";
                List<String> paths = onMethod.path().length == 0 ? List.of("") : List.of(onMethod.path());
                for (String base : bases) {
                    for (String path : paths) {
                        checkHttpPath(where, base + path, module, subdomain, problems);
                    }
                }
            }
        }
        fail("G41 HTTP path must start with /api/v1/ and be /api/v1/{subdomain}/{plural-kebab-resource}[/{id}][:{action}] (NAMING_STANDARD.md §2.3)", problems);
    }

    private static void checkHttpPath(String where, String full, String module, String subdomain, List<String> problems) {
        String at = where + " " + full + ": ";
        Matcher p = HTTP_PATH.matcher(full);
        if (!p.matches()) {
            problems.add(at + "does not match /api/v1/{subdomain}/{resources}[/{id}][:{action}]");
            return;
        }
        if (!p.group("slug").equals(subdomain)) {
            problems.add(at + "second segment '" + p.group("slug") + "' must be the subdomain slug '" + subdomain
                    + "' of module " + module + " (not the BC or a feature name)");
        }
        String resource = p.group("resource");
        if (!KEBAB.matcher(resource).matches()) {
            problems.add(at + "resource '" + resource + "' must be lower kebab-case");
        } else {
            String last = resource.substring(resource.lastIndexOf('-') + 1);
            if (Glossary.singular(last).equals(last)) {
                problems.add(at + "resource '" + resource + "' must be plural (e.g. " + resource + "s)");
            }
            words(at + "resource '" + resource + "' has ", resource, problems);
        }
        if (p.group("id") != null) {
            if (!CAMEL.matcher(p.group("id")).matches()) {
                problems.add(at + "path variable {" + p.group("id") + "} must be camelCase");
            }
            words(at + "path variable {" + p.group("id") + "} has ", p.group("id"), problems);
        }
        if (p.group("action") != null) {
            if (!KEBAB.matcher(p.group("action")).matches()) {
                problems.add(at + "action ':" + p.group("action") + "' must be lower kebab-case");
            }
            words(at + "action ':" + p.group("action") + "' has ", p.group("action"), problems);
        }
    }

    @Test
    void domainEventRecordComponentsAreCamelCaseGlossaryWords() {
        List<String> problems = new ArrayList<>();
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
            if (!c.getPackageName().matches(".*\\.events(\\..*)?") || !c.isRecord()) {
                continue;
            }
            for (RecordComponent rc : c.reflect().getRecordComponents()) {
                String where = c.getSimpleName() + "." + rc.getName() + ": ";
                if (!CAMEL.matcher(rc.getName()).matches()) {
                    problems.add(where + "event payload keys are camelCase (JSON = camelCase everywhere; snake_case only in the database)");
                }
                words(where, rc.getName(), problems);
            }
        }
        fail("G42 domain event components must be camelCase glossary words", problems);
    }

    static final Pattern PERMISSION = Pattern.compile("(?<module>[a-z]+\\.[a-z]+):(?<resource>[a-z]+(?:-[a-z]+)*):(?<action>[a-z]+)");
    static final Pattern AUTHORITY = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");

    @Test
    void permissionCodesAreModuleColonGlossaryResourceColonStandardAction() throws IllegalAccessException {
        List<String> problems = new ArrayList<>();
        Set<String> catalogue = new LinkedHashSet<>();
        for (Field f : Permission.Codes.class.getDeclaredFields()) {
            String code = (String) f.get(null);
            if (!catalogue.add(code)) {
                problems.add("Permission.Codes." + f.getName() + " duplicates code '" + code + "'");
            }
            checkPermission("Permission.Codes." + f.getName() + " = '" + code + "'", code, problems);
        }
        for (Permission p : Permission.values()) {
            if (!catalogue.contains(p.code())) {
                problems.add("Permission." + p.name() + " code '" + p.code() + "' is not a Permission.Codes constant");
            }
        }
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
            for (Method m : c.reflect().getDeclaredMethods()) {
                PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
                if (pre == null) {
                    continue;
                }
                Matcher a = AUTHORITY.matcher(pre.value());
                while (a.find()) {
                    for (String raw : a.group(1).split(",")) {
                        String code = raw.trim().replaceAll("^'|'$", "");
                        if (!catalogue.contains(code)) {
                            problems.add(c.getSimpleName() + "." + m.getName() + "(): @PreAuthorize uses '" + code
                                    + "', which is not in Permission.Codes (add it there and reference the constant)");
                        }
                    }
                }
            }
        }
        fail("G43 permission code must be {subdomain}.{bc}:{resource}:{action} (lower case, glossary resource, action from "
                + "standard_actions " + Glossary.standardActions() + ")", problems);
    }

    private static void checkPermission(String where, String code, List<String> problems) {
        Matcher p = PERMISSION.matcher(code);
        if (!p.matches()) {
            problems.add(where + ": not {subdomain}.{bc}:{kebab-resource}:{action}");
            return;
        }
        if (!MODULES.containsKey(p.group("module"))) {
            problems.add(where + ": '" + p.group("module") + "' is not a module; use one of " + businessModules());
        }
        words(where + ": resource has ", p.group("resource"), problems);
        if (!Glossary.standardActions().contains(p.group("action"))) {
            problems.add(where + ": action '" + p.group("action") + "' is not in standard_actions " + Glossary.standardActions()
                    + " (glossary/terms.yaml)");
        }
    }

    static final Pattern MIGRATION = Pattern.compile("V(?<ts>\\d{14})__(?<module>[a-z]+_[a-z]+)_(?<description>.+)\\.sql");
    static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);

    @Test
    void flywayFilesAreTimestampModuleEnglishSnakeCaseInTheModuleFolder() throws IOException {
        List<String> problems = new ArrayList<>();
        Map<String, Path> versions = new LinkedHashMap<>();
        Set<String> schemas = MODULES.keySet().stream().filter(m -> m.contains(".")).map(m -> m.replace('.', '_'))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Path file : files(MIGRATIONS, ".sql")) {
            String folder = file.getParent().getFileName().toString();
            String name = file.getFileName().toString();
            String where = MIGRATIONS.relativize(file) + ": ";
            if (!schemas.contains(folder)) {
                problems.add(where + "folder '" + folder + "' is not a module schema; use one of " + schemas);
            }
            Matcher m = MIGRATION.matcher(name);
            if (!m.matches()) {
                problems.add(where + "name must be V{yyyyMMddHHmmss}__" + folder + "_{english_snake_case}.sql");
                continue;
            }
            try {
                LocalDateTime.parse(m.group("ts"), TIMESTAMP);
            } catch (DateTimeParseException e) {
                problems.add(where + "version " + m.group("ts") + " is not a real yyyyMMddHHmmss timestamp");
            }
            Path clash = versions.put(m.group("ts"), file);
            if (clash != null) {
                problems.add(where + "version " + m.group("ts") + " is also used by " + MIGRATIONS.relativize(clash));
            }
            if (!m.group("module").equals(folder)) {
                problems.add(where + "module prefix '" + m.group("module") + "' must be the folder's module '" + folder + "'");
            }
            if (!m.group("description").matches("[a-z]+(_[a-z]+)*")) {
                problems.add(where + "description '" + m.group("description")
                        + "' must be English snake_case: lower-case ASCII letters and single underscores only");
            }
        }
        fail("G44 Flyway file must be V{14-digit timestamp}__{module}_{english_snake_case}.sql in db/migration/{module}/", problems);
    }

    @Test
    void numberingDocumentTypesAreUpperSnakeGlossaryWordsAndEachHasExactlyTheSeededRule() throws IOException {
        List<String> problems = new ArrayList<>();
        Set<String> declared = Arrays.stream(DocumentType.values()).map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> seeded = insertedValues("platform_numbering", "numbering_rules", "document_type");
        Set<String> all = new LinkedHashSet<>(declared);
        all.addAll(seeded.keySet());
        for (String code : all) {
            String where = "document type '" + code + "'" + (seeded.containsKey(code) ? " (seeded in " + seeded.get(code) + ")" : "") + ": ";
            if (!UPPER_SNAKE.matcher(code).matches()) {
                problems.add(where + "must be UPPER_SNAKE_CASE, e.g. JOURNAL_VOUCHER");
            }
            words(where, code, problems);
            if (!declared.contains(code)) {
                problems.add(where + "seeded but not a DocumentType constant (DocumentType.valueOf would fail at runtime)");
            }
            if (!seeded.containsKey(code)) {
                problems.add(where + "DocumentType constant without a seeded numbering_rules row; add one in a new platform_numbering migration");
            }
        }
        fail("G45 numbering rule codes (platform.numbering DocumentType + numbering_rules seed)", problems);
    }

    static final Pattern METER_BUILDER = Pattern.compile(
            "\\b(Counter|Gauge|Timer|DistributionSummary|LongTaskTimer|FunctionCounter|FunctionTimer|TimeGauge)\\.builder\\(\\s*([^,)]*)");
    static final Pattern METER_SHORTCUT = Pattern.compile("\\.(counter|timer|gauge|summary)\\(\\s*\"([^\"]*)\"");
    static final Pattern METER_ANNOTATION = Pattern.compile("@(Timed|Counted|Observed)\\(\\s*(?:(?:value|name)\\s*=\\s*)?\"([^\"]*)\"");
    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");
    static final Pattern PROPERTIES_PREFIX = Pattern.compile("@ConfigurationProperties\\(\\s*(?:(?:prefix|value)\\s*=\\s*)?\"([^\"]+)\"");
    static final Pattern OWN_KEY = Pattern.compile("forcitis\\.(?<module>[a-z]+\\.[a-z]+)(?<rest>(\\.[a-z]+(-[a-z]+)*)+)");

    @Test
    void scheduledJobsAndTheirLockNamesAreModuleDottedLowerCase() {
        List<String> problems = new ArrayList<>();
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
            Class<?> type = c.reflect();
            for (Method m : type.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(m, Scheduled.class) == null) {
                    continue;
                }
                String where = type.getSimpleName() + "." + m.getName() + "()";
                SchedulerLock lock = m.getAnnotation(SchedulerLock.class);
                if (lock == null) {
                    problems.add(where + ": @Scheduled job without @SchedulerLock(name = \"{subdomain}.{bc}.{name}\") (runs on every node)");
                    continue;
                }
                checkDottedName(where + " lock/job '" + lock.name() + "'", lock.name(), moduleOf(type).orElse("?"), problems);
            }
        }
        for (Map.Entry<String, String> jobType : allInsertedValues("job_type").entrySet()) {
            checkDottedName("job_type '" + jobType.getKey() + "' in " + jobType.getValue(), jobType.getKey(), null, problems);
        }
        fail("G46 job / ShedLock lock / job_type names must be {subdomain}.{bc}.{name} (dot-separated lower case)", problems);
    }

    @Test
    void metricNamesAreModuleDottedLowerCaseStringLiterals() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : files(MAIN_JAVA, ".java")) {
            String source = Files.readString(file);
            String module = moduleOfSource(file).orElse("?");
            String where = MAIN_JAVA.relativize(file).toString();
            Matcher b = METER_BUILDER.matcher(source);
            while (b.find()) {
                String arg = b.group(2).trim();
                if (!arg.matches("\"[^\"]*\"")) {
                    problems.add(where + ": " + b.group(1) + ".builder(" + arg + ") - pass the meter name as a string literal so it can be checked");
                } else {
                    checkMetricName(where + " metric " + arg, arg.substring(1, arg.length() - 1), module, problems);
                }
            }
            for (Pattern p : List.of(METER_SHORTCUT, METER_ANNOTATION)) {
                Matcher s = p.matcher(source);
                while (s.find()) {
                    checkMetricName(where + " metric \"" + s.group(2) + "\"", s.group(2), module, problems);
                }
            }
        }
        fail("G46 Micrometer meter names must be {subdomain}.{bc}.{name} of the declaring module (dot-separated lower case)", problems);
    }

    @Test
    void metricNameGuardCatchesAMissingPrefixAnAlienModuleAndTheWrongOwner() {
        List<String> problems = new ArrayList<>();
        checkMetricName("A", "platform.numbering.counters", "platform.numbering", problems);
        checkMetricName("B", "forcitis.platform.nothing.counters", "platform.nothing", problems);
        checkMetricName("C", "forcitis.platform.audit.entries", "platform.numbering", problems);
        checkMetricName("D", "forcitis.platform.numbering.counters.near.capacity", "platform.numbering", problems);
        assertThat(problems).hasSize(3);
        assertThat(problems.getFirst()).contains("forcitis.");
    }

    @Test
    void ownConfigurationKeysStartWithForcitisModulePrefixAndOthersAreKnownFrameworkKeys() throws IOException {
        List<String> problems = new ArrayList<>();
        Map<String, String> known = frameworkKeys();
        for (Path file : files(MAIN_RESOURCES, ".properties")) {
            if (!file.getFileName().toString().startsWith("application")) {
                continue;
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
            for (String key : new TreeMap<>(Map.copyOf(props.entrySet().stream()
                    .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> String.valueOf(e.getValue()))))).keySet()) {
                checkConfigKey(MAIN_RESOURCES.relativize(file) + " key '" + key + "'", key, null, known, problems);
            }
        }
        for (Path file : files(MAIN_JAVA, ".java")) {
            String source = Files.readString(file);
            String module = moduleOfSource(file).orElse("?");
            String where = MAIN_JAVA.relativize(file).toString();
            for (Pattern p : List.of(PLACEHOLDER, PROPERTIES_PREFIX)) {
                Matcher m = p.matcher(source);
                while (m.find()) {
                    checkConfigKey(where + " key '" + m.group(1) + "'", m.group(1), module, known, problems);
                }
            }
        }
        fail("G46 own configuration keys must be forcitis.{subdomain}.{bc}.{kebab-case} of the reading module; "
                + "any other key must be a framework key from spring-configuration-metadata.json", problems);
    }

    private static void checkConfigKey(String where, String key, String module, Map<String, String> known, List<String> problems) {
        if (key.startsWith("forcitis.kernel.")) {
            String rest = key.substring("forcitis.kernel.".length());
            if (!KEBAB.matcher(rest).matches()) {
                problems.add(where + ": must be forcitis.kernel.{kebab-case}");
            }
            words(where + ": ", rest, problems);
            return;
        }
        if (key.startsWith("forcitis.")) {
            Matcher k = OWN_KEY.matcher(key);
            if (!k.matches()) {
                problems.add(where + ": must be forcitis.{subdomain}.{bc}.{kebab-case}[.{kebab-case}...]");
                return;
            }
            if (!MODULES.containsKey(k.group("module"))) {
                problems.add(where + ": '" + k.group("module") + "' is not a module; use one of " + businessModules());
            } else if (module != null && !module.equals(k.group("module"))) {
                problems.add(where + ": read by module " + module + ", so it must start with forcitis." + module + ".");
            }
            words(where + ": ", k.group("rest"), problems);
        } else if (!isFrameworkKey(key, known)) {
            problems.add(where + ": not a known framework property; own keys go under forcitis.{subdomain}.{bc}."
                    + (module != null && module.contains(".") ? " (here: forcitis." + module + ".)" : ""));
        }
    }

    private static Map<String, String> frameworkKeys() throws IOException {
        Map<String, String> keys = new LinkedHashMap<>();
        JsonMapper json = JsonMapper.builder().build();
        for (String resource : List.of("META-INF/spring-configuration-metadata.json", "META-INF/additional-spring-configuration-metadata.json")) {
            Enumeration<URL> urls = NamingSurfaceTest.class.getClassLoader().getResources(resource);
            for (URL url : Collections.list(urls)) {
                try (InputStream in = url.openStream()) {
                    JsonNode root = json.readTree(in);
                    for (JsonNode p : root.path("properties")) {
                        keys.put(normalize(p.path("name").asString()), p.path("type").asString(""));
                    }
                }
            }
        }
        return keys;
    }

    private static boolean isFrameworkKey(String key, Map<String, String> known) {
        String k = normalize(key);
        if (known.containsKey(k)) {
            return true;
        }
        return known.entrySet().stream().anyMatch(e -> (e.getValue().startsWith("java.util.Map") || e.getValue().startsWith("java.util.List"))
                && (k.startsWith(e.getKey() + ".") || k.startsWith(e.getKey() + "[")));
    }

    private static String normalize(String key) {
        return key.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    /** platform.approval is renamed by its own work stream; remove the entry when that merges. */
    static final String PENDING_METRIC_RENAME = "platform/approval/";

    static void checkMetricName(String where, String name, String module, List<String> problems) {
        if (where.contains(PENDING_METRIC_RENAME)) {
            return;
        }
        if (!name.startsWith("forcitis.")) {
            problems.add(where + ": a metric name starts with forcitis. (forcitis.{subdomain}.{bc}.{name})");
            return;
        }
        checkDottedName(where, name.substring("forcitis.".length()), module, problems);
    }

    private static void checkDottedName(String where, String name, String module, List<String> problems) {
        Matcher d = DOTTED.matcher(name);
        if (!d.matches()) {
            problems.add(where + ": must be {subdomain}.{bc}.{name} in dot-separated lower case, e.g. platform.numbering.counter.capacity.check");
            return;
        }
        String owner = d.group(1) + "." + d.group(2);
        if (!MODULES.containsKey(owner)) {
            problems.add(where + ": '" + owner + "' is not a module; use one of " + businessModules());
        } else if (module != null && !module.equals(owner)) {
            problems.add(where + ": declared in module " + module + ", so it must start with " + module + ".");
        }
        words(where + ": ", name.substring(owner.length() + 1), problems);
    }

    static final Pattern DEFINITION_ID = Pattern.compile("(?<subdomain>[a-z]+)_(?<bc>[a-z]+)_(?<name>[a-z]+(?:_[a-z]+)*)");

    @Test
    void bpmnProcessAndDmnDecisionIdsAreModuleSnakeAndUserTasksCamelCaseGlossaryWords() throws Exception {
        List<String> problems = new ArrayList<>();
        Set<String> processIds = new LinkedHashSet<>();
        List<Path> definitions = files(MAIN_RESOURCES, "").stream()
                .filter(f -> f.toString().matches(".*\\.(bpmn|bpmn20\\.xml|dmn|dmn\\.xml)$")).toList();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        for (Path file : definitions) {
            String where = MAIN_RESOURCES.relativize(file).toString();
            String baseName = file.getFileName().toString().replaceFirst("\\.(bpmn|bpmn20\\.xml|dmn|dmn\\.xml)$", "");
            org.w3c.dom.Document xml = factory.newDocumentBuilder().parse(file.toFile());
            List<String> topIds = new ArrayList<>();
            for (String kind : List.of("process", "decision")) {
                NodeList nodes = xml.getElementsByTagNameNS("*", kind);
                for (int i = 0; i < nodes.getLength(); i++) {
                    String id = ((Element) nodes.item(i)).getAttribute("id");
                    topIds.add(id);
                    if (kind.equals("process")) {
                        processIds.add(id);
                    }
                    checkDefinitionId(where + " " + kind + " id '" + id + "'", id, problems);
                }
            }
            if (topIds.size() == 1 && !topIds.getFirst().equals(baseName)) {
                problems.add(where + ": file name must be the definition id: " + topIds.getFirst() + file.getFileName().toString().substring(baseName.length()));
            }
            NodeList tasks = xml.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < tasks.getLength(); i++) {
                String id = ((Element) tasks.item(i)).getAttribute("id");
                if (!CAMEL.matcher(id).matches()) {
                    problems.add(where + " userTask id '" + id + "': must be camelCase, e.g. approverReview");
                }
                words(where + " userTask id '" + id + "': ", id, problems);
            }
        }
        for (ProcessKey key : ProcessKey.values()) {
            if (!processIds.contains(key.key())) {
                problems.add("ProcessKey." + key.name() + ": no deployed BPMN process with id '" + key.key() + "'");
            }
        }
        Set<String> keys = Arrays.stream(ProcessKey.values()).map(ProcessKey::key).collect(Collectors.toSet());
        processIds.stream().filter(id -> !keys.contains(id))
                .forEach(id -> problems.add("process '" + id + "': add ProcessKey." + id.toUpperCase(Locale.ROOT) + " (typed catalogue)"));
        fail("G47 BPMN process / DMN decision id must be {subdomain}_{bc}_{snake_name} of the owning module "
                + "(= lower-case ProcessKey constant); userTask ids camelCase glossary words", problems);
    }

    private static void checkDefinitionId(String where, String id, List<String> problems) {
        Matcher d = DEFINITION_ID.matcher(id);
        if (!d.matches()) {
            problems.add(where + ": must be {subdomain}_{bc}_{snake_name}, e.g. cost_timesheet_approval");
            return;
        }
        String owner = d.group("subdomain") + "." + d.group("bc");
        if (!MODULES.containsKey(owner)) {
            problems.add(where + ": '" + owner + "' is not a module; use one of " + businessModules());
        }
        words(where + ": ", d.group("name"), problems);
    }

    @Test
    void errorCodesAreEnumConstantsNamedSubdomainBcReasonInUpperSnakeGlossaryWords() {
        List<String> problems = new ArrayList<>();
        int found = 0;
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
            if (!c.isAssignableTo(ErrorCode.class) || c.isEquivalentTo(ErrorCode.class)) {
                continue;
            }
            Class<?> type = c.reflect();
            String module = moduleOf(type).orElse("?");
            if (!type.isEnum()) {
                problems.add(type.getName() + ": error codes are ONE enum per module implementing ErrorCode (a closed set), not a "
                        + (type.isRecord() ? "record" : type.isInterface() ? "interface" : "class"));
                continue;
            }
            String prefix = module.toUpperCase(Locale.ROOT).replace('.', '_') + "_";
            for (Object constant : type.getEnumConstants()) {
                found++;
                String code = ((ErrorCode) constant).code();
                String where = type.getSimpleName() + "." + ((Enum<?>) constant).name() + " code '" + code + "': ";
                if (!UPPER_SNAKE.matcher(code).matches()) {
                    problems.add(where + "must be UPPER_SNAKE_CASE");
                }
                if (!code.startsWith(prefix) || code.length() == prefix.length()) {
                    problems.add(where + "must be " + prefix + "{REASON} (declared in module " + module + ")");
                } else {
                    words(where + "reason has ", code.substring(prefix.length()), problems);
                }
            }
        }
        if (found == 0) {
            problems.add("no ErrorCode enum found: the guard would pass vacuously");
        }
        fail("G48 error code must be {SUBDOMAIN}_{BC}_{REASON} (upper snake_case, glossary words)", problems);
    }

    private static void words(String where, String identifier, List<String> problems) {
        List<String> missing = Glossary.unregisteredWords(identifier);
        if (!missing.isEmpty()) {
            problems.add(where + Glossary.registerHint(missing));
        }
    }

    private static void fail(String rule, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new AssertionError(rule + ":\n  " + String.join("\n  ", new LinkedHashSet<>(problems)));
        }
    }

    static Optional<String> moduleOf(Class<?> type) {
        return moduleOfPackage(type.getPackageName());
    }

    static Optional<String> moduleOfPackage(String pkg) {
        return MODULES.entrySet().stream()
                .filter(e -> pkg.equals(e.getValue()) || pkg.startsWith(e.getValue() + "."))
                .max(Comparator.comparingInt(e -> e.getValue().length()))
                .map(Map.Entry::getKey);
    }

    private static Optional<String> moduleOfSource(Path javaFile) {
        Path dir = MAIN_JAVA.relativize(javaFile).getParent();
        return dir == null ? Optional.empty() : moduleOfPackage(dir.toString().replace('/', '.'));
    }

    private static List<String> businessModules() {
        return MODULES.keySet().stream().filter(m -> m.contains(".")).toList();
    }

    private static List<Path> files(Path root, String suffix) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(suffix)).sorted().toList();
        }
    }

    static final Pattern INSERT = Pattern.compile("INSERT INTO\\s+([a-z0-9_]+)\\.([a-z0-9_]+)\\s*\\(([^)]*)\\)\\s*VALUES\\s*(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    static final Pattern TUPLE = Pattern.compile("\\(((?:[^()']|'(?:[^']|'')*')*)\\)");

    private static Map<String, String> insertedValues(String schema, String table, String column) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (Path file : files(MIGRATIONS, ".sql")) {
            Matcher i = INSERT.matcher(Files.readString(file));
            while (i.find()) {
                if (schema != null && !(i.group(1).equalsIgnoreCase(schema) && i.group(2).equalsIgnoreCase(table))) {
                    continue;
                }
                List<String> columns = Arrays.stream(i.group(3).split(",")).map(String::trim).toList();
                int at = columns.indexOf(column);
                if (at < 0) {
                    continue;
                }
                Matcher t = TUPLE.matcher(i.group(4));
                while (t.find()) {
                    List<String> cells = splitCells(t.group(1));
                    if (at < cells.size()) {
                        values.put(cells.get(at).replaceAll("^'|'$", "").replace("''", "'"), file.getFileName().toString());
                    }
                }
            }
        }
        return values;
    }

    private static Map<String, String> allInsertedValues(String column) {
        try {
            return insertedValues(null, null, column);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> splitCells(String tuple) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (char ch : tuple.toCharArray()) {
            if (ch == '\'') {
                quoted = !quoted;
            }
            if (ch == ',' && !quoted) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }
}
