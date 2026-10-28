package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * ADR-0034 decision 1 item 3: in production the start-up reconciliation checks the current version of every role, custom ones included,
 * and alerts on each permission code the catalogue does not know or has deprecated; it only alerts, the start goes on. Review 084dce77a,
 * required change 3.
 */
class StartupPermissionCodeScanIntegrationTest extends IdentityIntegrationTest {

    static final String UNKNOWN = "platform.unknown:startup-scan:read";

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("ADR-0034 decision 1 item 3 the start-up reconciliation reads every role's current version: an unknown code of a custom role nobody has read yet is one ALERT with role, version and code, and the start does not fail")
    void theStartUpReconciliationAlertsOnAnUnknownCodeOfACustomRole() {
        UUID roleId = TestRoles.insertCustom(jdbc, "scanned", "Scanned", "ENABLED", "SELF", UNKNOWN, "platform.approval:task:read");
        UUID versionId = jdbc.sql("select role_version_id from platform_identity.role_versions where role_id = :id and effective_to_at is null")
                .param("id", roleId).query(UUID.class).single();
        SmartInitializingSingleton reconciliation = context.getBean("systemRoleReconciliation", SmartInitializingSingleton.class);

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            assertThatCode(reconciliation::afterSingletonsInstantiated).as("the start goes on").doesNotThrowAnyException();
        } finally {
            root.detachAppender(logged);
            logged.stop();
            TestRoles.deleteCustomRoles(jdbc);
        }

        List<String> alerts = logged.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .map(ILoggingEvent::getFormattedMessage).filter(message -> message.startsWith("ALERT ")).toList();
        assertThat(alerts.stream().filter(alert -> alert.contains(UNKNOWN)))
                .as("one ALERT for the unknown code, naming the role and the version: %s", alerts).singleElement()
                .satisfies(alert -> assertThat(alert).contains(roleId.toString()).contains(versionId.toString()));
        assertThat(alerts).as("the live code of the same version raises nothing").noneMatch(alert -> alert.contains("platform.approval:task:read"));
    }
}
