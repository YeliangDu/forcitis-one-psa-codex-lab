UPDATE platform_numbering.numbering_rules
   SET pattern = '{TYPE}-{LE}-{yyyyMM}-{seq:4}', reset_policy = 'MONTHLY', start_value = 1,
       confirmation_status = 'PENDING_CONFIRMATION', updated_at = now(), version = version + 1
 WHERE document_type IN ('TIMESHEET', 'JOURNAL_VOUCHER');
