package com.forcitis.one.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.payroll.api.EffectivePayrollRuleVersion;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import com.forcitis.one.organization.payroll.api.PayrollRuleVersionId;
import com.forcitis.one.organization.payroll.api.PayrollRuleVersions;
import com.forcitis.one.organization.payroll.application.PublishPayrollRuleVersion;
import com.forcitis.one.organization.payroll.domain.PayrollRuleVersionRepository;
import com.forcitis.one.organization.payroll.events.PayrollRuleVersionBecameEffective;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** #1429 (PY0): the publish command and the read port on a real PostgreSQL. There is no draft command yet, so drafts are inserted as rows. */
@RecordApplicationEvents
class PayrollRuleVersionIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(PayrollRuleVersionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private int publishedAuditEntries() throws Exception {
        return auditedActions("organization.payroll.rule-version.published").size();
    }

    private static final String DRAFTER = principalName(31);
    private static final String APPROVER = principalName(32);

    @Autowired
    PublishPayrollRuleVersion publish;
    @Autowired
    PayrollRuleVersions versions;
    @Autowired
    PayrollRuleVersionRepository repository;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ApplicationEvents events;

    /** The first day of the month after the business today: a start the publish accepts (CS-7.5); every date of the cases is counted from it. */
    private static final LocalDate BASE = NOW.atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().withDayOfMonth(1).plusMonths(1);

    /** Published rows are frozen by the database and nothing deletes them, so each test works in a jurisdiction of its own. */
    private String jurisdiction;
    private JurisdictionKey home;

    @BeforeEach
    void ownJurisdictionAndSignIn() {
        jurisdiction = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        home = new JurisdictionKey(jurisdiction);
        signInAs(APPROVER);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(String principal) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null, "ROLE_ORDINARY"));
    }

    private void publishAs(String principal, UUID id) {
        signInAs(principal);
        try (var acting = WorkContext.enter(UUID.randomUUID(), null, "test.organization.payroll")) {
            publish.execute(new PayrollRuleVersionId(id));
        }
    }

    private UUID draft(PayrollRuleKind kind, String jurisdiction, LocalDate from, boolean withRows) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into organization_payroll.payroll_rule_versions (id, rule_kind, jurisdiction_key, effective_from_date, status, drafted_by_user_id,
                    source_note, created_at, updated_at)
                values (:id, :kind, :jurisdiction, :from, 'DRAFT', :drafter, 'Synthetic citation', now(), now())
                """).param("id", id).param("kind", kind.name()).param("jurisdiction", jurisdiction).param("from", from).param("drafter", UUID.fromString(DRAFTER)).update();
        if (withRows) {
            jdbc.sql("""
                    insert into organization_payroll.payroll_rule_rows (id, rule_version_id, row_kind, row_key, rounding_kind, created_at, updated_at)
                    values (:rowId, :id, 'ROUNDING_MODE', '-', 'HALF_UP', now(), now())
                    """).param("rowId", UUID.randomUUID()).param("id", id).update();
        }
        return id;
    }

    private Map<String, Object> stored(UUID id) {
        return jdbc.sql("select status, effective_to_date, approved_by_user_id, published_at, version from organization_payroll.payroll_rule_versions where id = :id")
                .param("id", id).query().singleRow();
    }

    private static void assertRefused(Runnable action, PayrollError code, ErrorKind kind) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(code);
            assertThat(e.kind()).isEqualTo(kind);
        });
    }

    @Test
    @DisplayName("CS-7.5 the publish refuses a start in the past and a start that is not the first of a month, and leaves the draft as it was")
    void startMustBeAPeriodStartThatIsNotPast() throws Exception {
        UUID midMonth = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE.plusDays(14), true);
        UUID past = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE.minusMonths(2), true);
        int auditedBefore = publishedAuditEntries();

        assertRefused(() -> publishAs(APPROVER, midMonth), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_NOT_PERIOD_START, ErrorKind.UNPROCESSABLE);
        assertRefused(() -> publishAs(APPROVER, past), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_BACKDATED, ErrorKind.UNPROCESSABLE);

        assertThat(stored(midMonth)).containsEntry("status", "DRAFT");
        assertThat(stored(past)).containsEntry("status", "DRAFT");
        assertThat(publishedAuditEntries()).isEqualTo(auditedBefore);
    }

    @Test
    @DisplayName("PRC-180 a row written while the publish is under way cannot slip past its completeness check")
    void aRowWrittenDuringThePublishIsNotMissed() throws Exception {
        UUID id = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);
        try (java.sql.Connection other = java.sql.DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password())) {
            other.setAutoCommit(false);
            try (java.sql.PreparedStatement insert = other.prepareStatement("""
                    insert into organization_payroll.payroll_rule_rows (id, rule_version_id, row_kind, row_key, rounding_kind, created_at, updated_at)
                    values (?, ?, 'ROUNDING_MODE', 'X', 'HALF_UP', now(), now())
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, id);
                insert.executeUpdate();
            }
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                java.util.concurrent.Future<?> publishing = pool.submit(() -> publishAs(APPROVER, id));
                waitUntilSomeoneWaitsForALock();
                other.commit();
                assertThatThrownBy(() -> publishing.get(20, java.util.concurrent.TimeUnit.SECONDS)).hasRootCauseInstanceOf(DomainException.class)
                        .rootCause().isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE));
            } finally {
                pool.shutdownNow();
            }
        }

        assertThat(stored(id)).containsEntry("status", "DRAFT");
    }

    private void waitUntilSomeoneWaitsForALock() throws InterruptedException {
        for (int tries = 0; tries < 200; tries++) {
            if (jdbc.sql("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()").query(Integer.class).single() > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the publish never waited for the lock of the version row");
    }

    @Test
    @DisplayName("PRC-180 publishing a draft makes it EFFECTIVE with the approver and time, writes one audit entry, and raises the event once")
    void publishMakesTheDraftEffective() throws Exception {
        UUID id = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);

        int auditedBefore = publishedAuditEntries();
        publishAs(APPROVER, id);

        assertThat(stored(id)).containsEntry("status", "EFFECTIVE").containsEntry("approved_by_user_id", UUID.fromString(APPROVER)).containsEntry("version", 1L);
        assertThat(stored(id).get("published_at")).isNotNull();
        assertThat(publishedAuditEntries()).isEqualTo(auditedBefore + 1);
        assertThat(events.stream(PayrollRuleVersionBecameEffective.class).filter(e -> e.payrollRuleVersionId().value().equals(id))).singleElement().satisfies(e -> {
            assertThat(e.payrollRuleVersionId().value()).isEqualTo(id);
            assertThat(e.effectiveFromDate()).isEqualTo(BASE);
        });
    }

    @Test
    @DisplayName("PRC-180 publishing a successor closes the predecessor at the successor's start day, in the same call")
    void successorClosesThePredecessor() {
        UUID first = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);
        publishAs(APPROVER, first);
        UUID second = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE.plusMonths(12), true);

        publishAs(APPROVER, second);

        assertThat(stored(first)).containsEntry("status", "SUPERSEDED").containsEntry("effective_to_date", java.sql.Date.valueOf(BASE.plusMonths(12)));
        assertThat(stored(second)).containsEntry("status", "EFFECTIVE").containsEntry("effective_to_date", null);
    }

    @Test
    @DisplayName("INV-139 a successor that starts on or before the version in force is refused, and neither version changes")
    void overlappingSuccessorIsRefused() {
        publishAs(APPROVER, draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE.plusMonths(5), true));
        UUID earlier = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE.plusMonths(5), true);
        UUID before = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);

        assertRefused(() -> publishAs(APPROVER, earlier), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_OVERLAP, ErrorKind.CONFLICT);
        assertRefused(() -> publishAs(APPROVER, before), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_OVERLAP, ErrorKind.CONFLICT);

        assertThat(stored(earlier)).containsEntry("status", "DRAFT");
        assertThat(stored(before)).containsEntry("status", "DRAFT");
        assertThat(jdbc.sql("select count(*) from organization_payroll.payroll_rule_versions where jurisdiction_key = :j and status = 'EFFECTIVE' and effective_to_date is null")
                .param("j", jurisdiction).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("a version of another category or jurisdiction is not a predecessor and is left alone")
    void otherCategoryAndJurisdictionAreIndependent() {
        UUID tax = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);
        UUID shanghai = draft(PayrollRuleKind.ROUNDING, jurisdiction + "_X", BASE, true);
        publishAs(APPROVER, tax);

        publishAs(APPROVER, shanghai);

        assertThat(stored(tax)).containsEntry("status", "EFFECTIVE").containsEntry("effective_to_date", null);
        assertThat(stored(shanghai)).containsEntry("status", "EFFECTIVE");
    }

    @Test
    @DisplayName("four eyes: the drafter cannot publish; nothing is written, no audit entry, no event")
    void drafterCannotPublish() throws Exception {
        UUID id = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);

        int auditedBefore = publishedAuditEntries();
        assertRefused(() -> publishAs(DRAFTER, id), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_SAME_PERSON, ErrorKind.UNPROCESSABLE);

        assertThat(stored(id)).containsEntry("status", "DRAFT").containsEntry("approved_by_user_id", null);
        assertThat(publishedAuditEntries()).isEqualTo(auditedBefore);
        assertThat(events.stream(PayrollRuleVersionBecameEffective.class).filter(e -> e.payrollRuleVersionId().value().equals(id))).isEmpty();
    }

    @Test
    @DisplayName("a draft without the rows its category needs is refused; a published version, or an unknown id, cannot be published")
    void incompleteStateAndUnknownRefusals() {
        UUID empty = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, false);
        UUID done = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE.plusMonths(2), true);
        publishAs(APPROVER, done);

        assertRefused(() -> publishAs(APPROVER, empty), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
        assertRefused(() -> publishAs(APPROVER, done), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_STATE_CONFLICT, ErrorKind.CONFLICT);
        assertRefused(() -> publishAs(APPROVER, UUID.randomUUID()), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_NOT_FOUND, ErrorKind.NOT_FOUND);

        assertThat(stored(empty)).containsEntry("status", "DRAFT");
    }

    @Test
    @DisplayName("a save of a version changed since it was read is refused as a concurrent change, not lost")
    void staleSaveIsRefused() {
        UUID id = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);
        var read = repository.findForUpdate(id).orElseThrow();
        read.publish(new com.forcitis.one.kernel.UserId(UUID.fromString(APPROVER)), java.time.Instant.parse("2026-11-05T02:00:00Z"), BASE.minusDays(16),
                repository.rowsOf(id));
        jdbc.sql("update organization_payroll.payroll_rule_versions set version = version + 1 where id = :id").param("id", id).update();

        assertRefused(() -> repository.save(read), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_CONCURRENT_CHANGE, ErrorKind.CONFLICT);

        assertThat(stored(id)).containsEntry("status", "DRAFT");
    }

    @Test
    @DisplayName("read port: the version in force on a day is the one whose interval holds it, the start day included and the closing day not")
    void readPortFindsTheVersionInForce() {
        UUID first = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true);
        publishAs(APPROVER, first);
        UUID second = draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE.plusMonths(12), true);
        publishAs(APPROVER, second);

        assertThat(inForce(BASE)).extracting(v -> v.id().value()).isEqualTo(first);
        assertThat(inForce(BASE.plusMonths(12).minusDays(1))).extracting(v -> v.id().value()).isEqualTo(first);
        assertThat(inForce(BASE.plusMonths(12))).extracting(v -> v.id().value()).isEqualTo(second);
        assertThat(inForce(BASE.plusYears(4))).extracting(v -> v.id().value()).isEqualTo(second);
        assertThat(inForce(BASE.plusYears(4))).satisfies(v -> {
            assertThat(v.ruleKind()).isEqualTo(PayrollRuleKind.ROUNDING);
            assertThat(v.effectiveFromDate()).isEqualTo(BASE.plusMonths(12));
            assertThat(v.effectiveToDate()).isNull();
        });
    }

    @Test
    @DisplayName("read port: no version in force on the day fails with the named error, for a day before the first, another jurisdiction or category, and a draft")
    void readPortFailsWhenNoneIsInForce() {
        publishAs(APPROVER, draft(PayrollRuleKind.ROUNDING, jurisdiction, BASE, true));
        draft(PayrollRuleKind.HOUSING_FUND, jurisdiction, BASE, false);

        assertRefused(() -> inForce(BASE.minusDays(1)), PayrollError.ORGANIZATION_PAYROLL_NO_EFFECTIVE_RULE_VERSION, ErrorKind.NOT_FOUND);
        assertRefused(() -> versions.effectiveOn(PayrollRuleKind.ROUNDING, new JurisdictionKey(jurisdiction + "_X"), BASE.plusMonths(5)),
                PayrollError.ORGANIZATION_PAYROLL_NO_EFFECTIVE_RULE_VERSION, ErrorKind.NOT_FOUND);
        assertRefused(() -> versions.effectiveOn(PayrollRuleKind.INCOME_TAX, home, BASE.plusMonths(5)),
                PayrollError.ORGANIZATION_PAYROLL_NO_EFFECTIVE_RULE_VERSION, ErrorKind.NOT_FOUND);
        assertRefused(() -> versions.effectiveOn(PayrollRuleKind.HOUSING_FUND, home, BASE.plusMonths(5)),
                PayrollError.ORGANIZATION_PAYROLL_NO_EFFECTIVE_RULE_VERSION, ErrorKind.NOT_FOUND);
    }

    private EffectivePayrollRuleVersion inForce(LocalDate day) {
        return versions.effectiveOn(PayrollRuleKind.ROUNDING, home, day);
    }
}
