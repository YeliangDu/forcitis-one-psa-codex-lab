package com.forcitis.one.acceptance.platform.identity.grant_store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.UserId;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * #732 part A, 0034-G6-01 and G6-02 (ADR-0034 decision 4 items 3 to 5; DD sign-off section D: read-only, no new table or view). The read model
 * {@code AuthorizationHistory} has no api package yet and the specs fix no signature, so the test finds the bean by that simple name and the one
 * method that takes a user (UserId or UUID) and a moment (Instant or OffsetDateTime), and reads the answer as JSON without naming a field: it
 * looks for the role code and, in the same object, for the role's name, scope kind and permission codes of that moment. See the assumed
 * contract in the PR description.
 */
class AuthorizationHistoryAsOfIT extends GrantStoreSupport {

    static final TestDatabase postgres = TestPostgres.database(AuthorizationHistoryAsOfIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    JsonMapper json;

    private Object history() {
        List<Object> found = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type != null && type.getSimpleName().equals("AuthorizationHistory")) {
                found.add(context.getBean(name));
            }
        }
        if (found.size() != 1) {
            fail("expected exactly one bean named AuthorizationHistory (read-only as-of restore, ADR-0034 decision 4 item 5), found " + found.size());
        }
        return found.getFirst();
    }

    private static boolean isUser(Class<?> type) {
        return type == UserId.class || type == UUID.class;
    }

    private static boolean isMoment(Class<?> type) {
        return type == Instant.class || type == OffsetDateTime.class;
    }

    private Method asOfMethod(Object history) {
        List<Method> candidates = Arrays.stream(history.getClass().getMethods()).filter(m -> !Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class)
                .filter(m -> m.getParameterCount() == 2 && m.getReturnType() != void.class)
                .filter(m -> (isUser(m.getParameterTypes()[0]) && isMoment(m.getParameterTypes()[1])) || (isMoment(m.getParameterTypes()[0]) && isUser(m.getParameterTypes()[1])))
                .toList();
        if (candidates.size() != 1) {
            fail("AuthorizationHistory must have exactly one public method taking (user, moment) and returning the restored grants, found " + candidates);
        }
        return candidates.getFirst();
    }

    /** Invokes the restore; an exception of the method is handed back as the cause. */
    private String restore(UUID user, Instant moment) throws Throwable {
        Object history = history();
        Method method = asOfMethod(history);
        Object[] arguments = new Object[2];
        for (int i = 0; i < 2; i++) {
            Class<?> type = method.getParameterTypes()[i];
            arguments[i] = type == UserId.class ? new UserId(user) : type == UUID.class ? user : type == Instant.class ? moment : OffsetDateTime.ofInstant(moment, ZoneOffset.UTC);
        }
        try {
            return json.writeValueAsString(method.invoke(history, arguments));
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    /** The strings of the smallest JSON objects that carry the role code as a value; empty when no object does. */
    private static List<Set<String>> objectsOf(String restored, String roleCode) {
        List<Set<String>> found = new ArrayList<>();
        walk(JsonPath.parse(restored).json(), roleCode, found);
        return found;
    }

    private static boolean walk(Object node, String roleCode, List<Set<String>> found) {
        if (node instanceof Map<?, ?> map) {
            boolean deeper = false;
            for (Object child : map.values()) {
                deeper |= walk(child, roleCode, found);
            }
            if (!deeper && map.values().stream().anyMatch(v -> roleCode.equals(v))) {
                Set<String> strings = new HashSet<>();
                strings(map, strings);
                found.add(strings);
                return true;
            }
            return deeper;
        }
        if (node instanceof List<?> list) {
            boolean deeper = false;
            for (Object child : list) {
                deeper |= walk(child, roleCode, found);
            }
            return deeper;
        }
        return false;
    }

    private static void strings(Object node, Set<String> into) {
        if (node instanceof Map<?, ?> map) {
            map.values().forEach(v -> strings(v, into));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> strings(v, into));
        } else if (node instanceof String text) {
            into.add(text);
        }
    }

    private void assertHolds(String restored, String roleCode, String name, String scope, Set<String> codes, Set<String> notCodes, String otherName) {
        List<Set<String>> objects = objectsOf(restored, roleCode);
        assertThat(objects).as("the restore names role %s exactly once: %s", roleCode, restored).hasSize(1);
        Set<String> strings = objects.getFirst();
        assertThat(strings).as(restored).contains(name, scope).containsAll(codes).doesNotContainAnyElementsOf(notCodes).doesNotContain(otherName);
    }

    @Test
    @DisplayName("0034-G6-01 ADR-0034 第 4 项第 3 条：t1 建角色（{A}、SELF）并授予，t2 改为 {A,B}、ALL，t3 离职，t4 管理员重新授予：各区间内还原的角色、名称、范围类型、权限集合逐项等于当时的值；t3 后还原不含该角色；t4 后新开一行并还原出 t4 时的版本；与行谓词一致")
    void theRestoreFollowsRolesAndGrantsThroughTime() throws Throwable {
        SignedIn administrator = administrator();
        SignedIn target = person();
        String a = PLAIN_CODES.get(0);
        String b = PLAIN_CODES.get(1);
        String role = customRole(administrator, "SELF", a);
        UUID roleId = roleId(role);
        Instant t1 = NOW.plus(Duration.ofMinutes(1));
        Instant t2 = NOW.plus(Duration.ofMinutes(10));
        Instant t3 = NOW.plus(Duration.ofMinutes(20));
        Instant t4 = NOW.plus(Duration.ofMinutes(30));
        clock.set(t1);
        replacedRoles(administrator, target.userId(), List.of("employee", role));
        clock.set(t2);
        MockHttpServletResponse read = mvc.perform(get(ROLES + "/" + roleId).with(session(administrator))).andReturn().getResponse();
        MockHttpServletResponse changed = mvc.perform(put(ROLES + "/" + roleId).header("If-Match", read.getHeader("ETag")).with(csrf()).with(session(administrator))
                .contentType(MediaType.APPLICATION_JSON).content(definitionJson(null, "Grant store second name", "ALL", List.of(a, b)))).andReturn().getResponse();
        assertThat(changed.getStatus()).as(changed.getContentAsString()).isEqualTo(200);
        String firstName = "Grant store " + role;
        clock.set(t3);
        publishSeparated(target.employeeId(), newCommandId(), LocalDate.of(2026, 10, 20));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(openGrants(target.userId())).isEmpty());
        clock.set(t4);
        replacedRoles(administrator, target.userId(), List.of("employee", role));
        assertThat(grants(target.userId(), role)).as("the re-grant is a new row").hasSize(2);

        assertHolds(restore(target.userId(), t1.plusSeconds(1)), role, firstName, "SELF", Set.of(a), Set.of(b), "Grant store second name");
        assertHolds(restore(target.userId(), t2.plusSeconds(1)), role, "Grant store second name", "ALL", Set.of(a, b), Set.of(), firstName);
        assertThat(objectsOf(restore(target.userId(), t3.plusSeconds(1)), role)).as("after the separation the role is not held").isEmpty();
        assertHolds(restore(target.userId(), t4.plusSeconds(1)), role, "Grant store second name", "ALL", Set.of(a, b), Set.of(), firstName);
        assertThat(objectsOf(restore(target.userId(), NOW), role)).as("before the grant").isEmpty();
        for (Instant moment : List.of(NOW, t1.plusSeconds(1), t2.plusSeconds(1), t3.plusSeconds(1), t4.plusSeconds(1))) {
            boolean held = grantsAsOf(target.userId(), moment).stream().anyMatch(g -> g.roleCode().equals(role));
            assertThat(!objectsOf(restore(target.userId(), moment), role).isEmpty()).as("as of %s the row predicate says %s", moment, held).isEqualTo(held);
        }
    }

    @Test
    @DisplayName("0034-G6-02 ADR-0034 第 4 项第 3 条：asOf 早于全部记录：失败并写 ALERT，不回退到最早版本、不返回空集合")
    void aMomentBeforeTheEarliestRecordFailsWithAnAlert() throws Throwable {
        SignedIn target = person("business_admin");
        int mark = logMark();

        Throwable failure = null;
        String answer = null;
        try {
            answer = restore(target.userId(), Instant.parse("2000-01-01T00:00:00Z"));
        } catch (Throwable thrown) {
            failure = thrown;
        }

        assertThat(failure).as("the restore of a moment before every record must fail, answered %s", answer).isNotNull();
        assertThat(alertsSince(mark)).as("an ALERT is written").isNotEmpty();
    }

    @Test
    @DisplayName("DD 签发 D：按时刻还原只读，不写任何行（授予行、审计条数不变）")
    void theRestoreWritesNothing() throws Throwable {
        SignedIn target = person("business_admin");
        List<Grant> before = grants(target.userId());
        long total = grantRows();
        long audits = auditCount(REVOKED_ACTION);

        restore(target.userId(), NOW.plusSeconds(1));

        assertThat(grants(target.userId())).isEqualTo(before);
        assertThat(grantRows()).isEqualTo(total);
        assertThat(auditCount(REVOKED_ACTION)).isEqualTo(audits);
    }
}
