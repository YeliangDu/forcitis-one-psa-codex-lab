-- ADR-0027 第 2 项第 4 条：编号模板按版本保存、不可变；作者不得确认自己的版本；未确认的规则不发号。
CREATE TABLE platform_numbering.numbering_rule_versions (
    document_type        text        NOT NULL CHECK (document_type IN ('QUOTE', 'TIMESHEET', 'JOURNAL_VOUCHER')),
    version_number       integer     NOT NULL CHECK (version_number >= 1),
    template_text        text        NOT NULL,
    reset_policy         text        NOT NULL CHECK (reset_policy IN ('NEVER', 'YEARLY', 'MONTHLY', 'DAILY')),
    start_value          bigint      NOT NULL CHECK (start_value >= 1),
    effective_from_date  date        NOT NULL,
    author_id            bigint,
    authored_at          timestamptz NOT NULL,
    status               text        NOT NULL CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'SUPERSEDED')),
    confirmed_by         bigint,
    confirmed_at         timestamptz,
    finance_confirmed_by bigint,
    finance_confirmed_at timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_numbering_rule_versions PRIMARY KEY (document_type, version_number),
    CONSTRAINT fk_numbering_rule_versions_document_type FOREIGN KEY (document_type) REFERENCES platform_numbering.numbering_rules (document_type),
    CONSTRAINT ck_numbering_rule_versions_confirmer_is_not_author
        CHECK (author_id IS NULL OR ((confirmed_by IS NULL OR confirmed_by <> author_id) AND (finance_confirmed_by IS NULL OR finance_confirmed_by <> author_id))),
    CONSTRAINT ck_numbering_rule_versions_two_different_confirmers
        CHECK (confirmed_by IS NULL OR finance_confirmed_by IS NULL OR confirmed_by <> finance_confirmed_by),
    CONSTRAINT ck_numbering_rule_versions_confirmation_time
        CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL) AND (finance_confirmed_by IS NULL) = (finance_confirmed_at IS NULL))
);
CREATE UNIQUE INDEX uk_numbering_rule_versions_document_type ON platform_numbering.numbering_rule_versions (document_type)
    WHERE status = 'PENDING_CONFIRMATION';
COMMENT ON TABLE platform_numbering.numbering_rule_versions IS '编号规则版本：模板按版本保存，保存后内容不可变，确认只补不改（触发器拒绝改内容、改已有确认、非法状态转换与删除）；每种单据至多一个待确认版本，发号取生效日不晚于业务日期的最高已确认版本。粒度：每种单据的每个模板版本一行';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.document_type IS '单据类型（DocumentType 枚举名）';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.version_number IS '版本号：每种单据从 1 递增，每保存一次新模板加 1';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.template_text IS '编号模板，占位符 {TYPE} {LE} {yyyy} {yy} {MM} {dd} {yyyyMM} {yyyyMMdd} {yyMMdd} {seq:N}（N 为 1 至 12），如 {TYPE}-{LE}-{yyyyMM}-{seq:4}';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.reset_policy IS '流水号重置周期：NEVER / YEARLY / MONTHLY / DAILY';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.start_value IS '每个周期的起始流水号';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.effective_from_date IS '生效日：作者提交内容的一部分，不可改写；业务日期从这一天起用该版本发号。确认时若早于当天则拒绝，作者改日期重新提交；种子默认模板为 2000-01-01';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.author_id IS '作者用户 ID：保存该版本的人；种子默认模板为空；确认人不得是作者（四眼）';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.authored_at IS '保存该版本的时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.status IS '版本状态：PENDING_CONFIRMATION 待确认 / CONFIRMED 已确认 / SUPERSEDED 被新版本取代而未经确认';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.confirmed_by IS '业务确认人用户 ID：持有 numbering-rule:approve 的人';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.confirmed_at IS '业务确认时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.finance_confirmed_by IS '财务确认人用户 ID：凭证号模板另需；与业务确认人、作者都不是同一人';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.finance_confirmed_at IS '财务确认时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.numbering_rule_versions.updated_at IS '更新时间';

CREATE FUNCTION platform_numbering.guard_rule_version_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_numbering.numbering_rule_versions is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.document_type IS DISTINCT FROM OLD.document_type OR NEW.version_number IS DISTINCT FROM OLD.version_number
       OR NEW.template_text IS DISTINCT FROM OLD.template_text OR NEW.reset_policy IS DISTINCT FROM OLD.reset_policy
       OR NEW.start_value IS DISTINCT FROM OLD.start_value OR NEW.effective_from_date IS DISTINCT FROM OLD.effective_from_date
       OR NEW.author_id IS DISTINCT FROM OLD.author_id OR NEW.authored_at IS DISTINCT FROM OLD.authored_at THEN
        RAISE EXCEPTION 'a saved numbering template version is immutable' USING ERRCODE = 'restrict_violation';
    END IF;
    IF (OLD.confirmed_by IS NOT NULL AND (NEW.confirmed_by IS DISTINCT FROM OLD.confirmed_by OR NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at))
       OR (OLD.finance_confirmed_by IS NOT NULL AND (NEW.finance_confirmed_by IS DISTINCT FROM OLD.finance_confirmed_by
           OR NEW.finance_confirmed_at IS DISTINCT FROM OLD.finance_confirmed_at)) THEN
        RAISE EXCEPTION 'a confirmation of a numbering template version cannot be changed or taken back' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (OLD.status = 'PENDING_CONFIRMATION' AND NEW.status IN ('CONFIRMED', 'SUPERSEDED')) THEN
        RAISE EXCEPTION 'a numbering template version cannot go from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER numbering_rule_versions_guard
    BEFORE UPDATE OR DELETE ON platform_numbering.numbering_rule_versions
    FOR EACH ROW EXECUTE FUNCTION platform_numbering.guard_rule_version_change();
CREATE TRIGGER tg_numbering_rule_versions_append_only_truncate
    BEFORE TRUNCATE ON platform_numbering.numbering_rule_versions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_numbering.guard_rule_version_change();

-- 现有规则的模板成为版本 1，一律回到待确认：没有列能判断原作者，由业务管理员按四眼重新确认（凭证号模板另由财务确认）。
INSERT INTO platform_numbering.numbering_rule_versions (document_type, version_number, template_text, reset_policy, start_value, effective_from_date, authored_at, status)
SELECT document_type, 1, pattern, reset_policy, start_value, DATE '2000-01-01', now(), 'PENDING_CONFIRMATION'
  FROM platform_numbering.numbering_rules;

-- 旧列只追加地停用：不删列、不再写入，由版本表取代。
ALTER TABLE platform_numbering.numbering_rules
    ALTER COLUMN pattern DROP NOT NULL,
    ALTER COLUMN reset_policy DROP NOT NULL,
    ALTER COLUMN start_value DROP NOT NULL,
    ALTER COLUMN confirmation_status DROP NOT NULL;
COMMENT ON COLUMN platform_numbering.numbering_rules.pattern IS '停用，由 numbering_rule_versions 取代';
COMMENT ON COLUMN platform_numbering.numbering_rules.reset_policy IS '流水号重置周期：停用，由 numbering_rule_versions 取代';
COMMENT ON COLUMN platform_numbering.numbering_rules.start_value IS '停用，由 numbering_rule_versions 取代';
COMMENT ON COLUMN platform_numbering.numbering_rules.confirmation_status IS '确认状态：停用，由 numbering_rule_versions 取代';
COMMENT ON TABLE platform_numbering.numbering_rules IS '编号规则：每种单据一条，是版本的聚合根；模板内容在 numbering_rule_versions，由组织数据管理员维护、业务管理员确认（凭证号另需财务确认）';
