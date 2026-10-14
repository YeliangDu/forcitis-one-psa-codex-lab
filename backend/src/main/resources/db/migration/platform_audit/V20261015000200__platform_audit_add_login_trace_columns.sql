-- 登录与会话类条目的结构化列（意图 155、ADR-0027 第 3 项）：员工查自己的登录记录只取这些列，不解析说明文字。
ALTER TABLE platform_audit.audit_entries
    ADD COLUMN source_address text,
    ADD COLUMN outcome_kind text CHECK (outcome_kind IN ('SUCCEEDED', 'FAILED')),
    ADD COLUMN login_method_kind text CHECK (login_method_kind IN ('WECOM_WORKBENCH', 'WECOM_MINI_PROGRAM', 'WECOM_QR_CODE_LOGIN', 'SSO', 'SIGNED_TICKET', 'EMERGENCY'));
COMMENT ON COLUMN platform_audit.audit_entries.source_address IS '来源地址：登录与会话类条目的客户端地址（只取受信代理解析后的地址）；其他条目为空';
COMMENT ON COLUMN platform_audit.audit_entries.outcome_kind IS '结果：SUCCEEDED / FAILED；登录与会话类条目才有，其他条目为空';
COMMENT ON COLUMN platform_audit.audit_entries.login_method_kind IS '登录方式：企业微信（工作台、小程序、扫码）、单点登录、签名票据、应急登录；其他条目为空';
