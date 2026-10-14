-- D-1a-170/171：审计条目增加变更说明和变更前后值（来源地址、结果、登录方式已由登录与会话类条目的结构化列承担）。
ALTER TABLE platform_audit.audit_entries
    ADD COLUMN change_reason text,
    ADD COLUMN before_text text,
    ADD COLUMN after_text text;
COMMENT ON COLUMN platform_audit.audit_entries.change_reason IS '变更说明：操作人填写的原因（放行、强制下线等必填原因的动作）';
COMMENT ON COLUMN platform_audit.audit_entries.before_text IS '变更前的值：只写业务值，不含密钥和个人敏感信息（L3 / L4 不进审计）';
COMMENT ON COLUMN platform_audit.audit_entries.after_text IS '变更后的值：规则同变更前';
