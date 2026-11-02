package com.forcitis.one.platform.numbering;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Test set-up for the numbering schema only. A saved version is immutable and cannot be deleted, so the tests that need a known
 * start switch the guarding trigger off for the length of the statement, then restore the seeded defaults.
 */
final class NumberingFixtures {

    private static final String VERSIONS = "platform_numbering.numbering_rule_versions";
    private static final String TRIGGER = "numbering_rule_versions_guard";

    private NumberingFixtures() {
    }

    /** Every rule is back on its seeded default template, waiting for confirmation, with no counter and no history. */
    static void restoreSeededDefaults(JdbcClient jdbc) {
        jdbc.sql("alter table " + VERSIONS + " disable trigger " + TRIGGER).update();
        try {
            jdbc.sql("delete from " + VERSIONS).update();
            jdbc.sql("""
                    insert into platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value, effective_from_date, authored_at, status, source_kind)
                    select uuidv7(), r.numbering_rule_id, 1, case when t.code = 'EMPLOYEE' then '{TYPE}{seq:6}' else '{TYPE}-{LE}-{yyyyMM}-{seq:4}' end,
                           case when t.code = 'EMPLOYEE' then 'NEVER' else 'MONTHLY' end, 1, DATE '2000-01-01', now(), 'PENDING_CONFIRMATION', 'SEEDED'
                      from platform_numbering.numbering_rules r join platform_numbering.document_types t using (document_type_id)
                    """).update();
        } finally {
            jdbc.sql("alter table " + VERSIONS + " enable trigger " + TRIGGER).update();
        }
        jdbc.sql("update platform_numbering.numbering_rules set version = 0, event_count = 0").update();
        jdbc.sql("delete from platform_numbering.numbering_counters").update();
    }

    /** The seeded version is in force, as if two other people had confirmed it. */
    static void confirmSeeded(JdbcClient jdbc, String documentType) {
        jdbc.sql("""
                update platform_numbering.numbering_rule_versions
                   set status = 'CONFIRMED', confirmed_by = '01900000-0000-7000-8000-000000000101', confirmed_at = now(),
                       finance_confirmed_by = case when :type = 'JOURNAL_VOUCHER' then '01900000-0000-7000-8000-000000000102'::uuid end,
                       finance_confirmed_at = case when :type = 'JOURNAL_VOUCHER' then now() end
                 where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = :type)) and version_number = 1
                """).param("type", documentType).update();
    }

    static void useTemplate(JdbcClient jdbc, String documentType, String pattern, String resetPolicy) {
        jdbc.sql("alter table " + VERSIONS + " disable trigger " + TRIGGER).update();
        try {
            jdbc.sql("update " + VERSIONS + " set template_text = :pattern, reset_policy = :reset where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = :type)) and version_number = 1")
                    .param("pattern", pattern).param("reset", resetPolicy).param("type", documentType).update();
        } finally {
            jdbc.sql("alter table " + VERSIONS + " enable trigger " + TRIGGER).update();
        }
    }
}
