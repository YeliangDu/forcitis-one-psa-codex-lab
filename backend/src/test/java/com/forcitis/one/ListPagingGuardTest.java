package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * G-A2 (a collection answers {items, nextCursor} and takes cursor and limit, judged by the shape of the response, not by a class name)
 * and G-P1 (the size of a page is written once, in kernel, and every limit in the contract says 1..200, default 50).
 * Gaps that exist today are listed in architecture/list-paging-gaps.txt: a new violation fails, and so does a listed gap that is already closed.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@Testcontainers
class ListPagingGuardTest {
    static final Path GAPS = Path.of("architecture/list-paging-gaps.txt");
    static final Set<String> OWNERS = Set.of("K-3b-1", "K-3b-2", "K-3b-3", "K-3b-4", "例外");
    static final Pattern LIST_SIZE_CONSTANT = Pattern.compile("(?i)^(max(imum)?|default)_(limit|page_size|rows|results)$|^page_size$|^(export|import)_(max|limit)$");

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

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
            JsonNode schema = resolve(document, get.path("responses").path("200").path("content").properties().iterator().next().getValue().path("schema"));
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
        assertGaps("G-A2", violations, "GET ");
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
        assertGaps("G-P1", constants, "com.");

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
        assertThat(wrong).as("limit parameters that do not say minimum 1 / maximum 200 / default 50").isEmpty();
    }

    private JsonNode document() throws Exception {
        return json.readTree(mvc.perform(get("/v3/api-docs").with(user("1"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode resolve(JsonNode document, JsonNode schema) {
        String ref = schema.path("$ref").asString();
        return ref.isEmpty() ? schema : document.path("components").path("schemas").path(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static void assertGaps(String guard, Set<String> found, String pathPrefix) throws IOException {
        Set<String> listed = new TreeSet<>();
        for (String line : Files.readAllLines(GAPS)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split(" \\| ", -1);
            assertThat(columns).as("%s: path | owner card | deadline", line).hasSize(3);
            assertThat(OWNERS).as("owner of %s", line).contains(columns[1].strip());
            assertThat(columns[2].strip()).as("deadline of %s", line).isNotEmpty();
            if (columns[0].startsWith(pathPrefix)) {
                assertThat(listed.add(columns[0])).as("%s is listed twice", columns[0]).isTrue();
            }
        }
        Set<String> added = new TreeSet<>(found);
        added.removeAll(listed);
        Set<String> closed = new TreeSet<>(listed);
        closed.removeAll(found);
        assertThat(added).as("%s: new violations (fix them; the list is for the gaps that exist today)", guard).isEmpty();
        assertThat(closed).as("%s: listed in %s but already closed (delete the line)", guard, GAPS).isEmpty();
    }
}
