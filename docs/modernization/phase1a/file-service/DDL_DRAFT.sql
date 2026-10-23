-- 文件服务 platform_file 表结构草案第 4 版（IMP-1021-COO-02 单元 3），交 one-data 复核签发；签发前不进 db/migration。
-- 作者 one-platform-file，2026-10-21。依据：ADR-0060 决定 3–5、ADR-0061 决定 7、ADR-0070 决定 2、6、ADR-0058、契约 PR #502。
-- 第 1 版审：claude/one-data-handoff-1 @a282e2c3，第二轮 @e2184aba（data/reviews/IMP-1021-COO-02-U3-file-service.md）；逐条处理见文末「审改对照」。
-- current_file_preservations 用 security_invoker，须 PostgreSQL 15+，迁移时核实例版本。
-- 单据引用：document_type text + 格式 CHECK + document_id uuid，不建跨 schema 外键（Q1 已通过）。
-- 跨 schema 主体（用户、一次性链接）只存 ID 不建外键；平台模块存模块代码。
-- 时间列一律由应用传注入时钟，DDL 无 now()；version 初值 0 沿用基线惯例。

CREATE SCHEMA IF NOT EXISTS platform_file;

-- ═══ 0. 状态转换（ADR-0058；STATE_TRANSITIONS_DESIGN v2 第 1 节模板，<schema> = platform_file）════════
CREATE TABLE platform_file.state_transitions (
    id                      uuid        NOT NULL,
    aggregate_type          text        NOT NULL,
    aggregate_id            uuid        NOT NULL,
    sequence_no             bigint      NOT NULL,
    from_state              text,
    to_state                text        NOT NULL,
    occurred_on             date        NOT NULL,
    recorded_at             timestamptz NOT NULL,
    legal_entity_id         uuid,
    actor_type              text        NOT NULL,
    actor_id                uuid        NOT NULL,
    cause_kind              text        NOT NULL,
    command_id              uuid,
    event_id                uuid,
    correlation_id          uuid        NOT NULL,
    reverses_transition_id  uuid,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT pk_state_transitions PRIMARY KEY (id),
    CONSTRAINT uk_state_transitions_aggregate_id_sequence_no UNIQUE (aggregate_id, sequence_no),
    CONSTRAINT uk_state_transitions_id_aggregate_id UNIQUE (id, aggregate_id),
    CONSTRAINT fk_state_transitions_reverses_transition_id FOREIGN KEY (reverses_transition_id, aggregate_id)
        REFERENCES platform_file.state_transitions (id, aggregate_id) ON DELETE RESTRICT,
    CONSTRAINT ck_state_transitions_aggregate_type CHECK (aggregate_type ~ '^[A-Z][A-Za-z0-9]{0,99}$'),
    CONSTRAINT ck_state_transitions_from_state CHECK (from_state IS NULL OR from_state ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_state_transitions_to_state CHECK (to_state ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_state_transitions_sequence_no CHECK (sequence_no >= 0),
    CONSTRAINT ck_state_transitions_states_differ CHECK (from_state IS DISTINCT FROM to_state),
    CONSTRAINT ck_state_transitions_first CHECK (from_state IS NOT NULL OR cause_kind = 'MIGRATION' OR sequence_no <= 1),
    CONSTRAINT ck_state_transitions_actor_type CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_state_transitions_cause_kind CHECK (cause_kind IN ('COMMAND', 'REVERSAL', 'CORRECTION', 'MIGRATION', 'SYSTEM_JOB')),
    CONSTRAINT ck_state_transitions_trigger CHECK (num_nonnulls(command_id, event_id) <= 1
        AND (cause_kind <> 'COMMAND' OR command_id IS NOT NULL)
        AND (cause_kind <> 'SYSTEM_JOB' OR command_id IS NULL)
        AND (cause_kind <> 'MIGRATION' OR num_nonnulls(command_id, event_id) = 0)),
    CONSTRAINT ck_state_transitions_reversal CHECK ((cause_kind IN ('REVERSAL', 'CORRECTION')) = (reverses_transition_id IS NOT NULL)),
    CONSTRAINT ck_state_transitions_migration CHECK (cause_kind <> 'MIGRATION' OR from_state IS NULL),
    CONSTRAINT ck_state_transitions_times CHECK (created_at = recorded_at AND updated_at = recorded_at)
);
CREATE INDEX ix_state_transitions_legal_entity_id_occurred_on ON platform_file.state_transitions (legal_entity_id, occurred_on) WHERE legal_entity_id IS NOT NULL;
CREATE INDEX ix_state_transitions_recorded_at ON platform_file.state_transitions (recorded_at, id);
CREATE FUNCTION platform_file.refuse_transition_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- 清理只有一条通道：schema 属主角色经 SECURITY DEFINER 函数 platform_file.purge_expired_state_transitions 删除到期的整个聚合；其他角色都被拒。
    IF TG_OP = 'DELETE' AND current_user = (SELECT pg_get_userbyid(c.relowner) FROM pg_class c WHERE c.oid = TG_RELID) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'state transitions are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_state_transitions_append_only BEFORE UPDATE OR DELETE ON platform_file.state_transitions
    FOR EACH ROW EXECUTE FUNCTION platform_file.refuse_transition_rewrite();
CREATE TRIGGER tg_state_transitions_append_only_truncate BEFORE TRUNCATE ON platform_file.state_transitions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.refuse_transition_rewrite();
COMMENT ON TABLE platform_file.state_transitions IS '状态转换：本 schema 各聚合每次状态变更一行，只追加，回退也正向记（ADR-0058）。粒度：每次状态变更一行';
COMMENT ON COLUMN platform_file.state_transitions.id IS '状态转换 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.state_transitions.aggregate_type IS '聚合类型：StoredFile、FalsePositiveJudgment、QuarantineReadRequest、PreservationCategoryVersion';
COMMENT ON COLUMN platform_file.state_transitions.aggregate_id IS '聚合 ID';
COMMENT ON COLUMN platform_file.state_transitions.sequence_no IS '序号：本次变更之后聚合行的 version，与聚合行同事务递增';
COMMENT ON COLUMN platform_file.state_transitions.from_state IS '原状态：为空表示新建或迁入的初始状态';
COMMENT ON COLUMN platform_file.state_transitions.to_state IS '新状态';
COMMENT ON COLUMN platform_file.state_transitions.occurred_on IS '业务日期（Asia/Shanghai）';
COMMENT ON COLUMN platform_file.state_transitions.recorded_at IS '记录时间：注入时钟';
COMMENT ON COLUMN platform_file.state_transitions.legal_entity_id IS '法人主体 ID：为空表示该聚合无法人归属（文件服务四类聚合均无，注册表登记 none）';
COMMENT ON COLUMN platform_file.state_transitions.actor_type IS '操作者类型：USER 用户 / SYSTEM 系统操作者（一次性链接持有人、平台模块调用、作业均按 system-actors.txt 登记的系统操作者）';
COMMENT ON COLUMN platform_file.state_transitions.actor_id IS '操作者 ID：用户 ID 或系统操作者 UUIDv5';
COMMENT ON COLUMN platform_file.state_transitions.cause_kind IS '起因：COMMAND / REVERSAL / CORRECTION / MIGRATION / SYSTEM_JOB';
COMMENT ON COLUMN platform_file.state_transitions.command_id IS '命令 ID：为空表示不是命令触发';
COMMENT ON COLUMN platform_file.state_transitions.event_id IS '事件 ID：为空表示不是事件触发';
COMMENT ON COLUMN platform_file.state_transitions.correlation_id IS '关联 ID：同一请求或作业的一串变更共用';
COMMENT ON COLUMN platform_file.state_transitions.reverses_transition_id IS '抵消的转换 ID：为空表示不是冲销或更正';
COMMENT ON COLUMN platform_file.state_transitions.created_at IS '创建时间（恒等于记录时间）';
COMMENT ON COLUMN platform_file.state_transitions.updated_at IS '更新时间（恒等于记录时间）';

-- 合法流转清单（应用写转换行；各表触发器按同一清单拒绝非法流转；终态后状态不可改）：
--   聚合类型                      状态列                                            from → to
--   StoredFile                    stored_files.file_status                          ∅ → AVAILABLE；∅ → QUARANTINED；AVAILABLE → EXPIRED_UNBOUND
--                                                                                  （终态 QUARANTINED、EXPIRED_UNBOUND；DISPOSED 与 file_disposals 同批加 AVAILABLE → DISPOSED）
--   FalsePositiveJudgment         false_positive_judgments.judgment_status          ∅ → PENDING_REVIEW；PENDING_REVIEW → CONFIRMED；PENDING_REVIEW → REJECTED
--   QuarantineReadRequest         quarantine_read_requests.request_status           ∅ → PENDING；PENDING → APPROVED；PENDING → REJECTED；APPROVED → READ
--   PreservationCategoryVersion   preservation_category_versions.confirmation_status ∅ → PENDING；PENDING → CONFIRMED
-- 系统操作者（system-actors.txt 新增三行，UUIDv5 按目录命名空间算）：
--   platform.file.one-time-link-holder | af5fd2b9-1c66-5f5a-9803-ffe3839894a6 | platform.file | 一次性链接持有人上传与提交关联的操作者
--   platform.file.platform-module-call | 9dd1c4f7-f0ad-54c9-8190-62e09e87ee8f | platform.file | 平台模块（签章服务等）调用写入、关联、封存的操作者
--   platform.file.staging.expire       | 18a840ca-d8f0-5830-8e12-1bcd5ecad2f3 | platform.file | 作业：临时区到期未关联的文件置「未关联已过期」

-- ═══ 1. 存储文件（根）════════════════════════════════════════════════
CREATE TABLE platform_file.stored_files (
    stored_file_id           uuid        NOT NULL,
    object_key               text        NOT NULL,
    content_hash             text        NOT NULL,
    byte_count               bigint      NOT NULL,
    media_type_code          text        NOT NULL,
    uploader_kind            text        NOT NULL,
    uploader_user_id         uuid,
    uploader_link_id         uuid,
    uploader_module_code     text,
    file_status              text        NOT NULL,
    has_macros               boolean     NOT NULL,
    previous_file_id         uuid,
    derived_from_file_id     uuid,
    derivation_kind          text,
    scan_engine_version      text        NOT NULL,
    scan_definitions_version text        NOT NULL,
    threat_name              text,
    is_released_by_exception boolean     NOT NULL,
    stored_at                timestamptz NOT NULL,
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    version                  bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_stored_files PRIMARY KEY (stored_file_id),
    CONSTRAINT uk_stored_files_object_key UNIQUE (object_key),
    CONSTRAINT uk_stored_files_stored_file_id_content_hash UNIQUE (stored_file_id, content_hash),
    CONSTRAINT uk_stored_files_previous_file_id UNIQUE (previous_file_id),
    CONSTRAINT fk_stored_files_previous_file_id FOREIGN KEY (previous_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT fk_stored_files_derived_from_file_id FOREIGN KEY (derived_from_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stored_files_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_stored_files_byte_count CHECK (byte_count >= 0),
    CONSTRAINT ck_stored_files_object_key CHECK (object_key ~ '^[a-z0-9-]+/(files/[0-9]{4}/(0[1-9]|1[0-2])|quarantine)/[0-9a-f-]{36}$'
                                                 AND object_key LIKE '%/' || stored_file_id::text),
    CONSTRAINT ck_stored_files_media_type_code CHECK (media_type_code ~ '^[a-z]+/[a-z0-9.+-]+$' AND char_length(media_type_code) <= 127),
    CONSTRAINT ck_stored_files_uploader_kind CHECK (uploader_kind IN ('USER', 'ONE_TIME_LINK', 'PLATFORM_MODULE')),
    CONSTRAINT ck_stored_files_uploader_module_code CHECK (uploader_module_code IS NULL OR uploader_module_code ~ '^[a-z]+\.[a-z]+$'),
    CONSTRAINT ck_stored_files_uploader_one_subject CHECK (
        num_nonnulls(uploader_user_id, uploader_link_id, uploader_module_code) = 1
        AND (uploader_kind <> 'USER' OR uploader_user_id IS NOT NULL)
        AND (uploader_kind <> 'ONE_TIME_LINK' OR uploader_link_id IS NOT NULL)
        AND (uploader_kind <> 'PLATFORM_MODULE' OR uploader_module_code IS NOT NULL)),
    CONSTRAINT ck_stored_files_file_status CHECK (file_status IN ('AVAILABLE', 'QUARANTINED', 'EXPIRED_UNBOUND')),
    CONSTRAINT ck_stored_files_derivation_kind CHECK (derivation_kind IN ('SIGNING', 'FORMAT_CONVERSION')),
    CONSTRAINT ck_stored_files_derivation_pair CHECK ((derived_from_file_id IS NULL) = (derivation_kind IS NULL)),
    CONSTRAINT ck_stored_files_one_lineage CHECK (previous_file_id IS NULL OR derived_from_file_id IS NULL),
    CONSTRAINT ck_stored_files_not_own_lineage CHECK (previous_file_id <> stored_file_id AND derived_from_file_id <> stored_file_id),
    CONSTRAINT ck_stored_files_scan_versions CHECK (scan_engine_version <> '' AND char_length(scan_engine_version) <= 64
                                                    AND scan_definitions_version <> '' AND char_length(scan_definitions_version) <= 64),
    CONSTRAINT ck_stored_files_threat_name CHECK ((threat_name IS NOT NULL) = (file_status = 'QUARANTINED' OR is_released_by_exception)
                                                  AND (threat_name IS NULL OR (threat_name <> '' AND char_length(threat_name) <= 255))),
    CONSTRAINT ck_stored_files_quarantine_key CHECK ((file_status = 'QUARANTINED') = (object_key LIKE '%/quarantine/%'))
);
CREATE INDEX ix_stored_files_derived_from_file_id ON platform_file.stored_files (derived_from_file_id);
CREATE INDEX ix_stored_files_content_hash ON platform_file.stored_files (content_hash);
CREATE INDEX ix_stored_files_available_stored_at ON platform_file.stored_files (stored_at) WHERE file_status = 'AVAILABLE';  -- 临时区过期作业：可用且无关联
CREATE INDEX ix_stored_files_uploader_user_id ON platform_file.stored_files (uploader_user_id, stored_at) WHERE uploader_user_id IS NOT NULL;
COMMENT ON TABLE platform_file.stored_files IS '存储文件：一份不可变内容及其哈希；检测判定后才建行（ADR-0070），加密与不可用不建行。迁入文件的批次与老系统来源走 platform_integration.source_references。粒度：每个文件一行';
COMMENT ON COLUMN platform_file.stored_files.stored_file_id IS '存储文件 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.stored_files.object_key IS '对象键：建行即写定、不可改（ADR-0060 决定 3、ADR-0070 决定 2）。可用文件为正式键 <环境前缀>/files/<yyyy>/<mm>/<存储文件 ID>（关联时才复制到此），已隔离文件为 <环境前缀>/quarantine/<存储文件 ID>；临时区键 <环境前缀>/staging/<来源>/<存储文件 ID> 由 ID 与上传主体类型推出，不落列';
COMMENT ON COLUMN platform_file.stored_files.content_hash IS '内容哈希：SHA-256 小写十六进制 64 位，比对文件以它为准';
COMMENT ON COLUMN platform_file.stored_files.byte_count IS '字节数';
COMMENT ON COLUMN platform_file.stored_files.media_type_code IS '媒体类型：按内容判定并登记，白名单在配置';
COMMENT ON COLUMN platform_file.stored_files.uploader_kind IS '上传主体类型：USER 用户 / ONE_TIME_LINK 一次性链接 / PLATFORM_MODULE 平台模块；决定下面三列哪一列非空';
COMMENT ON COLUMN platform_file.stored_files.uploader_user_id IS '上传用户 ID：为空表示上传主体不是用户；用户在 platform_identity，不建外键';
COMMENT ON COLUMN platform_file.stored_files.uploader_link_id IS '上传链接 ID：为空表示上传主体不是一次性链接；链接在一次性链接访问模块，不建外键';
COMMENT ON COLUMN platform_file.stored_files.uploader_module_code IS '上传模块代码：为空表示上传主体不是平台模块；形如 platform.signing';
COMMENT ON COLUMN platform_file.stored_files.file_status IS '文件状态：AVAILABLE 可用 / QUARANTINED 已隔离 / EXPIRED_UNBOUND 未关联已过期；流转见 state_transitions，触发器拒绝非法流转';
COMMENT ON COLUMN platform_file.stored_files.has_macros IS '含宏：按扩展名或内容判定，判定失败按含宏（ADR-0070 决定 5）';
COMMENT ON COLUMN platform_file.stored_files.previous_file_id IS '上一版本：为空表示首版；唯一约束保证一个文件至多一个后继';
COMMENT ON COLUMN platform_file.stored_files.derived_from_file_id IS '派生自：为空表示非派生件；派生不占版本链';
COMMENT ON COLUMN platform_file.stored_files.derivation_kind IS '派生方式：SIGNING 签署 / FORMAT_CONVERSION 格式转换；为空表示非派生件';
COMMENT ON COLUMN platform_file.stored_files.scan_engine_version IS '检测引擎版本';
COMMENT ON COLUMN platform_file.stored_files.scan_definitions_version IS '病毒库版本';
COMMENT ON COLUMN platform_file.stored_files.threat_name IS '病毒名：为空表示引擎未检出；已隔离与按例外放行的必填';
COMMENT ON COLUMN platform_file.stored_files.is_released_by_exception IS '是否按例外放行：引擎检出但内容哈希在误报例外清单内（ADR-0070 决定 6 第 3 条）';
COMMENT ON COLUMN platform_file.stored_files.stored_at IS '写入时间';
COMMENT ON COLUMN platform_file.stored_files.created_at IS '创建时间（恒等于写入时间）';
COMMENT ON COLUMN platform_file.stored_files.updated_at IS '更新时间';
COMMENT ON COLUMN platform_file.stored_files.version IS '乐观锁版本';

-- 除 file_status（按流转清单）、updated_at、version 外全部不可改（含 object_key）；禁 DELETE / TRUNCATE。
CREATE FUNCTION platform_file.guard_stored_file_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_file.stored_files: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - ARRAY['file_status', 'updated_at', 'version'])
            IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['file_status', 'updated_at', 'version']) THEN
        RAISE EXCEPTION 'platform_file.stored_files: object key, content, subject, lineage and scan columns are immutable' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.file_status IS DISTINCT FROM OLD.file_status
            AND NOT (OLD.file_status = 'AVAILABLE' AND NEW.file_status = 'EXPIRED_UNBOUND') THEN
        RAISE EXCEPTION 'platform_file.stored_files: % -> % is not an allowed transition', OLD.file_status, NEW.file_status
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_stored_files_guard BEFORE UPDATE OR DELETE ON platform_file.stored_files
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_stored_file_change();
CREATE TRIGGER tg_stored_files_guard_truncate BEFORE TRUNCATE ON platform_file.stored_files
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.guard_stored_file_change();

-- ═══ 2. 原文件名（子表，受控单次更新）════════════════════════════════
CREATE TABLE platform_file.stored_file_names (
    stored_file_id   uuid        NOT NULL,
    original_name    text        NOT NULL,
    is_deidentified  boolean     NOT NULL,
    deidentified_at  timestamptz,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    CONSTRAINT pk_stored_file_names PRIMARY KEY (stored_file_id),
    CONSTRAINT fk_stored_file_names_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_stored_file_names_original_name CHECK (original_name <> '' AND char_length(original_name) <= 255),
    CONSTRAINT ck_stored_file_names_deidentified CHECK (is_deidentified = (deidentified_at IS NOT NULL)
                                                        AND is_deidentified = (original_name = '[已去标识]'))
);
COMMENT ON TABLE platform_file.stored_file_names IS '原文件名：与内容分列，使内容不可改与到期去标识不冲突（ADR-0060 决定 3）；去标识只一次、置固定占位值。粒度：每个文件一行';
COMMENT ON COLUMN platform_file.stored_file_names.stored_file_id IS '存储文件 ID';
COMMENT ON COLUMN platform_file.stored_file_names.original_name IS '原文件名：用途为下载时还原文件名；去标识后为固定值「[已去标识]」';
COMMENT ON COLUMN platform_file.stored_file_names.is_deidentified IS '是否已去标识';
COMMENT ON COLUMN platform_file.stored_file_names.deidentified_at IS '去标识时间：为空表示未去标识';
COMMENT ON COLUMN platform_file.stored_file_names.created_at IS '创建时间';
COMMENT ON COLUMN platform_file.stored_file_names.updated_at IS '更新时间：去标识时更新一次';

CREATE FUNCTION platform_file.guard_stored_file_name_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_file.stored_file_names: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.is_deidentified OR NOT NEW.is_deidentified OR NEW.original_name <> '[已去标识]'
            OR NEW.stored_file_id IS DISTINCT FROM OLD.stored_file_id OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'platform_file.stored_file_names: only one de-identification to the fixed placeholder is allowed' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_stored_file_names_guard BEFORE UPDATE OR DELETE ON platform_file.stored_file_names
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_stored_file_name_change();
CREATE TRIGGER tg_stored_file_names_guard_truncate BEFORE TRUNCATE ON platform_file.stored_file_names
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.guard_stored_file_name_change();

-- 只追加表共用的拒绝函数（形状同 main 上 refuse_version_rewrite）。
CREATE FUNCTION platform_file.refuse_append_only_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '%.% is append-only: % is not allowed', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP USING ERRCODE = 'restrict_violation';
END $$;

-- ═══ 3. 文件关联（只追加）════════════════════════════════════════════
CREATE TABLE platform_file.file_bindings (
    file_binding_id      uuid        NOT NULL,
    stored_file_id       uuid        NOT NULL,
    document_type        text        NOT NULL,
    document_id          uuid        NOT NULL,
    access_level         text        NOT NULL,
    personal_info_mark   text        NOT NULL,
    bound_by_kind        text        NOT NULL,
    bound_by_user_id     uuid,
    bound_by_link_id     uuid,
    bound_by_module_code text,
    bound_at             timestamptz NOT NULL,
    created_at           timestamptz NOT NULL,
    CONSTRAINT pk_file_bindings PRIMARY KEY (file_binding_id),
    CONSTRAINT uk_file_bindings_stored_file_id_document UNIQUE (stored_file_id, document_type, document_id),
    CONSTRAINT fk_file_bindings_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_bindings_document_type CHECK (document_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_file_bindings_access_level CHECK (access_level IN ('PUBLIC', 'INTERNAL', 'SENSITIVE', 'RESTRICTED')),
    CONSTRAINT ck_file_bindings_personal_info_mark CHECK (personal_info_mark IN ('NONE', 'PI')),
    CONSTRAINT ck_file_bindings_bound_by_kind CHECK (bound_by_kind IN ('USER', 'ONE_TIME_LINK', 'PLATFORM_MODULE')),
    CONSTRAINT ck_file_bindings_bound_by_module_code CHECK (bound_by_module_code IS NULL OR bound_by_module_code ~ '^[a-z]+\.[a-z]+$'),
    CONSTRAINT ck_file_bindings_bound_by_one_subject CHECK (
        num_nonnulls(bound_by_user_id, bound_by_link_id, bound_by_module_code) = 1
        AND (bound_by_kind <> 'USER' OR bound_by_user_id IS NOT NULL)
        AND (bound_by_kind <> 'ONE_TIME_LINK' OR bound_by_link_id IS NOT NULL)
        AND (bound_by_kind <> 'PLATFORM_MODULE' OR bound_by_module_code IS NOT NULL))
);
CREATE INDEX ix_file_bindings_document ON platform_file.file_bindings (document_type, document_id);
COMMENT ON TABLE platform_file.file_bindings IS '文件关联：所属模块把文件挂到单据并给出分级；只追加。一个文件可关联多张单据，各关联分级不同时下载取较严者。粒度：每对（文件，单据）一行';
COMMENT ON COLUMN platform_file.file_bindings.file_binding_id IS '文件关联 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.file_bindings.stored_file_id IS '存储文件 ID';
COMMENT ON COLUMN platform_file.file_bindings.document_type IS '单据类型码：是否已登记由调用方经编号模块校验，本 schema 不建外键（ADR-0066 决定 4）';
COMMENT ON COLUMN platform_file.file_bindings.document_id IS '单据 ID：与单据类型码合起来定位一张单据';
COMMENT ON COLUMN platform_file.file_bindings.access_level IS '访问级别：PUBLIC / INTERNAL / SENSITIVE / RESTRICTED（ADR-0043）';
COMMENT ON COLUMN platform_file.file_bindings.personal_info_mark IS '个人信息标记：NONE / PI；SPI 在 U-14 定前不接收，定后追加迁移加入';
COMMENT ON COLUMN platform_file.file_bindings.bound_by_kind IS '关联主体类型：决定下面三列哪一列非空';
COMMENT ON COLUMN platform_file.file_bindings.bound_by_user_id IS '关联用户 ID：为空表示关联主体不是用户';
COMMENT ON COLUMN platform_file.file_bindings.bound_by_link_id IS '关联链接 ID：为空表示关联主体不是一次性链接';
COMMENT ON COLUMN platform_file.file_bindings.bound_by_module_code IS '关联模块代码：为空表示关联主体不是平台模块';
COMMENT ON COLUMN platform_file.file_bindings.bound_at IS '关联时间';
COMMENT ON COLUMN platform_file.file_bindings.created_at IS '创建时间（恒等于关联时间）';
CREATE TRIGGER tg_file_bindings_append_only BEFORE UPDATE OR DELETE ON platform_file.file_bindings
    FOR EACH ROW EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();
CREATE TRIGGER tg_file_bindings_append_only_truncate BEFORE TRUNCATE ON platform_file.file_bindings
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();

-- ═══ 4. 封存登记（只追加）════════════════════════════════════════════
CREATE TABLE platform_file.file_holds (
    file_hold_id             uuid        NOT NULL,
    stored_file_id           uuid        NOT NULL,
    initiator_document_type  text        NOT NULL,
    initiator_document_id    uuid        NOT NULL,
    held_by_kind             text        NOT NULL,
    held_by_user_id          uuid,
    held_by_link_id          uuid,
    held_by_module_code      text,
    hold_reason              text        NOT NULL,
    held_at                  timestamptz NOT NULL,
    created_at               timestamptz NOT NULL,
    CONSTRAINT pk_file_holds PRIMARY KEY (file_hold_id),
    CONSTRAINT uk_file_holds_stored_file_id_initiator UNIQUE (stored_file_id, initiator_document_type, initiator_document_id),
    CONSTRAINT fk_file_holds_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_holds_initiator_document_type CHECK (initiator_document_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_file_holds_held_by_kind CHECK (held_by_kind IN ('USER', 'ONE_TIME_LINK', 'PLATFORM_MODULE')),
    CONSTRAINT ck_file_holds_held_by_module_code CHECK (held_by_module_code IS NULL OR held_by_module_code ~ '^[a-z]+\.[a-z]+$'),
    CONSTRAINT ck_file_holds_held_by_one_subject CHECK (
        num_nonnulls(held_by_user_id, held_by_link_id, held_by_module_code) = 1
        AND (held_by_kind <> 'USER' OR held_by_user_id IS NOT NULL)
        AND (held_by_kind <> 'ONE_TIME_LINK' OR held_by_link_id IS NOT NULL)
        AND (held_by_kind <> 'PLATFORM_MODULE' OR held_by_module_code IS NOT NULL)),
    CONSTRAINT ck_file_holds_hold_reason CHECK (hold_reason <> '' AND char_length(hold_reason) <= 500)
);
COMMENT ON TABLE platform_file.file_holds IS '封存登记：业务聚合封存时登记，只追加、不可撤销；保存期内拒绝处置。粒度：每对（文件，发起封存的单据）一行';
COMMENT ON COLUMN platform_file.file_holds.file_hold_id IS '封存登记 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.file_holds.stored_file_id IS '存储文件 ID：须已关联到发起封存的单据（应用校验）';
COMMENT ON COLUMN platform_file.file_holds.initiator_document_type IS '发起封存的单据类型码';
COMMENT ON COLUMN platform_file.file_holds.initiator_document_id IS '发起封存的单据 ID';
COMMENT ON COLUMN platform_file.file_holds.held_by_kind IS '封存主体类型：决定下面三列哪一列非空';
COMMENT ON COLUMN platform_file.file_holds.held_by_user_id IS '封存用户 ID：为空表示封存主体不是用户';
COMMENT ON COLUMN platform_file.file_holds.held_by_link_id IS '封存链接 ID：为空表示封存主体不是一次性链接';
COMMENT ON COLUMN platform_file.file_holds.held_by_module_code IS '封存模块代码：为空表示封存主体不是平台模块';
COMMENT ON COLUMN platform_file.file_holds.hold_reason IS '封存理由：不填个人信息';
COMMENT ON COLUMN platform_file.file_holds.held_at IS '封存时间';
COMMENT ON COLUMN platform_file.file_holds.created_at IS '创建时间（恒等于封存时间）';
CREATE TRIGGER tg_file_holds_append_only BEFORE UPDATE OR DELETE ON platform_file.file_holds
    FOR EACH ROW EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();
CREATE TRIGGER tg_file_holds_append_only_truncate BEFORE TRUNCATE ON platform_file.file_holds
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();

-- ═══ 5. 保存期限表：根表 + 配置型版本（D15、M6，形状仿 numbering_rules / numbering_rule_versions）═══
CREATE TABLE platform_file.preservation_categories (
    preservation_category_id uuid        NOT NULL,
    category_code            text        NOT NULL,
    category_name            text        NOT NULL,
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    version                  bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_preservation_categories PRIMARY KEY (preservation_category_id),
    CONSTRAINT uk_preservation_categories_category_code UNIQUE (category_code),
    CONSTRAINT ck_preservation_categories_category_code CHECK (category_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_preservation_categories_category_name CHECK (category_name <> '' AND char_length(category_name) <= 100)
);
COMMENT ON TABLE platform_file.preservation_categories IS '保存类别：保存期限表的根，年限等内容在 preservation_category_versions。粒度：每个类别一行';
COMMENT ON COLUMN platform_file.preservation_categories.preservation_category_id IS '保存类别 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.preservation_categories.category_code IS '保存类别代码：业务键，关联时由所属模块给出';
COMMENT ON COLUMN platform_file.preservation_categories.category_name IS '保存类别名称';
COMMENT ON COLUMN platform_file.preservation_categories.created_at IS '创建时间';
COMMENT ON COLUMN platform_file.preservation_categories.updated_at IS '更新时间';
COMMENT ON COLUMN platform_file.preservation_categories.version IS '乐观锁版本';

CREATE TABLE platform_file.preservation_category_versions (
    preservation_category_version_id uuid        NOT NULL,
    preservation_category_id         uuid        NOT NULL,
    version_number                   integer     NOT NULL,
    default_years_count              integer,
    personal_info_years_count        integer,
    start_basis_text                 text        NOT NULL,
    effective_from_date              date        NOT NULL,
    decision_ref                     text        NOT NULL,
    confirmation_status              text        NOT NULL,
    author_id                        uuid,
    authored_at                      timestamptz NOT NULL,
    confirmed_by                     uuid,
    confirmed_at                     timestamptz,
    created_at                       timestamptz NOT NULL,
    updated_at                       timestamptz NOT NULL,
    CONSTRAINT pk_preservation_category_versions PRIMARY KEY (preservation_category_version_id),
    CONSTRAINT uk_preservation_category_versions_category_version_number UNIQUE (preservation_category_id, version_number),
    CONSTRAINT fk_preservation_category_versions_category FOREIGN KEY (preservation_category_id)
        REFERENCES platform_file.preservation_categories (preservation_category_id) ON DELETE RESTRICT,
    CONSTRAINT ck_preservation_category_versions_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_preservation_category_versions_default_years CHECK (default_years_count IS NULL OR default_years_count >= 1),
    CONSTRAINT ck_preservation_category_versions_personal_info_years CHECK (personal_info_years_count IS NULL OR personal_info_years_count >= 1),
    CONSTRAINT ck_preservation_category_versions_start_basis_text CHECK (start_basis_text <> '' AND char_length(start_basis_text) <= 500),
    CONSTRAINT ck_preservation_category_versions_decision_ref CHECK (decision_ref <> '' AND char_length(decision_ref) <= 200),
    CONSTRAINT ck_preservation_category_versions_confirmation_status CHECK (confirmation_status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT ck_preservation_category_versions_confirmed CHECK ((confirmation_status = 'CONFIRMED') = (confirmed_by IS NOT NULL)
                                                                  AND (confirmed_by IS NULL) = (confirmed_at IS NULL)),
    CONSTRAINT ck_preservation_category_versions_confirmer_is_not_author CHECK (author_id IS NULL OR confirmed_by IS NULL OR confirmed_by <> author_id)
);
CREATE UNIQUE INDEX uk_preservation_category_versions_pending ON platform_file.preservation_category_versions (preservation_category_id)
    WHERE confirmation_status = 'PENDING';
COMMENT ON TABLE platform_file.preservation_category_versions IS '保存类别版本：配置型版本化，内容不可变、只补确认；「待确认」版本不得作删除依据（ADR-0061）；每个类别至多一个待确认版本。粒度：每个类别每个版本一行';
COMMENT ON COLUMN platform_file.preservation_category_versions.preservation_category_version_id IS '保存类别版本 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.preservation_category_versions.preservation_category_id IS '保存类别 ID';
COMMENT ON COLUMN platform_file.preservation_category_versions.version_number IS '版本号：每个类别从 1 递增；同日修订以版本号区分（D6）';
COMMENT ON COLUMN platform_file.preservation_category_versions.default_years_count IS '默认年限：为空表示该类别年限未定，按不处置保存（ADR-0061 决定 4）';
COMMENT ON COLUMN platform_file.preservation_category_versions.personal_info_years_count IS '个人信息年限：为空表示同默认年限';
COMMENT ON COLUMN platform_file.preservation_category_versions.start_basis_text IS '起算依据：说明文字，平台不枚举业务事件（ADR-0066 决定 3 第 4 条）';
COMMENT ON COLUMN platform_file.preservation_category_versions.effective_from_date IS '生效日：作者提交内容的一部分；版本号大于 1 的，确认时早于确认日即拒';
COMMENT ON COLUMN platform_file.preservation_category_versions.decision_ref IS '决定出处：intent、法务答复或 ADR 编号';
COMMENT ON COLUMN platform_file.preservation_category_versions.confirmation_status IS '确认状态：PENDING 待确认 / CONFIRMED 已确认';
COMMENT ON COLUMN platform_file.preservation_category_versions.author_id IS '作者用户 ID：为空表示迁移脚本写入的初始版本；确认人不得是作者';
COMMENT ON COLUMN platform_file.preservation_category_versions.authored_at IS '保存该版本的时间';
COMMENT ON COLUMN platform_file.preservation_category_versions.confirmed_by IS '确认人用户 ID：为空表示未确认';
COMMENT ON COLUMN platform_file.preservation_category_versions.confirmed_at IS '确认时间：为空表示未确认';
COMMENT ON COLUMN platform_file.preservation_category_versions.created_at IS '创建时间';
COMMENT ON COLUMN platform_file.preservation_category_versions.updated_at IS '更新时间：确认时更新一次';

CREATE FUNCTION platform_file.guard_preservation_category_version_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_file.preservation_category_versions: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NOT (OLD.confirmation_status = 'PENDING' AND NEW.confirmation_status = 'CONFIRMED')
            OR (to_jsonb(NEW) - ARRAY['confirmation_status', 'confirmed_by', 'confirmed_at', 'updated_at'])
               IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['confirmation_status', 'confirmed_by', 'confirmed_at', 'updated_at']) THEN
        RAISE EXCEPTION 'platform_file.preservation_category_versions: only PENDING -> CONFIRMED with the confirmation columns is allowed'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.version_number > 1 AND NEW.effective_from_date < (NEW.confirmed_at AT TIME ZONE 'Asia/Shanghai')::date THEN
        RAISE EXCEPTION 'platform_file.preservation_category_versions: effective_from_date is before the confirmation date' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION platform_file.guard_preservation_category_version_insert() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.confirmation_status <> 'PENDING' OR NEW.confirmed_by IS NOT NULL OR NEW.confirmed_at IS NOT NULL THEN
        RAISE EXCEPTION 'platform_file.preservation_category_versions: a version is inserted PENDING and confirmed by a person later'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_preservation_category_versions_insert_guard BEFORE INSERT ON platform_file.preservation_category_versions
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_preservation_category_version_insert();
CREATE TRIGGER tg_preservation_category_versions_guard BEFORE UPDATE OR DELETE ON platform_file.preservation_category_versions
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_preservation_category_version_change();
CREATE TRIGGER tg_preservation_category_versions_guard_truncate BEFORE TRUNCATE ON platform_file.preservation_category_versions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.guard_preservation_category_version_change();

-- ═══ 6. 文件保存期（只追加，序号递增，当前行只经视图读）═════════════
CREATE TABLE platform_file.file_preservations (
    file_preservation_id             uuid        NOT NULL,
    stored_file_id                   uuid        NOT NULL,
    preservation_seq                 integer     NOT NULL,
    preservation_category_version_id uuid        NOT NULL,
    anchor_document_type             text        NOT NULL,
    anchor_document_id               uuid        NOT NULL,
    start_date                       date,
    agreed_years_count               integer,
    expiry_date                      date,
    recorded_at                      timestamptz NOT NULL,
    created_at                       timestamptz NOT NULL,
    CONSTRAINT pk_file_preservations PRIMARY KEY (file_preservation_id),
    CONSTRAINT uk_file_preservations_stored_file_id_seq UNIQUE (stored_file_id, preservation_seq),
    CONSTRAINT fk_file_preservations_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT fk_file_preservations_category_version FOREIGN KEY (preservation_category_version_id)
        REFERENCES platform_file.preservation_category_versions (preservation_category_version_id) ON DELETE RESTRICT,
    CONSTRAINT ck_file_preservations_preservation_seq CHECK (preservation_seq >= 1),
    CONSTRAINT ck_file_preservations_anchor_document_type CHECK (anchor_document_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_file_preservations_agreed_years CHECK (agreed_years_count IS NULL OR agreed_years_count >= 1),
    CONSTRAINT ck_file_preservations_expiry CHECK (expiry_date IS NULL OR (start_date IS NOT NULL AND expiry_date > start_date))
);
CREATE INDEX ix_file_preservations_category_version ON platform_file.file_preservations (preservation_category_version_id);
CREATE INDEX ix_file_preservations_anchor ON platform_file.file_preservations (anchor_document_type, anchor_document_id);
CREATE INDEX ix_file_preservations_expiry_date ON platform_file.file_preservations (expiry_date) WHERE expiry_date IS NOT NULL;
COMMENT ON TABLE platform_file.file_preservations IS '文件保存期：首次关联、写起算日、写约定年限、年限表换版各追加一行，序号最大者为当前；一个文件一个类别与起算单据（插入触发器保证）；读当前行只许走 current_file_preservations。粒度：每次重算一行';
COMMENT ON COLUMN platform_file.file_preservations.file_preservation_id IS '文件保存期 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.file_preservations.stored_file_id IS '存储文件 ID';
COMMENT ON COLUMN platform_file.file_preservations.preservation_seq IS '保存期序号：每个文件从 1 递增，最大者为当前行';
COMMENT ON COLUMN platform_file.file_preservations.preservation_category_version_id IS '所用保存类别版本 ID';
COMMENT ON COLUMN platform_file.file_preservations.anchor_document_type IS '起算单据类型码';
COMMENT ON COLUMN platform_file.file_preservations.anchor_document_id IS '起算单据 ID';
COMMENT ON COLUMN platform_file.file_preservations.start_date IS '起算日：为空表示起算事件未发生，不到期';
COMMENT ON COLUMN platform_file.file_preservations.agreed_years_count IS '约定保存年限：为空表示合同无约定';
COMMENT ON COLUMN platform_file.file_preservations.expiry_date IS '到期日：起算日 + max(默认, 约定)；为空表示不到期（起算日为空或类别年限未定）';
COMMENT ON COLUMN platform_file.file_preservations.recorded_at IS '记录时间';
COMMENT ON COLUMN platform_file.file_preservations.created_at IS '创建时间（恒等于记录时间）';

CREATE FUNCTION platform_file.guard_file_preservation_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    last_row platform_file.file_preservations;
BEGIN
    PERFORM 1 FROM platform_file.stored_files WHERE stored_file_id = NEW.stored_file_id FOR UPDATE;
    SELECT * INTO last_row FROM platform_file.file_preservations
        WHERE stored_file_id = NEW.stored_file_id ORDER BY preservation_seq DESC LIMIT 1;
    IF NEW.preservation_seq <> COALESCE(last_row.preservation_seq, 0) + 1 THEN
        RAISE EXCEPTION 'platform_file.file_preservations: preservation_seq must be % for this file', COALESCE(last_row.preservation_seq, 0) + 1
            USING ERRCODE = 'check_violation';
    END IF;
    IF last_row.file_preservation_id IS NOT NULL AND (
            NEW.anchor_document_type IS DISTINCT FROM last_row.anchor_document_type
            OR NEW.anchor_document_id IS DISTINCT FROM last_row.anchor_document_id
            OR (SELECT preservation_category_id FROM platform_file.preservation_category_versions
                    WHERE preservation_category_version_id = NEW.preservation_category_version_id)
               IS DISTINCT FROM (SELECT preservation_category_id FROM platform_file.preservation_category_versions
                    WHERE preservation_category_version_id = last_row.preservation_category_version_id)) THEN
        RAISE EXCEPTION 'platform_file.file_preservations: a file keeps one preservation category and anchor' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_file_preservations_insert_guard BEFORE INSERT ON platform_file.file_preservations
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_file_preservation_insert();
CREATE TRIGGER tg_file_preservations_append_only BEFORE UPDATE OR DELETE ON platform_file.file_preservations
    FOR EACH ROW EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();
CREATE TRIGGER tg_file_preservations_append_only_truncate BEFORE TRUNCATE ON platform_file.file_preservations
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();

CREATE VIEW platform_file.current_file_preservations WITH (security_invoker = true) AS
SELECT DISTINCT ON (fp.stored_file_id) fp.*
FROM platform_file.file_preservations fp
ORDER BY fp.stored_file_id, fp.preservation_seq DESC;
COMMENT ON VIEW platform_file.current_file_preservations IS '文件当前保存期：每个文件序号最大的一行；到期作业与展示只读此视图，不直接按 expiry_date 扫基表';

-- ═══ 7. 误报判定、例外清单与撤销（ADR-0070 决定 6 第 3 条）═══════════════
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
    created_at                 timestamptz NOT NULL,
    updated_at                 timestamptz NOT NULL,
    version                    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_false_positive_judgments PRIMARY KEY (false_positive_judgment_id),
    CONSTRAINT uk_false_positive_judgments_id_status UNIQUE (false_positive_judgment_id, judgment_status),
    CONSTRAINT fk_false_positive_judgments_stored_file FOREIGN KEY (stored_file_id, content_hash)
        REFERENCES platform_file.stored_files (stored_file_id, content_hash) ON DELETE RESTRICT,
    CONSTRAINT ck_false_positive_judgments_judgment_status CHECK (judgment_status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_false_positive_judgments_reviewed CHECK ((judgment_status = 'PENDING_REVIEW') = (reviewed_by IS NULL)
                                                          AND (reviewed_by IS NULL) = (reviewed_at IS NULL)
                                                          AND (reviewed_by IS NULL) = (review_reason IS NULL)),
    CONSTRAINT ck_false_positive_judgments_two_people CHECK (reviewed_by IS NULL OR reviewed_by <> judged_by),
    CONSTRAINT ck_false_positive_judgments_judgment_reason CHECK (judgment_reason <> '' AND char_length(judgment_reason) <= 1000),
    CONSTRAINT ck_false_positive_judgments_review_reason CHECK (review_reason IS NULL OR (review_reason <> '' AND char_length(review_reason) <= 1000))
);
CREATE UNIQUE INDEX uk_false_positive_judgments_pending ON platform_file.false_positive_judgments (stored_file_id) WHERE judgment_status = 'PENDING_REVIEW';
COMMENT ON TABLE platform_file.false_positive_judgments IS '误报判定：安全角色判定、第二人复核（不得同一人），复核后不再改。粒度：每次判定一行';
COMMENT ON COLUMN platform_file.false_positive_judgments.false_positive_judgment_id IS '误报判定 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.false_positive_judgments.stored_file_id IS '被判定的已隔离文件 ID';
COMMENT ON COLUMN platform_file.false_positive_judgments.content_hash IS '内容哈希：与存储文件复合外键保证一致';
COMMENT ON COLUMN platform_file.false_positive_judgments.judgment_status IS '判定状态：PENDING_REVIEW 待复核 / CONFIRMED 确认误报 / REJECTED 驳回';
COMMENT ON COLUMN platform_file.false_positive_judgments.judgment_reason IS '判定理由：不填个人信息';
COMMENT ON COLUMN platform_file.false_positive_judgments.judged_by IS '判定人用户 ID（安全角色）';
COMMENT ON COLUMN platform_file.false_positive_judgments.judged_at IS '判定时间';
COMMENT ON COLUMN platform_file.false_positive_judgments.review_reason IS '复核意见：为空表示未复核；不填个人信息';
COMMENT ON COLUMN platform_file.false_positive_judgments.reviewed_by IS '复核人用户 ID：为空表示未复核；不得是判定人';
COMMENT ON COLUMN platform_file.false_positive_judgments.reviewed_at IS '复核时间：为空表示未复核';
COMMENT ON COLUMN platform_file.false_positive_judgments.created_at IS '创建时间';
COMMENT ON COLUMN platform_file.false_positive_judgments.updated_at IS '更新时间';
COMMENT ON COLUMN platform_file.false_positive_judgments.version IS '乐观锁版本';

CREATE FUNCTION platform_file.guard_false_positive_judgment_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_file.false_positive_judgments: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NOT (OLD.judgment_status = 'PENDING_REVIEW' AND NEW.judgment_status IN ('CONFIRMED', 'REJECTED'))
            OR (to_jsonb(NEW) - ARRAY['judgment_status', 'review_reason', 'reviewed_by', 'reviewed_at', 'updated_at', 'version'])
               IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['judgment_status', 'review_reason', 'reviewed_by', 'reviewed_at', 'updated_at', 'version']) THEN
        RAISE EXCEPTION 'platform_file.false_positive_judgments: only one review of a pending judgment is allowed' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_false_positive_judgments_guard BEFORE UPDATE OR DELETE ON platform_file.false_positive_judgments
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_false_positive_judgment_change();
CREATE TRIGGER tg_false_positive_judgments_guard_truncate BEFORE TRUNCATE ON platform_file.false_positive_judgments
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.guard_false_positive_judgment_change();

CREATE TABLE platform_file.scan_exceptions (
    content_hash               text        NOT NULL,
    false_positive_judgment_id uuid        NOT NULL,
    judgment_status            text        NOT NULL,
    added_at                   timestamptz NOT NULL,
    created_at                 timestamptz NOT NULL,
    CONSTRAINT pk_scan_exceptions PRIMARY KEY (content_hash),
    CONSTRAINT uk_scan_exceptions_false_positive_judgment_id UNIQUE (false_positive_judgment_id),
    CONSTRAINT fk_scan_exceptions_judgment FOREIGN KEY (false_positive_judgment_id, judgment_status)
        REFERENCES platform_file.false_positive_judgments (false_positive_judgment_id, judgment_status) ON DELETE RESTRICT,
    CONSTRAINT ck_scan_exceptions_content_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_scan_exceptions_judgment_status CHECK (judgment_status = 'CONFIRMED')
);
COMMENT ON TABLE platform_file.scan_exceptions IS '误报例外清单：只追加；引擎检出且内容哈希在清单内、且未撤销，即按通过并审计「按例外放行」；只豁免该内容。撤销后同一哈希不再加回。粒度：每个内容哈希一行';
COMMENT ON COLUMN platform_file.scan_exceptions.content_hash IS '内容哈希：被豁免的内容；应用写入时取自判定行';
COMMENT ON COLUMN platform_file.scan_exceptions.false_positive_judgment_id IS '确认误报的判定 ID';
COMMENT ON COLUMN platform_file.scan_exceptions.judgment_status IS '判定状态：恒为 CONFIRMED，与判定 ID 复合外键保证只引已确认的判定';
COMMENT ON COLUMN platform_file.scan_exceptions.added_at IS '加入时间';
COMMENT ON COLUMN platform_file.scan_exceptions.created_at IS '创建时间（恒等于加入时间）';
CREATE TRIGGER tg_scan_exceptions_append_only BEFORE UPDATE OR DELETE ON platform_file.scan_exceptions
    FOR EACH ROW EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();
CREATE TRIGGER tg_scan_exceptions_append_only_truncate BEFORE TRUNCATE ON platform_file.scan_exceptions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();

CREATE TABLE platform_file.scan_exception_revocations (
    content_hash        text        NOT NULL,
    revocation_reason   text        NOT NULL,
    revoked_by          uuid        NOT NULL,
    confirmed_by        uuid        NOT NULL,
    revoked_at          timestamptz NOT NULL,
    created_at          timestamptz NOT NULL,
    CONSTRAINT pk_scan_exception_revocations PRIMARY KEY (content_hash),
    CONSTRAINT fk_scan_exception_revocations_content_hash FOREIGN KEY (content_hash)
        REFERENCES platform_file.scan_exceptions (content_hash) ON DELETE RESTRICT,
    CONSTRAINT ck_scan_exception_revocations_two_people CHECK (confirmed_by <> revoked_by),
    CONSTRAINT ck_scan_exception_revocations_revocation_reason CHECK (revocation_reason <> '' AND char_length(revocation_reason) <= 1000)
);
COMMENT ON TABLE platform_file.scan_exception_revocations IS '例外撤销：误判豁免被发现后撤销，两人（发起与确认不得同一人），只追加；撤销后该哈希再上传即按检测结论处理。粒度：每个被撤销的内容哈希一行';
COMMENT ON COLUMN platform_file.scan_exception_revocations.content_hash IS '被撤销豁免的内容哈希';
COMMENT ON COLUMN platform_file.scan_exception_revocations.revocation_reason IS '撤销理由：不填个人信息';
COMMENT ON COLUMN platform_file.scan_exception_revocations.revoked_by IS '发起撤销的用户 ID（安全角色）';
COMMENT ON COLUMN platform_file.scan_exception_revocations.confirmed_by IS '确认撤销的用户 ID：不得是发起人';
COMMENT ON COLUMN platform_file.scan_exception_revocations.revoked_at IS '撤销时间';
COMMENT ON COLUMN platform_file.scan_exception_revocations.created_at IS '创建时间（恒等于撤销时间）';
CREATE TRIGGER tg_scan_exception_revocations_append_only BEFORE UPDATE OR DELETE ON platform_file.scan_exception_revocations
    FOR EACH ROW EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();
CREATE TRIGGER tg_scan_exception_revocations_append_only_truncate BEFORE TRUNCATE ON platform_file.scan_exception_revocations
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.refuse_append_only_rewrite();

-- ═══ 8. 隔离内容读取申请（ADR-0070 决定 6 第 2 条、决定 7 第 2 条）══════════
CREATE TABLE platform_file.quarantine_read_requests (
    quarantine_read_request_id uuid        NOT NULL,
    stored_file_id             uuid        NOT NULL,
    request_status             text        NOT NULL,
    request_reason             text        NOT NULL,
    requested_by               uuid        NOT NULL,
    requested_at               timestamptz NOT NULL,
    decided_by                 uuid,
    decided_at                 timestamptz,
    approval_expires_at        timestamptz,
    read_at                    timestamptz,
    created_at                 timestamptz NOT NULL,
    updated_at                 timestamptz NOT NULL,
    version                    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_quarantine_read_requests PRIMARY KEY (quarantine_read_request_id),
    CONSTRAINT fk_quarantine_read_requests_stored_file_id FOREIGN KEY (stored_file_id) REFERENCES platform_file.stored_files (stored_file_id) ON DELETE RESTRICT,
    CONSTRAINT ck_quarantine_read_requests_request_status CHECK (request_status IN ('PENDING', 'APPROVED', 'REJECTED', 'READ')),
    CONSTRAINT ck_quarantine_read_requests_two_people CHECK (decided_by IS NULL OR decided_by <> requested_by),
    CONSTRAINT ck_quarantine_read_requests_decided CHECK ((request_status = 'PENDING') = (decided_by IS NULL)
                                                         AND (decided_by IS NULL) = (decided_at IS NULL)),
    CONSTRAINT ck_quarantine_read_requests_approval_expiry CHECK ((request_status IN ('APPROVED', 'READ')) = (approval_expires_at IS NOT NULL)
                                                                  AND (approval_expires_at IS NULL OR approval_expires_at > decided_at)),
    CONSTRAINT ck_quarantine_read_requests_read CHECK ((request_status = 'READ') = (read_at IS NOT NULL)
                                                      AND (read_at IS NULL OR read_at <= approval_expires_at)),
    CONSTRAINT ck_quarantine_read_requests_request_reason CHECK (request_reason <> '' AND char_length(request_reason) <= 1000)
);
CREATE INDEX ix_quarantine_read_requests_stored_file_id ON platform_file.quarantine_read_requests (stored_file_id);
COMMENT ON TABLE platform_file.quarantine_read_requests IS '隔离内容读取申请：安全角色申请、另一人审批，批准有失效期，批准后读一次即结；全程审计。粒度：每次申请一行';
COMMENT ON COLUMN platform_file.quarantine_read_requests.quarantine_read_request_id IS '隔离内容读取申请 ID（UUIDv7）';
COMMENT ON COLUMN platform_file.quarantine_read_requests.stored_file_id IS '已隔离文件 ID';
COMMENT ON COLUMN platform_file.quarantine_read_requests.request_status IS '申请状态：PENDING 待审批 / APPROVED 已批准 / REJECTED 已驳回 / READ 已读取';
COMMENT ON COLUMN platform_file.quarantine_read_requests.request_reason IS '申请理由：不填个人信息';
COMMENT ON COLUMN platform_file.quarantine_read_requests.requested_by IS '申请人用户 ID（安全角色）';
COMMENT ON COLUMN platform_file.quarantine_read_requests.requested_at IS '申请时间';
COMMENT ON COLUMN platform_file.quarantine_read_requests.decided_by IS '审批人用户 ID：为空表示待审批；不得是申请人';
COMMENT ON COLUMN platform_file.quarantine_read_requests.decided_at IS '审批时间：为空表示待审批';
COMMENT ON COLUMN platform_file.quarantine_read_requests.approval_expires_at IS '批准失效时间：批准时必填，为空表示未批准；超期读取即拒';
COMMENT ON COLUMN platform_file.quarantine_read_requests.read_at IS '读取时间：为空表示未读取';
COMMENT ON COLUMN platform_file.quarantine_read_requests.created_at IS '创建时间';
COMMENT ON COLUMN platform_file.quarantine_read_requests.updated_at IS '更新时间';
COMMENT ON COLUMN platform_file.quarantine_read_requests.version IS '乐观锁版本';

CREATE FUNCTION platform_file.guard_quarantine_read_request_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'platform_file.quarantine_read_requests: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NOT ((OLD.request_status = 'PENDING' AND NEW.request_status IN ('APPROVED', 'REJECTED'))
            OR (OLD.request_status = 'APPROVED' AND NEW.request_status = 'READ'))
            OR (to_jsonb(NEW) - ARRAY['request_status', 'decided_by', 'decided_at', 'approval_expires_at', 'read_at', 'updated_at', 'version'])
               IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['request_status', 'decided_by', 'decided_at', 'approval_expires_at', 'read_at', 'updated_at', 'version'])
            OR (OLD.request_status = 'APPROVED' AND (NEW.decided_by, NEW.decided_at, NEW.approval_expires_at)
                IS DISTINCT FROM (OLD.decided_by, OLD.decided_at, OLD.approval_expires_at)) THEN
        RAISE EXCEPTION 'platform_file.quarantine_read_requests: % -> % is not an allowed transition', OLD.request_status, NEW.request_status
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_quarantine_read_requests_guard BEFORE UPDATE OR DELETE ON platform_file.quarantine_read_requests
    FOR EACH ROW EXECUTE FUNCTION platform_file.guard_quarantine_read_request_change();
CREATE TRIGGER tg_quarantine_read_requests_guard_truncate BEFORE TRUNCATE ON platform_file.quarantine_read_requests
    FOR EACH STATEMENT EXECUTE FUNCTION platform_file.guard_quarantine_read_request_change();

-- ═══ 9. 登记、分级、保留（M7；随表结构 PR 提交，这里列要点）════════════════
-- data-access-registry.yaml（十二张表 + 视图）：
--   stored_files                    role AGGREGATE_ROOT, mutability CONDITIONAL_UPDATE, condition「只有 file_status 按流转清单可改」，
--                                   business_key [object_key]（另 content_hash 非唯一），retention PERMANENT（人员类标识随 RETENTION_PROPOSAL，清理作业另卡）
--   stored_file_names               CONDITIONAL_UPDATE「去标识一次」，去标识作业 platform.file.deidentify-file-names（ADR-0061 处置建成时实现）
--   file_bindings / file_holds / scan_exceptions / scan_exception_revocations / file_preservations   APPEND_ONLY
--   file_preservations              另注「读当前行只经 current_file_preservations」
--   preservation_categories         role CONFIG_VERSIONED，versions_table preservation_category_versions，business_key [category_code]
--   preservation_category_versions  role AGGREGATE_CHILD（种子初始版本以 PENDING 写入，由人确认）
--   false_positive_judgments / quarantine_read_requests   CONDITIONAL_UPDATE（按流转清单）
--   state_transitions               LOG_APPEND_ONLY（按 STATE_TRANSITIONS_DESIGN 第 6 节）
-- data-classification.txt（ADR-0043 格式）：
--   stored_file_names.original_name                     INTERNAL | PI | 下载时还原文件名，到期去标识
--   *_user_id、judged_by、reviewed_by、requested_by、decided_by、author_id、confirmed_by、revoked_by   INTERNAL | PI | 人员标识
--   hold_reason、judgment_reason、review_reason、request_reason、revocation_reason   INTERNAL | PI | 自由文本可能夹带，注释要求不填
--   其余列 INTERNAL | NONE（未登记默认）

-- ═══ 审改对照（第 1 版审 → 本版）═════════════════════════════════════
-- H1 建 state_transitions（模板）；四个状态列各有流转清单，stored_files、false_positive_judgments、quarantine_read_requests、
--    preservation_category_versions 触发器拒非法流转，终态后状态不可改。
-- H2 加根表 preservation_categories（业务键 category_code，名称在根表）；版本表列名改 author_id、authored_at、confirmed_by、confirmed_at、
--    decision_ref、effective_from_date，加 version_number；触发器只允 PENDING→CONFIRMED 且只改确认列；版本号 >1 时生效日不早于确认日。
-- H3 删 stored_files.migration_batch_id；迁入批次与来源走 source_references。
-- H4 三组主体列拆为 *_user_id / *_link_id / *_module_code，具名 CHECK 按 kind 恰好一个非空。
-- H5 file_preservations 加 preservation_seq（唯一）、插入触发器（序号连续、类别根与起算单据不变）、视图 current_file_preservations（security_invoker）。
-- M1 object_key CHECK 加末段等于本行 ID。  M2 去标识固定占位值「[已去标识]」，CHECK 与触发器双重保证。
-- M3 file_status 去掉 DISPOSED，与 file_disposals 同批加回。
-- M4 判定表以 (stored_file_id, content_hash) 复合外键引存储文件；例外表以 (判定 ID, 'CONFIRMED') 复合外键引判定；撤销选「追加表」：
--    scan_exception_revocations（两人）。撤销后同一哈希不再加回例外清单（主键 content_hash），如需再豁免另议。
-- M5 quarantine_read_requests 加 approval_expires_at（批准必填，读取不晚于它）。
-- M6 所有只追加表有 UPDATE/DELETE 与 TRUNCATE 两个触发器；逐表逐列中文注释。
-- M7 见第 9 节。  Q5 personal_info_mark 只列 NONE、PI。
-- 低档：分级取较严者已写入 file_bindings 表注释；自由文本列注释「不填个人信息」并分级 PI；改键与插关联的顺序由集成测试覆盖（单元 6）。

-- 第二轮：N1 补 state_transitions 完整 DDL、合法流转清单与三个系统操作者；N2 版本表加 BEFORE INSERT 只许 PENDING（种子同）；
--    N3 注册表根表 CONFIG_VERSIONED + versions_table、版本表 AGGREGATE_CHILD。P1–P3 已获同意。

-- 第四轮（one-architecture 2026-10-21 指出，与 ADR-0060 决定 3、ADR-0070 决定 2 对齐）：object_key 建行即写正式键或隔离键、触发器禁改；
--    临时区键由 ID 与来源推出不落列；去掉 staging 键形与 ix_stored_files_staging；正式区孤儿只读核对作业（N 天配置项，只告警）进单元 6。

-- ═══ 仍待定 ═══════════════════════════════════════════════════════════
-- 无（P1 SYSTEM + system-actors.txt、P2 契约补「取较严者」、P3 Asia/Shanghai 均已同意）。
