package com.forcitis.one.acceptance.platform.integration;

import com.forcitis.one.acceptance.RowRuleProbe;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #730 (F-1 card section 7.3, contract item 16 source 1): the delivery subject kind PERMISSION_ADJUSTMENT and the purpose
 * PERMISSION_ADJUSTMENT_ASSIGNED (the WeCom card to the new department head) are CHECK values of outbound_deliveries; the values already on
 * main stay; near-miss and unknown values are still refused, each by its own CHECK. Escalation and "no data administrator left" cards have no
 * value yet (open item R-4) and are not asserted.
 */
class OutboundDeliveryPermissionAdjustmentRowRulesIT {

    private static final String SUBJECT_CHECK = "ck_outbound_deliveries_subject_kind";
    private static final String PURPOSE_CHECK = "ck_outbound_deliveries_purpose_kind";
    private static final String CARD = "insert into platform_integration.outbound_deliveries (id, subject_kind, subject_id, purpose_kind, recipient_codes, title,"
            + " target_url, status, kind, created_at, updated_at) values (?, ?, ?, ?, array['alice'], 'title', 'https://one.example.test/x', 'PENDING', 'CARD', now(), now())";

    private RowRuleProbe db;

    @BeforeEach
    void aMigratedDatabase() throws Exception {
        db = new RowRuleProbe(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private static Object[] card(String subjectKind, String purpose) {
        return new Object[] {UUID.randomUUID(), subjectKind, UUID.randomUUID(), purpose};
    }

    @Test
    @DisplayName("#730 7.3 a CARD row about a PERMISSION_ADJUSTMENT with purpose PERMISSION_ADJUSTMENT_ASSIGNED is accepted")
    void theAssignedCardIsAccepted() throws Exception {
        db.accepted(CARD, card("PERMISSION_ADJUSTMENT", "PERMISSION_ADJUSTMENT_ASSIGNED"));
    }

    @Test
    @DisplayName("#730 7.3 the values already on main stay accepted: APPROVAL_TASK/TASK_ASSIGNED, SEPARATION_HOLD/SEPARATION_HOLD_NOTICE and PERSONNEL_COMMAND/UNPAID_LEAVE_START_NOTICE")
    void theExistingValuesStay() throws Exception {
        db.accepted(CARD, card("APPROVAL_TASK", "TASK_ASSIGNED"));
        db.accepted(CARD, card("SEPARATION_HOLD", "SEPARATION_HOLD_NOTICE"));
        db.accepted(CARD, card("PERSONNEL_COMMAND", "UNPAID_LEAVE_START_NOTICE"));
    }

    @Test
    @DisplayName("#730 7.3 a near-miss or unknown subject kind is refused by ck_outbound_deliveries_subject_kind, purpose by ck_outbound_deliveries_purpose_kind; each value is checked on its own column")
    void unknownValuesAreRefused() throws Exception {
        db.checkRefused(SUBJECT_CHECK, CARD, card("PERMISSION_ADJUSTMENTS", "TASK_ASSIGNED"));
        db.checkRefused(SUBJECT_CHECK, CARD, card("PERMISSION_ADJUSTMENT_ASSIGNED", "TASK_ASSIGNED"));
        db.checkRefused(PURPOSE_CHECK, CARD, card("APPROVAL_TASK", "PERMISSION_ADJUSTMENT_ASSIGNEDS"));
        db.checkRefused(PURPOSE_CHECK, CARD, card("APPROVAL_TASK", "PERMISSION_ADJUSTMENT"));
        db.checkRefused(PURPOSE_CHECK, CARD, card("PERMISSION_ADJUSTMENT", "PERMISSION_ADJUSTMENT_ESCALATED"));
    }
}
