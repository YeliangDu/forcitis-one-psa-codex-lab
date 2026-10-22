package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import com.forcitis.one.cost.timesheet.application.TimesheetView;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * TS-UNIQ acceptance items 1, 2, 3, 7, 8, 9 and 12 (card TS-UNIQ section 4): one timesheet per employee per work date whatever its
 * status, told apart from a number clash and from an unknown constraint by the name of the constraint that fired.
 */
@Testcontainers
class TimesheetEmployeeDayUniqueIT extends TimesheetWorkerSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @MockitoSpyBean
    DocumentNumbers numbers;

    @MockitoSpyBean
    TimesheetRepository timesheets;

    @Autowired
    TransactionTemplate transaction;

    private ListAppender<ILoggingEvent> everything;
    private Logger root;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        everything = new ListAppender<>();
        everything.start();
        root.addAppender(everything);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(everything);
        everything.stop();
    }

    private static LocalDate workDay() {
        return TODAY.minusDays(3);
    }

    @Test
    @DisplayName("TS-UNIQ 同一员工同一工作日并发创建两张工时单，后到者被拒，返回 COST_TIMESHEET_WORK_DATE_DUPLICATE 而不是 COST_TIMESHEET_CONCURRENT_CHANGE，库内只有一行，也没有多发一个单据编号")
    void concurrentCreationOfTheSameEmployeeDayLeavesOneRowAndOneNumber() throws Exception {
        SignedIn worker = newWorker();
        long issuedBefore = nextTimesheetSequence();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> runs = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                Callable<Object> create = () -> {
                    go.await();
                    actInSession(worker);
                    try {
                        return submitTimesheet.execute(new SubmitTimesheetCommand(newTimesheetId(), workDay(),
                                Hours.of("8"), employeeIdOf(projectManager)));
                    } catch (RuntimeException refused) {
                        return refused;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                runs.add(pool.submit(create));
            }
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> run : runs) {
                outcomes.add(run.get(60, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn(outcome -> outcome instanceof TimesheetView).as("exactly one creation wins").hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !(outcome instanceof TimesheetView)).singleElement().isInstanceOfSatisfying(DomainException.class,
                    refused -> assertThat(refused.errorCode().code()).isEqualTo(WORK_DATE_DUPLICATE));
            assertThat(timesheetRowsOf(worker.employeeId(), workDay())).isEqualTo(1);
            assertThat(nextTimesheetSequence() - issuedBefore).as("the loser's number is not issued: the next number moved by the winner's one only").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("TS-UNIQ 预检被绕过时唯一约束仍兜底并映射为 COST_TIMESHEET_WORK_DATE_DUPLICATE：库内仍只有一行，也没有多发一个单据编号")
    void whenThePrecheckIsBypassedTheUniqueConstraintStillRefusesAsWorkDateDuplicate() throws Exception {
        SignedIn worker = newWorker();
        submit(worker, newTimesheetId(), workDay(), "8");
        long issuedBefore = nextTimesheetSequence();
        doReturn(false).when(timesheets).existsFor(any(), any());

        refusedWith(WORK_DATE_DUPLICATE, () -> submit(worker, newTimesheetId(), workDay(), "4"));

        verify(timesheets, atLeastOnce()).existsFor(any(), eq(workDay()));
        assertThat(timesheetRowsOf(worker.employeeId(), workDay())).isEqualTo(1);
        assertThat(nextTimesheetSequence()).as("the refused creation rolled its number back").isEqualTo(issuedBefore);
    }

    @Test
    @DisplayName("TS-UNIQ 已有草稿、已提交、已退回、已通过、已驳回任一状态的工时单时，同一员工同一工作日新建被预检拒绝，返回 COST_TIMESHEET_WORK_DATE_DUPLICATE，且不消耗单据编号")
    void anyExistingStatusRefusesANewTimesheetForTheSameDayBeforeNumbering() throws Exception {
        SignedIn worker = newWorker();
        LegalEntityId entity = new LegalEntityId(legalEntity);
        int offset = 1;
        for (TimesheetStatus status : TimesheetStatus.values()) {
            LocalDate day = TODAY.minusDays(offset++);
            TimesheetId existing = newTimesheetId();
            submit(worker, existing, day, "8");
            if (status != TimesheetStatus.SUBMITTED) {
                // the status is seeded straight into the row: the uniqueness must not depend on how the row got there
                jdbc.sql("UPDATE cost_timesheet.timesheets SET status = :status WHERE id = :id").param("status", status.name())
                        .param("id", existing.value()).update();
            }
            long issuedBefore = nextTimesheetSequence();
            clearInvocations(numbers);

            refusedWith(WORK_DATE_DUPLICATE, () -> submit(worker, newTimesheetId(), day, "4"));

            transaction.executeWithoutResult(tx -> verify(numbers, never()).next(eq(new DocumentTypeCode("TIMESHEET")), eq(entity), any()));
            assertThat(nextTimesheetSequence()).as("no number consumed against the %s timesheet of %s", status, day).isEqualTo(issuedBefore);
            assertThat(timesheetRowsOf(worker.employeeId(), day)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("TS-UNIQ 非工作日（周末）与工作日一样，每人每天只能有一张工时单")
    void aWeekendDayAllowsOneTimesheetPerEmployeeLikeAnyOtherDay() throws Exception {
        LocalDate saturday = LocalDate.of(2026, 10, 31);
        LocalDate sunday = saturday.plusDays(1);
        assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        SignedIn worker = newWorker();
        SignedIn colleague = newWorker();

        assertThat(submit(worker, newTimesheetId(), saturday, "6").status()).isEqualTo(TimesheetStatus.SUBMITTED);
        refusedWith(WORK_DATE_DUPLICATE, () -> submit(worker, newTimesheetId(), saturday, "2"));
        assertThat(submit(worker, newTimesheetId(), sunday, "3").status()).as("the next day is another day").isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(submit(colleague, newTimesheetId(), saturday, "5").status()).as("another employee, the same Saturday").isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(timesheetRowsOf(worker.employeeId(), saturday)).isEqualTo(1);
    }

    @Test
    @DisplayName("TS-UNIQ 表结构守护：timesheets 上存在唯一约束 uk_timesheets_employee_id_work_date，列为（employee_id, work_date），无谓词；ix_timesheets_employee_id 不存在；uk_timesheets_timesheet_number 仍在；numbering_rule_version_id 列存在、类型 uuid、可空")
    void theTableCarriesTheEmployeeDayConstraint() {
        var unique = jdbc.sql("""
                SELECT pg_get_constraintdef(c.oid) AS definition, i.indpred IS NULL AS no_predicate
                  FROM pg_constraint c JOIN pg_index i ON i.indexrelid = c.conindid
                 WHERE c.conrelid = CAST('cost_timesheet.timesheets' AS regclass) AND c.contype = 'u' AND c.conname = :name
                """).param("name", "uk_timesheets_employee_id_work_date").query((rs, n) -> rs.getString("definition") + "|" + rs.getBoolean("no_predicate")).list();
        assertThat(unique).containsExactly("UNIQUE (employee_id, work_date)|true");

        assertThat(jdbc.sql("SELECT count(*) FROM pg_indexes WHERE schemaname = 'cost_timesheet' AND indexname = 'ix_timesheets_employee_id'")
                .query(Long.class).single()).as("the plain index is redundant next to the constraint").isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM pg_constraint WHERE conrelid = CAST('cost_timesheet.timesheets' AS regclass) AND conname = 'uk_timesheets_timesheet_number' AND contype = 'u'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM pg_index WHERE indrelid = CAST('cost_timesheet.timesheets' AS regclass) AND indisunique AND indpred IS NOT NULL")
                .query(Long.class).single()).as("no partial unique index").isZero();

        var column = jdbc.sql("""
                SELECT udt_name || '|' || is_nullable || '|' || coalesce(column_default, 'none')
                  FROM information_schema.columns
                 WHERE table_schema = 'cost_timesheet' AND table_name = 'timesheets' AND column_name = 'numbering_rule_version_id'
                """).query(String.class).list();
        assertThat(column).containsExactly("uuid|YES|none");
    }

    @Test
    @DisplayName("TS-UNIQ timesheet_number 冲突仍映射为 KERNEL_CONCURRENT_CHANGE")
    void aTimesheetNumberClashIsStillAConcurrentChange() throws Exception {
        SignedIn first = newWorker();
        SignedIn second = newWorker();
        TimesheetId taken = newTimesheetId();
        submit(first, taken, workDay(), "8");
        String number = jdbc.sql("SELECT timesheet_number FROM cost_timesheet.timesheets WHERE id = :id").param("id", taken.value())
                .query(String.class).single();
        IssuedNumber real = transaction.execute(tx -> {
            tx.setRollbackOnly();
            return numbers.next(new DocumentTypeCode("TIMESHEET"), new LegalEntityId(legalEntity), TODAY);
        });
        IssuedNumber reused = new IssuedNumber(new DocumentNumber(number), real.counterId(), real.ruleVersionId());
        transaction.executeWithoutResult(tx -> doReturn(reused).when(numbers).next(any(), any(), any()));

        refusedWith(CONCURRENT_CHANGE, () -> submit(second, newTimesheetId(), workDay(), "8"));

        assertThat(timesheetRowsOf(second.employeeId(), workDay())).isZero();
    }

    @Test
    @DisplayName("TS-UNIQ 其他约束名的重复键异常不被当作并发修改：原样抛出，且日志不含冲突值")
    void aDuplicateKeyOfAnUnknownConstraintIsNotAConcurrentChange() throws Exception {
        SignedIn first = newWorker();
        SignedIn second = newWorker();
        String clashingHours = "6.37";
        jdbc.sql("CREATE UNIQUE INDEX ux_ts_uniq_probe ON cost_timesheet.timesheets (project_manager_id, submitted_hours, work_date)").update();
        try {
            submit(first, newTimesheetId(), workDay(), clashingHours);
            int mark = logMark();
            int logged = everything.list.size();

            Throwable thrown = catchThrowable(() -> submit(second, newTimesheetId(), workDay(), clashingHours));

            assertThat(thrown).as("the clash of a constraint the code does not know").isInstanceOf(DuplicateKeyException.class);
            assertThat(alertsSince(mark)).as("an ALERT is logged").isNotEmpty();
            List<ILoggingEvent> warnings = everything.list.subList(logged, everything.list.size()).stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).toList();
            for (ILoggingEvent event : warnings) {
                String text = event.getFormattedMessage() + " " + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy()));
                assertThat(text).as("log line %s", event.getFormattedMessage()).doesNotContain(clashingHours).doesNotContain(projectManager.toString());
            }
        } finally {
            jdbc.sql("DROP INDEX IF EXISTS cost_timesheet.ux_ts_uniq_probe").update();
        }
    }

    @Test
    @DisplayName("TS-UNIQ 工作日期按自然日保存不做时区转换；「不晚于今天」按上海业务日判断（固定 Clock 在 UTC 前一日 16:30 时，上海次日的工作日期被接受）")
    void workDateIsACalendarDayAndTodayIsTheShanghaiDay() throws Exception {
        SignedIn worker = newWorker();
        LocalDate shanghaiNextDay = LocalDate.of(2026, 11, 3);

        clock.set(Instant.parse("2026-11-02T15:59:00Z"));
        refusedWith("COST_TIMESHEET_WORK_DATE_IN_FUTURE", () -> submit(worker, newTimesheetId(), shanghaiNextDay, "8"));

        clock.set(Instant.parse("2026-11-02T16:30:00Z"));
        TimesheetId accepted = newTimesheetId();
        TimesheetView view = submit(worker, accepted, shanghaiNextDay, "8");

        assertThat(view.workDate()).isEqualTo(shanghaiNextDay);
        assertThat(jdbc.sql("SELECT work_date FROM cost_timesheet.timesheets WHERE id = :id").param("id", accepted.value())
                .query(LocalDate.class).single()).isEqualTo(shanghaiNextDay);
        refusedWith("COST_TIMESHEET_WORK_DATE_IN_FUTURE", () -> submit(worker, newTimesheetId(), shanghaiNextDay.plusDays(1), "8"));
    }
}
