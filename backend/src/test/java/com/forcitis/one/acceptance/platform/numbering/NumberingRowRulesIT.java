package com.forcitis.one.acceptance.platform.numbering;

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
 * DATA-REPAIR-approval AP-09, AP-11, AP-14 (numbering side), AP-17 (PR-7, one-data ruling 2026-10-16): a confirmed template version names its
 * confirmer (one-way only: finance confirmation stays, ADR-0030 U-3); the five retired columns of numbering_rules are gone and the seeds
 * carry event_count 0; narrowing approval requests to TIMESHEET leaves numbering untouched; counters carry a period-key format, a
 * last_value range and a document-type set. Each refused statement breaks exactly one rule.
 */
class NumberingRowRulesIT {

    private static final UUID TIMESHEET_RULE = UUID.fromString("019a0000-0000-7000-8000-000000000302");
    private static final String VERSION = "insert into platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id,"
            + " version_number, template_text, reset_policy, start_value, effective_from_date, author_id, authored_at, status, source_kind, confirmed_by,"
            + " confirmed_at, finance_confirmed_by, finance_confirmed_at) values (?, ?, 2, '{TYPE}-{seq:4}', 'NEVER', 1, DATE '2027-01-01', null, now(), ?,"
            + " 'SEEDED', ?, ?, ?, ?)";
    private static final String COUNTER = "insert into platform_numbering.numbering_counters (numbering_counter_id, document_type_id, counter_scope_kind,"
            + " legal_entity_id, period_key, last_value) values (?, (select document_type_id from platform_numbering.document_types where code = ?), 'GLOBAL', null, ?, ?)";
    private static final String COUNTER_BY_TYPE_ID = "insert into platform_numbering.numbering_counters (numbering_counter_id, document_type_id, counter_scope_kind,"
            + " legal_entity_id, period_key, last_value) values (?, ?, 'GLOBAL', null, ?, ?)";

    private RowRuleProbe db;

    @BeforeEach
    void aMigratedDatabase() throws Exception {
        db = new RowRuleProbe(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    /** The seed's version 1 is the rule's one pending version; a second pending version needs it out of the way. */
    private void supersedeSeedVersion() throws SQLException {
        db.setup("update platform_numbering.numbering_rule_versions set status = 'SUPERSEDED' where numbering_rule_id = ?", TIMESHEET_RULE);
    }

    private void version(String status, UUID confirmedBy, boolean confirmedAt, UUID financeBy, String refusedBy) throws SQLException {
        Object now = java.sql.Timestamp.from(java.time.Instant.parse("2026-10-16T00:00:00Z"));
        Object[] parameters = {UUID.randomUUID(), TIMESHEET_RULE, status, confirmedBy, confirmedAt ? now : null, financeBy, financeBy == null ? null : now};
        if (refusedBy == null) {
            db.accepted(VERSION, parameters);
        } else {
            db.checkRefused(refusedBy, VERSION, parameters);
        }
    }

    // RULE-489
    @Test
    @DisplayName("DATA-REPAIR-approval AP-09: a CONFIRMED template version without a confirmer is refused by ck_numbering_rule_versions_confirmed")
    void confirmedWithoutConfirmerRefused() throws SQLException {
        supersedeSeedVersion();
        version("CONFIRMED", null, false, null, "ck_numbering_rule_versions_confirmed");
        version("CONFIRMED", null, false, UUID.randomUUID(), "ck_numbering_rule_versions_confirmed");
    }

    // RULE-489
    @Test
    @DisplayName("DATA-REPAIR-approval AP-09: a CONFIRMED version with a confirmer, and a PENDING_CONFIRMATION version without one, go through")
    void confirmedWithConfirmerAndPendingWithoutAccepted() throws SQLException {
        supersedeSeedVersion();
        version("CONFIRMED", UUID.randomUUID(), true, null, null);
        version("PENDING_CONFIRMATION", null, false, null, null);
    }

    // RULE-489
    @Test
    @DisplayName("DATA-REPAIR-approval AP-09: the rule is one-way, a PENDING or SUPERSEDED version keeping its business confirmation (finance still to confirm) goes through")
    void businessConfirmationOnUnconfirmedStatusStillAccepted() throws SQLException {
        supersedeSeedVersion();
        version("PENDING_CONFIRMATION", UUID.randomUUID(), true, null, null);
        version("SUPERSEDED", UUID.randomUUID(), true, null, null);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-11: numbering_rules no longer has pattern, reset_policy, start_value, confirmation_status or is_active")
    void retiredColumnsAreGone() throws SQLException {
        assertThat(db.count("select count(*) from information_schema.columns where table_schema = 'platform_numbering'"
                + " and table_name = 'numbering_rules' and column_name in ('pattern', 'reset_policy', 'start_value', 'confirmation_status', 'is_active')"))
                .isZero();
        assertThat(db.column("select column_name from information_schema.columns where table_schema = 'platform_numbering'"
                + " and table_name = 'numbering_rules' order by column_name"))
                .containsExactlyInAnyOrder("created_at", "document_type_id", "event_count", "numbering_rule_id", "updated_at", "version");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-11: the four seed rules stay with event_count 0 and the seed versions keep their template; EMPLOYEE is global ({TYPE}{seq:6}, NEVER, no {LE}) and pending confirmation (#959, #963)")
    void seedsKeepTheirTemplateAndStartAtEventCountZero() throws SQLException {
        assertThat(db.column("select t.code || ':' || r.event_count || ':' || r.version from platform_numbering.numbering_rules r join platform_numbering.document_types t using (document_type_id) order by t.code"))
                .containsExactly("EMPLOYEE:0:1", "JOURNAL_VOUCHER:0:1", "QUOTE:0:1", "TIMESHEET:0:1");
        assertThat(db.column("select t.code || '|' || v.template_text || '|' || v.reset_policy || '|' || v.start_value || '|' || v.status"
                + " || '|' || v.version_number || '|' || v.numbering_rule_version_id from platform_numbering.numbering_rule_versions v"
                + " join platform_numbering.numbering_rules r using (numbering_rule_id) join platform_numbering.document_types t using (document_type_id) order by t.code"))
                .containsExactly(
                        "EMPLOYEE|{TYPE}{seq:6}|NEVER|1|PENDING_CONFIRMATION|1|019a0000-0000-7000-8000-000000000504",
                        "JOURNAL_VOUCHER|{TYPE}-{LE}-{yyyyMM}-{seq:4}|MONTHLY|1|PENDING_CONFIRMATION|1|019a0000-0000-7000-8000-000000000503",
                        "QUOTE|{TYPE}-{LE}-{yyyyMM}-{seq:4}|MONTHLY|1|PENDING_CONFIRMATION|1|019a0000-0000-7000-8000-000000000501",
                        "TIMESHEET|{TYPE}-{LE}-{yyyyMM}-{seq:4}|MONTHLY|1|PENDING_CONFIRMATION|1|019a0000-0000-7000-8000-000000000502");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-14: numbering still takes QUOTE and JOURNAL_VOUCHER, only a document type id that is not registered is refused (fk_numbering_counters_document_type_id)")
    void numberingKeepsQuoteAndJournalVoucher() throws SQLException {
        assertThat(db.column("select t.code from platform_numbering.numbering_rules r join platform_numbering.document_types t using (document_type_id) order by t.code"))
                .containsExactly("EMPLOYEE", "JOURNAL_VOUCHER", "QUOTE", "TIMESHEET");
        db.accepted(COUNTER, UUID.randomUUID(), "QUOTE", "202610", 1L);
        db.accepted(COUNTER, UUID.randomUUID(), "JOURNAL_VOUCHER", "202610", 1L);
        db.refused("23503", "fk_numbering_counters_document_type_id", COUNTER_BY_TYPE_ID, UUID.randomUUID(), UUID.randomUUID(), "202610", 1L);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-17: the four period keys '-', yyyy, yyyyMM and yyyyMMdd go through")
    void legalPeriodKeysAccepted() throws SQLException {
        for (String key : List.of("-", "2026", "202610", "20261014")) {
            db.accepted(COUNTER, UUID.randomUUID(), "TIMESHEET", key, 1L);
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-17: a period key of another shape is refused by ck_numbering_counters_period_key")
    void illegalPeriodKeysRefused() throws SQLException {
        for (String key : List.of("2026-10", "20261", "", "2026101", "202610140", "abcd", "--", " 2026", "2026\n")) {
            db.checkRefused("ck_numbering_counters_period_key", COUNTER, UUID.randomUUID(), "TIMESHEET", key, 1L);
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-17: last_value of 1 and of 999999999999 goes through")
    void lastValueInRangeAccepted() throws SQLException {
        db.accepted(COUNTER, UUID.randomUUID(), "TIMESHEET", "202610", 1L);
        db.accepted(COUNTER, UUID.randomUUID(), "TIMESHEET", "202610", 999_999_999_999L);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-17: last_value of 0, a negative number or 10^12 is refused by ck_numbering_counters_last_value")
    void lastValueOutOfRangeRefused() throws SQLException {
        for (long value : new long[] {0L, -1L, Long.MIN_VALUE, 1_000_000_000_000L, Long.MAX_VALUE}) {
            db.checkRefused("ck_numbering_counters_last_value", COUNTER, UUID.randomUUID(), "TIMESHEET", "202610", value);
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-17: a document type id outside the registry is refused by fk_numbering_counters_document_type_id")
    void unknownCounterDocumentTypeRefused() throws SQLException {
        for (int attempt = 0; attempt < 4; attempt++) {
            db.refused("23503", "fk_numbering_counters_document_type_id", COUNTER_BY_TYPE_ID, UUID.randomUUID(), UUID.randomUUID(), "202610", 1L);
        }
    }
}
