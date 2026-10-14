-- 数据打分卡 K4：法人的代码、名称、状态按生效区间保留历史，只追加；与法人主表在同一事务写入，同日再次变化关闭当天版本并追加下一修订。
CREATE TABLE organization_workforce.legal_entity_versions (
    legal_entity_id     bigint      NOT NULL,
    effective_from_date date        NOT NULL,
    revision_number     integer     NOT NULL DEFAULT 0,
    effective_to_date   date,
    code                text        NOT NULL,
    name                text        NOT NULL,
    status              text        NOT NULL CONSTRAINT ck_legal_entity_versions_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_legal_entity_versions PRIMARY KEY (legal_entity_id, effective_from_date, revision_number),
    CONSTRAINT fk_legal_entity_versions_legal_entity_id FOREIGN KEY (legal_entity_id) REFERENCES organization_workforce.legal_entities (id),
    CONSTRAINT ck_legal_entity_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ex_legal_entity_versions_overlap EXCLUDE USING gist (legal_entity_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
);
COMMENT ON TABLE organization_workforce.legal_entity_versions IS '法人版本：法人代码、名称、状态的变化历史，只追加；粒度：每个法人的每个生效区间每个修订一行';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.legal_entity_id IS '法人主体 ID';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.effective_from_date IS '生效日（含），按业务时区截日';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.revision_number IS '同一生效日内的修订序号：同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.effective_to_date IS '失效日（不含）；为空表示至今';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.code IS '法人代码';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.name IS '法人名称';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.status IS '状态：ACTIVE 启用 / INACTIVE 停用';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.updated_at IS '更新时间';

CREATE TRIGGER tg_legal_entity_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.legal_entity_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();

INSERT INTO organization_workforce.legal_entity_versions (legal_entity_id, effective_from_date, code, name, status)
SELECT id, (created_at AT TIME ZONE 'Asia/Shanghai')::date, code, name, status FROM organization_workforce.legal_entities;
