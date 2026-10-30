package com.forcitis.one.acceptance.platform.integration;

import com.forcitis.one.acceptance.RowRuleProbe;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #943 (DD sign-off on the card): the delivery subject kind PERSONNEL_COMMAND and the purpose UNPAID_LEAVE_START_ALERT are CHECK values of
 * outbound_deliveries (subject_id = the personnel command id); the values already on main stay; unknown values are still refused, each by its own CHECK.
 */
class OutboundDeliveryUnpaidLeaveAlertRowRulesIT {

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
    @DisplayName("#943 a CARD row about a PERSONNEL_COMMAND with purpose UNPAID_LEAVE_START_ALERT is accepted")
    void theUnpaidLeaveAlertCardIsAccepted() throws Exception {
        db.accepted(CARD, card("PERSONNEL_COMMAND", "UNPAID_LEAVE_START_ALERT"));
    }

    @Test
    @DisplayName("#943 the values of main stay accepted: APPROVAL_TASK/TASK_ASSIGNED and SEPARATION_HOLD/SEPARATION_HOLD_NOTICE")
    void theExistingValuesStay() throws Exception {
        db.accepted(CARD, card("APPROVAL_TASK", "TASK_ASSIGNED"));
        db.accepted(CARD, card("SEPARATION_HOLD", "SEPARATION_HOLD_NOTICE"));
    }

    @Test
    @DisplayName("#943 an unknown subject kind is refused by ck_outbound_deliveries_subject_kind, an unknown purpose by ck_outbound_deliveries_purpose_kind")
    void unknownValuesAreRefused() throws Exception {
        db.checkRefused(SUBJECT_CHECK, CARD, card("PERSONNEL_COMMANDS", "TASK_ASSIGNED"));
        db.checkRefused(PURPOSE_CHECK, CARD, card("APPROVAL_TASK", "UNPAID_LEAVE_START_ALERTS"));
        db.checkRefused(SUBJECT_CHECK, CARD, card("UNKNOWN_KIND", "TASK_ASSIGNED"));
        db.checkRefused(PURPOSE_CHECK, CARD, card("APPROVAL_TASK", "UNKNOWN_PURPOSE"));
    }
}
