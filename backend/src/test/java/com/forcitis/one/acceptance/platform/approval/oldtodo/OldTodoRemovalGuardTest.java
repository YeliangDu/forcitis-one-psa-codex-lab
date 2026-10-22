package com.forcitis.one.acceptance.platform.approval.oldtodo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IMP-1021-PAR-01 (split plan F, ADR-0055 decision 6): the old to-do merge is gone from the backend. Judged on the main sources, the
 * compiled main classes, the external read register, the main configuration and the OpenAPI file. The department short names
 * (LegacyOrgNames and the like) are plan G and are not judged here. Paths are relative to the backend directory, where Maven runs.
 */
class OldTodoRemovalGuardTest {

    static final Path MAIN = Path.of("src/main/java");
    static final Path RESOURCES = Path.of("src/main/resources");
    static final Path REGISTRY = Path.of("architecture/external-read-registry.yaml");
    static final Path OPENAPI = Path.of("../frontend/packages/api/openapi.json");
    static final Path APPROVAL = MAIN.resolve("com/forcitis/one/platform/approval");

    static final List<String> REMOVED_TYPES = List.of("JdbcLegacyInbox", "LegacyInbox", "InboxMerger", "LegacyTodo", "LegacyTodoRow",
            "LegacyInboxConfiguration", "LegacyInboxProperties");
    static final Pattern REMOVED_TYPE_MENTION = Pattern.compile("\\b(" + String.join("|", REMOVED_TYPES) + ")\\b");
    static final Pattern LEGACY_SOURCE_VALUE = Pattern.compile("\\bLEGACY\\b");

    private static List<Path> files(Path root, String suffix) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(suffix)).sorted().toList();
        }
    }

    @Test
    @DisplayName("F-IMP1021-10 src/main/java 里没有 JdbcLegacyInbox、LegacyInbox、InboxMerger、LegacyTodo（及 LegacyTodoRow、LegacyInboxConfiguration、LegacyInboxProperties）的源文件")
    void noSourceFileOfTheOldToDoTypes() throws IOException {
        List<Path> javaFiles = files(MAIN, ".java");
        assertThat(javaFiles).as("main sources were found").hasSizeGreaterThan(100);

        List<String> found = javaFiles.stream().filter(path -> REMOVED_TYPES.contains(path.getFileName().toString().replace(".java", "")))
                .map(Path::toString).toList();

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("F-IMP1021-11 编译产物里没有上述老待办类（含嵌套类）")
    void noCompiledClassOfTheOldToDoTypes() {
        JavaClasses main = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
        assertThat(main.contain("com.forcitis.one.platform.approval.application.ListInbox")).as("main classes were imported").isTrue();

        TreeSet<String> found = new TreeSet<>();
        for (JavaClass type : main) {
            String top = type.getName().replaceAll("\\$.*", "");
            if (REMOVED_TYPES.contains(top.substring(top.lastIndexOf('.') + 1))) {
                found.add(type.getName());
            }
        }

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("F-IMP1021-12 main 的任何源文件都不再提到这些老待办类型")
    void noMainSourceMentionsTheOldToDoTypes() throws IOException {
        List<String> mentions = new ArrayList<>();
        for (Path path : files(MAIN, ".java")) {
            var matcher = REMOVED_TYPE_MENTION.matcher(Files.readString(path));
            if (matcher.find()) {
                mentions.add(path + " -> " + matcher.group());
            }
        }

        assertThat(mentions).isEmpty();
    }

    @Test
    @DisplayName("F-IMP1021-13 approval 的收件箱类型（ListInbox、MergedInbox、InboxItem、InboxResponse、InboxItemResponse）里没有 legacyCount、legacyAvailable、detailUrl、InboxSource，approval 源码里没有 LEGACY 来源取值")
    void approvalInboxTypesCarryNoOldToDoSource() throws IOException {
        List<String> inboxFiles = List.of("ListInbox", "MergedInbox", "InboxItem", "InboxResponse", "InboxItemResponse");
        Pattern oldFields = Pattern.compile("legacyCount|legacyAvailable|detailUrl|InboxSource|\\bLEGACY\\b");
        List<String> mentions = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Path path : files(APPROVAL, ".java")) {
            String name = path.getFileName().toString().replace(".java", "");
            String text = Files.readString(path);
            if (inboxFiles.contains(name)) {
                seen.add(name);
                var matcher = oldFields.matcher(text);
                if (matcher.find()) {
                    mentions.add(name + " -> " + matcher.group());
                }
            } else if (LEGACY_SOURCE_VALUE.matcher(text).find()) {
                mentions.add(name + " -> LEGACY");
            }
        }

        assertThat(seen).as("the inbox types are still there to be judged").contains("ListInbox", "MergedInbox", "InboxResponse", "InboxItemResponse");
        assertThat(mentions).isEmpty();
    }

    @Test
    @DisplayName("F-IMP1021-14 external-read-registry.yaml 里没有用途 RUNTIME_TODO 的条目，也没有 v_psa_one_legacy_todo 对象")
    void noRuntimeTodoEntryInTheExternalReadRegister() throws IOException {
        List<String> lines = Files.readAllLines(REGISTRY);
        List<String> entries = lines.stream().filter(line -> line.stripLeading().startsWith("- {")).toList();
        assertThat(entries).as("the register still has entries (the department sync reads stay)").hasSizeGreaterThan(5);

        assertThat(entries.stream().filter(line -> line.contains("purpose: RUNTIME_TODO")).toList()).isEmpty();
        assertThat(entries.stream().filter(line -> line.contains("v_psa_one_legacy_todo")).toList()).isEmpty();
    }

    @Test
    @DisplayName("F-IMP1021-15 OpenAPI：InboxResponse 没有 legacyCount、legacyAvailable，InboxItemResponse 没有 source、detailUrl，计数与任务字段仍在")
    void openApiInboxModelHasNoOldToDoFields() throws IOException {
        JsonNode schemas = new ObjectMapper().readTree(Files.readString(OPENAPI)).path("components").path("schemas");
        JsonNode response = schemas.path("InboxResponse");
        JsonNode item = schemas.path("InboxItemResponse");
        assertThat(response.isObject()).as("InboxResponse is in the OpenAPI file").isTrue();
        assertThat(item.isObject()).as("InboxItemResponse is in the OpenAPI file").isTrue();

        TreeSet<String> responseFields = new TreeSet<>();
        response.path("properties").fieldNames().forEachRemaining(responseFields::add);
        TreeSet<String> itemFields = new TreeSet<>();
        item.path("properties").fieldNames().forEachRemaining(itemFields::add);
        TreeSet<String> required = new TreeSet<>();
        response.path("required").forEach(node -> required.add(node.asText()));

        assertThat(responseFields).contains("items", "newCount", "noticeCount", "totalCount").doesNotContain("legacyCount", "legacyAvailable");
        assertThat(required).doesNotContain("legacyCount", "legacyAvailable");
        assertThat(itemFields).contains("taskId", "title", "claimed", "category", "overdue").doesNotContain("source", "detailUrl");
    }

    @Test
    @DisplayName("F-IMP1021-16 main 的资源配置里没有 legacy.inbox 开头的键（老待办连接配置）")
    void noOldToDoConnectionSettingsInMainResources() throws IOException {
        List<Path> configs = new ArrayList<>(files(RESOURCES, ".properties"));
        configs.addAll(files(RESOURCES, ".yaml"));
        configs.addAll(files(RESOURCES, ".yml"));
        assertThat(configs).as("main configuration files were found").isNotEmpty();

        List<String> found = new ArrayList<>();
        for (Path path : configs) {
            for (String line : Files.readAllLines(path)) {
                String code = line.stripLeading();
                if (!code.startsWith("#") && code.contains("legacy.inbox")) {
                    found.add(path + ": " + code);
                }
            }
        }

        assertThat(found).isEmpty();
    }
}
