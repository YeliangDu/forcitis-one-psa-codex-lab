package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ClassUtils;

/**
 * R-1b P3b, ADR-0033 section 3 item 6 and RULING-P3B-D8 / RULING-P3B-7: the read-only operations command that lists the employee ids and
 * user ids of the people an external subject (a WeCom user id) belongs to. The provider kinds are
 * external identity values and live in platform.integration only (ADR-0039 items 1 and 4), so the command's entry is there: a bean of
 * platform.integration with {@code int run(ApplicationArguments, PrintStream out, PrintStream err)}, the same shape as the operations
 * commands of identity; it is looked up at run time, and a missing one fails the test.
 */
class ExternalSubjectLookupCommandIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(ExternalSubjectLookupCommandIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** Command and argument names of RULING-P3B-D8 (one-glossary 2026-10-15): the subject and the kind of its identity provider. */
    static final String FIND_ACCOUNTS = "find-accounts-by-external-subject";
    static final String EXTERNAL_SUBJECT = "external-subject";
    static final String PROVIDER_KIND = "provider-kind";
    /** Values of --provider-kind (provider_kind, one-glossary 2026-10-15). */
    static final String WECOM_USER_ID = "WECOM_USER_ID";
    static final String INTEGRATION = "com.forcitis.one.platform.integration";

    @Autowired
    ApplicationContext context;

    UUID unit;

    record Outcome(int exit, String out, String err) {
    }

    @BeforeEach
    void aDepartment() throws SQLException {
        unit = newOrgUnitId();
        orgUnit(unit, null, null);
    }

    private Outcome find(String providerKind, String subject) {
        return command("--" + PROVIDER_KIND + "=" + providerKind, "--" + EXTERNAL_SUBJECT + "=" + subject);
    }

    private Outcome command(String... options) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        List<String> line = new ArrayList<>(List.of("--operation=" + FIND_ACCOUNTS));
        line.addAll(List.of(options));
        ApplicationArguments arguments = new DefaultApplicationArguments(line.toArray(String[]::new));
        int exit = runCommand(arguments, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Outcome(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private int runCommand(ApplicationArguments arguments, PrintStream out, PrintStream err) {
        List<String> seen = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type == null || !ClassUtils.getUserClass(type).getPackageName().startsWith(INTEGRATION)) {
                continue;
            }
            for (Method method : ClassUtils.getUserClass(type).getMethods()) {
                if (method.getName().equals("run") && method.getReturnType() == int.class && method.getParameterCount() == 3
                        && method.getParameterTypes()[0] == ApplicationArguments.class && method.getParameterTypes()[1] == PrintStream.class
                        && method.getParameterTypes()[2] == PrintStream.class) {
                    seen.add(name);
                    try {
                        return (int) method.invoke(context.getBean(name), arguments, out, err);
                    } catch (InvocationTargetException failure) {
                        throw new AssertionError("the command of bean " + name + " threw", failure.getCause());
                    } catch (IllegalAccessException inaccessible) {
                        throw new AssertionError(inaccessible);
                    }
                }
            }
        }
        throw new AssertionError("RULING-P3B-7: no operations command bean in " + INTEGRATION + " with int run(ApplicationArguments, PrintStream, PrintStream) "
                + seen);
    }

    /** Rows of the identity tables a command could write. */
    private List<Integer> identityRows() throws SQLException {
        List<Integer> rows = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity")) {
            for (String table : List.of("user_accounts", "account_role_grants", "emergency_admins", "authorization_requests")) {
                try (PreparedStatement count = identity.prepareStatement("select count(*) from " + table); ResultSet rs = count.executeQuery()) {
                    rs.next();
                    rows.add(rs.getInt(1));
                }
            }
            // the old account_roles count was "roles held": the grant store needs the total (history) and the open rows (held) both
            try (PreparedStatement open = identity.prepareStatement("select count(*) from account_role_grants where effective_to_at is null"); ResultSet rs = open.executeQuery()) {
                rs.next();
                rows.add(rs.getInt(1));
            }
        }
        return rows;
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 3 项第 6 条 / RULING-P3B-D8：只读命令按企业微信 userid 返回员工 ID 与 user_id；查无此人时不列出任何员工")
    void theReadOnlyCommandFindsThePersonByWecomUserId() throws Exception {
        UUID person = newEmployeeId();
        UUID other = newEmployeeId();
        employee(person, unit, null);
        employee(other, unit, null);
        UUID account = signIn(person).userId();
        List<Integer> before = identityRows();

        Outcome found = find(WECOM_USER_ID, wecomUserIdOf(person));
        Outcome nobody = find(WECOM_USER_ID, "acceptance-nobody-" + serialOf(person));

        assertThat(found.exit()).as(found.err()).isZero();
        assertThat(found.out()).contains(person.toString(), account.toString()).doesNotContain(other.toString());
        assertThat(nobody.out()).doesNotContain(person.toString()).doesNotContain(other.toString());
        assertThat(identityRows()).as("a read-only command writes nothing").isEqualTo(before);
    }

    /** Rows of the audit log, read once no event publication is still waiting for or in its listeners (the audit listener is asynchronous). */
    private long auditEntriesSettled() throws SQLException {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(this::noPublicationInFlight);
        try (Connection audit = schemaConnection("platform_audit");
             PreparedStatement count = audit.prepareStatement("select count(*) from audit_entries"); ResultSet rs = count.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean noPublicationInFlight() throws SQLException {
        try (Connection registry = schemaConnection("platform_integration");
             PreparedStatement count = registry.prepareStatement("select count(*) from event_publication where status in ('PUBLISHED', 'PROCESSING', 'RESUBMITTED')");
             ResultSet rs = count.executeQuery()) {
            rs.next();
            return rs.getLong(1) == 0;
        }
    }


    @Test
    @DisplayName("R-1b P3b RULING-P3B-15：--provider-kind 不是 WECOM_USER_ID 时退出码 2（用法错误），不列出任何员工")
    void anUnknownProviderKindIsWrongUsage() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);

        Outcome outcome = find("ACCEPTANCE_UNKNOWN_KIND", wecomUserIdOf(person));

        assertThat(outcome.exit()).as(outcome.err()).isEqualTo(2);
        assertThat(outcome.out()).doesNotContain(person.toString());
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-15：缺 --provider-kind 时退出码 2（用法错误），不按任何一种外部主体查人")
    void aMissingProviderKindIsWrongUsage() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);

        Outcome outcome = command("--" + EXTERNAL_SUBJECT + "=" + wecomUserIdOf(person));

        assertThat(outcome.exit()).as(outcome.err()).isEqualTo(2);
        assertThat(outcome.out()).doesNotContain(person.toString());
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-15：--external-subject 为空白时退出码 2（用法错误）")
    void aBlankExternalSubjectIsWrongUsage() {
        Outcome outcome = find(WECOM_USER_ID, "   ");

        assertThat(outcome.exit()).as(outcome.err()).isEqualTo(2);
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-15：查无此人时退出码 1，不列出任何员工")
    void nobodyFoundEndsWithExitCodeOne() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);

        Outcome outcome = find(WECOM_USER_ID, "acceptance-nobody-" + serialOf(person));

        assertThat(outcome.exit()).as(outcome.err()).isEqualTo(1);
        assertThat(outcome.out()).doesNotContain(person.toString());
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-15 / ADR-0037 只读：找到、查无此人、用法错误三种结果都不写审计，audit_entries 行数运行前后不变")
    void noOutcomeWritesAnAuditEntry() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        long before = auditEntriesSettled();

        Outcome found = find(WECOM_USER_ID, wecomUserIdOf(person));
        Outcome nobody = find(WECOM_USER_ID, "acceptance-nobody-" + serialOf(person));
        Outcome wrongUsage = find("ACCEPTANCE_UNKNOWN_KIND", wecomUserIdOf(person));

        assertThat(List.of(found.exit(), nobody.exit(), wrongUsage.exit())).as(found.err() + nobody.err() + wrongUsage.err()).containsExactly(0, 1, 2);
        assertThat(auditEntriesSettled()).as("a read-only command audits nothing").isEqualTo(before);
    }
}
