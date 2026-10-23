package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The request row's current round and instance are a projection of its newest start round; this reconciles them in SQL. */
class ApprovalRoundProjectionIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    private ApprovalInstanceId startFor(String key, long initiator, long manager) {
        return tx.execute(status -> approvals.start(startRequest(key, TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(manager).toString()))))));
    }

    private void assertEveryRequestPointsAtItsNewestRound() {
        List<Map<String, Object>> mismatches = jdbc.queryForList("""
                select r.id, r.current_round, r.current_instance_id
                from platform_approval.approval_requests r
                where r.current_round <> (select max(s.round_number) from platform_approval.start_rounds s where s.approval_request_id = r.id)
                   or r.current_instance_id <> (
                        select s.instance_id from platform_approval.start_rounds s
                        where s.approval_request_id = r.id and s.round_number = r.current_round)
                   or not exists (select 1 from platform_approval.start_rounds s where s.approval_request_id = r.id)
                """);
        assertThat(mismatches).as("requests whose current_round / current_instance_id differ from their newest start round").isEmpty();
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-03 首轮发起、驳回后原单再发起、撤回后再发起：current_round 等于最大发起轮次，current_instance_id 等于该轮实例")
    void currentRoundAndInstanceFollowTheNewestStartRound() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        actAs(initiator);
        String key = newKey("ap03-projection");

        ApprovalInstanceId first = startFor(key, initiator, manager);
        assertThat(roundOf(key)).isEqualTo(1);
        assertThat(instanceOf(key)).isEqualTo(first.value());
        assertEveryRequestPointsAtItsNewestRound();

        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(openTaskId(first), DecisionOutcome.REJECT, "back")), OptionalLong.empty());
        ApprovalInstanceId second = startFor(key, initiator, manager);
        assertThat(second).isNotEqualTo(first);
        assertThat(roundOf(key)).isEqualTo(2);
        assertThat(instanceOf(key)).isEqualTo(second.value());
        assertEveryRequestPointsAtItsNewestRound();

        RetractionResult withdrawn = tx.execute(status -> approvals.withdraw(TimesheetApprovalProcess.KEY, document(key), TestAccounts.account(initiator), "wrong hours"));
        assertThat(withdrawn).isEqualTo(RetractionResult.DONE);
        ApprovalInstanceId third = startFor(key, initiator, manager);
        assertThat(third).isNotEqualTo(second);
        assertThat(roundOf(key)).isEqualTo(3);
        assertThat(instanceOf(key)).isEqualTo(third.value());
        assertThat(jdbc.queryForObject("""
                select count(*) from platform_approval.start_rounds
                where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid))
                """, Integer.class, key)).isEqualTo(3);
        assertEveryRequestPointsAtItsNewestRound();
    }

    private int roundOf(String key) {
        return jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key);
    }

    private java.util.UUID instanceOf(String key) {
        return jdbc.queryForObject("select current_instance_id from platform_approval.approval_requests where document_id = cast(? as uuid)",
                java.util.UUID.class, key);
    }
}
