package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1151 user decision Q3 (completion line 6): when the escalation finds no eligible recipient, or the recipients have no WeCom user id, the job
 * writes one ALERT P3 line for the item, still marks the item escalated, sends no card and throws nothing. Its own class and database: the only
 * super_admin holder is admin1, and admin1 has no WeCom user id, so no other test's recipients can fill the gap. The shape of the line follows the
 * P3 lines of platform.identity ("ALERT P3 platform.identity ", the item id, no personal names; compare PermissionAdjustmentJobsIT).
 */
class PermissionAdjustmentEscalationNoRecipientIT extends PermissionAdjustmentEscalationSupport {

    static final TestDatabase postgres = TestPostgres.database(PermissionAdjustmentEscalationNoRecipientIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        apiWorld();
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement erased = workforce
                .prepareStatement("update employees set wecom_user_id = null where id = ?")) {
            erased.setObject(1, stored(UUID.fromString(employeeIdOf("admin1"))));
            erased.executeUpdate();
        }
    }

    private List<ILoggingEvent> alertsAboutWhileEscalating(UUID item) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        try {
            escalate();
        } finally {
            root.detachAppender(logged);
        }
        return logged.list.stream().filter(line -> line.getFormattedMessage().startsWith("ALERT P3 platform.identity ")
                && line.getFormattedMessage().contains(item.toString())).toList();
    }

    @Test
    @DisplayName("#1151 完成标准 6 与 Q3（收件人没有企业微信号）：唯一符合条件的超级管理员 admin1 没有企业微信号时，作业不抛异常，条目照常标记升级（状态仍待处理），不发卡，恰好一条以 \"ALERT P3 platform.identity \" 开头、含条目 id、不含姓名与工号的日志")
    void aRecipientWithoutAWecomIdGivesOneAlertAndTheItemIsStillEscalated() throws Exception {
        UUID item = sensitiveItemAbout("mover");

        List<ILoggingEvent> alerts = alertsAboutWhileEscalating(item);

        assertThat(alerts).as("exactly one P3 alert line for the item").hasSize(1);
        assertThat(alerts.getFirst().getFormattedMessage()).doesNotContain("Synthetic").doesNotContain(employeeCode("mover")).doesNotContain(employeeCode("admin1"));
        assertEscalatedAtRunTime(item);
        assertThat(deliveredAbout(item)).isEmpty();
    }

    @Test
    @DisplayName("#1151 完成标准 6 与 Q3（没有符合条件的收件人）：条目关于唯一的超级管理员本人，当事人被排除后无人可收，同样一条 ALERT P3、仍标记升级、不发卡、不抛异常；再跑一次不再发出第二条 ALERT")
    void noEligibleRecipientGivesOneAlertAndTheItemIsStillEscalated() throws Exception {
        UUID item = sensitiveItemAbout("admin1");

        List<ILoggingEvent> alerts = alertsAboutWhileEscalating(item);
        List<ILoggingEvent> again = alertsAboutWhileEscalating(item);

        assertThat(alerts).as("exactly one P3 alert line for the item").hasSize(1);
        assertThat(alerts.getFirst().getFormattedMessage()).doesNotContain("Synthetic").doesNotContain(employeeCode("admin1"));
        assertThat(again).as("an escalated item is not looked at again: no second alert").isEmpty();
        assertEscalatedAtRunTime(item);
        assertThat(deliveredAbout(item)).isEmpty();
    }
}
