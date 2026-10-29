package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.forcitis.one.acceptance.P4Names;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P4 DDL-04 (acceptance item 3) and ADR-0033 section 1 item 2 (item 7): the engine's instance ids are unique where they are
 * stored. A second start round with an instance id that is already used, two approval requests with the same current instance, and
 * a second skip record for the same process instance are each refused by the named unique constraint; a request with no current
 * instance is refused (NOT NULL). The rows are written with plain SQL on the approval schema, which is the only way to bring two of them
 * into conflict.
 */
class ApprovalUniqueKeysIT extends ApprovalP4Support {

    static final TestDatabase postgres = TestPostgres.database(ApprovalUniqueKeysIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static String refusedBy(ThrowingSql statement) {
        try {
            statement.run();
        } catch (PSQLException refused) {
            assertThat(refused.getSQLState()).as("unique violation").isEqualTo("23505");
            return refused.getServerErrorMessage() == null ? null : refused.getServerErrorMessage().getConstraint();
        } catch (SQLException other) {
            throw new AssertionError(other);
        }
        return null;
    }

    private interface ThrowingSql {
        void run() throws SQLException;
    }

    private UUID instanceOf(UUID requestId) throws SQLException {
        return (UUID) rows("select current_instance_id from approval_requests where id = ?", requestId).getFirst().get("current_instance_id");
    }

    @Test
    @DisplayName("R-1b P4 DDL-04：同一流程实例 ID 写入第二条发起轮次被唯一约束 uk_start_rounds_instance_id 拒绝")
    void aSecondStartRoundCannotUseTheSameInstance() throws Exception {
        UUID request = requestIdOf(startProjectHours(projectManager));

        String constraint = refusedBy(() -> execute("insert into start_rounds (id, " + P4Names.APPROVAL_REQUEST_ID + ", round_number, initiator_id, instance_id,"
                + " trigger_event_id) select ?, " + P4Names.APPROVAL_REQUEST_ID + ", round_number + 1, initiator_id, instance_id, ? from start_rounds"
                + " where " + P4Names.APPROVAL_REQUEST_ID + " = ?", UUID.randomUUID(), UUID.randomUUID(), request));

        assertThat(constraint).as("the constraint that refused the second round").isEqualTo(P4Names.UK_ROUND_INSTANCE);
    }

    @Test
    @DisplayName("R-1b P4 DDL-04：两个审批请求不能指向同一当前实例（唯一索引 uk_approval_requests_current_instance_id）")
    void twoRequestsCannotShareTheCurrentInstance() throws Exception {
        UUID first = requestIdOf(startProjectHours(projectManager));
        UUID second = requestIdOf(startProjectHours(projectManager));

        String constraint = refusedBy(() -> execute("update approval_requests set current_instance_id = ? where id = ?", instanceOf(first), second));

        assertThat(constraint).isEqualTo(P4Names.UK_REQUEST_CURRENT_INSTANCE);
        assertThat(instanceOf(second)).as("the second request keeps its own instance").isNotEqualTo(instanceOf(first));
    }

    @Test
    @DisplayName("R-1b P4 DDL-04 / DATA-REPAIR-approval AP-03：当前实例为空的审批请求被拒（current_instance_id 为 NOT NULL，23502）")
    void aRequestWithoutACurrentInstanceIsRefused() throws Exception {
        UUID first = requestIdOf(startProjectHours(projectManager));

        PSQLException refused = org.junit.jupiter.api.Assertions.assertThrows(PSQLException.class,
                () -> execute("update approval_requests set current_instance_id = null where id = ?", first));

        assertThat(refused.getSQLState()).as("not null violation").isEqualTo("23502");
        assertThat(refused.getServerErrorMessage().getColumn()).isEqualTo("current_instance_id");
        assertThat(instanceOf(first)).as("the request keeps its instance").isNotNull();
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 1 项第 2 条：已归档但引擎删除失败的流程实例只记一次跳过——process_instance_id 为 uuid，同一实例第二次写入被 uk_process_archive_skips_process_instance_id 拒绝，主键是独立的 process_archive_skip_id")
    void aProcessInstanceIsSkippedOnlyOnce() throws Exception {
        UUID instance = UUID.randomUUID();
        Timestamp when = Timestamp.from(NOW);
        String insert = "insert into process_archive_skips (" + P4Names.PROCESS_ARCHIVE_SKIP_ID
                + ", process_instance_id, object_key, failure_message, skipped_at) values (?, ?, 'synthetic/object', 'synthetic failure', ?)";

        execute(insert, UUID.randomUUID(), instance, when);
        String constraint = refusedBy(() -> execute(insert, UUID.randomUUID(), instance, when));

        assertThat(constraint).isEqualTo(P4Names.UK_SKIP_INSTANCE);
        assertThat(rows("select count(*) as n from process_archive_skips where process_instance_id = ?", instance).getFirst().get("n")).isEqualTo(1L);
        assertThat(refusedBy(() -> execute(insert, rows("select " + P4Names.PROCESS_ARCHIVE_SKIP_ID + " as id from process_archive_skips where process_instance_id = ?",
                instance).getFirst().get("id"), UUID.randomUUID(), when))).as("a skip id is a primary key").isNotNull();
    }
}
