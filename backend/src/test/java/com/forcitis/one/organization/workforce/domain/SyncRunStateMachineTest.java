package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.SyncRunStatus.FAILED;
import static com.forcitis.one.organization.workforce.domain.SyncRunStatus.HELD;
import static com.forcitis.one.organization.workforce.domain.SyncRunStatus.RELEASED;
import static com.forcitis.one.organization.workforce.domain.SyncRunStatus.RUNNING;
import static com.forcitis.one.organization.workforce.domain.SyncRunStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import com.forcitis.one.organization.workforce.events.SyncRunFailed;
import com.forcitis.one.organization.workforce.events.SyncRunHeld;
import com.forcitis.one.organization.workforce.events.SyncRunReleased;
import com.forcitis.one.organization.workforce.events.SyncRunSucceeded;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncRunStateMachineTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    static final RunStats STATS = new RunStats(10, 20, 10, 20, 10, 20, 1, 1, 0, 0, 0, 0);
    static final SnapshotChange NO_CHANGE = new SnapshotChange(List.of(), List.of(), List.of());

    static SyncRun at(SyncRunStatus status) {
        return SyncRun.restore(new SyncRunId(5), SyncTrigger.SCHEDULED, status.name(), NOW, null, null, null, null, null, null, null, 3, 3);
    }

    @Test
    @DisplayName("every operation is tried from every status: only the legal transitions succeed, the rest are refused and change nothing")
    void exhaustiveTransitions() {
        new TransitionTable<SyncRunStatus, SyncRun>(SyncRunStatus.class, SyncRunStateMachineTest::at, SyncRun::status)
                .operation("succeed", run -> run.succeed(NOW, STATS, NO_CHANGE), RUNNING, SUCCEEDED)
                .operation("hold", run -> run.hold(NOW, STATS, "shrunk"), RUNNING, HELD, HELD, HELD)
                .operation("fail", run -> run.fail(NOW, "down"), RUNNING, FAILED)
                .operation("release", run -> run.release(new UserId(9), new SyncRunId(6), NOW, new com.forcitis.one.kernel.Reason("synthetic")), HELD, RELEASED)
                .verify();
    }

    @Test
    @DisplayName("the machine declares exactly the transitions the operations use")
    void machineMatchesTheOperations() {
        for (SyncRunStatus from : SyncRunStatus.values()) {
            for (SyncRunStatus to : SyncRunStatus.values()) {
                boolean expected = (from == RUNNING && (to == SUCCEEDED || to == HELD || to == FAILED))
                        || (from == HELD && (to == HELD || to == RELEASED));
                assertThat(SyncRun.MACHINE.allows(from, to)).as(from + " -> " + to).isEqualTo(expected);
            }
        }
    }

    @Test
    @DisplayName("a successful run records SyncRunSucceeded, and OrgSnapshotChanged only when something changed; versions are contiguous")
    void succeedingRecordsEvents() {
        SyncRun quiet = at(RUNNING);
        quiet.succeed(NOW, STATS, NO_CHANGE);
        assertThat(quiet.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(SyncRunSucceeded.class);

        SyncRun busy = at(RUNNING);
        busy.succeed(NOW, STATS, new SnapshotChange(List.of(new UserId(1)), List.of(new UserId(2)), List.of(new OrgUnitId(3))));
        List<DomainEvent> events = busy.drainEvents(TestEnvelopes.INSTANCE);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(SyncRunSucceeded.class);
        assertThat(events.get(1)).isInstanceOfSatisfying(OrgSnapshotChanged.class, e -> {
            assertThat(e.separatedEmployees()).containsExactly(new UserId(2));
            assertThat(e.envelope().aggregateType()).isEqualTo("SyncRun");
            assertThat(e.envelope().aggregateId()).isEqualTo("5");
        });
        assertThat(events).extracting(e -> e.envelope().aggregateVersion()).containsExactly(4L, 5L);
        assertThat(busy.eventSequence()).isEqualTo(5);
        assertThat(busy.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("hold, fail and release record their own events")
    void otherEvents() {
        SyncRun held = at(RUNNING);
        held.hold(NOW, STATS, "shrunk");
        assertThat(held.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(SyncRunHeld.class);
        assertThat(held.heldReason()).isEqualTo("shrunk");

        SyncRun failed = at(RUNNING);
        failed.fail(NOW, "down");
        assertThat(failed.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(SyncRunFailed.class);

        SyncRun released = at(HELD);
        released.release(new UserId(9), new SyncRunId(6), NOW, new com.forcitis.one.kernel.Reason("checked with the source"));
        assertThat(released.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(SyncRunReleased.class,
                e -> {
                    assertThat(e.releasedByRunId()).isEqualTo(new SyncRunId(6));
                    assertThat(e.reason()).isEqualTo(new com.forcitis.one.kernel.Reason("checked with the source"));
                });
        assertThat(released.releasedBy()).isEqualTo(new UserId(9));
    }

    @Test
    @DisplayName("a refused transition records no event")
    void refusedTransitionRecordsNothing() {
        SyncRun done = at(SUCCEEDED);
        try {
            done.fail(NOW, "late");
        } catch (RuntimeException expected) {
            assertThat(done.hasPendingEvents()).isFalse();
        }
        assertThat(done.status()).isEqualTo(SUCCEEDED);
    }
}
