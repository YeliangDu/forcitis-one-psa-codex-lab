package com.forcitis.one.scenario;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.fixture.doubles.TestEventEnvelopes;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.ReleaseTask;
import com.forcitis.one.platform.approval.application.TransferTask;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.forcitis.one.fixture.doubles.TestOrgIds;

/**
 * DDD-3 with the database in between: after every save the stored event count is the number of the last event of the aggregate, and
 * the numbers the events carry run 1..n without a gap, over any sequence of operations. One test per aggregate kind (Timesheet,
 * ApprovalRequest); EventSequenceCoverageTest fails when an aggregate root is named in none of these tests.
 */
class EventSequencePersistenceIT extends ApprovalIntegrationSupport {

    static final AtomicLong IDS = new AtomicLong(900_000);
    static final TestEventEnvelopes ENVELOPES = new TestEventEnvelopes();

    @Autowired
    TimesheetRepository timesheets;

    @Autowired
    ClaimTask claimTask;

    @Autowired
    ReleaseTask releaseTask;

    @Autowired
    TransferTask transferTask;

    @Test
    @DisplayName("Timesheet: over random operations the stored event count follows the events and their numbers have no gap")
    void timesheetEventNumbersSurviveSaves() {
        for (long seed = 0; seed < 15; seed++) {
            Random random = new Random(seed);
            TimesheetId id = new TimesheetId(TestIds.uuid(IDS.incrementAndGet()));
            LocalDate today = BusinessDay.today();
            EmployeeId owner = TestAccounts.employee(newId());
            Timesheet created = Timesheet.draft(id, new DocumentNumber("TS-SEQ-" + id.value()), new AggregateReference("NumberingCounter", TestIds.uuid(11)),
                    new NumberingRuleVersionId(TestIds.uuid(12)), owner, new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(1), today.minusDays(1), Hours.of("8"), TestAccounts.employee(newId()), today);
            List<Long> numbers = new ArrayList<>();
            tx.executeWithoutResult(s -> timesheets.save(created));
            created.drainEvents(ENVELOPES).forEach(event -> numbers.add(event.envelope().aggregateVersion()));
            for (int step = 0; step < 25; step++) {
                Timesheet loaded = timesheets.find(id).orElseThrow();
                try {
                    switch (random.nextInt(6)) {
                        case 0 -> loaded.submit(owner, today);
                        case 1 -> loaded.approve();
                        case 2 -> loaded.reject(new Reason("no"));
                        case 3 -> loaded.returnForRevision(new Reason("fix"));
                        case 4 -> loaded.cancelSubmission(new Reason("changed"));
                        default -> loaded.revise(owner, loaded.workDate(), Hours.of("7"), loaded.projectManager());
                    }
                } catch (DomainException illegal) {
                    // an illegal move records nothing
                }
                tx.executeWithoutResult(s -> timesheets.save(loaded));
                List<DomainEvent> published = loaded.drainEvents(ENVELOPES);
                published.forEach(event -> numbers.add(event.envelope().aggregateVersion()));
                assertThat(jdbc.queryForObject("select event_count from cost_timesheet.timesheets where id = ?", Long.class, id.value()))
                        .as("seed %s step %s", seed, step).isEqualTo((long) numbers.size());
                assertThat(timesheets.find(id).orElseThrow().eventSequence()).isEqualTo(numbers.size());
            }
            for (int i = 0; i < numbers.size(); i++) {
                assertThat(numbers.get(i)).as("seed %s", seed).isEqualTo(i + 1L);
            }
        }
    }

    @Test
    @DisplayName("ApprovalRequest: over claim, release and transfer the stored event count follows the events and their numbers have no gap")
    void approvalEventNumbersSurviveSaves() {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        long colleague = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null).person(colleague, unit, null);
        String key = newKey("sequence");
        ApprovalTaskId task = openTaskId(start(key, initiator, manager));
        Random random = new Random(7);
        UserId holder = TestAccounts.account(manager);
        for (int step = 0; step < 30; step++) {
            try {
                switch (random.nextInt(3)) {
                    case 0 -> claimTask.execute(task, holder, OptionalLong.empty());
                    case 1 -> releaseTask.execute(task, holder, OptionalLong.empty());
                    default -> {
                        EmployeeId other = holder.equals(TestAccounts.account(manager)) ? TestAccounts.employee(colleague) : TestAccounts.employee(manager);
                        transferTask.execute(task, holder, other, "swap", OptionalLong.empty());
                        holder = TestAccounts.account(other);
                    }
                }
            } catch (RuntimeException illegal) {
                // a refused move records nothing
            }
            List<Long> numbers = recorded.stream(DomainEvent.class)
                    .filter(event -> event.envelope().aggregateType().equals("ApprovalRequest") && event.envelope().aggregateId().equals(requestIdOf(key)))
                    .map(event -> event.envelope().aggregateVersion()).toList();
            assertThat(jdbc.queryForObject("select event_count from platform_approval.approval_requests where document_id = cast(? as uuid)", Long.class, key))
                    .as("step %s", step).isEqualTo((long) numbers.size());
        }
        List<Long> numbers = recorded.stream(DomainEvent.class)
                .filter(event -> event.envelope().aggregateType().equals("ApprovalRequest") && event.envelope().aggregateId().equals(requestIdOf(key)))
                .map(event -> event.envelope().aggregateVersion()).sorted().toList();
        for (int i = 0; i < numbers.size(); i++) {
            assertThat(numbers.get(i)).isEqualTo(i + 1L);
        }
    }
}
