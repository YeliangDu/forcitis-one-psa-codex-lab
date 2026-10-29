package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.ListLimits;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A-3 (no list cut in memory, no export or import cap above the agreed one), G-A2 (a collection answers {items, nextCursor} and takes cursor and limit, judged by the shape of the response, not by a class name)
 * and G-P1 (the size of a page is written once, in kernel, and every limit in the contract says 1..200, default 50).
 * Gaps that exist today are listed in architecture/list-paging-gaps.txt: a new violation fails, and so does a listed gap that is already closed.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
class ListPagingGuardTest {
    static final Path GAPS = Path.of("architecture/list-paging-gaps.txt");
    static final Set<String> OWNERS = Set.of("K-3b-1", "K-3b-2", "K-3b-3", "K-3b-4", "例外");
    static final Set<String> PREFIXES = Set.of("GET ", "com.", "slice:", "config:");
    static final Path CONFIG = Path.of("src/main/resources/config");
    static final Pattern LIST_SIZE_CONSTANT = Pattern.compile("(?i)^(max(imum)?|default)_(limit|page_size|rows|results)$|^page_size$|^(export|import)_(max|limit)$");

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(ListPagingGuardTest.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    @DisplayName("G-A2 every collection GET answers exactly {items, nextCursor} and declares cursor and limit, or is a listed gap")
    void collectionsAnswerTheCursorPageShape() throws Exception {
        JsonNode document = document();
        Set<String> violations = new TreeSet<>();
        int checked = 0;
        for (var path : document.path("paths").properties()) {
            JsonNode get = path.getValue().path("get");
            String last = path.getKey().substring(path.getKey().lastIndexOf('/') + 1);
            if (get.isMissingNode() || last.startsWith("{") || last.contains(":")) {
                continue;
            }
            var content = get.path("responses").path("200").path("content").properties();
            assertThat(content).as(GuardMessage.of("G-A2", "GET " + path.getKey() + " declares no 200 answer with content", "declare the response body of the 200 answer in the controller, a GET answers a body")).isNotEmpty();
            JsonNode schema = resolve(document, content.iterator().next().getValue().path("schema"));
            boolean array = "array".equals(schema.path("type").asString());
            if (!array && !schema.path("properties").has("items")) {
                continue;
            }
            checked++;
            Set<String> properties = new TreeSet<>();
            schema.path("properties").propertyNames().forEach(properties::add);
            Set<String> parameters = new TreeSet<>();
            get.path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asString()));
            if (array || !properties.equals(Set.of("items", "nextCursor")) || !parameters.containsAll(Set.of("cursor", "limit"))) {
                violations.add("GET " + path.getKey());
            }
        }

        assertThat(checked).as("the document has collections").isGreaterThan(10);
        assertGaps("G-A2", violations, "a collection GET does not answer exactly {items, nextCursor} or does not take cursor and limit",
                "make the endpoint answer {items, nextCursor} and take the cursor and limit parameters", "GET ");
    }

    @Test
    @DisplayName("G-P1 the size of a page is a constant of kernel only, and every limit parameter of the contract declares minimum 1, maximum 200, default 50")
    void pageSizeIsWrittenOnceAndTheContractSaysSo() throws Exception {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
        Set<String> constants = new TreeSet<>();
        classes.stream().filter(type -> !type.getPackageName().startsWith("com.forcitis.one.kernel")).forEach(type -> type.getFields().stream()
                .filter(field -> field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC)
                        && field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                        && LIST_SIZE_CONSTANT.matcher(field.getName()).matches())
                .forEach(field -> constants.add(type.getName() + "." + field.getName())));
        assertGaps("G-P1", constants, "a page size constant is defined outside kernel",
                "delete the constant and use kernel ListLimits", "com.");

        JsonNode document = document();
        List<String> wrong = new ArrayList<>();
        int declared = 0;
        for (var path : document.path("paths").properties()) {
            for (var operation : path.getValue().properties()) {
                for (JsonNode parameter : operation.getValue().path("parameters")) {
                    if ("limit".equals(parameter.path("name").asString())) {
                        declared++;
                        JsonNode schema = parameter.path("schema");
                        if (schema.path("minimum").asInt() != 1 || schema.path("maximum").asInt() != 200 || schema.path("default").asInt() != 50) {
                            wrong.add(operation.getKey() + " " + path.getKey() + " " + schema);
                        }
                    }
                }
            }
        }
        assertThat(declared).as("the document declares limit parameters").isGreaterThan(5);
        assertThat(wrong).as(GuardMessage.of("G-P1", "a limit parameter of the contract does not say minimum 1, maximum 200, default 50",
                "declare the limit parameter with ListLimits (minimum 1, maximum 200, default 50) in the controller")).isEmpty();
    }

    @Test
    @DisplayName("A-3 no list is cut in memory (PageRequest.slice callers), and no export or import row cap in the configuration exceeds the agreed one, or is a listed gap")
    void inMemoryPagingAndConfiguredCapsAreListed() throws Exception {
        JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
        Set<String> found = new TreeSet<>();
        classes.stream().filter(type -> !type.isEquivalentTo(PageRequest.class)).forEach(type -> type.getMethodCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().isEquivalentTo(PageRequest.class) && call.getName().equals("slice"))
                .forEach(call -> found.add("slice:" + call.getOriginOwner().getName().replaceAll("\\$.*", ""))));
        for (Path file : configurationFiles()) {
            Properties properties = new Properties();
            if (file.toString().endsWith(".properties")) {
                try (var in = Files.newInputStream(file)) {
                    properties.load(in);
                }
            } else {
                YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
                yaml.setResources(new FileSystemResource(file));
                Properties flat = yaml.getObject();
                if (flat != null) {
                    properties.putAll(flat);
                }
            }
            properties.forEach((key, value) -> {
                String name = key.toString();
                int cap = name.endsWith(".export.max-rows") ? ListLimits.EXPORT_MAX : name.endsWith(".import.max-rows") ? ListLimits.IMPORT_MAX : Integer.MAX_VALUE;
                if (cap != Integer.MAX_VALUE && Integer.parseInt(value.toString().strip()) > cap) {
                    found.add("config:" + name);
                }
            });
        }

        assertGaps("CS-3.10 / A-3", found, "a list is cut in memory with PageRequest.slice, or an export or import row cap in the configuration exceeds the agreed one",
                "page in SQL with the cursor and limit, and set the cap at or below ListLimits.EXPORT_MAX / IMPORT_MAX", "slice:", "config:");
    }

    @Test
    @DisplayName("every line of the gap list is well formed and starts with a prefix a guard reads: a line no guard reads would be silently ignored")
    void everyGapLineIsReadBySomeGuard() throws IOException {
        for (String line : Files.readAllLines(GAPS)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split(" \\| ", -1);
            assertThat(columns).as(GuardMessage.of("G-A2", "a line of " + GAPS + " is not path | owner card | deadline: " + line, "write the line as <path> | <owner card> | <deadline>")).hasSize(3);
            assertThat(PREFIXES.stream().anyMatch(columns[0]::startsWith)).as(GuardMessage.of("G-A2", "a line of " + GAPS + " starts with none of " + PREFIXES + ", so no guard reads it: " + line, "start the path with one of those prefixes")).isTrue();
            assertThat(OWNERS).as(GuardMessage.of("G-A2", "the owner card of a line of " + GAPS + " is unknown: " + line, "use one of the owner cards " + OWNERS)).contains(columns[1].strip());
            assertThat(columns[2].strip()).as(GuardMessage.of("G-A2", "a line of " + GAPS + " has no deadline: " + line, "write the deadline as the third column")).isNotEmpty();
        }
    }

    /** config/*.properties and the application*.properties / .yml / .yaml next to it. */
    private static List<Path> configurationFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (var config = Files.list(CONFIG); var application = Files.list(CONFIG.getParent())) {
            config.filter(f -> f.toString().endsWith(".properties")).forEach(files::add);
            application.filter(f -> f.getFileName().toString().matches("application.*\\.(properties|yml|yaml)")).forEach(files::add);
        }
        assertThat(files).as("configuration files found").isNotEmpty();
        return files;
    }

    private JsonNode document() throws Exception {
        return json.readTree(mvc.perform(get("/v3/api-docs").with(user("1"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode resolve(JsonNode document, JsonNode schema) {
        String ref = schema.path("$ref").asString();
        return ref.isEmpty() ? schema : document.path("components").path("schemas").path(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static void assertGaps(String guard, Set<String> found, String wrong, String fix, String... pathPrefixes) throws IOException {
        Set<String> listed = new TreeSet<>();
        for (String line : Files.readAllLines(GAPS)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String path = line.split(" \\| ", -1)[0];
            if (java.util.Arrays.stream(pathPrefixes).anyMatch(path::startsWith)) {
                assertThat(listed.add(path)).as(GuardMessage.of(guard, path + " is listed twice in " + GAPS, "delete the duplicate line")).isTrue();
            }
        }
        Set<String> added = new TreeSet<>(found);
        added.removeAll(listed);
        Set<String> closed = new TreeSet<>(listed);
        closed.removeAll(found);
        assertThat(added).as(GuardMessage.of(guard, wrong + " (new violations)", fix + "; " + GAPS + " is only for the gaps that exist today, do not add a line")).isEmpty();
        assertThat(closed).as(GuardMessage.of(guard, "a gap listed in " + GAPS + " is already closed", "delete its line from " + GAPS)).isEmpty();
    }
}
