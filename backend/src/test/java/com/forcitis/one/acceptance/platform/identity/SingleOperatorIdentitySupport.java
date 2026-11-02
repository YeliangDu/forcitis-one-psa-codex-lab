package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.SingleOperatorMode;
import com.forcitis.one.platform.identity.application.OperationsCommands;
import com.forcitis.one.platform.identity.IdentityIntegrationTest;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

/**
 * Application-level ground of the P2 acceptance: the read-only mode query of the api is replaced by a switch the test turns, so the relaxations are tried
 * in both modes without the start-up check (which only the start-up acceptance and the end-to-end test run). The switch is read at the moment of use,
 * so the code under test must ask {@link SingleOperatorMode#enabled()} each time it decides and not keep the answer from its construction.
 */
@Import(SingleOperatorIdentitySupport.Switches.class)
public abstract class SingleOperatorIdentitySupport extends IdentityIntegrationTest {

    static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    static final String WITNESS_ID = "01900000-0000-7000-8000-00000000a002";
    static final int REFUSED = 1;
    static final int WRONG_USAGE = 2;

    static final String APPLIED = "platform.identity.single-operator-mode.applied";
    static final String ROLE_GRANTED = "platform.identity.operations.account.role.granted";
    static final String ROLE_REVOKED = "platform.identity.operations.account.role.revoked";
    static final String ADMIN_REGISTERED = "platform.identity.operations.emergency-admin.registered";
    static final String ADMIN_RESET = "platform.identity.operations.emergency-admin.credentials-reset";
    static final String ADMIN_DISABLED = "platform.identity.operations.emergency-admin.disabled";
    static final String ADMIN_ENABLED = "platform.identity.operations.emergency-admin.enabled";
    static final String CHECK_FAILED = "platform.identity.emergency-admin.check-failed";
    static final String WITNESS_REQUIRED = "PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED";

    public static final class ModeSwitch implements SingleOperatorMode {
        private volatile boolean on;

        @Override
        public boolean enabled() {
            return on;
        }

        void turn(boolean value) {
            on = value;
        }
    }

    public static final class EventLog {
        private final List<DomainEvent> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(DomainEvent event) {
            seen.add(event);
        }

        <T extends DomainEvent> List<T> of(Class<T> type) {
            return seen.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Switches {
        @Bean
        @Primary
        ModeSwitch singleOperatorModeSwitch() {
            return new ModeSwitch();
        }

        @Bean
        EventLog singleOperatorEventLog() {
            return new EventLog();
        }
    }

    record Result(int exitCode, String out, String err) {
    }

    @Autowired
    ModeSwitch modeSwitch;

    @Autowired
    EventLog eventLog;

    @Autowired
    OperationsCommands commands;

    @BeforeEach
    void startWithTheModeOff() {
        modeSwitch.turn(false);
    }

    void mode(boolean on) {
        modeSwitch.turn(on);
    }

    Result run(String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = commands.run(new DefaultApplicationArguments(arguments), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    static String[] join(String[] first, String... more) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(List.of(more));
        return all.toArray(String[]::new);
    }

    /** Operator and a different witness given. */
    static String[] attested(String operation, String... more) {
        return join(new String[] {"--operation=" + operation, "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID}, more);
    }

    /** Operator only: the witness is not given at all. */
    static String[] alone(String operation, String... more) {
        return join(new String[] {"--operation=" + operation, "--operator=" + OPERATOR_ID}, more);
    }

    static String employee(long id) {
        return "--employee-id=" + TestAccounts.employee(id).value();
    }

    List<AuditEntry> entries(String action) {
        return recorded().stream().filter(entry -> entry.action().equals(action)).toList();
    }

    List<AuditEntry> applied() {
        return entries(APPLIED);
    }

    boolean holds(long id, String roleCode) {
        return jdbc.sql("""
                select count(*) from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id
                 join platform_identity.user_accounts ua on ua.user_id = ar.user_id where ua.employee_id = :employee and r.code = :code
                   and ar.effective_to_at is null
                """).param("employee", TestAccounts.employee(id).value()).param("code", roleCode).query(Long.class).single() == 1;
    }

    /** Everything the commands could change in the identity tables. */
    List<Long> identityRows() {
        return List.of(count("user_accounts"), count("account_role_grants"), jdbc.sql("select count(*) from platform_identity.account_role_grants where effective_to_at is null").query(Long.class).single(),
                count("emergency_admins"), count("sessions"));
    }

    void assertNothingChanged(List<Long> before) {
        assertThat(identityRows()).as("no row of the identity tables changed").isEqualTo(before);
        assertThat(applied()).as("no relaxation was applied, so none is marked").isEmpty();
    }
}
