package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.HeldSeparation;
import com.forcitis.one.organization.workforce.api.PreviewOrgUnitMerge;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommands;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PersonnelCommands;
import com.forcitis.one.organization.workforce.api.WithdrawOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * #907 review findings, as developer tests on the real beans (the acceptance tests of #906 stay untouched): what a merge does after a person it counted
 * leaves the merged unit by another command, which receiving units it accepts, who may join the merged unit after the merge day, and the locks of the
 * org-structure commands (tree lock, unit root, no deadlock with the foreign-key check of a personnel command). Every test uses units and people of its
 * own and signs in by authority, so it needs no sessions and no truncation.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
@Import(WorkforceTestSources.class)
class OrgMergeSafetyIT {
    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    static final String ORG_PERMISSION = "organization.workforce:org-structure-command:create";
    static final String PERSONNEL_PERMISSION = "organization.workforce:personnel-command:create";
    static final LocalDate SINCE = LocalDate.of(2020, 1, 1);

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    AdjustableClock clock;

    @Autowired
    BusinessZone zone;

    @Autowired
    OrgStructureCommands commands;

    @Autowired
    PersonnelCommands personnel;

    @Autowired
    OrgMergeSteps steps;

    @Autowired
    PersonnelCommandDueJob dueJob;

    @MockitoBean
    SeparationReview review;

    private WorkContext.Scope acting;
    private LocalDate today;

    @BeforeEach
    void actAsTheTestJob() {
        clock.reset();
        today = zone.today(clock);
        acting = WorkContext.enter(UUID.randomUUID(), null, "test.organization.workforce");
        signIn(ORG_PERMISSION);
    }

    @AfterEach
    void leave() {
        SecurityContextHolder.clearContext();
        acting.close();
        clock.reset();
    }

    private static void signIn(String permission) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(UUID.randomUUID().toString(), null, permission));
    }

    // ---- seeds ----

    private UUID unit() {
        UUID id = Uuids.timeOrdered(clock);
        jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (:id, 'unit', 'ACTIVE')").param("id", id).update();
        jdbc.sql("""
                insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, status, region_kind, is_delivery_unit)
                values (:version, :id, :since, 'unit', 'ACTIVE', 'HEADQUARTERS', false)
                """).param("version", Uuids.timeOrdered(clock)).param("id", id).param("since", SINCE).update();
        return id;
    }

    private UUID person(UUID unit, String phase) {
        UUID id = Uuids.timeOrdered(clock);
        UUID position = Uuids.timeOrdered(clock);
        String serial = id.toString().substring(24);
        jdbc.sql("insert into organization_workforce.positions (id, code, name, status) values (:id, :code, 'position', 'ACTIVE')").param("id", position).param("code", "P" + serial).update();
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, wecom_user_id, employment_status) values (:id, :code, :name, :wecom, 'EMPLOYED')")
                .param("id", id).param("code", "T" + serial).param("name", "person " + serial).param("wecom", "w" + serial).update();
        jdbc.sql("insert into organization_workforce.assignments (employee_id, org_unit_id) values (:id, :unit)").param("id", id).param("unit", unit).update();
        jdbc.sql("""
                insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                       employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (:version, :id, :since, 0, 'EMPLOYED', :phase, false, false, :since, 'LEGACY_HR_RECORD', 'MIGRATION', now(), now())
                """).param("version", Uuids.timeOrdered(clock)).param("id", id).param("since", SINCE).param("phase", phase).update();
        jdbc.sql("""
                insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)
                values (uuidv7(), :employee, 'PRIMARY', now(), now()) on conflict (employee_id) where assignment_kind = 'PRIMARY' do nothing""")
                .param("employee", id).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                       org_unit_id, position_id, change_channel_kind, created_at, updated_at)
                select :version, :id, assignment_id, assignment_kind, :since, :unit, :position, 'MIGRATION', now(), now()
                  from organization_workforce.employee_assignments where employee_id = :id and assignment_kind = 'PRIMARY'
                """).param("version", Uuids.timeOrdered(clock)).param("id", id).param("since", SINCE).param("unit", unit).param("position", position).update();
        return id;
    }

    /** The person's open assignment ends on {@code from} and a new one in {@code toUnit} starts that day: what a transfer dated then leaves. */
    private void assignedFrom(UUID person, UUID toUnit, LocalDate from) {
        UUID position = jdbc.sql("select position_id from organization_workforce.assignment_versions where employee_id = :id and effective_to_date is null")
                .param("id", person).query(UUID.class).single();
        jdbc.sql("update organization_workforce.assignment_versions set effective_to_date = :from, closed_at = now(), updated_at = now() where employee_id = :id and effective_to_date is null")
                .param("from", from).param("id", person).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                       org_unit_id, position_id, change_channel_kind, created_at, updated_at)
                select :version, :id, assignment_id, assignment_kind, :from, :unit, :position, 'MIGRATION', now(), now()
                  from organization_workforce.employee_assignments where employee_id = :id and assignment_kind = 'PRIMARY'
                """).param("version", Uuids.timeOrdered(clock)).param("id", person).param("from", from).param("unit", toUnit).param("position", position).update();
    }

    private OrgStructureCommandId merge(UUID merged, UUID receiving, LocalDate day) {
        return commands.enter(unitCommand(OrgStructureCommandKind.MERGE, merged, day, null, receiving));
    }

    private static EnterOrgStructureCommand unitCommand(OrgStructureCommandKind kind, UUID target, LocalDate day, UUID parent, UUID mergeInto) {
        return new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT, kind, new OrgUnitId(target), null, day, null, kind == OrgStructureCommandKind.SET_ATTRIBUTES ? "renamed" : null,
                null, null, null, parent == null ? null : new OrgUnitId(parent), null, null, mergeInto == null ? null : new OrgUnitId(mergeInto), null);
    }

    private void regularizeOn(UUID person, LocalDate day) {
        signIn(PERSONNEL_PERMISSION);
        personnel.enter(new EnterPersonnelCommand(new EmployeeId(person), PersonnelCommandKind.REGULARIZE, day, null, null, null, null, null, null, null, null, null, null));
        signIn(ORG_PERMISSION);
    }

    // ---- reading ----

    private String commandStatus(OrgStructureCommandId id) {
        return jdbc.sql("select status from organization_workforce.org_structure_commands where org_structure_command_id = :id").param("id", id.value()).query(String.class).single();
    }

    private int transferred(OrgStructureCommandId id) {
        return jdbc.sql("select transferred_employee_count from organization_workforce.org_structure_commands where org_structure_command_id = :id").param("id", id.value())
                .query(Integer.class).single();
    }

    private String unitStatusOn(UUID unit, LocalDate day) {
        return jdbc.sql("""
                select status from organization_workforce.org_unit_versions where org_unit_id = :id and superseded_at is null and effective_from_date <= :day
                   and (effective_to_date is null or effective_to_date > :day)
                """).param("id", unit).param("day", day).query(String.class).single();
    }

    private UUID unitOf(UUID person, LocalDate day) {
        return jdbc.sql("""
                select org_unit_id from organization_workforce.assignment_versions where employee_id = :id and superseded_at is null and effective_from_date <= :day
                   and (effective_to_date is null or effective_to_date > :day)
                """).param("id", person).param("day", day).query(UUID.class).single();
    }

    private long commandCount() {
        return jdbc.sql("select count(*) from organization_workforce.org_structure_commands").query(Long.class).single();
    }

    /** A merge due in five days with a ready person and a stuck one (an open command of theirs on that day), the clock then moved to that day. */
    private record StuckMerge(UUID merged, UUID receiving, UUID ready, UUID stuck, OrgStructureCommandId merge, LocalDate day) {
    }

    private StuckMerge aMergeWithOneStuckPerson() {
        UUID merged = unit();
        UUID receiving = unit();
        UUID ready = person(merged, "REGULAR");
        UUID stuck = person(merged, "PROBATION");
        LocalDate day = today.plusDays(5);
        regularizeOn(stuck, day);
        OrgStructureCommandId merge = merge(merged, receiving, day);
        clock.advance(Duration.ofDays(5));
        return new StuckMerge(merged, receiving, ready, stuck, merge, day);
    }

    // ---- what the merge does with the people ----

    @Test
    @DisplayName("#907 复审 M1、设计说明 §8 QF1 (a)：被拒的人之后被别的命令调出被并入单元，重跑时算作已转：合并转 EFFECTIVE、单元撤销")
    void aRefusedPersonWhoLeavesByAnotherCommandCountsAsDone() {
        StuckMerge m = aMergeWithOneStuckPerson();
        catchThrowable(() -> commands.executeMerge(m.merge()));
        assertThat(commandStatus(m.merge())).isEqualTo("IN_PROGRESS");
        assertThat(transferred(m.merge())).isEqualTo(1);
        assignedFrom(m.stuck(), unit(), m.day());

        commands.executeMerge(m.merge());

        assertThat(commandStatus(m.merge())).isEqualTo("EFFECTIVE");
        assertThat(transferred(m.merge())).isEqualTo(2);
        assertThat(unitStatusOn(m.merged(), m.day())).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("#907 复审 M5：被并入单元里还有人时，收尾步骤拒绝：合并仍在执行中、单元不撤销（去掉这道检查则按已转人数收尾而撤销有人的单元）")
    void finishingRefusesWhileSomebodyIsStillInTheMergedUnit() {
        StuckMerge m = aMergeWithOneStuckPerson();
        catchThrowable(() -> commands.executeMerge(m.merge()));
        assertThat(unitOf(m.stuck(), m.day())).isEqualTo(m.merged());

        Throwable refused = catchThrowable(() -> steps.finish(m.merge(), UUID.randomUUID()));

        assertThat(refused).isInstanceOf(DomainException.class);
        assertThat(commandStatus(m.merge())).isEqualTo("IN_PROGRESS");
        assertThat(unitStatusOn(m.merged(), m.day())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("#907 终审 M1：合并开始之后才有人被安排在合并日之后调入被并入单元：收尾步骤拒绝（只看合并日当天会撤销有人的单元），合并仍在执行中、单元不撤销")
    void finishingRefusesWhenSomebodyJoinsAfterTheMergeDay() {
        UUID latecomer = person(unit(), "REGULAR");
        StuckMerge m = aMergeWithOneStuckPerson();
        catchThrowable(() -> commands.executeMerge(m.merge()));
        assignedFrom(m.stuck(), unit(), m.day());
        assignedFrom(latecomer, m.merged(), m.day().plusDays(3));

        Throwable refused = catchThrowable(() -> commands.executeMerge(m.merge()));

        assertThat(refused).isInstanceOf(DomainException.class);
        assertThat(commandStatus(m.merge())).isEqualTo("IN_PROGRESS");
        assertThat(unitStatusOn(m.merged(), m.day().plusDays(3))).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("#907 终审 M1：已录入的将来调入使合并在录入时被拒，不留命令行")
    void aMergeIsRefusedWhileSomebodyIsStillToJoinTheMergedUnit() {
        UUID merged = unit();
        UUID receiving = unit();
        UUID coming = person(unit(), "REGULAR");
        assignedFrom(coming, merged, today.plusDays(8));
        long before = commandCount();

        assertThatThrownBy(() -> merge(merged, receiving, today.plusDays(5))).isInstanceOf(DomainException.class);

        assertThat(commandCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("#907 复审 M2：承接单元要从合并日起一直在用、自己没有待办的合并；已有合并要并入的单元，撤销或被合并走也被拒")
    void theReceivingUnitMustStayInForce() {
        UUID dying = unit();
        UUID leaving = unit();
        commands.enter(unitCommand(OrgStructureCommandKind.DISSOLVE, dying, today.plusDays(5), null, null));
        merge(leaving, unit(), today.plusDays(6));
        long before = commandCount();

        assertThatThrownBy(() -> merge(unit(), dying, today)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> merge(unit(), leaving, today.plusDays(6))).isInstanceOf(DomainException.class);

        assertThat(commandCount()).isEqualTo(before);
        UUID home = unit();
        merge(unit(), home, today);
        assertThatThrownBy(() -> commands.enter(unitCommand(OrgStructureCommandKind.DISSOLVE, home, today.plusDays(2), null, null))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> merge(home, unit(), today)).isInstanceOf(DomainException.class);
        assertThat(commands.enter(unitCommand(OrgStructureCommandKind.DISSOLVE, unit(), today.plusDays(2), null, null))).as("a unit nobody merges into is not held back").isNotNull();
    }

    // ---- locks ----

    private Connection lock(String table, UUID id) throws SQLException {
        Connection holder = dataSource.getConnection();
        holder.setAutoCommit(false);
        try (PreparedStatement statement = holder.prepareStatement("select 1 from organization_workforce." + table + " where id = ? for update")) {
            statement.setObject(1, id);
            statement.executeQuery().close();
        }
        return holder;
    }

    private Connection holdTreeLock() throws SQLException {
        Connection holder = dataSource.getConnection();
        holder.setAutoCommit(false);
        try (PreparedStatement statement = holder.prepareStatement("select pg_advisory_xact_lock(hashtextextended('organization.workforce.org-tree', 0))")) {
            statement.executeQuery().close();
        }
        return holder;
    }

    private void awaitBlocked(String queryPattern, int count) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(25)).until(() -> {
            try (Connection watcher = dataSource.getConnection(); PreparedStatement statement = watcher.prepareStatement(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like ?")) {
                statement.setString(1, queryPattern);
                try (ResultSet blocked = statement.executeQuery()) {
                    return blocked.next() && blocked.getInt(1) >= count;
                }
            }
        });
    }

    private void awaitBlockedOn(String table) {
        awaitBlocked("%from organization_workforce." + table + " where id = %for " + ("org_units".equals(table) ? "no key update" : "update") + "%", 1);
    }

    /** Runs the call as the signed-in caller of the test thread, on another thread. */
    private <T> CompletableFuture<T> asCaller(Supplier<T> call) {
        SecurityContext context = SecurityContextHolder.getContext();
        return CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.setContext(context);
            try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "test.organization.workforce")) {
                return call.get();
            }
        });
    }

    private OrgStructureCommandId aDueMerge(UUID merged) {
        return merge(merged, unit(), today);
    }

    @Test
    @DisplayName("#907 lead 完成标准、one-design #908 裁定：合并 PENDING 到 IN_PROGRESS 先取被并入单元根行锁：他人持有该锁时执行在锁上等待、合并仍 PENDING、没有调岗；锁释放后执行完成")
    void startingAMergeWaitsForTheOrgUnitRoot() throws Exception {
        UUID merged = unit();
        person(merged, "REGULAR");
        OrgStructureCommandId merge = aDueMerge(merged);
        Connection holder = lock("org_units", merged);
        CompletableFuture<Void> run = asCaller(() -> {
            commands.executeMerge(merge);
            return null;
        });

        awaitBlockedOn("org_units");

        assertThat(run).as("the run waits for the unit").isNotDone();
        assertThat(commandStatus(merge)).isEqualTo("PENDING");
        holder.commit();
        holder.close();
        run.get(30, TimeUnit.SECONDS);
        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
    }

    @Test
    @DisplayName("#907 lead 完成标准、one-design #908 裁定：每条生成的调岗先取被并入单元根行锁、再取员工根行锁：员工行被他人锁住时，执行持有单元根行锁在员工行上等待，员工锁释放后调岗完成")
    void eachTransferHoldsTheOrgUnitRootWhileItWaitsForTheEmployee() throws Exception {
        UUID merged = unit();
        UUID person = person(merged, "REGULAR");
        OrgStructureCommandId merge = aDueMerge(merged);
        Connection holder = lock("employees", person);
        CompletableFuture<Void> run = asCaller(() -> {
            commands.executeMerge(merge);
            return null;
        });

        awaitBlockedOn("employees");

        assertThat(run).isNotDone();
        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            try (PreparedStatement statement = other.prepareStatement("select 1 from organization_workforce.org_units where id = ? for update nowait")) {
                statement.setObject(1, merged);
                assertThatThrownBy(statement::executeQuery).isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
            }
            other.rollback();
        }
        holder.commit();
        holder.close();
        run.get(30, TimeUnit.SECONDS);
        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
        assertThat(unitOf(person, today)).isNotEqualTo(merged);
    }

    @Test
    @DisplayName("#907 终审 M2：合并取单元根行锁不挡外键检查的键共享锁（人事命令先锁员工、再写任职时外键检查要它），所以不会与普通人事命令死锁；他人持有键共享锁时合并照常完成")
    void theMergeDoesNotBlockTheForeignKeyCheckOfAPersonnelCommand() throws Exception {
        UUID merged = unit();
        person(merged, "REGULAR");
        OrgStructureCommandId merge = aDueMerge(merged);
        Connection holder = dataSource.getConnection();
        holder.setAutoCommit(false);
        try (PreparedStatement statement = holder.prepareStatement("select 1 from organization_workforce.org_units where id = ? for key share")) {
            statement.setObject(1, merged);
            statement.executeQuery().close();
        }

        try {
            asCaller(() -> {
                commands.executeMerge(merge);
                return null;
            }).get(30, TimeUnit.SECONDS);
        } finally {
            holder.rollback();
            holder.close();
        }

        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
    }

    @Test
    @DisplayName("#907 复审 M5/M3：录入先取树级锁、再取目标单元根行锁：他人持有目标单元根行锁时，录入在锁上等待、不留命令行；锁释放后录入完成")
    void enteringWaitsForTheTargetUnitRoot() throws Exception {
        UUID target = unit();
        long before = commandCount();
        Connection holder = lock("org_units", target);

        CompletableFuture<OrgStructureCommandId> entry = asCaller(() -> commands.enter(unitCommand(OrgStructureCommandKind.SET_ATTRIBUTES, target, today.plusDays(3), null, null)));

        awaitBlockedOn("org_units");
        assertThat(entry).isNotDone();
        assertThat(commandCount()).isEqualTo(before);
        holder.commit();
        holder.close();
        assertThat(entry.get(30, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    @DisplayName("#907 复审 M5：撤回同样先取目标单元根行锁：他人持有该根行锁时，撤回在锁上等待、命令仍待生效；锁释放后撤回完成")
    void withdrawingWaitsForTheTargetUnitRoot() throws Exception {
        UUID target = unit();
        OrgStructureCommandId command = commands.enter(unitCommand(OrgStructureCommandKind.SET_ATTRIBUTES, target, today.plusDays(3), null, null));
        Connection holder = lock("org_units", target);

        CompletableFuture<Void> withdrawal = asCaller(() -> {
            commands.withdraw(new WithdrawOrgStructureCommand(command, "synthetic: waits for the root"));
            return null;
        });

        awaitBlockedOn("org_units");
        assertThat(withdrawal).isNotDone();
        assertThat(commandStatus(command)).isEqualTo("PENDING");
        holder.commit();
        holder.close();
        withdrawal.get(30, TimeUnit.SECONDS);
        assertThat(commandStatus(command)).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("#907 复审 M3：树规则的写入串行：撤销上级与把下级调到该上级之下同时录入，只有一条成功，不会留下在用的下级挂在已撤销的上级之下")
    void twoCommandsThatBreakTheTreeTogetherCannotBothPass() throws Exception {
        UUID parent = unit();
        UUID child = unit();
        LocalDate day = today.plusDays(3);
        Connection tree = holdTreeLock();

        CompletableFuture<OrgStructureCommandId> dissolve = asCaller(() -> commands.enter(unitCommand(OrgStructureCommandKind.DISSOLVE, parent, day, null, null)));
        CompletableFuture<OrgStructureCommandId> reparent = asCaller(() -> commands.enter(unitCommand(OrgStructureCommandKind.REPARENT, child, day.plusDays(1), parent, null)));

        awaitBlocked("%pg_advisory_xact_lock%", 2);
        tree.commit();
        tree.close();
        int succeeded = 0;
        for (CompletableFuture<OrgStructureCommandId> one : java.util.List.of(dissolve, reparent)) {
            try {
                one.get(30, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException refused) {
                assertThat(refused.getCause()).isInstanceOf(DomainException.class);
            }
        }

        assertThat(succeeded).as("exactly one of the two passes the rules").isEqualTo(1);
    }

    @Test
    @DisplayName("#907 复审 M5：需要权限才能录入：没有该权限的调用被拒（去掉 @PreAuthorize 则红），不留命令行")
    void enteringNeedsThePermission() {
        UUID target = unit();
        long before = commandCount();
        signIn("organization.workforce:personnel-command:create");

        Throwable refused = catchThrowable(() -> commands.enter(unitCommand(OrgStructureCommandKind.SET_ATTRIBUTES, target, today, null, null)));

        assertThat(refused).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(commandCount()).isEqualTo(before);
    }

    // ---- later arrivals (one-design ruling on #907, user answer a) ----

    private PersonnelCommandId transferInto(UUID person, UUID unit, LocalDate day) {
        signIn(PERSONNEL_PERMISSION);
        try {
            return personnel.enter(new EnterPersonnelCommand(new EmployeeId(person), PersonnelCommandKind.TRANSFER, day, null, new OrgUnitId(unit), null, null, null, null, null, null,
                    null, null));
        } finally {
            signIn(ORG_PERMISSION);
        }
    }

    @Test
    @DisplayName("#907 one-design 裁定：人事命令把人放进有待办合并的部门，日期不早于合并日的，录入被拒（具名冲突错误）；合并日前一天的可以，合并会把人转走；合并撤回后同样的调岗可以录入")
    void aPersonnelCommandCannotPlaceSomebodyInAMergedUnitFromTheMergeDay() {
        UUID merged = unit();
        UUID receiving = unit();
        UUID early = person(unit(), "REGULAR");
        UUID onTheDay = person(unit(), "REGULAR");
        UUID later = person(unit(), "REGULAR");
        LocalDate day = today.plusDays(5);
        OrgStructureCommandId merge = merge(merged, receiving, day);

        for (Throwable refused : java.util.List.of(catchThrowable(() -> transferInto(onTheDay, merged, day)), catchThrowable(() -> transferInto(later, merged, day.plusDays(2))))) {
            assertThat(refused).isInstanceOf(DomainException.class);
            assertThat(((DomainException) refused).errorCode().toString()).isEqualTo("ORGANIZATION_WORKFORCE_ORG_UNIT_MERGE_OPEN");
        }
        transferInto(early, merged, day.minusDays(1));
        clock.advance(Duration.ofDays(5));
        commands.executeMerge(merge);

        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
        assertThat(unitOf(early, day)).as("the merge moved the person who arrived the day before").isEqualTo(receiving);
    }

    @Test
    @DisplayName("#907 one-design 裁定：合并撤回后，原先被拒的调岗可以录入")
    void afterTheMergeIsWithdrawnTheSameTransferIsAccepted() {
        UUID merged = unit();
        UUID person = person(unit(), "REGULAR");
        LocalDate day = today.plusDays(5);
        OrgStructureCommandId merge = merge(merged, unit(), day);
        assertThatThrownBy(() -> transferInto(person, merged, day)).isInstanceOf(DomainException.class);

        commands.withdraw(new WithdrawOrgStructureCommand(merge, "synthetic: not this month"));

        assertThat(transferInto(person, merged, day)).isNotNull();
    }

    @Test
    @DisplayName("#907 用户答复 (a)、one-design 裁定：合并之前已记下的将来调入使合并被拒，拒绝信息和合并预演都列出阻塞的员工与他们的人事命令")
    void theRefusalAndThePreviewNameWhoBlocksTheMerge() {
        UUID merged = unit();
        UUID receiving = unit();
        UUID resident = person(merged, "REGULAR");
        UUID coming = person(unit(), "REGULAR");
        LocalDate day = today.plusDays(5);
        var command = transferInto(coming, merged, day.plusDays(3));

        var preview = commands.previewMerge(new com.forcitis.one.organization.workforce.api.PreviewOrgUnitMerge(new OrgUnitId(merged), new OrgUnitId(receiving), day));
        Throwable refused = catchThrowable(() -> merge(merged, receiving, day));

        assertThat(preview.employees()).containsExactly(new EmployeeId(resident));
        assertThat(preview.blocking()).singleElement().satisfies(blocking -> {
            assertThat(blocking.employeeId()).isEqualTo(new EmployeeId(coming));
            assertThat(blocking.startDate()).isEqualTo(day.plusDays(3));
            assertThat(blocking.personnelCommandId()).isEqualTo(command);
        });
        assertThat(refused).isInstanceOf(DomainException.class).hasMessageContaining(coming.toString()).hasMessageContaining(command.toString());
    }

    // ---- final review (round 3) ----

    /** A pending position-only transfer: it writes a new assignment row in the same unit and names no unit. */
    private void positionChangeOn(UUID person, LocalDate day) {
        UUID position = Uuids.timeOrdered(clock);
        jdbc.sql("insert into organization_workforce.positions (id, code, name, status) values (:id, :code, 'other position', 'ACTIVE')").param("id", position)
                .param("code", "Q" + position.toString().substring(24)).update();
        signIn(PERSONNEL_PERMISSION);
        personnel.enter(new EnterPersonnelCommand(new EmployeeId(person), PersonnelCommandKind.TRANSFER, day, null, null, new com.forcitis.one.organization.workforce.api.PositionId(position),
                null, null, null, null, null, null, null));
        signIn(ORG_PERMISSION);
    }

    @Test
    @DisplayName("#907 终审 M3：同一单元里的后续任职行（之后的岗位或直属领导变更）不是调入：住户不阻塞合并的录入与开始，预演只把他列在要转走的人里；合并后他在承接单元")
    void aResidentWithALaterPositionChangeIsNotAnArrival() {
        UUID merged = unit();
        UUID receiving = unit();
        UUID resident = person(merged, "REGULAR");
        LocalDate day = today.plusDays(5);
        positionChangeOn(resident, day.plusDays(3));

        var preview = commands.previewMerge(new com.forcitis.one.organization.workforce.api.PreviewOrgUnitMerge(new OrgUnitId(merged), new OrgUnitId(receiving), day));
        OrgStructureCommandId merge = merge(merged, receiving, day);
        clock.advance(Duration.ofDays(5));
        commands.executeMerge(merge);

        assertThat(preview.employees()).containsExactly(new EmployeeId(resident));
        assertThat(preview.blocking()).isEmpty();
        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
        assertThat(unitOf(resident, day.plusDays(3))).isEqualTo(receiving);
    }

    @Test
    @DisplayName("#907 终审 M2：合并执行中（人数已冻结），任何日期放进被并入单元的人事命令都被拒：否则新来的人占走被拒者的名额，合并永远转不完")
    void nobodyMayBePlacedInAMergedUnitWhileItsMergeIsInProgress() {
        StuckMerge m = aMergeWithOneStuckPerson();
        catchThrowable(() -> commands.executeMerge(m.merge()));
        assertThat(commandStatus(m.merge())).isEqualTo("IN_PROGRESS");
        UUID newcomer = person(unit(), "REGULAR");

        Throwable refused = catchThrowable(() -> transferInto(newcomer, m.merged(), m.day().minusDays(1)));

        assertThat(refused).isInstanceOf(DomainException.class);
        assertThat(((DomainException) refused).errorCode().toString()).isEqualTo("ORGANIZATION_WORKFORCE_ORG_UNIT_MERGE_OPEN");
    }

    @Test
    @DisplayName("#907 终审 M1：人事命令录入先在被放入的单元根行上取共享锁：它在写入过程中，合并的录入在单元根行锁上等待，等人事命令提交后才看得到这条调入而被拒；去掉共享锁则合并录入不等待、调入被留在撤销的单元里")
    void aPersonnelEntryAndTheMergeOfItsUnitSerialize() throws Exception {
        UUID merged = unit();
        UUID receiving = unit();
        UUID latecomer = person(unit(), "REGULAR");
        UUID position = jdbc.sql("select position_id from organization_workforce.assignment_versions where employee_id = :id").param("id", latecomer).query(UUID.class).single();
        Connection holder = lock("positions", position);
        signIn(PERSONNEL_PERMISSION);
        CompletableFuture<?> entry = asCaller(() -> personnel.enter(new EnterPersonnelCommand(new EmployeeId(latecomer), PersonnelCommandKind.TRANSFER, today.plusDays(2), null,
                new OrgUnitId(merged), new com.forcitis.one.organization.workforce.api.PositionId(position), null, null, null, null, null, null, null)));
        awaitBlocked("%insert into organization_workforce.personnel_commands%", 1);
        signIn(ORG_PERMISSION);

        CompletableFuture<OrgStructureCommandId> mergeEntry = asCaller(() -> merge(merged, receiving, today));

        awaitBlockedOn("org_units");
        assertThat(mergeEntry).as("the merge waits for the personnel entry that holds the unit").isNotDone();
        holder.commit();
        holder.close();
        entry.get(30, TimeUnit.SECONDS);
        assertThatThrownBy(() -> mergeEntry.get(30, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(DomainException.class);
        assertThat(unitStatusOn(merged, today.plusDays(2))).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("#907 终审四轮 M1：合并转人先锁员工行再看人还在不在被并入单元：人事刚把此人调去别的单元（提交晚于合并开始），合并不再把他从别处转走，也不生成调岗")
    void aMoveDoesNotOverrideATransferThatCommitsWhileItWaitsForTheEmployee() throws Exception {
        UUID merged = unit();
        UUID elsewhere = unit();
        UUID resident = person(merged, "REGULAR");
        UUID position = jdbc.sql("select position_id from organization_workforce.assignment_versions where employee_id = :id").param("id", resident).query(UUID.class).single();
        OrgStructureCommandId merge = aDueMerge(merged);
        Connection holder = lock("positions", position);
        signIn(PERSONNEL_PERMISSION);
        CompletableFuture<?> transfer = asCaller(() -> personnel.enter(new EnterPersonnelCommand(new EmployeeId(resident), PersonnelCommandKind.TRANSFER, today.minusDays(1), null,
                new OrgUnitId(elsewhere), new com.forcitis.one.organization.workforce.api.PositionId(position), null, null, null, null, null, null, null)));
        awaitBlocked("%insert into organization_workforce.personnel_commands%", 1);
        signIn(ORG_PERMISSION);

        CompletableFuture<Void> run = asCaller(() -> {
            commands.executeMerge(merge);
            return null;
        });

        awaitBlocked("%from organization_workforce.employees where id = %for update%", 1);
        holder.commit();
        holder.close();
        transfer.get(30, TimeUnit.SECONDS);
        run.get(30, TimeUnit.SECONDS);

        assertThat(unitOf(resident, today)).as("the person stays where HR put them").isEqualTo(elsewhere);
        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
    }

    @Test
    @DisplayName("#907 终审 M4：合并开始时再校验承接单元：录入之后承接单元被撤销（写在命令之外），开始被拒，合并仍待生效、没有调岗")
    void startingAMergeChecksTheReceivingUnitAgain() {
        UUID merged = unit();
        UUID receiving = unit();
        UUID resident = person(merged, "REGULAR");
        OrgStructureCommandId merge = merge(merged, receiving, today);
        jdbc.sql("update organization_workforce.org_unit_versions set effective_to_date = :day, closed_at = now(), updated_at = now() where org_unit_id = :id and effective_to_date is null")
                .param("day", today).param("id", receiving).update();
        jdbc.sql("""
                insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, status, region_kind, is_delivery_unit)
                values (:version, :id, :day, 'unit', 'REVOKED', 'HEADQUARTERS', false)
                """).param("version", Uuids.timeOrdered(clock)).param("id", receiving).param("day", today).update();

        Throwable refused = catchThrowable(() -> commands.executeMerge(merge));

        assertThat(refused).isInstanceOf(DomainException.class);
        assertThat(commandStatus(merge)).isEqualTo("PENDING");
        assertThat(unitOf(resident, today)).isEqualTo(merged);
    }

    @Test
    @DisplayName("#907 终审 M4：合并开始先取树级锁：他人持有树级锁时，执行在锁上等待、合并仍待生效；锁释放后执行完成")
    void startingAMergeWaitsForTheTreeLock() throws Exception {
        UUID merged = unit();
        person(merged, "REGULAR");
        OrgStructureCommandId merge = aDueMerge(merged);
        Connection tree = holdTreeLock();

        CompletableFuture<Void> run = asCaller(() -> {
            commands.executeMerge(merge);
            return null;
        });

        awaitBlocked("%pg_advisory_xact_lock%", 1);
        assertThat(run).isNotDone();
        assertThat(commandStatus(merge)).isEqualTo("PENDING");
        tree.commit();
        tree.close();
        run.get(30, TimeUnit.SECONDS);
        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
    }

    // ---- #1098: placements that name no unit, and merges that chain ----

    private PersonnelCommandId transferOut(UUID person, UUID toUnit, LocalDate day) {
        signIn(PERSONNEL_PERMISSION);
        try {
            return personnel.enter(new EnterPersonnelCommand(new EmployeeId(person), PersonnelCommandKind.TRANSFER, day, null, new OrgUnitId(toUnit), null, null, null, null, null, null, null, null));
        } finally {
            signIn(ORG_PERMISSION);
        }
    }

    private void withdraw(PersonnelCommandId command) {
        signIn(PERSONNEL_PERMISSION);
        try {
            personnel.withdraw(new WithdrawPersonnelCommand(command, "entered by mistake"));
        } finally {
            signIn(ORG_PERMISSION);
        }
    }

    @Test
    @DisplayName("#1098 S1a-1：合并 IN_PROGRESS 时，撤回会把冻结名单之外的人放回被并入单元的待办调出：被拒，调出命令仍待生效、人仍在原处")
    void withdrawingATransferOutIsRefusedWhileTheMergeIsInProgress() {
        UUID merged = unit();
        UUID away = unit();
        UUID mover = person(merged, "REGULAR");
        UUID stuck = person(merged, "PROBATION");
        LocalDate day = today.plusDays(5);
        regularizeOn(stuck, day);
        PersonnelCommandId out = transferOut(mover, away, day.minusDays(2));
        OrgStructureCommandId merge = merge(merged, unit(), day);
        clock.advance(Duration.ofDays(5));
        catchThrowable(() -> commands.executeMerge(merge));
        assertThat(commandStatus(merge)).isEqualTo("IN_PROGRESS");

        Throwable refused = catchThrowable(() -> withdraw(out));

        assertThat(refused).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).hasToString("ORGANIZATION_WORKFORCE_ORG_UNIT_MERGE_OPEN"));
        assertThat(unitOf(mover, day)).isEqualTo(away);
        assertThat(jdbc.sql("select status from organization_workforce.personnel_commands where personnel_command_id = :id").param("id", out.value()).query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("#1098 S1a-2：合并 PENDING 时，撤回使这个人在合并日仍在被并入单元：允许（合并会移走他）")
    void withdrawingATransferOutIsAllowedWhileTheMergeIsPendingAndTheMergeDayIsCovered() {
        UUID merged = unit();
        UUID mover = person(merged, "REGULAR");
        LocalDate day = today.plusDays(5);
        PersonnelCommandId out = transferOut(mover, unit(), day.minusDays(2));
        merge(merged, unit(), day);

        withdraw(out);

        assertThat(unitOf(mover, day)).isEqualTo(merged);
    }

    @Test
    @DisplayName("#1098 S2-1：Y 并入 Z 已录入且合并日不晚于 X 并入 Y 的日期：录入 X 并入 Y 被拒；Y 并入 Z 晚于它则放行")
    void aChainOfMergesIsRefusedWhenTheReceivingUnitIsMergedAwayFirst() {
        UUID x = unit();
        UUID y = unit();
        UUID z = unit();
        merge(y, z, today.plusDays(3));
        long before = commandCount();

        assertThat(catchThrowable(() -> merge(x, y, today.plusDays(3)))).isInstanceOf(DomainException.class);
        assertThat(catchThrowable(() -> merge(x, y, today.plusDays(4)))).isInstanceOf(DomainException.class);
        assertThat(commandCount()).isEqualTo(before);
        assertThat(merge(x, y, today.plusDays(2))).as("X arrives in Y a day before Y goes away").isNotNull();
    }

    @Test
    @DisplayName("#1098 S2-2：X 并入 Y 已录入且还没有人移走（甚至进行中）：补录合并日不晚于它的 Y 并入 Z 被拒；晚于它的放行；撤回 X 并入 Y 后同样放行")
    void aBackDatedMergeOfTheReceivingUnitIsRefused() {
        UUID x = unit();
        UUID y = unit();
        UUID z = unit();
        person(x, "REGULAR");
        OrgStructureCommandId first = merge(x, y, today.plusDays(3));
        long before = commandCount();

        assertThat(catchThrowable(() -> merge(y, z, today.plusDays(2)))).isInstanceOf(DomainException.class);
        assertThat(catchThrowable(() -> merge(y, z, today.plusDays(3)))).isInstanceOf(DomainException.class);
        assertThat(commandCount()).isEqualTo(before);
        assertThat(merge(y, z, today.plusDays(4))).as("Y goes away after X has arrived").isNotNull();
        commands.withdraw(new WithdrawOrgStructureCommand(first, "re-planned"));
        assertThat(merge(unit(), y, today.plusDays(1))).isNotNull();
    }

    @Test
    @DisplayName("#1098 S1b：到期日待放行离职的人在进行中合并的被并入单元里：保留照常生效（人仍在职、不报错），写一条告警；收尾被拒且消息与预览都点名此人和被保留的命令；HR 调出后收尾完成")
    void aDueDayHoldStandsWhileTheMergeIsInProgressAndTheMergeNamesTheHeldPerson() {
        UUID merged = unit();
        UUID away = unit();
        UUID held = person(merged, "REGULAR");
        UUID stuck = person(merged, "PROBATION");
        LocalDate day = today.plusDays(3);
        regularizeOn(stuck, day);
        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(), true, 2, "", "", Set.of()));
        signIn(PERSONNEL_PERMISSION);
        PersonnelCommandId separation = personnel.enter(new EnterPersonnelCommand(new EmployeeId(held), PersonnelCommandKind.SEPARATE, null, day.minusDays(1), null, null, null, null, null, null, null, null, null));
        signIn(ORG_PERMISSION);
        OrgStructureCommandId merge = merge(merged, unit(), day);
        clock.advance(Duration.ofDays(3));
        catchThrowable(() -> commands.executeMerge(merge));
        assertThat(commandStatus(merge)).isEqualTo("IN_PROGRESS");
        when(review.review(any(), any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(new EmployeeId(held)), true, 1, "", "", Set.of()));
        Logger logger = (Logger) LoggerFactory.getLogger(ApplyDueCommand.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);

        try {
            dueJob.run();
        } finally {
            logger.detachAppender(logged);
        }

        assertThat(jdbc.sql("select status from organization_workforce.personnel_commands where personnel_command_id = :id").param("id", separation.value()).query(String.class).single()).isEqualTo("HELD");
        assertThat(unitOf(held, day)).isEqualTo(merged);
        assertThat(logged.list).anyMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains(separation.value().toString()) && e.getFormattedMessage().contains(merge.value().toString()));
        Throwable refused = catchThrowable(() -> steps.finish(merge, UUID.randomUUID()));
        assertThat(refused).isInstanceOf(DomainException.class).hasMessageContaining(held.toString()).hasMessageContaining(separation.value().toString());
        assertThat(commands.previewMerge(new PreviewOrgUnitMerge(new OrgUnitId(merged), new OrgUnitId(away), day)).held())
                .containsExactly(new HeldSeparation(new EmployeeId(held), separation));

        withdraw(separation);
        transferOut(held, away, zone.today(clock));
        assignedFrom(stuck, away, day);
        commands.executeMerge(merge);
        assertThat(commandStatus(merge)).isEqualTo("EFFECTIVE");
    }

    /** What a race leaves: a merge of the receiving unit Y dated the day before the merge into it, copied from that merge's row because the entry rules refuse it. */
    private void slipInMergeAwayFrom(OrgStructureCommandId template, UUID y, UUID z, LocalDate day) {
        List<String> columns = jdbc.sql("select column_name from information_schema.columns where table_schema = 'organization_workforce' and table_name = 'org_structure_commands'"
                + " order by ordinal_position").query(String.class).list();
        String values = columns.stream().map(c -> switch (c) {
            case "org_structure_command_id" -> ":id";
            case "target_org_unit_id" -> ":target";
            case "merge_into_org_unit_id" -> ":into";
            case "effective_date" -> ":day";
            default -> c;
        }).collect(java.util.stream.Collectors.joining(", "));
        jdbc.sql("insert into organization_workforce.org_structure_commands (" + String.join(", ", columns) + ") select " + values
                + " from organization_workforce.org_structure_commands where org_structure_command_id = :template")
                .param("id", UUID.randomUUID()).param("target", y).param("into", z).param("day", day).param("template", template.value()).update();
    }

    @Test
    @DisplayName("#1098 S2 第二道线（启动）：录入时漏过的竞态连环合并：X 并入 Y 启动时被拒，合并仍 PENDING，没人被放进 Y")
    void aChainedMergeThatSlippedInStopsTheStart() {
        UUID x = unit();
        UUID y = unit();
        UUID resident = person(x, "REGULAR");
        OrgStructureCommandId first = merge(x, y, today);
        slipInMergeAwayFrom(first, y, unit(), today.minusDays(1));

        Throwable refused = catchThrowable(() -> commands.executeMerge(first));

        assertThat(refused).isInstanceOf(DomainException.class);
        assertThat(commandStatus(first)).isEqualTo("PENDING");
        assertThat(unitOf(resident, today)).isEqualTo(x);
    }

    @Test
    @DisplayName("#1098 S2 第二道线（生成的调岗）：合并已进行中、之后漏进来的连环合并：生成的调岗在 Y 上被拒，没人被放进 Y")
    void aChainedMergeThatSlippedInStopsTheGeneratedTransfer() {
        UUID x = unit();
        UUID y = unit();
        UUID resident = person(x, "REGULAR");
        OrgStructureCommandId first = merge(x, y, today);
        steps.start(first, UUID.randomUUID());
        slipInMergeAwayFrom(first, y, unit(), today.minusDays(1));

        Throwable refused = catchThrowable(() -> steps.move(first, new EmployeeId(resident), UUID.randomUUID()));

        assertThat(refused).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).hasToString("ORGANIZATION_WORKFORCE_ORG_UNIT_MERGE_OPEN"));
        assertThat(unitOf(resident, today)).isEqualTo(x);
    }
}
