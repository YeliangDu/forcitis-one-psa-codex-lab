-- RULE-385：已关闭的法人归属期间不改写；结束日写错时追加一条更正记录。本表只追加，触发器拒绝修改、删除和清空。
CREATE TABLE organization_workforce.legal_entity_attribution_corrections (
    correction_id                uuid        NOT NULL,
    correction_number            bigint      GENERATED ALWAYS AS IDENTITY,
    subject_kind                 text        NOT NULL CONSTRAINT ck_legal_entity_attribution_corrections_subject_kind CHECK (subject_kind IN ('ORG_UNIT', 'EMPLOYEE')),
    subject_id                   bigint      NOT NULL,
    period_effective_from_date   date        NOT NULL,
    previous_effective_to_date   date        NOT NULL,
    new_effective_to_date        date        NOT NULL,
    correction_reason            text        NOT NULL,
    corrected_by_user_id         bigint      NOT NULL,
    created_at                   timestamptz NOT NULL DEFAULT now(),
    updated_at                   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_legal_entity_attribution_corrections PRIMARY KEY (correction_id),
    CONSTRAINT uk_legal_entity_attribution_corrections_correction_number UNIQUE (correction_number),
    CONSTRAINT ck_legal_entity_attribution_corrections_range CHECK (new_effective_to_date > period_effective_from_date),
    CONSTRAINT ck_legal_entity_attribution_corrections_reason CHECK (btrim(correction_reason) <> '')
);
CREATE INDEX ix_legal_entity_attribution_corrections_subject ON organization_workforce.legal_entity_attribution_corrections
    (subject_kind, subject_id, period_effective_from_date, correction_number DESC);
COMMENT ON TABLE organization_workforce.legal_entity_attribution_corrections IS '法人归属更正记录：只追加；解析法人时，期间的结束日取该期间最新一条更正的新结束日；粒度：每次更正一行';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.correction_id IS '更正记录 ID';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.correction_number IS '更正序号：全表递增，同一期间多次更正时序号最大的为准';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.subject_kind IS '归属对象类别：ORG_UNIT 部门 / EMPLOYEE 员工';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.subject_id IS '归属对象 ID：部门 ID 或员工 ID';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.period_effective_from_date IS '被更正期间的生效日（含）：与期间表中该期间的生效日相同';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.previous_effective_to_date IS '更正前的结束日（不含）';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.new_effective_to_date IS '更正后的结束日（不含）';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.correction_reason IS '更正原因：操作人填写，不得为空';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.corrected_by_user_id IS '操作人 ID：来自登录会话';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.updated_at IS '更新时间（行不可更新，恒等于创建时间）';

CREATE FUNCTION organization_workforce.reject_attribution_correction_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'organization_workforce.legal_entity_attribution_corrections is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER legal_entity_attribution_corrections_reject_update_delete
    BEFORE UPDATE OR DELETE ON organization_workforce.legal_entity_attribution_corrections
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.reject_attribution_correction_change();

CREATE TRIGGER legal_entity_attribution_corrections_reject_truncate
    BEFORE TRUNCATE ON organization_workforce.legal_entity_attribution_corrections
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.reject_attribution_correction_change();
