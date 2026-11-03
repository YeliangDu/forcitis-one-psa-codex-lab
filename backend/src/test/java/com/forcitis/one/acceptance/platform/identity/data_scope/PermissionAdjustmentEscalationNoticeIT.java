package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1151 (acceptance part; user decision of 2026-11-03 on Q1 to Q4, ADR-0032 section 4 item 3 clause 3 and item 3 content minimisation,
 * ADR-0044 items 4 to 6): the daily escalation job sends one WeCom card per escalated item to the enabled super_admin holders who are employed
 * and not on unpaid leave, the person the item is about left out. The world: eligible are admin1, owner and headA; headB is on unpaid leave,
 * plain is separated, oda2 has a disabled account; each is a super_admin holder, so a wrong rule shows as a wrong recipient set. The card body
 * still says "account-role:update holders" (from ADR-0032); the user decision names super_admin and is taken here.
 */
class PermissionAdjustmentEscalationNoticeIT extends PermissionAdjustmentEscalationSupport {

    static final TestDatabase postgres = TestPostgres.database(PermissionAdjustmentEscalationNoticeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void organisation() throws Exception {
        apiWorld();
        for (String key : List.of("owner", "headA", "headB", "plain", "oda2")) {
            grantByOperations(key, SUPER_ADMIN);
        }
        try (Connection workforce = schemaConnection("organization_workforce")) {
            try (PreparedStatement separated = workforce.prepareStatement("update employees set employment_status = 'SEPARATED', separated_at = ? where id = ?")) {
                separated.setObject(1, OffsetDateTime.parse(CREATED));
                separated.setObject(2, stored(UUID.fromString(employeeIdOf("plain"))));
                separated.executeUpdate();
            }
            try (PreparedStatement leave = workforce.prepareStatement("""
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, employment_status, employment_phase_kind,
                        is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                    values (?, ?, date '2000-01-01', 'EMPLOYED', 'REGULAR', true, false, date '2000-01-01', 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""")) {
                leave.setObject(1, UUID.randomUUID());
                leave.setObject(2, stored(UUID.fromString(employeeIdOf("headB"))));
                leave.setObject(3, OffsetDateTime.parse(CREATED));
                leave.setObject(4, OffsetDateTime.parse(CREATED));
                leave.executeUpdate();
            }
        }
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement disabled = identity
                .prepareStatement("update user_accounts set status = 'DISABLED' where user_id = ?")) {
            disabled.setObject(1, UUID.fromString(userId("oda2")));
            disabled.executeUpdate();
        }
    }

    private static final String[] ELIGIBLE = {"admin1", "owner", "headA"};

    @Test
    @DisplayName("#1151 完成标准 1、5（敏感角色复核）、9 与用户决定 Q1、Q2：作业升级一条逾期待处理的敏感角色复核，恰好送出一张卡片，收件人恰为在职、未休假、账号启用的超级管理员（admin1、owner、headA；停薪留职、离职、账号停用的超级管理员不在内），用途为新增的 PERMISSION_ADJUSTMENT 用途、对象是该条目 id；卡片正文不含权限码、范围、工号")
    void anOverdueSensitiveReviewGoesToTheEligibleSuperAdminsInOneCard() throws Exception {
        UUID item = sensitiveItemAbout("mover");

        escalate();

        assertEscalatedAtRunTime(item);
        WecomCard card = escalationCardOf(item);
        assertThat(recipientsOf(card)).containsExactlyInAnyOrderElementsOf(wecomIdsOf(ELIGIBLE));
        assertMinimised(card, "mover");
        assertNothingSentOutsideATransaction();
    }

    @Test
    @DisplayName("#1151 完成标准 5（范围复核）与 ADR-0044 第 6 项：逾期的范围复核（关于一个人、收件人是新部门负责人）与关于角色的范围复核（部门停用）同样各升级出一张卡，收件人同为符合条件的超级管理员，不发给原收件人（新部门负责人）")
    void bothKindsOfScopeReviewEscalateTheSameWay() throws Exception {
        UUID aboutAPerson = scopeReviewAbout("mover2", "headB");
        UUID aboutARole = inactiveUnitItem();

        escalate();

        for (UUID item : List.of(aboutAPerson, aboutARole)) {
            assertEscalatedAtRunTime(item);
            WecomCard card = escalationCardOf(item);
            assertThat(recipientsOf(card)).as("recipients of " + item).containsExactlyInAnyOrderElementsOf(wecomIdsOf(ELIGIBLE));
            assertMinimised(card, "mover2");
        }
        assertNothingSentOutsideATransaction();
    }

    @Test
    @DisplayName("#1151 用户决定 Q2：条目所关于的那个人若是超级管理员，不收这张卡（headA 作为当事人被排除，其余符合条件的人照收）；同一个人在别人的条目上照常收")
    void thePersonTheItemIsAboutIsLeftOut() throws Exception {
        UUID aboutHeadA = sensitiveItemAbout("headA");
        UUID aboutMover = sensitiveItemAbout("mover");

        escalate();

        assertThat(recipientsOf(escalationCardOf(aboutHeadA))).containsExactlyInAnyOrderElementsOf(wecomIdsOf("admin1", "owner"));
        assertThat(recipientsOf(escalationCardOf(aboutMover))).as("headA is a recipient of another person's item").contains(wecomUserIdOf("headA"));
    }

    @Test
    @DisplayName("#1151 完成标准 2：再跑一次作业、隔一天再跑，已升级的条目不再发卡（仍只有一张）；时间上已逾期但 escalated_at 已有值的条目，作业不发卡")
    void rerunsAndAnAlreadyEscalatedItemSendNothingMore() throws Exception {
        UUID item = sensitiveItemAbout("mover");
        UUID escalatedBefore = scopeReviewAbout("mover2", "headB");
        alreadyEscalatedAt(escalatedBefore, java.time.Instant.parse("2026-09-08T12:00:00Z"));

        escalate();
        escalate();
        advanceTo(RUN_AT.plusSeconds(86400));
        runJob("EscalatePermissionAdjustments");

        assertThat(deliveredAbout(item)).as("one card in three runs").hasSize(1);
        assertThat(rowOf(item)).containsEntry("escalated_at", RUN_AT.toString());
        assertThat(deliveredAbout(escalatedBefore)).as("an item already escalated").isEmpty();
        assertThat(rowOf(escalatedBefore)).containsEntry("escalated_at", "2026-09-08T12:00:00Z");
    }

    @Test
    @DisplayName("#1151 完成标准 4：未到期限的待处理条目、已维持（UPHELD）、已到期（EXPIRED）、已调整（ADJUSTED）的条目，作业不升级、不发卡")
    void notYetDueAndNonOpenItemsSendNothing() throws Exception {
        UUID notDue = sensitiveItemAbout("mover");
        dueAt(notDue, NOT_YET_DUE);
        UUID upheld = sensitiveItemAbout("mover2");
        inState(upheld, "UPHELD");
        UUID expired = sensitiveItemAbout("mover");
        inState(expired, "EXPIRED");
        UUID adjusted = scopeReviewAbout("mover2", null);
        inState(adjusted, "ADJUSTED");

        escalate();

        for (UUID item : List.of(notDue, upheld, expired, adjusted)) {
            assertThat(deliveredAbout(item)).as("cards about " + item).isEmpty();
            assertNotEscalated(item);
        }
    }

    @Test
    @DisplayName("#1151 完成标准 3：升级事务在排好卡之后失败（提交时被约束触发器拒绝）时，不送出任何卡片、条目没有被标记升级；去掉故障后再跑一次，才升级并送出恰好一张")
    void aRolledBackEscalationSendsNoCard() throws Exception {
        UUID item = sensitiveItemAbout("mover");
        failCommitOfEscalationOf(item);
        try {
            Throwable failure = catchThrowable(this::escalate);

            assertThat(deliveredAbout(item)).as("no card for a transaction that did not commit (job failure: " + failure + ")").isEmpty();
            assertNotEscalated(item);
        } finally {
            removeFailure();
        }

        escalate();

        assertEscalatedAtRunTime(item);
        escalationCardOf(item);
        assertNothingSentOutsideATransaction();
    }

    /** A constraint trigger that fails when the transaction commits, after the job has queued the card, for the one item. */
    private void failCommitOfEscalationOf(UUID item) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); Statement statement = identity.createStatement()) {
            statement.execute("""
                    create function f1_1151_fail_commit() returns trigger language plpgsql as $$
                    begin
                        if new.permission_adjustment_id = '%s'::uuid and new.escalated_at is not null then
                            raise exception 'f1-1151 forced failure at commit';
                        end if;
                        return new;
                    end $$""".formatted(item));
            statement.execute("create constraint trigger f1_1151_fail_commit after update on permission_adjustments deferrable initially deferred"
                    + " for each row execute function f1_1151_fail_commit()");
        }
    }

    private void removeFailure() throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); Statement statement = identity.createStatement()) {
            statement.execute("drop trigger if exists f1_1151_fail_commit on permission_adjustments");
            statement.execute("drop function if exists f1_1151_fail_commit()");
        }
    }
}
