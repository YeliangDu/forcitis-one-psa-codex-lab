package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.RowRuleProbe;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #732 part B, DD sign-off section C (one-design, 2026-10-30): outbound_deliveries accepts the subject kinds ACCOUNT_ROLE_GRANT and USER_ACCOUNT
 * and the purposes ROLE_REVOCATION_NOTICE and ACCOUNT_DISABLE_NOTICE, keeps the old values, refuses unknown ones, and lists the new values (and the
 * disable trigger) in its column comments. One card per subject, purpose and trigger: two disables of one account differ by trigger.
 */
class RevokeDisableDeliveryRowRulesIT {

    private static final String INSERT = "insert into platform_integration.outbound_deliveries (id, subject_kind, subject_id, purpose_kind, trigger_event_id,"
            + " title, target_url, status, recipient_codes) values (?, ?, ?, ?, ?, 'synthetic', '/synthetic', 'PENDING', array['wx-synthetic'])";

    private RowRuleProbe db;

    @BeforeEach
    void aMigratedDatabase() throws Exception {
        db = new RowRuleProbe(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private static Object[] row(String kind, String purpose, UUID trigger) {
        return new Object[] {UUID.randomUUID(), kind, UUID.randomUUID(), purpose, trigger};
    }

    @Test
    @DisplayName("#732B DD C: the four new delivery values are accepted, with the trigger the DD gives (none for a revocation notice, an event id for a disable notice); the earlier values still are")
    void newValuesAreAcceptedAndOldOnesKept() throws SQLException {
        db.accepted(INSERT, row("ACCOUNT_ROLE_GRANT", "ROLE_REVOCATION_NOTICE", null));
        db.accepted(INSERT, row("USER_ACCOUNT", "ACCOUNT_DISABLE_NOTICE", UUID.randomUUID()));
        for (String kind : MAIN_KINDS) {
            for (String purpose : MAIN_PURPOSES) {
                db.accepted(INSERT, row(kind, purpose, null));
            }
        }
    }

    /** The values on main before this card (V20261014133000 and V20261030150100): the new CHECKs must keep every one (they may add more). */
    private static final List<String> MAIN_KINDS = List.of("APPROVAL_TASK", "APPROVAL_INSTANCE", "SEPARATION_HOLD");
    private static final List<String> MAIN_PURPOSES = List.of("TASK_ASSIGNED", "TASK_TRANSFERRED", "TASK_REASSIGNED", "TASK_OVERDUE", "TASK_ESCALATED",
            "APPROVAL_COMPLETED", "SEPARATION_HOLD_NOTICE");

    @Test
    @DisplayName("#732B DD C merge order: the CHECK value lists, read from the catalogue, contain every value main has (3 kinds, 7 purposes) as well as the new ones")
    void checkListsAreSupersetsOfMain() throws SQLException {
        String kinds = String.valueOf(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'ck_outbound_deliveries_subject_kind'").get(0));
        String purposes = String.valueOf(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'ck_outbound_deliveries_purpose_kind'").get(0));
        for (String kind : MAIN_KINDS) {
            assertThat(kinds).contains("'" + kind + "'");
        }
        for (String purpose : MAIN_PURPOSES) {
            assertThat(purposes).contains("'" + purpose + "'");
        }
        assertThat(kinds).contains("'ACCOUNT_ROLE_GRANT'", "'USER_ACCOUNT'");
        assertThat(purposes).contains("'ROLE_REVOCATION_NOTICE'", "'ACCOUNT_DISABLE_NOTICE'");
    }

    @Test
    @DisplayName("#732B DD C: an unknown subject kind or purpose is refused by ck_outbound_deliveries_subject_kind / ck_outbound_deliveries_purpose_kind")
    void unknownValuesAreRefused() throws SQLException {
        db.checkRefused("ck_outbound_deliveries_subject_kind", INSERT, row("NO_SUCH_KIND", "ROLE_REVOCATION_NOTICE", null));
        db.checkRefused("ck_outbound_deliveries_purpose_kind", INSERT, row("USER_ACCOUNT", "NO_SUCH_PURPOSE", UUID.randomUUID()));
    }

    @Test
    @DisplayName("#732B DD C: one card per subject, purpose and trigger: a grant gets one revocation notice; one account gets one disable notice per disable (trigger = audit entry id), the same disable twice is refused")
    void oneCardPerSubjectPurposeAndTrigger() throws SQLException {
        UUID grant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID firstDisable = UUID.randomUUID();
        db.setup(INSERT, UUID.randomUUID(), "ACCOUNT_ROLE_GRANT", grant, "ROLE_REVOCATION_NOTICE", null);
        db.setup(INSERT, UUID.randomUUID(), "USER_ACCOUNT", account, "ACCOUNT_DISABLE_NOTICE", firstDisable);

        db.refused("23505", "uk_outbound_deliveries_card", INSERT, UUID.randomUUID(), "ACCOUNT_ROLE_GRANT", grant, "ROLE_REVOCATION_NOTICE", null);
        db.refused("23505", "uk_outbound_deliveries_card", INSERT, UUID.randomUUID(), "USER_ACCOUNT", account, "ACCOUNT_DISABLE_NOTICE", firstDisable);
        db.accepted(INSERT, UUID.randomUUID(), "USER_ACCOUNT", account, "ACCOUNT_DISABLE_NOTICE", UUID.randomUUID());
    }

    @Test
    @DisplayName("#732B DD C: the column comments list the new kinds and purposes, and the trigger comment says a disable takes that disable's audit entry id")
    void commentsListTheNewValues() throws SQLException {
        String subject = String.valueOf(db.column("select col_description('platform_integration.outbound_deliveries'::regclass, "
                + "(select attnum from pg_attribute where attrelid = 'platform_integration.outbound_deliveries'::regclass and attname = 'subject_kind'))").get(0));
        String purpose = String.valueOf(db.column("select col_description('platform_integration.outbound_deliveries'::regclass, "
                + "(select attnum from pg_attribute where attrelid = 'platform_integration.outbound_deliveries'::regclass and attname = 'purpose_kind'))").get(0));
        String trigger = String.valueOf(db.column("select col_description('platform_integration.outbound_deliveries'::regclass, "
                + "(select attnum from pg_attribute where attrelid = 'platform_integration.outbound_deliveries'::regclass and attname = 'trigger_event_id'))").get(0));
        assertThat(subject).contains("ACCOUNT_ROLE_GRANT", "USER_ACCOUNT");
        assertThat(purpose).contains("ROLE_REVOCATION_NOTICE", "ACCOUNT_DISABLE_NOTICE");
        assertThat(trigger).contains("账号停用取该次停用的审计条目 ID");
    }
}
