package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.RowRuleProbe;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubjectKind;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #1151 completion line 7: the new purpose of the escalation card is a CHECK value of outbound_deliveries beside the subject kind
 * PERMISSION_ADJUSTMENT; the values already on main stay; near-misses are refused, each by its own CHECK. The purpose has no name in the card, so it
 * is the one DeliveryPurpose about a PERMISSION_ADJUSTMENT besides PERMISSION_ADJUSTMENT_ASSIGNED (a missing or a second one fails). Lives with
 * the other outbound_deliveries row rules: the guard (CS-5.5) keeps a test to its own module's schema.
 */
class OutboundDeliveryEscalationNoticeRowRulesIT {

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

    private static DeliveryPurpose escalationPurpose() {
        List<DeliveryPurpose> found = Arrays.stream(DeliveryPurpose.values())
                .filter(purpose -> purpose.subjectKind() == DeliverySubjectKind.PERMISSION_ADJUSTMENT && purpose != DeliveryPurpose.PERMISSION_ADJUSTMENT_ASSIGNED).toList();
        assertThat(found).as("#1151 adds exactly one DeliveryPurpose about a PERMISSION_ADJUSTMENT besides PERMISSION_ADJUSTMENT_ASSIGNED").hasSize(1);
        return found.getFirst();
    }

    private static Object[] card(String subjectKind, String purpose) {
        return new Object[] {UUID.randomUUID(), subjectKind, UUID.randomUUID(), purpose};
    }

    @Test
    @DisplayName("#1151 a CARD row about a PERMISSION_ADJUSTMENT with the new escalation purpose is accepted")
    void theEscalationCardIsAccepted() throws Exception {
        db.accepted(CARD, card("PERMISSION_ADJUSTMENT", escalationPurpose().name()));
    }

    @Test
    @DisplayName("#1151 the values already on main stay accepted, including PERMISSION_ADJUSTMENT_ASSIGNED")
    void theExistingValuesStay() throws Exception {
        db.accepted(CARD, card("APPROVAL_TASK", "TASK_ASSIGNED"));
        db.accepted(CARD, card("SEPARATION_HOLD", "SEPARATION_HOLD_NOTICE"));
        db.accepted(CARD, card("PERSONNEL_COMMAND", "UNPAID_LEAVE_START_NOTICE"));
        db.accepted(CARD, card("PERMISSION_ADJUSTMENT", "PERMISSION_ADJUSTMENT_ASSIGNED"));
    }

    @Test
    @DisplayName("#1151 a near-miss of the new purpose (plural, lower case, prefix, truncated) is refused by ck_outbound_deliveries_purpose_kind, a near-miss subject kind by ck_outbound_deliveries_subject_kind")
    void nearMissesAreRefused() throws Exception {
        String purpose = escalationPurpose().name();
        db.checkRefused(PURPOSE_CHECK, CARD, card("PERMISSION_ADJUSTMENT", purpose + "S"));
        db.checkRefused(PURPOSE_CHECK, CARD, card("PERMISSION_ADJUSTMENT", purpose.toLowerCase()));
        db.checkRefused(PURPOSE_CHECK, CARD, card("PERMISSION_ADJUSTMENT", "X_" + purpose));
        db.checkRefused(PURPOSE_CHECK, CARD, card("PERMISSION_ADJUSTMENT", purpose.substring(0, purpose.length() - 1)));
        db.checkRefused(SUBJECT_CHECK, CARD, card("PERMISSION_ADJUSTMENTS", purpose));
    }
}
