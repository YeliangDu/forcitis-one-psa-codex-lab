package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
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

    private UUID seededOrgCommand(String kind, String status) throws Exception {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            if (kind.equals("MERGE")) {
                update(workforce, """
                        insert into org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                          merge_into_org_unit_id, created_at, created_by, updated_at) values (?, 'MERGE', 'ORG_UNIT', ?, ?, 'PENDING', ?, ?, ?, ?)""",
                        id, stored(seedUnit(topE1)), TODAY, stored(receiving), at, drafter, at);
                if (!status.equals("PENDING")) {
                    update(workforce, "update org_structure_commands set status = 'IN_PROGRESS', affected_employee_count = 1, transferred_employee_count = 0, updated_at = ?"
                            + " where org_structure_command_id = ?", at, id);
                }
                if (status.equals("EFFECTIVE")) {
                    update(workforce, "update org_structure_commands set status = 'EFFECTIVE', transferred_employee_count = 1, applied_at = ?, updated_at = ?"
                            + " where org_structure_command_id = ?", at, at, id);
                }
            } else {
                update(workforce, """
                        insert into org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                          target_legal_entity_id, created_at, created_by, updated_at) values (?, 'ASSIGN_LEGAL_ENTITY', 'ORG_UNIT', ?, ?, ?, ?, ?, ?, ?)""",
                        id, stored(seedUnit(topE1)), TODAY, status, e2, at, drafter, at);
            }
        }
        return id;
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

    /** A decided WITHDRAWAL row (requested by the drafter, sequence 1): the guard counts CHANGE rows only (#908 DD section 3). */
    private void rejectedWithdrawalRow(UUID command, UUID entity) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into attribution_countersigns (attribution_countersign_id, org_structure_command_id, drafted_by, request_kind, request_sequence_number,
                      legal_entity_id, requested_at, requested_by, due_on_date, request_reason, created_at, updated_at) values (?, ?, ?, 'WITHDRAWAL', 1, ?, ?, ?, ?, 'synthetic reason', ?, ?)""",
                    Uuids.timeOrdered(NOW), command, drafter, entity, at, drafter, TODAY.plusDays(3), at, at);
            update(workforce, "update attribution_countersigns set decision_kind = 'REJECTED', decided_at = ?, decided_by = ?, decision_reason = 'synthetic reason',"
                    + " decision_audit_entry_id = ?, updated_at = ? where org_structure_command_id = ? and request_kind = 'WITHDRAWAL'", at, headE1.userId(), Uuids.timeOrdered(NOW), at, command);
        }
    }

    private void basisRow(UUID command) throws Exception {
        UUID person = inheritingIn(merged);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into " + BASES + " (attribution_countersign_basis_id, org_structure_command_id, employee_id, previous_legal_entity_id, new_legal_entity_id, created_at, updated_at)"
                    + " values (?, ?, ?, ?, ?, ?, ?)", Uuids.timeOrdered(NOW), command, stored(person), e1, e2, at, at);
        }
    }

    /**
     * The guard fires for every status other than AWAITING_ATTRIBUTION_COUNTERSIGN (#908 DD section 3), so each refused case is tried both as a
     * PENDING row and as an EFFECTIVE one, the shape of a merge-generated transfer (review rv1041a M2).
     */
    private static final String[] NOT_AWAITING = {"PENDING", "EFFECTIVE"};

    /** Inserts a TRANSFER that refers to the org command; null when the database accepted it, else the refusal. */
    private Throwable transferReferring(UUID command, String status) throws Exception {
        UUID person = inheritingIn(merged);
        return catchThrowable(() -> {
            try (Connection workforce = schemaConnection("organization_workforce")) {
                // #711: a TRANSFER names the assignment it moves (here the person's PRIMARY root).
                UUID root = AssignmentRoots.primaryOf(workforce, stored(person));
                update(workforce, """
                        insert into personnel_commands (personnel_command_id, employee_id, employment_relationship_id, command_kind, effective_date, status, target_org_unit_id,
                          target_assignment_id, target_assignment_kind,
                          org_structure_command_id, countersign_previous_legal_entity_id, countersign_new_legal_entity_id, applied_at, created_at, created_by, updated_at)
                        values (?, ?, ?, 'TRANSFER', ?, ?, ?, ?, 'PRIMARY', ?, ?, ?, ?, ?, ?, ?)""",
                        Uuids.timeOrdered(NOW), stored(person), EmploymentRelationshipSeeds.relationshipOf(workforce, stored(person)), TODAY, status, stored(receiving), root, command,
                        status.equals(AWAITING) ? e1 : null, status.equals(AWAITING) ? e2 : null, status.equals("EFFECTIVE") ? at : null, at, drafter, at);
            }
        });
    }

    private static void assertGuardRefused(Throwable failure) {
        assertThat(failure).as("refused by " + GUARD_TRIGGER).isInstanceOf(SQLException.class);
        assertThat(((SQLException) failure).getSQLState()).isEqualTo(RESTRICT_VIOLATION);
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（红例）：合并还是 PENDING 时，引用它的调岗（待生效或已生效）被数据库拒绝；已 EFFECTIVE 的合并同样被拒")
    void aMergeNotInProgressIsRefused() throws Exception {
        for (String status : NOT_AWAITING) {
            assertGuardRefused(transferReferring(seededOrgCommand("MERGE", "PENDING"), status));
            assertGuardRefused(transferReferring(seededOrgCommand("MERGE", "EFFECTIVE"), status));
        }
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（红例）：IN_PROGRESS 合并的 CHANGE 行有一行未决、被拒或已失效时，引用它的调岗（待生效或已生效）被拒")
    void anIncompleteCountersignIsRefused() throws Exception {
        for (String status : NOT_AWAITING) {
            for (String other : new String[] {null, REJECTED, LAPSED}) {
                UUID merge = seededOrgCommand("MERGE", "IN_PROGRESS");
                changeRow(merge, e1, APPROVED);
                changeRow(merge, e2, other);
                assertGuardRefused(transferReferring(merge, status));
            }
        }
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（红例）：IN_PROGRESS 合并有会签依据却没有 CHANGE 行时被拒；引用非合并组织命令的调岗被拒（待生效或已生效）")
    void aBasisWithoutRequestsAndANonMergeAreRefused() throws Exception {
        for (String status : NOT_AWAITING) {
            UUID merge = seededOrgCommand("MERGE", "IN_PROGRESS");
            basisRow(merge);
            assertGuardRefused(transferReferring(merge, status));
            assertGuardRefused(transferReferring(seededOrgCommand("ASSIGN_LEGAL_ENTITY", "PENDING"), status));
        }
    }

    @Test
    @DisplayName("#910 note §4.5、#908 数据设计第 3 节（通过的孪生）：IN_PROGRESS 合并的 CHANGE 行全部批准时放行（另有被拒的 WITHDRAWAL 行也放行）；既无 CHANGE 行也无依据时放行（待生效或已生效）；调岗本身待法人归属会签时无论合并状态都放行")
    void theTwinsPass() throws Exception {
        for (String status : NOT_AWAITING) {
            UUID approved = seededOrgCommand("MERGE", "IN_PROGRESS");
            changeRow(approved, e1, APPROVED);
            changeRow(approved, e2, APPROVED);
            assertThat(transferReferring(approved, status)).isNull();
            UUID withdrawalRejected = seededOrgCommand("MERGE", "IN_PROGRESS");
            changeRow(withdrawalRejected, e1, APPROVED);
            changeRow(withdrawalRejected, e2, APPROVED);
            rejectedWithdrawalRow(withdrawalRejected, e1);
            assertThat(transferReferring(withdrawalRejected, status)).as("a rejected WITHDRAWAL row is not a CHANGE row (rv1041x suggestion 1)").isNull();
            assertThat(transferReferring(seededOrgCommand("MERGE", "IN_PROGRESS"), status)).isNull();
        }
        assertThat(transferReferring(seededOrgCommand("MERGE", "PENDING"), AWAITING)).isNull();
    }

    // ---- one-design ruling on #908 (comment 6590861391) item 1: leaving AWAITING_ATTRIBUTION_COUNTERSIGN is fail-closed ----

    /** An ASSIGN_LEGAL_ENTITY awaiting countersign, on a unit of its own (one open command per unit and day). */
    private UUID awaitingAssignment() throws Exception {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                      target_legal_entity_id, created_at, created_by, updated_at) values (?, 'ASSIGN_LEGAL_ENTITY', 'ORG_UNIT', ?, ?, ?, ?, ?, ?, ?)""",
                    id, stored(seedUnit(topE1)), TODAY.plusDays(3), AWAITING, e2, at, drafter, at);
        }
        return id;
    }

    /** Moves the command to the status directly in the schema; null when the database accepted it, else the refusal. */
    private Throwable moveTo(UUID command, String status) {
        return catchThrowable(() -> {
            try (Connection workforce = schemaConnection("organization_workforce")) {
                if (status.equals("WITHDRAWN")) {
                    update(workforce, "update org_structure_commands set status = 'WITHDRAWN', withdrawn_at = ?, withdrawn_by = ?, withdrawal_reason = 'synthetic', updated_at = ?"
                            + " where org_structure_command_id = ?", at, drafter, at, command);
                } else {
                    update(workforce, "update org_structure_commands set status = ?, applied_at = ?, updated_at = ? where org_structure_command_id = ?",
                            status, status.equals("EFFECTIVE") ? at : null, at, command);
                }
            }
        });
    }

    private void assertExitRefused(UUID command, String status) throws Exception {
        assertThat(moveTo(command, status)).as("AWAITING -> " + status + " refused by the transition trigger").isInstanceOf(SQLException.class);
        assertThat(rows("select status from org_structure_commands where org_structure_command_id = ?", command).get(0).get("status")).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("#910 one-design 裁定（#908 comment 6590861391 第 1 项，fail-closed）：待法人归属会签的组织命令，有一行 CHANGE 未决、被拒或已失效、或一行 CHANGE 都没有时，数据库拒绝把它改成待生效或已生效，状态不变")
    void anAwaitingCommandCannotLeaveWithoutEveryChangeApproved() throws Exception {
        for (String other : new String[] {null, REJECTED, LAPSED}) {
            UUID command = awaitingAssignment();
            changeRow(command, e1, APPROVED);
            changeRow(command, e2, other);
            assertExitRefused(command, "PENDING");
            assertExitRefused(command, "EFFECTIVE");
        }
        UUID none = awaitingAssignment();
        assertExitRefused(none, "PENDING");
        assertExitRefused(none, "EFFECTIVE");
    }

    @Test
    @DisplayName("#910 one-design 裁定（#908 comment 6590861391 第 1 项）通过的孪生：所有 CHANGE 行都批准时可以转待生效；有未决 CHANGE 行时仍可以转已撤回（拒签、失效、起草人撤回要能走）")
    void theTwinsLeaveAwaiting() throws Exception {
        UUID approved = awaitingAssignment();
        changeRow(approved, e1, APPROVED);
        changeRow(approved, e2, APPROVED);
        assertThat(moveTo(approved, "PENDING")).isNull();

        UUID withdrawn = awaitingAssignment();
        changeRow(withdrawn, e1, null);
        assertThat(moveTo(withdrawn, "WITHDRAWN")).isNull();
    }
}
