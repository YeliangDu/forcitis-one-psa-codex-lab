-- D2（#934）：同步记录已删除，审计对象类别 SYNC_RUN 退休；已有条目照常可读。
UPDATE platform_audit.audit_subject_kinds
   SET status = 'RETIRED', retired_date = DATE '2026-11-02', updated_at = TIMESTAMPTZ '2026-11-02 00:00:00+00'
 WHERE code = 'SYNC_RUN' AND status = 'ACTIVE';
