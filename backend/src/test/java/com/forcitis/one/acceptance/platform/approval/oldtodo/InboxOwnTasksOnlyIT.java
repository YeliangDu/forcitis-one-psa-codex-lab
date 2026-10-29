package com.forcitis.one.acceptance.platform.approval.oldtodo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * IMP-1021-PAR-01 (split plan F, ADR-0055 decision 6): the inbox holds this system's approval tasks only. The application here has no
 * old to-do connection configured, so what the response says is what the approval module alone produces. The response must not carry
 * the old to-do source or the merge: top level legacyCount and legacyAvailable, per item source and detailUrl.
 */
class InboxOwnTasksOnlyIT extends ApprovalP4Support {

    static final String INBOX = "/api/v1/platform/approval-tasks";
    static final List<String> TOP_LEVEL_OLD_TODO_FIELDS = List.of("legacyCount", "legacyAvailable");
    static final List<String> ITEM_OLD_TODO_FIELDS = List.of("source", "detailUrl");

    static final TestDatabase postgres = TestPostgres.database(InboxOwnTasksOnlyIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApplicationContext context;

    private String inboxOf(SignedIn caller) throws Exception {
        MockHttpServletResponse response = mvc.perform(get(INBOX).with(session(caller))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return response.getContentAsString();
    }

    /** Every object key at any depth of the JSON document. */
    private static void keysOf(Object node, TreeSet<String> keys) {
        if (node instanceof Map<?, ?> object) {
            object.forEach((key, value) -> {
                keys.add(String.valueOf(key));
                keysOf(value, keys);
            });
        } else if (node instanceof List<?> array) {
            array.forEach(element -> keysOf(element, keys));
        }
    }

    private static TreeSet<String> allKeys(String json) {
        TreeSet<String> keys = new TreeSet<>();
        keysOf(JsonPath.parse(json).json(), keys);
        return keys;
    }

    private static List<String> keysNamingLegacy(TreeSet<String> keys) {
        return keys.stream().filter(key -> key.toLowerCase(Locale.ROOT).contains("legacy")).toList();
    }

    @Test
    @DisplayName("F-IMP1021-01 一个待办的收件箱：items 恰为这一个本系统任务，响应里没有 legacyCount、legacyAvailable，条目里没有 source、detailUrl")
    void inboxWithOneTaskCarriesOnlyThatTaskAndNoOldTodoFields() throws Exception {
        UUID task = openTaskIds(startProjectHours(projectManager)).getFirst();

        String body = inboxOf(projectManagerAccount);

        List<String> ids = JsonPath.read(body, "$.items[*].taskId");
        assertThat(ids).containsExactly(task.toString());
        assertThat((Integer) JsonPath.read(body, "$.newCount")).as(body).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.totalCount")).as(body).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body, "$.noticeCount")).as(body).isZero();
        Map<String, Object> top = JsonPath.read(body, "$");
        assertThat(top.keySet()).as("top level fields of %s", body).doesNotContainAnyElementsOf(TOP_LEVEL_OLD_TODO_FIELDS);
        Map<String, Object> item = JsonPath.read(body, "$.items[0]");
        assertThat(item.keySet()).as("item fields of %s", body).doesNotContainAnyElementsOf(ITEM_OLD_TODO_FIELDS);
        assertThat(item.keySet()).as("the approval task fields stay").contains("taskId", "title", "claimed", "category", "overdue");
    }

    @Test
    @DisplayName("F-IMP1021-02 没有待办的收件箱：items 为空，计数为零，没有 legacy 开头的字段")
    void emptyInboxHasNoItemsAndNoOldTodoFields() throws Exception {
        startProjectHours(projectManager);

        String body = inboxOf(initiatorAccount);

        List<Object> items = JsonPath.read(body, "$.items");
        assertThat(items).as(body).isEmpty();
        assertThat((Integer) JsonPath.read(body, "$.newCount")).isZero();
        assertThat((Integer) JsonPath.read(body, "$.totalCount")).isZero();
        assertThat(keysNamingLegacy(allKeys(body))).as(body).isEmpty();
    }

    @Test
    @DisplayName("F-IMP1021-03 两个待办：两个本系统任务都在，总数等于任务数，整个响应树里没有任何含 legacy 的键，也没有 source、detailUrl")
    void twoTasksAreCountedAsTwoAndNothingNamedLegacyAppearsAnywhere() throws Exception {
        List<UUID> first = openTaskIds(startProjectHours(projectManager));
        List<UUID> second = openTaskIds(startProjectHours(projectManager));

        String body = inboxOf(projectManagerAccount);

        List<String> ids = JsonPath.read(body, "$.items[*].taskId");
        assertThat(ids).containsExactlyInAnyOrder(first.getFirst().toString(), second.getFirst().toString());
        assertThat((Integer) JsonPath.read(body, "$.newCount")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(body, "$.totalCount")).isEqualTo(2);
        TreeSet<String> keys = allKeys(body);
        assertThat(keysNamingLegacy(keys)).as(body).isEmpty();
        assertThat(keys).doesNotContainAnyElementsOf(ITEM_OLD_TODO_FIELDS);
    }

    @Test
    @DisplayName("F-IMP1021-04 重复请求：同一收件箱连续读两次，响应正文逐字相同，且没有老待办字段")
    void askingTwiceGivesTheSameBody() throws Exception {
        startProjectHours(projectManager);

        String first = inboxOf(projectManagerAccount);
        String second = inboxOf(projectManagerAccount);

        assertThat(second).isEqualTo(first);
        List<String> oldFields = new ArrayList<>(TOP_LEVEL_OLD_TODO_FIELDS);
        oldFields.addAll(ITEM_OLD_TODO_FIELDS);
        assertThat(allKeys(first)).doesNotContainAnyElementsOf(oldFields);
    }

    @Test
    @DisplayName("F-IMP1021-05 ListInbox 的字段与构造参数里没有 Legacy 开头的依赖，应用上下文里也没有名为 legacyInbox 的 bean")
    void listInboxDoesNotDependOnTheOldToDoPort() throws Exception {
        Class<?> listInbox = Class.forName("com.forcitis.one.platform.approval.application.ListInbox");

        List<String> dependencyTypes = new ArrayList<>();
        for (Field field : listInbox.getDeclaredFields()) {
            dependencyTypes.add(field.getType().getName());
        }
        for (Constructor<?> constructor : listInbox.getDeclaredConstructors()) {
            Arrays.stream(constructor.getParameterTypes()).forEach(type -> dependencyTypes.add(type.getName()));
        }

        assertThat(dependencyTypes).as("types ListInbox holds or is built from").isNotEmpty();
        assertThat(dependencyTypes.stream().filter(name -> name.contains("Legacy")).toList()).isEmpty();
        assertThat(Arrays.stream(context.getBeanDefinitionNames()).filter(name -> name.toLowerCase(Locale.ROOT).contains("legacyinbox")).toList())
                .as("beans of the old to-do reader").isEmpty();
    }
}
