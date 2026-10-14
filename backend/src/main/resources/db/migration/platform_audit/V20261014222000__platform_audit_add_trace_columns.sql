-- D-1a-170/171：审计条目增加来源地址、结果、变更说明、变更前后值；追踪号即关联号 correlation_id（已有）。
ALTER TABLE platform_audit.audit_entries
    ADD COLUMN source_address text,
    ADD COLUMN result_kind text CONSTRAINT ck_audit_entries_result_kind CHECK (result_kind IN ('SUCCESS', 'FAILURE')),
    ADD COLUMN change_reason text,
    ADD COLUMN before_text text,
    ADD COLUMN after_text text;
CREATE INDEX ix_audit_entries_source_address ON platform_audit.audit_entries (source_address, created_at DESC);
COMMENT ON COLUMN platform_audit.audit_entries.source_address IS '来源地址：发起操作的客户端地址（只取可信代理解析后的值）；系统动作为空';
COMMENT ON COLUMN platform_audit.audit_entries.result_kind IS '结果：SUCCESS 成功 / FAILURE 失败；不区分结果的动作为空';
COMMENT ON COLUMN platform_audit.audit_entries.change_reason IS '变更说明：操作人填写的原因（放行、强制下线等必填原因的动作）';
COMMENT ON COLUMN platform_audit.audit_entries.before_text IS '变更前的值：只写业务值，不含密钥和个人敏感信息（L3 / L4 不进审计）';
COMMENT ON COLUMN platform_audit.audit_entries.after_text IS '变更后的值：规则同变更前';
