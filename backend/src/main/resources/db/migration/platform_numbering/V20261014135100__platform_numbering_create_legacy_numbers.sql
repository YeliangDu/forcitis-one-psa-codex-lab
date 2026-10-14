CREATE TABLE platform_numbering.legacy_numbers (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_type   text        NOT NULL,
    legacy_number   text        NOT NULL,
    document_id     bigint      NOT NULL,
    legal_entity_id bigint      NOT NULL,
    import_batch    text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT legacy_numbers_type_number_key UNIQUE (document_type, legacy_number)
);
CREATE INDEX legacy_numbers_document_idx ON platform_numbering.legacy_numbers (document_type, document_id);
COMMENT ON TABLE platform_numbering.legacy_numbers IS '原编号：新系统单据与其迁移前编号的对照，只读检索；各单据在其切换阶段导入';
COMMENT ON COLUMN platform_numbering.legacy_numbers.id IS '主键';
COMMENT ON COLUMN platform_numbering.legacy_numbers.document_type IS '单据类型（DocumentType 枚举名）；与原编号合起来唯一';
COMMENT ON COLUMN platform_numbering.legacy_numbers.legacy_number IS '原编号：迁移前该单据在老系统中的编号，按原样保留';
COMMENT ON COLUMN platform_numbering.legacy_numbers.document_id IS '单据：新系统单据 id，与单据类型合起来定位一张单据';
COMMENT ON COLUMN platform_numbering.legacy_numbers.legal_entity_id IS '法人主体 ID';
COMMENT ON COLUMN platform_numbering.legacy_numbers.import_batch IS '导入批次：写入这条对照的导入批次标识，便于整批核对与回溯';
COMMENT ON COLUMN platform_numbering.legacy_numbers.created_at IS '创建时间';
COMMENT ON COLUMN platform_numbering.legacy_numbers.updated_at IS '更新时间';
