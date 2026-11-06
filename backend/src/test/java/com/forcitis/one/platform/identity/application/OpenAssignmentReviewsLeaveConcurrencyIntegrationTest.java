package com.forcitis.one.platform.identity.application;

import com.forcitis.one.BusinessDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.identity.IdentityIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("#918 concurrent deliveries of one leave-start command open one review: the account row lock is the only guard (no unique index, one-design amendment)")
class OpenAssignmentReviewsLeaveConcurrencyIntegrationTest extends IdentityIntegrationTest {

    private static final String REVIEWS = "permission_adjustments where kind = 'SENSITIVE_ROLE_REVIEW' and origin_kind = 'UNPAID_LEAVE_START'";

    @MockitoBean
    WorkCalendar calendar;

    @Autowired
    OpenAssignmentReviews reviews;

    @Autowired
    TransactionTemplate tx;

    @AfterEach
    void clean() {
        jdbc.sql("delete from platform_identity.permission_adjustments").update();
    }

    @Test
    void concurrentDeliveriesOfOneCommandOpenOneReview() throws Exception {
        when(calendar.endOfWorkdayAfter(any(), anyInt(), any())).thenReturn(BusinessDay.instant().plus(Duration.ofDays(7)));
        knownEmployee(7301, EmploymentStatus.EMPLOYED);
        createAccount(7301, "super_admin");
        UserId user = TestAccounts.account(7301);
        EmployeeId employee = TestAccounts.employee(7301);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        int rounds = 8;
        try {
            for (int round = 0; round < rounds; round++) {
                PersonnelCommandId command = new PersonnelCommandId(UUID.randomUUID());
                EventEnvelope envelope = TestEnvelopes.of("organization.workforce.EmployeeUnpaidLeaveStarted", "Employee", employee.value());
                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> all = new ArrayList<>();
                for (int t = 0; t < 4; t++) {
                    all.add(pool.submit(() -> {
                        go.await();
                        try (WorkContext.Scope s = WorkContext.enter(UUID.randomUUID(), null, "platform.identity.permission-adjustment")) {
                            tx.executeWithoutResult(status -> {
                                reviews.openForLeave(user, envelope, command);
                                // keeps the transaction open so the other deliveries meet it: without the account row lock all four pass the existence check
                                await().pollDelay(Duration.ofMillis(50)).until(() -> true);
                            });
                        }
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> f : all) {
                    f.get();
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.sql("select count(*) from platform_identity." + REVIEWS + " group by trigger_personnel_command_id having count(*) > 1")
                .query(Long.class).list()).as("duplicates per command").isEmpty();
        assertThat(count(REVIEWS)).isEqualTo(rounds);
    }
}
