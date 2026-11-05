package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #1438 (WC1, data design signed in docs/data/reviews/workforce-contract-separation-wc0.md): the schema-level done-when items 1 to 7 of the three tables. */
class ContractSeparationTablesIntegrationTest extends WorkforceIntegrationSupport {
    private static final String CONTRACTS = "organization_workforce.employment_contracts";
    private static final String REQUESTS = "organization_workforce.separation_requests";
    private static final String BASES = "organization_workforce.separation_bases";
    private static final UUID HR_A = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");
    private static final UUID HR_B = UUID.fromString("019a0000-0000-7000-8000-0000000000b2");
    private static final LocalDate D2026 = LocalDate.of(2026, 1, 1);

    @Autowired
    DataSource dataSource;

    private UUID employee;
    private UUID relationship;

    @BeforeEach
    void anEmployeeWithARelationship() {
        employee = PersonnelSeedSupport.employed(jdbc, clock, "CS-" + UUID.randomUUID().toString().substring(0, 6)).employee();
        relationship = EmploymentRelationshipSeed.relationship(jdbc, employee);
    }

    private UUID contract(String status, String kind, LocalDate start, LocalDate end, Map<String, Object> extra) {
        UUID id = UUID.randomUUID();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("employment_contract_id", id);
        c.put("employment_relationship_id", relationship);
        c.put("employee_id", employee);
        c.put("contract_kind", kind);
        c.put("term_start_date", start);
        c.put("term_end_date", end);
        c.put("status", status);
        boolean submitted = !status.equals("DRAFT") && !status.equals("VOID");
        c.put("submitted_by", submitted ? HR_A : null);
        c.put("approved_by", submitted && !status.equals("IN_APPROVAL") ? HR_B : null);
        c.put("signed_on_date", status.equals("SIGNED") || status.equals("TERMINATED") ? start : null);
        c.putAll(extra);
        insert(CONTRACTS, c);
        return id;
    }

    private UUID contract(String status, LocalDate start, LocalDate end) {
        return contract(status, end == null ? "OPEN_ENDED" : "FIXED_TERM", start, end, Map.of());
    }

    private void insert(String table, Map<String, Object> values) {
        Map<String, Object> all = new LinkedHashMap<>(values);
        all.put("created_at", java.time.OffsetDateTime.now());
        all.put("updated_at", java.time.OffsetDateTime.now());
        String columns = String.join(", ", all.keySet());
        String params = String.join(", ", all.keySet().stream().map(k -> ":" + k).toList());
        var statement = jdbc.sql("insert into " + table + " (" + columns + ") values (" + params + ")");
        for (var e : all.entrySet()) {
            statement = statement.param(e.getKey(), e.getValue());
        }
        statement.update();
    }

    private UUID request(String status, String basis, Map<String, Object> extra) {
        UUID id = UUID.randomUUID();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("separation_request_id", id);
        r.put("employment_relationship_id", relationship);
        r.put("employee_id", employee);
        r.put("initiator_kind", "EMPLOYEE");
        r.put("separation_basis_key", basis);
        r.put("request_source_kind", "HR_ON_BEHALF");
        r.put("requested_by", HR_A);
        r.put("requested_on_date", D2026);
        r.put("status", status);
        r.putAll(extra);
        insert(REQUESTS, r);
        return id;
    }

    private UUID request() {
        return request("REQUESTED", "RESIGN_WITH_NOTICE", Map.of());
    }

    @Test
    @DisplayName("#1438 done-when 1: two SIGNED contracts of one relationship may not overlap; the day after the term is accepted; drafts never conflict")
    void signedContractsDoNotOverlap() {
        contract("SIGNED", D2026, LocalDate.of(2026, 12, 31));
        assertThatThrownBy(() -> contract("SIGNED", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31))).hasMessageContaining("ex_employment_contracts_in_force_period_overlap");
        assertThatThrownBy(() -> contract("SIGNED", LocalDate.of(2026, 6, 1), null)).hasMessageContaining("ex_employment_contracts_in_force_period_overlap");
        contract("DRAFT", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));
        contract("SIGNED", LocalDate.of(2027, 1, 1), null);
    }

    @Test
    @DisplayName("#1438 done-when 1: a terminated contract frees the days after its termination date")
    void aTerminatedContractEndsAtItsTerminationDate() {
        UUID first = contract("SIGNED", D2026, null);
        assertThatThrownBy(() -> contract("SIGNED", LocalDate.of(2026, 7, 1), null)).hasMessageContaining("ex_employment_contracts_in_force_period_overlap");
        jdbc.sql("update " + CONTRACTS + " set status = 'TERMINATED', terminated_on_date = '2026-06-30', termination_basis_key = 'RESIGN_WITH_NOTICE', updated_at = now(), version = version + 1 where employment_contract_id = :id")
                .param("id", first).update();
        contract("SIGNED", LocalDate.of(2026, 7, 1), null);
    }

    @Test
    @DisplayName("#1438 done-when 2: term, probation and renewal rules each fail a named CHECK")
    void contractRowRulesAreNamedChecks() {
        assertThatThrownBy(() -> contract("DRAFT", "FIXED_TERM", D2026, null, Map.of())).hasMessageContaining("ck_employment_contracts_term_end");
        assertThatThrownBy(() -> contract("DRAFT", "OPEN_ENDED", D2026, LocalDate.of(2027, 1, 1), Map.of())).hasMessageContaining("ck_employment_contracts_term_end");
        assertThatThrownBy(() -> contract("DRAFT", "FIXED_TERM", D2026, LocalDate.of(2025, 12, 31), Map.of())).hasMessageContaining("ck_employment_contracts_term_end");
        assertThatThrownBy(() -> contract("DRAFT", "OPEN_ENDED", D2026, null, Map.of("probation_months_count", 7))).hasMessageContaining("ck_employment_contracts_probation_months");
        UUID first = contract("DRAFT", "OPEN_ENDED", D2026, null, Map.of("probation_months_count", 6));
        assertThatThrownBy(() -> contract("DRAFT", "OPEN_ENDED", LocalDate.of(2027, 1, 1), null, Map.of("renews_contract_id", first, "probation_months_count", 1)))
                .hasMessageContaining("ck_employment_contracts_renewal_no_probation");
        UUID renewal = contract("DRAFT", "OPEN_ENDED", LocalDate.of(2027, 1, 1), null, Map.of("renews_contract_id", first));
        assertThat(renewal).isNotNull();
        assertThatThrownBy(() -> contract("DRAFT", "OPEN_ENDED", LocalDate.of(2028, 1, 1), null, Map.of("renews_contract_id", first)))
                .hasMessageContaining("uk_employment_contracts_one_successor");
    }

    @Test
    @DisplayName("#1438 done-when 3: the approver of a contract or a human-filed request is not the submitter or requester; a SYSTEM request may repeat the actor")
    void separationOfDuties() {
        assertThatThrownBy(() -> contract("APPROVED", "OPEN_ENDED", D2026, null, Map.of("approved_by", HR_A))).hasMessageContaining("ck_employment_contracts_approver_not_submitter");
        UUID rejectable = request();
        assertThatThrownBy(() -> jdbc.sql("update " + REQUESTS + " set status = 'REJECTED', decided_by = :hr, updated_at = now(), version = version + 1 where separation_request_id = :id")
                .param("hr", HR_A).param("id", rejectable).update()).hasMessageContaining("ck_separation_requests_approver_not_requester");
        request("COMPLETED", "MIGRATION", Map.of("request_source_kind", "SYSTEM", "decided_by", HR_A, "requested_by", HR_A, "approved_on_date", D2026, "last_working_date", D2026));
    }

    @Test
    @DisplayName("#1438 done-when 4: one open request per relationship; a closed one lets a new one in")
    void oneOpenRequestPerRelationship() {
        UUID first = request();
        assertThatThrownBy(this::request).hasMessageContaining("uk_separation_requests_open_per_relationship");
        jdbc.sql("update " + REQUESTS + " set status = 'WITHDRAWN', updated_at = now(), version = version + 1 where separation_request_id = :id").param("id", first).update();
        request();
    }

    @Test
    @DisplayName("#1438 done-when 6: an employer-initiated request needs the four dismissal-check columns to be approved; an employer-change one does not")
    void dismissalCheckNeededForEmployerInitiatedBases() {
        UUID id = request("REQUESTED", "FAULT_DISMISSAL", Map.of("initiator_kind", "EMPLOYER", "last_working_date", D2026.plusDays(30)));
        String approve = "update " + REQUESTS + " set status = 'APPROVED', approved_on_date = '2026-01-02', decided_by = :hr, updated_at = now(), version = version + 1 where separation_request_id = :id";
        assertThatThrownBy(() -> jdbc.sql(approve).param("hr", HR_B).param("id", id).update()).hasMessageContaining("dismissal");
        jdbc.sql("update " + REQUESTS + " set protected_circumstances_checked_on_date = '2026-01-01', union_notified_on_date = '2026-01-01', union_opinion_note = 'none given', union_notice_result_note = 'done', updated_at = now(), version = version + 1 where separation_request_id = :id")
                .param("id", id).update();
        assertThat(jdbc.sql(approve).param("hr", HR_B).param("id", id).update()).isEqualTo(1);
        jdbc.sql("update " + REQUESTS + " set status = 'WITHDRAWN', updated_at = now(), version = version + 1 where separation_request_id = :id").param("id", id).update();
        request("APPROVED", "EMPLOYER_CHANGE", Map.of("request_source_kind", "SYSTEM", "initiator_kind", "BOTH", "requested_by", HR_A, "decided_by", HR_A, "approved_on_date", D2026, "last_working_date", D2026.plusDays(5)));
    }

    @Test
    @DisplayName("#1438 a human-filed request may not use a system-only basis, and a SYSTEM request may not use an ordinary one")
    void systemOnlyBasesBelongToSystemRequests() {
        assertThatThrownBy(() -> request("REQUESTED", "MIGRATION", Map.of())).hasMessageContaining("system");
        assertThatThrownBy(() -> request("APPROVED", "RESIGN_WITH_NOTICE", Map.of("request_source_kind", "SYSTEM", "decided_by", HR_A, "requested_by", HR_A, "approved_on_date", D2026, "last_working_date", D2026)))
                .hasMessageContaining("system");
    }

    @Test
    @DisplayName("#1438 done-when 5: linking needs a SEPARATE of the same relationship dated the day after the last working day")
    void linkingASeparateIsChecked() {
        UUID id = request("REQUESTED", "RESIGN_WITH_NOTICE", Map.of("last_working_date", LocalDate.of(2026, 3, 31)));
        jdbc.sql("update " + REQUESTS + " set status = 'APPROVED', approved_on_date = '2026-01-02', decided_by = :hr, updated_at = now(), version = version + 1 where separation_request_id = :id")
                .param("hr", HR_B).param("id", id).update();
        UUID wrongDay = command(LocalDate.of(2026, 4, 2));
        UUID right = command(LocalDate.of(2026, 4, 1));
        String link = "update " + REQUESTS + " set status = 'SEPARATING', personnel_command_id = :cmd, updated_at = now(), version = version + 1 where separation_request_id = :id";
        assertThatThrownBy(() -> jdbc.sql(link).param("cmd", wrongDay).param("id", id).update()).hasMessageContaining("last_working_date");
        assertThat(jdbc.sql(link).param("cmd", right).param("id", id).update()).isEqualTo(1);
        jdbc.sql("update " + REQUESTS + " set status = 'APPROVED', personnel_command_id = null, updated_at = now(), version = version + 1 where separation_request_id = :id").param("id", id).update();
    }

    private UUID command(LocalDate effective) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, employment_relationship_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                values (:id, :employee, :relationship, 'SEPARATE', :effective, 'PENDING', now(), :by, now())""")
                .param("id", id).param("employee", employee).param("relationship", relationship).param("effective", effective).param("by", HR_A).update();
        return id;
    }

    @Test
    @DisplayName("#1438 done-when 7: updates outside the legal edges are refused, delete is for the table owner only, truncate is refused on all three")
    void illegalWritesAreRefused() throws SQLException {
        UUID signed = contract("SIGNED", D2026, null);
        assertThatThrownBy(() -> jdbc.sql("update " + CONTRACTS + " set term_start_date = '2025-12-31', updated_at = now(), version = version + 1 where employment_contract_id = :id").param("id", signed).update())
                .hasMessageContaining("employment contracts");
        UUID approved = request("APPROVED", "RESIGN_WITH_NOTICE", Map.of("decided_by", HR_B, "approved_on_date", D2026, "last_working_date", D2026.plusDays(30)));
        assertThatThrownBy(() -> jdbc.sql("update " + REQUESTS + " set last_working_date = '2026-03-01', updated_at = now(), version = version + 1 where separation_request_id = :id").param("id", approved).update())
                .hasMessageContaining("separation requests");
        assertThatThrownBy(() -> jdbc.sql("update " + BASES + " set display_name = 'x' where separation_basis_key = 'MUTUAL_AGREEMENT'").update()).hasMessageContaining("separation bases");
        for (String table : new String[] {CONTRACTS, REQUESTS, BASES}) {
            assertThatThrownBy(() -> jdbc.sql("truncate table " + table + " cascade").update()).hasMessageContaining("truncate");
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'contract_runtime') then create role contract_runtime; end if; end $$");
            statement.execute("grant usage on schema organization_workforce to contract_runtime");
            statement.execute("grant select, delete on " + CONTRACTS + ", " + REQUESTS + ", " + BASES + " to contract_runtime");
            statement.execute("set role contract_runtime");
            try {
                for (String table : new String[] {CONTRACTS, REQUESTS, BASES}) {
                    assertThatThrownBy(() -> statement.execute("delete from " + table)).hasMessageContaining("delete");
                }
            } finally {
                statement.execute("reset role");
            }
        }
        jdbc.sql("delete from " + CONTRACTS + " where employment_contract_id = :id").param("id", signed).update();
        jdbc.sql("delete from " + REQUESTS + " where separation_request_id = :id").param("id", approved).update();
    }

    @Test
    @DisplayName("#1438 the basis seed: statute-based keys, employer-initiated only for art. 39 to 41, two system-only keys, no key that reveals health")
    void theBasisSeed() {
        var rows = jdbc.sql("select separation_basis_key, is_employer_initiated, is_system_only from " + BASES).query((rs, n) -> rs.getString(1) + ":" + rs.getBoolean(2) + ":" + rs.getBoolean(3)).list();
        assertThat(rows).contains("FAULT_DISMISSAL:true:false", "NO_FAULT_DISMISSAL:true:false", "ECONOMIC_LAYOFF:true:false", "MUTUAL_AGREEMENT:false:false",
                "EMPLOYER_CHANGE:false:true", "MIGRATION:false:true");
        assertThat(rows).noneMatch(r -> r.matches("(?i).*(MEDICAL|HEALTH|SICK|ILL|INCAPAC|PREGNAN|INJUR|DISAB).*"));
        assertThat(rows.stream().filter(r -> r.endsWith(":true")).count()).isEqualTo(2);
    }
}
