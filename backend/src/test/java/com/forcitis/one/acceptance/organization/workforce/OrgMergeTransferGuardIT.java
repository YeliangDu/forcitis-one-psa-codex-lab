package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #910 acceptance part (#909 F4a: "the merge-transfer guard red cases"), note section 4.5 and the #908 DD section 3: the database refuses a
 * personnel command that refers to an org command and does not itself await countersign, unless the org command is a MERGE IN_PROGRESS that
 * is countersigned (every CHANGE row APPROVED, at least one) or needed none (no CHANGE rows and no basis rows). The rows are written
 * directly in the schema, as a writer that bypassed the application would, so only the database line of defence is under test.
 */
class OrgMergeTransferGuardIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgMergeTransferGuardIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String RESTRICT_VIOLATION = "23001";

    private OffsetDateTime at;
    private UUID drafter;
    private UUID merged;
    private UUID receiving;

    @BeforeEach
    void units() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        drafter = orgAdmin.userId();
        merged = seedUnit(topE1);
        receiving = seedUnit(topE2);
    }

    private UUID seededOrgCommand(String kind, String status) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            if (kind.equals("MERGE")) {
                update(workforce, """
                        insert into org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                          merge_into_org_unit_id, created_at, created_by, updated_at) values (?, 'MERGE', 'ORG_UNIT', ?, ?, 'PENDING', ?, ?, ?, ?)""",
                        id, stored(seedUnitQuietly()), TODAY, stored(receiving), at, drafter, at);
                if (status.equals("IN_PROGRESS")) {
                    update(workforce, "update org_structure_commands set status = 'IN_PROGRESS', affected_employee_count = 1, transferred_employee_count = 0, updated_at = ?"
                            + " where org_structure_command_id = ?", at, id);
                }
            } else {
                update(workforce, """
                        insert into org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                          target_legal_entity_id, created_at, created_by, updated_at) values (?, 'ASSIGN_LEGAL_ENTITY', 'ORG_UNIT', ?, ?, ?, ?, ?, ?, ?)""",
                        id, stored(merged), TODAY, status, e2, at, drafter, at);
            }
        }
        return id;
    }

    private UUID seedUnitQuietly() throws SQLException {
        try {
            return seedUnit(topE1);
        } catch (Exception failure) {
            throw new SQLException(failure);
        }
    }

    private void changeRow(UUID command, UUID entity, String decision) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into attribution_countersigns (attribution_countersign_id, org_structure_command_id, drafted_by, request_kind, request_sequence_number,
                      legal_entity_id, requested_at, requested_by, due_on_date, created_at, updated_at) values (?, ?, ?, 'CHANGE', 1, ?, ?, ?, ?, ?, ?)""",
                    Uuids.timeOrdered(NOW), command, drafter, entity, at, drafter, TODAY.plusDays(3), at, at);
            if (decision != null) {
                update(workforce, "update attribution_countersigns set decision_kind = ?, decided_at = ?, decided_by = ?, decision_reason = ?, decision_audit_entry_id = ?, updated_at = ?"
                                + " where org_structure_command_id = ? and legal_entity_id = ?", decision, at, decision.equals(LAPSED) ? null : headE1.userId(),
                        decision.equals(REJECTED) ? "synthetic reason" : null, Uuids.timeOrdered(NOW), at, command, entity);
            }
        }
    }

    private void basisRow(UUID command) throws Exception {
        UUID person = inheritingIn(merged);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into " + BASES + " (attribution_countersign_basis_id, org_structure_command_id, employee_id, previous_legal_entity_id, new_legal_entity_id, created_at)"
                    + " values (?, ?, ?, ?, ?, ?)", Uuids.timeOrdered(NOW), command, stored(person), e1, e2, at);
        }
    }

    /** Inserts a TRANSFER that refers to the org command; null when the database accepted it, else the refusal. */
    private Throwable transferReferring(UUID command, String status) throws Exception {
        UUID person = inheritingIn(merged);
        return catchThrowable(() -> {
            try (Connection workforce = schemaConnection("organization_workforce")) {
                update(workforce, """
                        insert into personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, target_org_unit_id,
                          org_structure_command_id, countersign_previous_legal_entity_id, countersign_new_legal_entity_id, created_at, created_by, updated_at)
                        values (?, ?, 'TRANSFER', ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        Uuids.timeOrdered(NOW), stored(person), TODAY.plusDays(1), status, stored(receiving), command,
                        status.equals(AWAITING) ? e1 : null, status.equals(AWAITING) ? e2 : null, at, drafter, at);
            }
        });
    }

    private static void assertGuardRefused(Throwable failure) {
        assertThat(failure).as("refused by " + GUARD_TRIGGER).isInstanceOf(SQLException.class);
        assertThat(((SQLException) failure).getSQLState()).isEqualTo(RESTRICT_VIOLATION);
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（红例）：合并还是 PENDING 时，引用它的调岗被数据库拒绝")
    void aPendingMergeIsRefused() throws Exception {
        assertGuardRefused(transferReferring(seededOrgCommand("MERGE", "PENDING"), "PENDING"));
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（红例）：IN_PROGRESS 合并的 CHANGE 行有一行未决、被拒或已失效时，引用它的调岗被拒")
    void anIncompleteCountersignIsRefused() throws Exception {
        for (String other : new String[] {null, REJECTED, LAPSED}) {
            UUID merge = seededOrgCommand("MERGE", "IN_PROGRESS");
            changeRow(merge, e1, APPROVED);
            changeRow(merge, e2, other);
            assertGuardRefused(transferReferring(merge, "PENDING"));
        }
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（红例）：IN_PROGRESS 合并有会签依据却没有 CHANGE 行时被拒；引用非合并组织命令的调岗被拒")
    void aBasisWithoutRequestsAndANonMergeAreRefused() throws Exception {
        UUID merge = seededOrgCommand("MERGE", "IN_PROGRESS");
        basisRow(merge);
        assertGuardRefused(transferReferring(merge, "PENDING"));
        assertGuardRefused(transferReferring(seededOrgCommand("ASSIGN_LEGAL_ENTITY", "PENDING"), "PENDING"));
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（通过的孪生）：IN_PROGRESS 合并的 CHANGE 行全部批准时放行；既无 CHANGE 行也无依据时放行；调岗本身待法人归属会签时无论合并状态都放行")
    void theTwinsPass() throws Exception {
        UUID approved = seededOrgCommand("MERGE", "IN_PROGRESS");
        changeRow(approved, e1, APPROVED);
        changeRow(approved, e2, APPROVED);
        assertThat(transferReferring(approved, "PENDING")).isNull();
        assertThat(transferReferring(seededOrgCommand("MERGE", "IN_PROGRESS"), "PENDING")).isNull();
        assertThat(transferReferring(seededOrgCommand("MERGE", "PENDING"), AWAITING)).isNull();
    }
}
