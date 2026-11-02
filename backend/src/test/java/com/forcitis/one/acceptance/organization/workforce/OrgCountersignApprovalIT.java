package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.ORG_DATA_ADMIN_ROLE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #910 acceptance part (#909 F4a), note section 5 rules 2, 5 (deciders) and 6: nothing is written while an org command awaits; the last
 * approval re-checks the basis and either writes the versions (PENDING or EFFECTIVE) or turns into a re-check failure; any head may reject;
 * a decider holds the approve permission and is the finance head of the row's entity in today's version, business_admin only from
 * escalation level 2, never the drafter; when the command takes effect EmployeeLegalEntityChanged goes out once per changed employee.
 */
class OrgCountersignApprovalIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgCountersignApprovalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate DAY = TODAY.plusDays(10);

    private record Waiting(UUID unit, UUID first, UUID second, UUID command) {
    }

    /** A decision the api refuses: a business refusal or a refusal of the caller's rights, not a missing bean or a broken fixture. */
    private static void assertDecisionRefused(ThrowingCallable decision) {
        Throwable failure = catchThrowable(decision);
        assertThat(failure).as("the decision is refused").isNotNull();
        assertThat(failure instanceof DomainException || isPermissionRefusal(failure)).as("refused by the api, not broken: " + failure).isTrue();
    }

    /** Runs the two decisions at the same moment, each in its own thread with its decider's security context; answers their failures (null = none). */
    private List<Throwable> atOnce(Staff firstWho, ThrowingConsumer<OrgCountersignProbe> first, Staff secondWho, ThrowingConsumer<OrgCountersignProbe> second) throws Exception {
        List<SecurityContext> contexts = new ArrayList<>();
        for (Staff who : List.of(firstWho, secondWho)) {
            actIn(who);
            restoreActing();
            contexts.add(SecurityContextHolder.getContext());
        }
        actIn(orgAdmin);
        OrgCountersignProbe probe = OrgCountersignProbe.of(springContext);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Throwable>> runs = new ArrayList<>();
        List<ThrowingConsumer<OrgCountersignProbe>> calls = List.of(first, second);
        for (int i = 0; i < 2; i++) {
            SecurityContext context = contexts.get(i);
            ThrowingConsumer<OrgCountersignProbe> call = calls.get(i);
            runs.add(pool.submit(() -> {
                SecurityContextHolder.setContext(context);
                try {
                    start.await();
                    return catchThrowable(() -> call.accept(probe));
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("neither decision hangs").isTrue();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> run : runs) {
            failures.add(run.get());
        }
        return failures;
    }

    private Waiting waiting(LocalDate day) throws Exception {
        UUID unitX = seedUnit(topE1);
        UUID first = inheritingIn(unitX);
        UUID second = inheritingIn(unitX);
        UUID command = assignLegalEntity(unitX, day, e2);
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        return new Waiting(unitX, first, second, command);
    }

    @Test
    @DisplayName("#910 note §5 第 2 条：只有一位负责人批准时命令仍待会签、不写版本；两位都批准后写单元归属版本（E2，自生效日起，来源为本命令）并转待生效；生效日未到不发「员工成本法人已变更」")
    void bothApprovalsWriteTheVersionsAndOneIsNotEnough() throws Exception {
        Waiting waiting = waiting(DAY);

        approve(headE1, waiting.command(), KIND_CHANGE, e1);

        assertThat(statusOf(waiting.command())).isEqualTo(AWAITING);
        assertThat(unitAttributionRowsWrittenBy(waiting.command())).isZero();
        approve(headE2, waiting.command(), KIND_CHANGE, e2);

        assertThat(statusOf(waiting.command())).isEqualTo(PENDING);
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isEqualTo(APPROVED);
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isEqualTo(APPROVED);
        assertThat(unitAttributions(waiting.unit())).singleElement().satisfies(row -> {
            assertThat(row.get("legal_entity_id")).isEqualTo(e2);
            assertThat(row.get("effective_from_date")).isEqualTo(DAY);
            assertThat(row.get("change_org_structure_command_id")).isEqualTo(waiting.command());
        });
        assertThat(legalEntityChangesOf(waiting.first())).isZero();
    }

    @Test
    @DisplayName("#910 note §5 第 2、6 条：今天生效的命令两位都批准后直接生效：转 EFFECTIVE，每位依据里的员工各发一次「员工成本法人已变更」（之前 E1、之后 E2），别的人不发")
    void approvingATodayCommandTakesEffectAndPublishesOncePerEmployee() throws Exception {
        Waiting waiting = waiting(TODAY);
        UUID bystander = inheritingIn(topE1);

        approve(headE1, waiting.command(), KIND_CHANGE, e1);
        approve(headE2, waiting.command(), KIND_CHANGE, e2);

        assertThat(statusOf(waiting.command())).isEqualTo(EFFECTIVE);
        assertThat(legalEntityChangesOf(waiting.first())).isEqualTo(1);
        assertThat(legalEntityChangesOf(waiting.second())).isEqualTo(1);
        assertThat(legalEntityChangesOf(bystander)).isZero();
    }

    @Test
    @DisplayName("#910 note §5 第 2 条、用户答复 Q1（任一方可拒）：任一负责人拒签：命令转已撤回，拒签行带理由与决定人，另一行未决请求转 LAPSED（无决定人），不写版本、不发事件；此后另一位不能再批准")
    void eitherHeadMayReject() throws Exception {
        Waiting waiting = waiting(TODAY);

        reject(headE2, waiting.command(), KIND_CHANGE, e2, "synthetic: wrong entity");

        assertThat(statusOf(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(request(waiting.command(), KIND_CHANGE, e2).get("decision_reason")).isEqualTo("synthetic: wrong entity");
        assertThat(fromStored(request(waiting.command(), KIND_CHANGE, e2).get("decided_by"))).isEqualTo(headE2.userId());
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isEqualTo(LAPSED);
        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isNull();
        assertThat(unitAttributionRowsWrittenBy(waiting.command())).isZero();
        assertThat(legalEntityChangesOf(waiting.first())).isZero();
        assertDecisionRefused(() -> approve(headE1, waiting.command(), KIND_CHANGE, e1));
        assertThat(statusOf(waiting.command())).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#910 note §5 第 2 条（复核）：录入后单元里多了一位继承法人的在职员工（例如同步写入），最后一次批准时重算的依据与存的不同：该请求转 REJECTED 并带理由，命令转已撤回，其余未决行 LAPSED，不写版本")
    void theLastApprovalReChecksTheBasis() throws Exception {
        Waiting waiting = waiting(DAY);
        approve(headE1, waiting.command(), KIND_CHANGE, e1);
        inheritingIn(waiting.unit());

        catchThrowable(() -> approve(headE2, waiting.command(), KIND_CHANGE, e2));

        assertThat(statusOf(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isEqualTo(REJECTED);
        assertThat(request(waiting.command(), KIND_CHANGE, e2).get("decision_reason")).as("the re-check failure says why").isNotNull();
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).as("an earlier approval is not rewritten").isEqualTo(APPROVED);
        assertThat(unitAttributionRowsWrittenBy(waiting.command())).isZero();
        assertThat(unitAttributions(waiting.unit())).isEmpty();
    }

    @Test
    @DisplayName("#910 note §5 第 2 条（复核的反例）：录入后发生的变化不影响依据（单元外来了一个人）时，最后一次批准照常写版本")
    void anUnrelatedChangeDoesNotFailTheReCheck() throws Exception {
        Waiting waiting = waiting(DAY);
        inheritingIn(topE1);

        approve(headE1, waiting.command(), KIND_CHANGE, e1);
        approve(headE2, waiting.command(), KIND_CHANGE, e2);

        assertThat(statusOf(waiting.command())).isEqualTo(PENDING);
        assertThat(unitAttributionRowsWrittenBy(waiting.command())).isEqualTo(1);
    }

    @Test
    @DisplayName("#910 note §5 第 5 条（决定人，越权）：别的法人（E3）的负责人、只有权限而不是该法人负责人的人、起草人自己都不能决定 E1 的行；被拒后请求仍未决、命令仍待会签")
    void onlyTheFinanceHeadOfTheRowsEntityDecides() throws Exception {
        Waiting waiting = waiting(DAY);
        Staff holderOnly = person(FINANCE_HEAD_ROLE);

        assertDecisionRefused(() -> approve(headE3, waiting.command(), KIND_CHANGE, e1));
        assertDecisionRefused(() -> approve(holderOnly, waiting.command(), KIND_CHANGE, e1));
        assertDecisionRefused(() -> reject(headE3, waiting.command(), KIND_CHANGE, e1, "synthetic: not mine"));
        assertDecisionRefused(() -> approve(orgAdmin, waiting.command(), KIND_CHANGE, e1));

        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isNull();
        assertThat(statusOf(waiting.command())).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("#910 note §5 第 5 条、用户答复 Q3：只被绑成负责人而没有审批权限的人不能决定；起草人即使同时是该法人负责人也不能决定自己起草的行（ck_attribution_countersigns_not_drafter）")
    void bindingWithoutThePermissionAndTheDrafterAsHeadAreRefused() throws Exception {
        Staff boundOnly = person();
        bind(boundOnly, e2);
        Staff drafterHead = person(ORG_DATA_ADMIN_ROLE, FINANCE_HEAD_ROLE);
        bind(drafterHead, e1);
        actIn(drafterHead);
        Waiting waiting = waiting(DAY);

        assertDecisionRefused(() -> approve(boundOnly, waiting.command(), KIND_CHANGE, e2));
        assertDecisionRefused(() -> approve(drafterHead, waiting.command(), KIND_CHANGE, e1));

        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isNull();
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isNull();
    }

    @Test
    @DisplayName("#910 note §5 第 5 条、用户答复 Q3（边界）：业务管理员在升级级别 0、1 时不能决定，升到 2 时可以；多角色：同时是 E1 与 E2 负责人的人可以分别批准两行")
    void theEscalatedRoleFromLevelTwoAndOnePersonHeadingTwoEntities() throws Exception {
        Waiting waiting = waiting(DAY);
        Staff escalated = person(ESCALATED_ROLE);

        assertDecisionRefused(() -> approve(escalated, waiting.command(), KIND_CHANGE, e1));
        escalateTo(waiting.command(), KIND_CHANGE, e1, 1);
        assertDecisionRefused(() -> approve(escalated, waiting.command(), KIND_CHANGE, e1));
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isNull();
        escalateTo(waiting.command(), KIND_CHANGE, e1, 2);
        approve(escalated, waiting.command(), KIND_CHANGE, e1);
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isEqualTo(APPROVED);

        Waiting other = waiting(DAY.plusDays(1));
        Staff both = head(e1, e2);
        approve(both, other.command(), KIND_CHANGE, e1);
        approve(both, other.command(), KIND_CHANGE, e2);
        assertThat(statusOf(other.command())).isEqualTo(PENDING);
    }

    @RepeatedTest(3)
    @DisplayName("#910 note §5 第 2 条「最后一次批准锁组织单元根行」（并发）：两位负责人同时批准：两次调用都不失败，恰好写一次单元归属版本，命令转待生效，两行都 APPROVED、没有未决行")
    void twoHeadsApprovingAtOnceWriteExactlyOnce() throws Exception {
        Waiting waiting = waiting(DAY);
        UUID rowE1 = requestId(waiting.command(), KIND_CHANGE, e1);
        UUID rowE2 = requestId(waiting.command(), KIND_CHANGE, e2);

        List<Throwable> failures = atOnce(headE1, probe -> probe.approve(rowE1), headE2, probe -> probe.approve(rowE2));

        assertThat(failures).containsOnlyNulls();
        assertThat(statusOf(waiting.command())).isEqualTo(PENDING);
        assertThat(unitAttributionRowsWrittenBy(waiting.command())).isEqualTo(1);
        assertThat(unitAttributions(waiting.unit())).hasSize(1);
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isEqualTo(APPROVED);
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isEqualTo(APPROVED);
    }

    @RepeatedTest(3)
    @DisplayName("#910 note §5 第 2 条、用户答复 Q1（并发）：一位负责人批准的同时另一位拒签：命令转已撤回，不写版本，没有未决行；拒签行为 REJECTED，另一行或为先到的 APPROVED、或为 LAPSED（批准被拒）")
    void approveAndRejectAtOnceLeaveNoHalfState() throws Exception {
        Waiting waiting = waiting(DAY);
        UUID rowE1 = requestId(waiting.command(), KIND_CHANGE, e1);
        UUID rowE2 = requestId(waiting.command(), KIND_CHANGE, e2);

        List<Throwable> failures = atOnce(headE1, probe -> probe.approve(rowE1), headE2, probe -> probe.reject(rowE2, "synthetic: no"));

        assertThat(failures.get(1)).as("the rejection itself goes through").isNull();
        assertThat(failures.get(0) == null || failures.get(0) instanceof DomainException).as("a refused approval is a business refusal: " + failures.get(0)).isTrue();
        assertThat(statusOf(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(unitAttributionRowsWrittenBy(waiting.command())).isZero();
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isEqualTo(REJECTED);
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isIn(APPROVED, LAPSED);
        assertThat(rows("select 1 from " + COUNTERSIGNS + " where " + ORG_PARENT_COLUMN + " = ? and decision_kind is null", waiting.command())).isEmpty();
    }

    @Test
    @DisplayName("#910 note §5 第 2、3 条（复核经应用路径）：待会签期间录入一条更早生效的调岗（允许），把依据里的人调出单元；最后一次批准时依据不同：该请求 REJECTED、命令已撤回、不写版本")
    void anEarlierDatedTransferFailsTheReCheck() throws Exception {
        Waiting waiting = waiting(DAY);
        approve(headE1, waiting.command(), KIND_CHANGE, e1);
        actInSpecialist();
        transfer(waiting.first(), DAY.minusDays(3), topE1);
        actIn(orgAdmin);

        catchThrowable(() -> approve(headE2, waiting.command(), KIND_CHANGE, e2));

        assertThat(statusOf(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isEqualTo(REJECTED);
        assertThat(unitAttributionRowsWrittenBy(waiting.command())).isZero();
    }

    @Test
    @DisplayName("#910 note §5 第 5 条「起草人自己的法人或自己所在单元链的法人会被改变时被拒」：有自有法人归属的组织数据管理员，所在单元链的法人会被改变时录入同样被拒")
    void aDrafterWithAnOwnAttributionIsRefusedWhenTheirChainChanges() throws Exception {
        UUID unitX = seedUnit(topE1);
        inheritingIn(unitX);
        UUID selfId = employedIn(unitX, REGULAR, true);
        grantByOperations(selfId, ORG_DATA_ADMIN_ROLE);
        actIn(new Staff(selfId, signIn(selfId)));
        long commands = orgCommandCount();

        assertRefused(() -> assignLegalEntity(unitX, DAY, e2));

        assertThat(orgCommandCount()).isEqualTo(commands);
    }
}
