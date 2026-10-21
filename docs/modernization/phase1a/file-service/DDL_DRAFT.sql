-- 文件服务 platform_file 表结构草案（IMP-1021-COO-02 单元 3），交 one-data 签发；签发前不进 db/migration。
-- 作者 one-platform-file，2026-10-21。依据：ADR-0060 决定 3–5、ADR-0061 决定 7、ADR-0070 决定 2、6；契约 PR #502。
-- 单据引用按 REG-1（origin/claude/nx5-doctype-registry）在 numbering 之外的形状：document_type text + 格式 CHECK + document_id uuid，
--   不建跨 schema 外键（ADR-0066 决定 4 第 2、5 条：基础层不依赖 numbering，类型码是否登记由调用方负责）。
-- 运行角色：无 DELETE、无 TRUNCATE（随 REG-1 的运行角色回收写法另写授权段）；行级不可变由触发器兜底。
-- 待签发问题见文末。

CREATE SCHEMA IF NOT EXISTS platform_file;

-- ── 1. 存储文件（根）────────────────────────────────────────────────
CREATE TABLE platform_file.stored_files (
    stored_file_id           uuid        NOT NULL,
    object_key               text        NOT NULL,
    content_hash             text        NOT NULL,
    byte_count               bigint      NOT NULL,
    media_type_code          text        NOT NULL,
    uploader_kind            text        NOT NULL,
    uploader_key             text        NOT NULL,
    file_status              text        NOT NULL,
    has_macros               boolean     NOT NULL,
    previous_file_id         uuid,
    derived_from_file_id     uuid,
    derivation_kind          text,
    scan_engine_version      text        NOT NULL,
    scan_definitions_version text        NOT NULL,
    threat_name              text,
    is_released_by_exception boolean     NOT NULL,
    migration_batch_id       uuid,
    stored_at                timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    version                  bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_stored_files PRIMARY KEY (stored_file_id),
    CONSTRAINT uk_stored_files_object_key UNIQUE (object_key),
    CONSTRAINT uk_stored_files_previous_file_id UNIQUE (previous_file_id),
    CONSTRAINT fk_stored_files_previous_file_id FOREIGN KEY (previous_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stored_files_derived_from_file_id FOREIGN KEY (derived_from_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stored_files_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_stored_files_byte_count CHECK (byte_count >= 0),
    CONSTRAINT ck_stored_files_object_key CHECK (object_key ~ '^[a-z0-9-]+/(staging/[a-z_]+|files/[0-9]{4}/[0-9]{2}|quarantine)/[0-9a-f-]{36}$'),
    CONSTRAINT ck_stored_files_media_type_code CHECK (media_type_code ~ '^[a-z]+/[a-z0-9.+-]+$' AND char_length(media_type_code) <= 127),
    CONSTRAINT ck_stored_files_uploader_kind CHECK (uploader_kind IN ('USER', 'ONE_TIME_LINK', 'PLATFORM_MODULE')),
    CONSTRAINT ck_stored_files_uploader_key CHECK (uploader_key <> '' AND char_length(uploader_key) <= 64),
    CONSTRAINT ck_stored_files_file_status CHECK (file_status IN ('AVAILABLE', 'QUARANTINED', 'EXPIRED_UNBOUND', 'DISPOSED')),
    CONSTRAINT ck_stored_files_derivation_kind CHECK (derivation_kind IN ('SIGNING', 'FORMAT_CONVERSION')),
    CONSTRAINT ck_stored_files_derivation_pair CHECK ((derived_from_file_id IS NULL) = (derivation_kind IS NULL)),
    CONSTRAINT ck_stored_files_one_lineage CHECK (previous_file_id IS NULL OR derived_from_file_id IS NULL),
    CONSTRAINT ck_stored_files_not_own_lineage CHECK (previous_file_id <> stored_file_id AND derived_from_file_id <> stored_file_id),
    CONSTRAINT ck_stored_files_threat_name CHECK ((threat_name IS NOT NULL) = (file_status = 'QUARANTINED' OR is_released_by_exception)
                                                  AND (threat_name IS NULL OR (threat_name <> '' AND char_length(threat_name) <= 255))),
    CONSTRAINT ck_stored_files_quarantine_key CHECK ((file_status = 'QUARANTINED') = (object_key LIKE '%/quarantine/%'))
);
CREATE INDEX ix_stored_files_derived_from_file_id ON platform_file.stored_files (derived_from_file_id);
CREATE INDEX ix_stored_files_content_hash ON platform_file.stored_files (content_hash);
CREATE INDEX ix_stored_files_uploader_unbound ON platform_file.stored_files (uploader_kind, uploader_key, stored_at) WHERE file_status IN ('AVAILABLE', 'QUARANTINED');
COMMENT ON TABLE platform_file.stored_files IS '存储文件：一份不可变内容及其哈希；判定后才建行（ADR-0070），加密与不可用不建行。粒度：每个文件一行';
COMMENT ON COLUMN platform_file.stored_files.object_key IS '对象键：<环境前缀>/staging|files|quarantine/…/<文件 ID>，不含文件名、单据号、人名；关联时由临时区键改为正式键，此外不可改';
COMMENT ON COLUMN platform_file.stored_files.content_hash IS '内容哈希：SHA-256 小写十六进制 64 位';
COMMENT ON COLUMN platform_file.stored_files.byte_count IS '字节数';
COMMENT ON COLUMN platform_file.stored_files.media_type_code IS '媒体类型：按内容判定并登记，白名单在配置';
COMMENT ON COLUMN platform_file.stored_files.uploader_kind IS '上传主体类型';
COMMENT ON COLUMN platform_file.stored_files.uploader_key IS '上传主体标识：用户 ID、一次性链接 ID 或平台模块 id，按上传主体类型解读';
COMMENT ON COLUMN platform_file.stored_files.file_status IS '文件状态：只经领域方法改';
COMMENT ON COLUMN platform_file.stored_files.has_macros IS '含宏：按扩展名或内容判定，判定失败按含宏（ADR-0070 决定 5）';
COMMENT ON COLUMN platform_file.stored_files.previous_file_id IS '上一版本：为空表示首版；唯一约束保证一个文件至多一个后继';
COMMENT ON COLUMN platform_file.stored_files.derived_from_file_id IS '派生自：为空表示非派生件；派生不占版本链';
COMMENT ON COLUMN platform_file.stored_files.derivation_kind IS '派生方式：为空表示非派生件';
COMMENT ON COLUMN platform_file.stored_files.scan_engine_version IS '检测引擎版本';
COMMENT ON COLUMN platform_file.stored_files.scan_definitions_version IS '病毒库版本';
COMMENT ON COLUMN platform_file.stored_files.threat_name IS '病毒名：为空表示引擎未检出；已隔离与按例外放行的必填';
COMMENT ON COLUMN platform_file.stored_files.is_released_by_exception IS '是否按例外放行：引擎检出但内容哈希在误报例外清单（ADR-0070 决定 6 第 3 条）';
COMMENT ON COLUMN platform_file.stored_files.migration_batch_id IS '迁入批次 ID：为空表示原生上传；批次登记在 platform_integration，不建跨 schema 外键';
COMMENT ON COLUMN platform_file.stored_files.stored_at IS '写入时间';
COMMENT ON COLUMN platform_file.stored_files.updated_at IS '更新时间';
COMMENT ON COLUMN platform_file.stored_files.version IS '版本号（乐观锁）';

-- 内容列、上传主体、两个引用列、检测结论拒绝 UPDATE；对象键只允许 staging → files 一次；禁 DELETE / TRUNCATE。
CREATE FUNCTION platform_file.guard_stored_file_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_file.stored_files: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.stored_file_id IS DISTINCT FROM OLD.stored_file_id OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
            OR NEW.byte_count IS DISTINCT FROM OLD.byte_count OR NEW.media_type_code IS DISTINCT FROM OLD.media_type_code
            OR NEW.uploader_kind IS DISTINCT FROM OLD.uploader_kind OR NEW.uploader_key IS DISTINCT FROM OLD.uploader_key
            OR NEW.previous_file_id IS DISTINCT FROM OLD.previous_file_id OR NEW.derived_from_file_id IS DISTINCT FROM OLD.derived_from_file_id
            OR NEW.derivation_kind IS DISTINCT FROM OLD.derivation_kind OR NEW.has_macros IS DISTINCT FROM OLD.has_macros
            OR NEW.scan_engine_version IS DISTINCT FROM OLD.scan_engine_version
            OR NEW.scan_definitions_version IS DISTINCT FROM OLD.scan_definitions_version
            OR NEW.threat_name IS DISTINCT FROM OLD.threat_name OR NEW.is_released_by_exception IS DISTINCT FROM OLD.is_released_by_exception
            OR NEW.migration_batch_id IS DISTINCT FROM OLD.migration_batch_id OR NEW.stored_at IS DISTINCT FROM OLD.stored_at THEN
        RAISE EXCEPTION 'platform_file.stored_files: content, lineage and scan columns are immutable' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.object_key IS DISTINCT FROM OLD.object_key
            AND NOT (OLD.object_key LIKE '%/staging/%' AND NEW.object_key LIKE '%/files/%') THEN
        RAISE EXCEPTION 'platform_file.stored_files: object_key moves from staging to files once only' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_stored_files_guard BEFORE UPDATE OR DELETE ON platform_file.stored_files
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_stored_file_change();
CREATE TRIGGER tg_stored_files_guard_truncate BEFORE TRUNCATE ON platform_file.stored_files
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.guard_stored_file_change();

-- ── 2. 原文件名（子表，受控单次更新）────────────────────────────────
CREATE TABLE platform_file.stored_file_names (
    stored_file_id   uuid        NOT NULL,
    original_name    text        NOT NULL,
    is_deidentified  boolean     NOT NULL,
    deidentified_at  timestamptz,
    CONSTRAINT pk_stored_file_names PRIMARY KEY (stored_file_id),
    CONSTRAINT fk_stored_file_names_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stored_file_names_original_name CHECK (original_name <> '' AND char_length(original_name) <= 255),
    CONSTRAINT ck_stored_file_names_deidentified CHECK (is_deidentified = (deidentified_at IS NOT NULL))
);
COMMENT ON TABLE platform_file.stored_file_names IS '原文件名：与内容分列，使内容不可改与到期去标识不冲突（ADR-0060 决定 3）。粒度：每个文件一行';
COMMENT ON COLUMN platform_file.stored_file_names.original_name IS '原文件名：用途为下载时还原文件名；INTERNAL + PI';
COMMENT ON COLUMN platform_file.stored_file_names.is_deidentified IS '是否已去标识';
COMMENT ON COLUMN platform_file.stored_file_names.deidentified_at IS '去标识时间：为空表示未去标识';

CREATE FUNCTION platform_file.guard_stored_file_name_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_file.stored_file_names: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.is_deidentified OR NOT NEW.is_deidentified OR NEW.stored_file_id IS DISTINCT FROM OLD.stored_file_id THEN
        RAISE EXCEPTION 'platform_file.stored_file_names: only one de-identification is allowed' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_stored_file_names_guard BEFORE UPDATE OR DELETE ON platform_file.stored_file_names
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_stored_file_name_change();
CREATE TRIGGER tg_stored_file_names_guard_truncate BEFORE TRUNCATE ON platform_file.stored_file_names
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.guard_stored_file_name_change();

-- ── 3. 文件关联（只追加）──────────────────────────────────────────
CREATE TABLE platform_file.file_bindings (
    file_binding_id     uuid        NOT NULL,
    stored_file_id      uuid        NOT NULL,
    document_type       text        NOT NULL,
    document_id         uuid        NOT NULL,
    access_level        text        NOT NULL,
    personal_info_mark  text        NOT NULL,
    bound_by_kind       text        NOT NULL,
    bound_by_key        text        NOT NULL,
    bound_at            timestamptz NOT NULL,
    CONSTRAINT pk_file_bindings PRIMARY KEY (file_binding_id),
    CONSTRAINT uk_file_bindings_stored_file_id_document UNIQUE (stored_file_id, document_type, document_id),
    CONSTRAINT fk_file_bindings_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_bindings_document_type CHECK (document_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_file_bindings_access_level CHECK (access_level IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED')),
    CONSTRAINT ck_file_bindings_personal_info_mark CHECK (personal_info_mark IN ('NONE', 'PI', 'SPI')),
    CONSTRAINT ck_file_bindings_bound_by_kind CHECK (bound_by_kind IN ('USER', 'ONE_TIME_LINK', 'PLATFORM_MODULE')),
    CONSTRAINT ck_file_bindings_bound_by_key CHECK (bound_by_key <> '' AND char_length(bound_by_key) <= 64)
);
CREATE INDEX ix_file_bindings_document ON platform_file.file_bindings (document_type, document_id);
COMMENT ON TABLE platform_file.file_bindings IS '文件关联：所属模块把文件挂到单据，同时给出分级；只追加。一个文件可关联多张单据。粒度：每对（文件，单据）一行';
-- 列注释与 append-only 触发器同 numbering.legacy_numbers 写法（略）。

-- ── 4. 封存登记（只追加）──────────────────────────────────────────
CREATE TABLE platform_file.file_holds (
    file_hold_id               uuid        NOT NULL,
    stored_file_id             uuid        NOT NULL,
    initiator_document_type    text        NOT NULL,
    initiator_document_id      uuid        NOT NULL,
    held_by_kind               text        NOT NULL,
    held_by_key                text        NOT NULL,
    hold_reason                text        NOT NULL,
    held_at                    timestamptz NOT NULL,
    CONSTRAINT pk_file_holds PRIMARY KEY (file_hold_id),
    CONSTRAINT uk_file_holds_stored_file_id_initiator UNIQUE (stored_file_id, initiator_document_type, initiator_document_id),
    CONSTRAINT fk_file_holds_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_holds_initiator_document_type CHECK (initiator_document_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_file_holds_held_by_kind CHECK (held_by_kind IN ('USER', 'ONE_TIME_LINK', 'PLATFORM_MODULE')),
    CONSTRAINT ck_file_holds_held_by_key CHECK (held_by_key <> '' AND char_length(held_by_key) <= 64),
    CONSTRAINT ck_file_holds_hold_reason CHECK (hold_reason <> '' AND char_length(hold_reason) <= 500)
);
COMMENT ON TABLE platform_file.file_holds IS '封存登记：业务聚合封存时登记，只追加、不可撤销；保存期内拒绝处置。粒度：每对（文件，发起封存的单据）一行';

-- ── 5. 保存期限表（配置型版本化）──────────────────────────────────
CREATE TABLE platform_file.preservation_category_versions (
    preservation_category_version_id uuid        NOT NULL,
    category_code                    text        NOT NULL,
    category_name                    text        NOT NULL,
    default_years_count              integer,
    personal_info_years_count        integer,
    start_basis_text                 text        NOT NULL,
    effective_from_date              date        NOT NULL,
    confirmation_status              text        NOT NULL,
    decision_ref_key                 text        NOT NULL,
    created_by                       uuid        NOT NULL,
    confirmed_by                     uuid,
    created_at                       timestamptz NOT NULL,
    confirmed_at                     timestamptz,
    CONSTRAINT pk_preservation_category_versions PRIMARY KEY (preservation_category_version_id),
    CONSTRAINT uk_preservation_category_versions_code_effective UNIQUE (category_code, effective_from_date),
    CONSTRAINT ck_preservation_category_versions_category_code CHECK (category_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_preservation_category_versions_years CHECK (default_years_count IS NULL OR default_years_count >= 1),
    CONSTRAINT ck_preservation_category_versions_pi_years CHECK (personal_info_years_count IS NULL OR personal_info_years_count >= 1),
    CONSTRAINT ck_preservation_category_versions_confirmation_status CHECK (confirmation_status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT ck_preservation_category_versions_confirmed CHECK ((confirmation_status = 'CONFIRMED') = (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
    CONSTRAINT ck_preservation_category_versions_four_eyes CHECK (confirmed_by IS NULL OR confirmed_by <> created_by)
);
COMMENT ON TABLE platform_file.preservation_category_versions IS '保存期限表：配置型版本化，只追加；「待确认」版本不得作删除依据（ADR-0061）。粒度：每个类别每个生效日一行';
COMMENT ON COLUMN platform_file.preservation_category_versions.default_years_count IS '默认年限：为空表示该类别年限未定，按不处置保存（ADR-0061 决定 4）';
COMMENT ON COLUMN platform_file.preservation_category_versions.personal_info_years_count IS '个人信息年限：为空表示同默认年限';
COMMENT ON COLUMN platform_file.preservation_category_versions.start_basis_text IS '起算依据：说明文字，平台不枚举业务事件（ADR-0066 决定 3 第 4 条）';

-- ── 6. 文件保存期（只追加，新行取代旧行）──────────────────────────
CREATE TABLE platform_file.file_preservations (
    file_preservation_id             uuid        NOT NULL,
    stored_file_id                   uuid        NOT NULL,
    preservation_category_version_id uuid        NOT NULL,
    anchor_document_type             text        NOT NULL,
    anchor_document_id               uuid        NOT NULL,
    start_date                       date,
    agreed_years_count               integer,
    expiry_date                      date,
    recorded_at                      timestamptz NOT NULL,
    CONSTRAINT pk_file_preservations PRIMARY KEY (file_preservation_id),
    CONSTRAINT fk_file_preservations_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT fk_file_preservations_category_version FOREIGN KEY (preservation_category_version_id)
        REFERENCES platform_file.preservation_category_versions (preservation_category_version_id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_preservations_anchor_document_type CHECK (anchor_document_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_file_preservations_agreed_years CHECK (agreed_years_count IS NULL OR agreed_years_count >= 1),
    CONSTRAINT ck_file_preservations_expiry CHECK (expiry_date IS NULL OR (start_date IS NOT NULL AND expiry_date > start_date))
);
CREATE INDEX ix_file_preservations_stored_file_id ON platform_file.file_preservations (stored_file_id, recorded_at DESC);
CREATE INDEX ix_file_preservations_category_version ON platform_file.file_preservations (preservation_category_version_id);
CREATE INDEX ix_file_preservations_anchor ON platform_file.file_preservations (anchor_document_type, anchor_document_id);
CREATE INDEX ix_file_preservations_expiry_date ON platform_file.file_preservations (expiry_date) WHERE expiry_date IS NOT NULL;
COMMENT ON TABLE platform_file.file_preservations IS '文件保存期：每次关联、写起算日、写约定年限、年限表换版都追加一行，最新一行为当前；一个文件一个类别与起算单据。粒度：每次重算一行';
COMMENT ON COLUMN platform_file.file_preservations.start_date IS '起算日：为空表示起算事件未发生，不到期';
COMMENT ON COLUMN platform_file.file_preservations.agreed_years_count IS '约定保存年限：为空表示合同无约定';
COMMENT ON COLUMN platform_file.file_preservations.expiry_date IS '到期日：起算日 + max(默认, 约定)；为空表示不到期（起算日为空或类别年限未定）';

-- ── 7. 误报判定与例外清单（ADR-0070 决定 6 第 3 条）────────────────
CREATE TABLE platform_file.false_positive_judgments (
    false_positive_judgment_id uuid        NOT NULL,
    stored_file_id             uuid        NOT NULL,
    content_hash               text        NOT NULL,
    judgment_status            text        NOT NULL,
    judgment_reason            text        NOT NULL,
    judged_by                  uuid        NOT NULL,
    judged_at                  timestamptz NOT NULL,
    review_reason              text,
    reviewed_by                uuid,
    reviewed_at                timestamptz,
    version                    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_false_positive_judgments PRIMARY KEY (false_positive_judgment_id),
    CONSTRAINT fk_false_positive_judgments_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_false_positive_judgments_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_false_positive_judgments_judgment_status CHECK (judgment_status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_false_positive_judgments_reviewed CHECK ((judgment_status = 'PENDING_REVIEW') = (reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL)),
    CONSTRAINT ck_false_positive_judgments_two_people CHECK (reviewed_by IS NULL OR reviewed_by <> judged_by),
    CONSTRAINT ck_false_positive_judgments_reasons CHECK (judgment_reason <> '' AND char_length(judgment_reason) <= 1000
                                                          AND (review_reason IS NULL OR (review_reason <> '' AND char_length(review_reason) <= 1000)))
);
CREATE UNIQUE INDEX uk_false_positive_judgments_pending ON platform_file.false_positive_judgments (stored_file_id) WHERE judgment_status = 'PENDING_REVIEW';
COMMENT ON TABLE platform_file.false_positive_judgments IS '误报判定：安全角色判定、第二人复核（不得同一人），复核后状态不再改。粒度：每次判定一行';

CREATE TABLE platform_file.scan_exceptions (
    content_hash               text        NOT NULL,
    false_positive_judgment_id uuid        NOT NULL,
    added_at                   timestamptz NOT NULL,
    CONSTRAINT pk_scan_exceptions PRIMARY KEY (content_hash),
    CONSTRAINT uk_scan_exceptions_judgment UNIQUE (false_positive_judgment_id),
    CONSTRAINT fk_scan_exceptions_judgment FOREIGN KEY (false_positive_judgment_id)
        REFERENCES platform_file.false_positive_judgments (false_positive_judgment_id) ON DELETE RESTRICT,
    CONSTRAINT ck_scan_exceptions_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$')
);
COMMENT ON TABLE platform_file.scan_exceptions IS '误报例外清单：只追加；引擎检出且内容哈希在清单内即按通过并审计「按例外放行」，只豁免该内容。粒度：每个内容哈希一行';

-- ── 8. 隔离内容读取审批（ADR-0070 决定 6 第 2 条、决定 7 第 2 条）──
CREATE TABLE platform_file.quarantine_read_requests (
    quarantine_read_request_id uuid        NOT NULL,
    stored_file_id             uuid        NOT NULL,
    request_status             text        NOT NULL,
    request_reason             text        NOT NULL,
    requested_by               uuid        NOT NULL,
    requested_at               timestamptz NOT NULL,
    decided_by                 uuid,
    decided_at                 timestamptz,
    read_at                    timestamptz,
    version                    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_quarantine_read_requests PRIMARY KEY (quarantine_read_request_id),
    CONSTRAINT fk_quarantine_read_requests_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_quarantine_read_requests_request_status CHECK (request_status IN ('PENDING', 'APPROVED', 'REJECTED', 'READ')),
    CONSTRAINT ck_quarantine_read_requests_two_people CHECK (decided_by IS NULL OR decided_by <> requested_by),
    CONSTRAINT ck_quarantine_read_requests_decided CHECK ((request_status = 'PENDING') = (decided_by IS NULL AND decided_at IS NULL)),
    CONSTRAINT ck_quarantine_read_requests_read CHECK ((request_status = 'READ') = (read_at IS NOT NULL)),
    CONSTRAINT ck_quarantine_read_requests_request_reason CHECK (request_reason <> '' AND char_length(request_reason) <= 1000)
);
CREATE INDEX ix_quarantine_read_requests_stored_file_id ON platform_file.quarantine_read_requests (stored_file_id);
COMMENT ON TABLE platform_file.quarantine_read_requests IS '隔离内容读取申请：安全角色申请、另一人审批，批准后读一次即结；全程审计。粒度：每次申请一行';

-- ── 待 one-data 签发的问题 ─────────────────────────────────────────
-- Q1 单据引用形状：协调口径写「document_type_id 形状」，但 REG-1 分支在 numbering 之外（如 platform_approval.approval_requests）用的是
--    document_type text + 格式 CHECK，document_type_id 只在 numbering 内部作外键。本稿按后者；如须存 document_type_id uuid，请定。
-- Q2 media_type_code：类别词用 _code（格式 CHECK）而不是 _type（_type 要求 CHECK 列出取值，白名单在配置、会变）。
-- Q3 object_key：ADR-0060 交 one-glossary 定类别词；本稿用 _key。CHECK 写死键形状是否过严（环境前缀字符集待 one-ops）。
-- Q4 uploader_key / bound_by_key / held_by_key：主体标识跨类型（用户 uuid、链接 uuid、模块 id 文本），故用 _key text；或拆为 _user_id uuid + _module_code。
-- Q5 personal_info_mark CHECK 含 SPI（与 Java 枚举一致，NAMING §2.4）；U-14 定前由应用拒收。是否改为 DB 也拒。
-- Q6 file_preservations「最新一行为当前」的读法与 data-access-registry 登记（APPEND_ONLY + 当前行视图？）。
-- Q7 file_disposals（ADR-0061 处置记录）按 §8 首个到期前 90 天建，本稿不建。
-- Q8 隔离保存期取证延长：对象过期由 OSS 前缀生命周期执行，单对象延长需对象标签或复制到另一前缀，待 one-ops；本稿不建延长表。
-- Q9 加密拒收、不可用只写审计不建行（ADR-0070），故 file_status 无对应取值。
-- Q10 stored_files.version 与 updated_at：只有 object_key（关联时）与 file_status 会变，保留乐观锁。
