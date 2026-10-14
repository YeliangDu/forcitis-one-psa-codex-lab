CREATE SCHEMA IF NOT EXISTS platform_numbering;
COMMENT ON SCHEMA platform_numbering IS '单据编号（权限流程与集成平台 BC · 平台配置）';

CREATE TABLE platform_numbering.numbering_rules (
    document_type text        PRIMARY KEY,
    pattern       text        NOT NULL,
    reset_policy  text        NOT NULL CHECK (reset_policy IN ('NEVER', 'YEARLY', 'MONTHLY', 'DAILY')),
    start_value   bigint      NOT NULL CHECK (start_value >= 1),
    is_active     boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0
);
COMMENT ON TABLE platform_numbering.numbering_rules IS '编号规则：每种单据一条，可由管理员配置';
COMMENT ON COLUMN platform_numbering.numbering_rules.document_type IS '单据类型（DocumentType 枚举名）';
COMMENT ON COLUMN platform_numbering.numbering_rules.pattern IS '编号模板，如 QU-{yyyy}-{seq:5}；占位符 {LE} {yyyy} {yy} {MM} {dd} {seq:N}';
COMMENT ON COLUMN platform_numbering.numbering_rules.reset_policy IS '流水号重置周期：NEVER / YEARLY / MONTHLY / DAILY';
COMMENT ON COLUMN platform_numbering.numbering_rules.start_value IS '每个周期的起始流水号（切换时可承接老系统最大值）';
COMMENT ON COLUMN platform_numbering.numbering_rules.is_active IS '是否启用';
COMMENT ON COLUMN platform_numbering.numbering_rules.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_rules.updated_at IS '更新时间';
COMMENT ON COLUMN platform_numbering.numbering_rules.version IS '乐观锁版本';

CREATE TABLE platform_numbering.numbering_counters (
    document_type   text        NOT NULL,
    legal_entity_id bigint      NOT NULL,
    period_key      text        NOT NULL,
    last_value      bigint      NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (document_type, legal_entity_id, period_key)
);
COMMENT ON TABLE platform_numbering.numbering_counters IS '编号计数器：按单据类型 × 法人 × 周期计数，与业务写入同事务递增（不跳号）';
COMMENT ON COLUMN platform_numbering.numbering_counters.document_type IS '单据类型';
COMMENT ON COLUMN platform_numbering.numbering_counters.legal_entity_id IS '法人主体 ID';
COMMENT ON COLUMN platform_numbering.numbering_counters.period_key IS '周期键：- / 2026 / 202610 / 20261014';
COMMENT ON COLUMN platform_numbering.numbering_counters.last_value IS '已发出的最大流水号';
COMMENT ON COLUMN platform_numbering.numbering_counters.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_counters.updated_at IS '更新时间';

INSERT INTO platform_numbering.numbering_rules (document_type, pattern, reset_policy, start_value) VALUES
    ('QUOTE', '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1),
    ('TIMESHEET', '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1),
    ('JOURNAL_VOUCHER', '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1);
