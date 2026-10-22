-- 事件发布登记（Spring Modulith 事件登记，JDBC，标准结构 v2）：模块事件至少投递一次（RULE-295/303/304）。
-- 表由 Flyway 创建并归 platform.integration 的 schema 所有；spring.modulith.events.jdbc.schema 指向本 schema，
-- 框架自带的建表（schema-initialization）关闭。结构沿用框架的表，另加两个审计列（框架插入时指定列名，默认值生效）。
CREATE SCHEMA IF NOT EXISTS platform_integration;
COMMENT ON SCHEMA platform_integration IS '权限流程与集成平台 BC · 集成：外部系统防腐层、出站投递与事件发布登记';

CREATE TABLE platform_integration.event_publication (
    id                     uuid        PRIMARY KEY,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text        CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED')),
    completion_attempts    integer,
    last_resubmission_date timestamptz,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX event_publication_serialized_event_hash_idx ON platform_integration.event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON platform_integration.event_publication (completion_date);
COMMENT ON TABLE platform_integration.event_publication IS '事件发布登记：每个事件对每个监听者一行；与发布事件的业务事务同提交，未完成的行重新投递（至少一次）';
COMMENT ON COLUMN platform_integration.event_publication.id IS '主键';
COMMENT ON COLUMN platform_integration.event_publication.listener_id IS '监听者标识（监听方法的全名）';
COMMENT ON COLUMN platform_integration.event_publication.event_type IS '事件类型（事件类的全名）';
COMMENT ON COLUMN platform_integration.event_publication.serialized_event IS '事件内容（JSON 文本）';
COMMENT ON COLUMN platform_integration.event_publication.publication_date IS '发布时间';
COMMENT ON COLUMN platform_integration.event_publication.completion_date IS '处理完成时间；为空表示尚未完成';
COMMENT ON COLUMN platform_integration.event_publication.status IS '状态：PUBLISHED 已发布 / PROCESSING 处理中 / COMPLETED 已完成 / FAILED 失败 / RESUBMITTED 已重新投递';
COMMENT ON COLUMN platform_integration.event_publication.completion_attempts IS '处理尝试次数';
COMMENT ON COLUMN platform_integration.event_publication.last_resubmission_date IS '最近一次重新投递时间';
COMMENT ON COLUMN platform_integration.event_publication.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.event_publication.updated_at IS '更新时间';

CREATE TABLE platform_integration.outbound_deliveries (
    id                      uuid        PRIMARY KEY,
    subject_kind            text        NOT NULL CONSTRAINT ck_outbound_deliveries_subject_kind CHECK (subject_kind IN ('APPROVAL_TASK', 'APPROVAL_INSTANCE')),
    subject_id              uuid        NOT NULL,
    purpose_kind            text        NOT NULL CONSTRAINT ck_outbound_deliveries_purpose_kind CHECK (purpose_kind IN ('TASK_ASSIGNED', 'TASK_TRANSFERRED', 'TASK_REASSIGNED', 'TASK_OVERDUE', 'TASK_ESCALATED', 'APPROVAL_COMPLETED')),
    trigger_event_id        uuid,
    title                   text        NOT NULL,
    description             text,
    target_url              text        NOT NULL,
    status                  text        NOT NULL CHECK (status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED', 'DEAD_LETTER')),
    attempt_count           integer     NOT NULL DEFAULT 0,
    next_attempt_at         timestamptz,
    last_error              text,
    response_code           text,
    sent_at                 timestamptz,
    handled_note            text,
    handled_result          text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    kind                    text        NOT NULL DEFAULT 'CARD' CHECK (kind IN ('CARD', 'HANDLED_NOTE')),
    target_delivery_id      uuid,
    version                 bigint      NOT NULL DEFAULT 0,
    correlation_id          uuid,
    event_count             bigint      NOT NULL DEFAULT 0,
    recipient_codes         text[]      NOT NULL,
    skipped_recipient_codes text[],
    CONSTRAINT outbound_deliveries_handled_note_target CHECK ((kind = 'HANDLED_NOTE') = (target_delivery_id IS NOT NULL)),
    CONSTRAINT fk_outbound_deliveries_target_delivery_id FOREIGN KEY (target_delivery_id) REFERENCES platform_integration.outbound_deliveries (id)
);
CREATE UNIQUE INDEX uk_outbound_deliveries_card ON platform_integration.outbound_deliveries (subject_kind, subject_id, purpose_kind, trigger_event_id) NULLS NOT DISTINCT WHERE kind = 'CARD';
CREATE UNIQUE INDEX uk_outbound_deliveries_target_delivery_id ON platform_integration.outbound_deliveries (target_delivery_id);
CREATE INDEX outbound_deliveries_due_idx ON platform_integration.outbound_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX outbound_deliveries_dead_idx ON platform_integration.outbound_deliveries (status) WHERE status = 'DEAD_LETTER';
COMMENT ON TABLE platform_integration.outbound_deliveries IS '外发投递：企业微信文本卡片的待发队列与结果，事务提交后由投递作业发送，失败退避重试，超过次数进死信；粒度：每张待发或已发的卡片一行（按对象、用途与触发事件唯一）';
COMMENT ON COLUMN platform_integration.outbound_deliveries.id IS '投递 ID（UUIDv7）';
COMMENT ON COLUMN platform_integration.outbound_deliveries.subject_kind IS '对象类别 / 对象 ID：被记录或被投递的业务对象的类别与自有 ID。对象类别：APPROVAL_TASK 审批任务 / APPROVAL_INSTANCE 审批实例';
COMMENT ON COLUMN platform_integration.outbound_deliveries.subject_id IS '对象 ID：被投递的业务对象的自有 ID（UUID）；与对象类别、用途、触发事件一起让同一张卡只投递一次，也用于事后把卡片改为已处理';
COMMENT ON COLUMN platform_integration.outbound_deliveries.purpose_kind IS '投递用途：一张卡为什么发。TASK_ASSIGNED 任务指派 / TASK_TRANSFERRED 任务转交 / TASK_REASSIGNED 任务改派 / TASK_OVERDUE 任务超时 / TASK_ESCALATED 任务升级 / APPROVAL_COMPLETED 审批完成';
COMMENT ON COLUMN platform_integration.outbound_deliveries.trigger_event_id IS '触发事件 ID：同一对象、同一用途可因多次事件重发时（转交、改派）取触发事件的 ID，其余为空';
COMMENT ON COLUMN platform_integration.outbound_deliveries.title IS '卡片标题';
COMMENT ON COLUMN platform_integration.outbound_deliveries.description IS '卡片说明';
COMMENT ON COLUMN platform_integration.outbound_deliveries.target_url IS '卡片点击后的跳转链接';
COMMENT ON COLUMN platform_integration.outbound_deliveries.status IS '投递状态：PENDING 待发送 / SENT 已发送 / SKIPPED 按规则跳过 / FAILED 无法发送（无收件人）/ DEAD_LETTER 重试耗尽进死信';
COMMENT ON COLUMN platform_integration.outbound_deliveries.attempt_count IS '已尝试发送次数；也是认领编号：状态变更只在编号与认领时一致时生效，过期的认领写入被拒绝';
COMMENT ON COLUMN platform_integration.outbound_deliveries.next_attempt_at IS '下次尝试时间；认领后为认领租约到期时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.last_error IS '最近一次失败或跳过的原因';
COMMENT ON COLUMN platform_integration.outbound_deliveries.response_code IS '企业微信发送后返回的卡片更新码，用于把卡片改为已处理；未返回则为空';
COMMENT ON COLUMN platform_integration.outbound_deliveries.sent_at IS '发送成功时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.handled_note IS '已处理说明，如“已由某人处理”';
COMMENT ON COLUMN platform_integration.outbound_deliveries.handled_result IS '把卡片改为已处理的结果：UPDATED 已更新 / NOT_APPLIED 未处理（附原因）/ FAILED 调用失败（附原因）';
COMMENT ON COLUMN platform_integration.outbound_deliveries.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.updated_at IS '更新时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.kind IS '投递种类：CARD 发送卡片 / HANDLED_NOTE 把已发送的卡片替换为已处理说明；两种共用同一套重试与死信';
COMMENT ON COLUMN platform_integration.outbound_deliveries.target_delivery_id IS '被替换的投递 ID：被替换卡片的投递 ID；仅 HANDLED_NOTE 投递有值，每张卡至多一条说明，说明文字存放在 description';
COMMENT ON COLUMN platform_integration.outbound_deliveries.version IS '乐观锁版本';
COMMENT ON COLUMN platform_integration.outbound_deliveries.correlation_id IS '关联号：产生这条投递的请求、作业或事件处理的关联号；投递作业恢复它，发出的请求带上它';
COMMENT ON COLUMN platform_integration.outbound_deliveries.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON COLUMN platform_integration.outbound_deliveries.recipient_codes IS '收件人：企业微信用户 ID，每人一个元素；只用精确用户投递，不用部门、标签或全员';
COMMENT ON COLUMN platform_integration.outbound_deliveries.skipped_recipient_codes IS '被跳过的收件人：测试环境白名单之外的企业微信用户 ID，每人一个元素；没有则为空';

-- 事件发布登记的归档表（Spring Modulith completion-mode=archive）：处理完成的发布从 event_publication 移入本表，保留 30 天后由清理作业删除。
-- 结构沿用框架的归档表，另加两个审计列（框架插入时指定列名，默认值生效）。
CREATE TABLE platform_integration.event_publication_archive (
    id                     uuid        PRIMARY KEY,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text        CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED')),
    completion_attempts    integer,
    last_resubmission_date timestamptz,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX event_publication_archive_serialized_event_hash_idx ON platform_integration.event_publication_archive USING hash (serialized_event);
CREATE INDEX event_publication_archive_by_completion_date_idx ON platform_integration.event_publication_archive (completion_date);
COMMENT ON TABLE platform_integration.event_publication_archive IS '事件发布归档：已处理完成的发布，保留 30 天供排查与对账，之后由清理作业删除';
COMMENT ON COLUMN platform_integration.event_publication_archive.id IS '主键';
COMMENT ON COLUMN platform_integration.event_publication_archive.listener_id IS '监听者标识（监听方法的全名）';
COMMENT ON COLUMN platform_integration.event_publication_archive.event_type IS '事件类型（事件类的全名）';
COMMENT ON COLUMN platform_integration.event_publication_archive.serialized_event IS '事件内容（JSON 文本）';
COMMENT ON COLUMN platform_integration.event_publication_archive.publication_date IS '发布时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.completion_date IS '处理完成时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.status IS '状态：与 event_publication 相同';
COMMENT ON COLUMN platform_integration.event_publication_archive.completion_attempts IS '处理尝试次数';
COMMENT ON COLUMN platform_integration.event_publication_archive.last_resubmission_date IS '最近一次重新投递时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.updated_at IS '更新时间';

-- ADR-0033 第 2 项：外部系统中对象的标识只登记在这张表，领域模块只用自有 ID。排他约束需要 btree_gist，由建库脚本预建，迁移只检查存在。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: the database setup script (one_db_roles_schemas.sql) creates it before the migrations run';
    END IF;
END
$$;

CREATE TABLE platform_integration.source_references (
    source_reference_id uuid        NOT NULL,
    source_system       text        NOT NULL CONSTRAINT ck_source_references_source_system CHECK (source_system IN ('ORG_SOURCE', 'LEGACY_SYSTEM', 'ACCOUNTING_SYSTEM')),
    source_object_kind  text        NOT NULL CONSTRAINT ck_source_references_source_object_kind CHECK (source_object_kind IN ('EMPLOYEE', 'ORG_UNIT', 'POSITION', 'LEGAL_ENTITY')),
    source_id           bigint      NOT NULL,
    own_id              uuid        NOT NULL,
    confirmation_status text        NOT NULL CONSTRAINT ck_source_references_confirmation_status CHECK (confirmation_status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    effective_from_date date,
    effective_to_date   date,
    created_by          uuid,
    decided_by          uuid,
    decided_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    event_count         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_source_references PRIMARY KEY (source_reference_id),
    CONSTRAINT ck_source_references_accounting_code_positive
        CHECK (NOT (source_system = 'ACCOUNTING_SYSTEM' AND source_object_kind = 'LEGAL_ENTITY') OR source_id > 0),
    CONSTRAINT ck_source_references_period
        CHECK (CASE WHEN source_object_kind = 'LEGAL_ENTITY'
                    THEN effective_from_date IS NOT NULL AND (effective_to_date IS NULL OR effective_to_date >= effective_from_date)
                    ELSE effective_from_date IS NULL AND effective_to_date IS NULL END),
    CONSTRAINT ck_source_references_decision
        CHECK ((decided_by IS NULL) = (decided_at IS NULL) AND (decided_by IS NULL OR confirmation_status <> 'PENDING')),
    CONSTRAINT ck_source_references_decider_is_not_author CHECK (created_by IS NULL OR decided_by IS NULL OR decided_by <> created_by),
    CONSTRAINT ck_source_references_manual_row_is_decided_by_someone CHECK (created_by IS NULL OR confirmation_status = 'PENDING' OR decided_by IS NOT NULL),
    CONSTRAINT ck_source_references_accounting_code_is_manual_registration
        CHECK (NOT (source_system = 'ACCOUNTING_SYSTEM' AND source_object_kind = 'LEGAL_ENTITY') OR created_by IS NOT NULL),
    CONSTRAINT ex_source_references_source_id EXCLUDE USING gist (source_system WITH =, source_object_kind WITH =, source_id WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (confirmation_status = 'CONFIRMED' AND source_object_kind = 'LEGAL_ENTITY'),
    CONSTRAINT ex_source_references_own_id EXCLUDE USING gist (source_system WITH =, source_object_kind WITH =, own_id WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (confirmation_status = 'CONFIRMED' AND source_object_kind = 'LEGAL_ENTITY')
);
CREATE UNIQUE INDEX uk_source_references_source_id ON platform_integration.source_references (source_system, source_object_kind, source_id)
    WHERE confirmation_status = 'CONFIRMED' AND source_object_kind <> 'LEGAL_ENTITY';
CREATE UNIQUE INDEX uk_source_references_own_id ON platform_integration.source_references (source_system, source_object_kind, own_id)
    WHERE confirmation_status = 'CONFIRMED' AND source_object_kind <> 'LEGAL_ENTITY';
CREATE INDEX ix_source_references_pending ON platform_integration.source_references (source_system, source_object_kind, source_reference_id) WHERE confirmation_status = 'PENDING';
CREATE INDEX ix_source_references_own_id ON platform_integration.source_references (own_id, source_system, source_object_kind, source_reference_id);
COMMENT ON TABLE platform_integration.source_references IS '来源对照：外部系统或老系统中一个对象的标识与自有 ID 的对应，只在集成层；已确认行的来源标识与自有 ID 各自在任一时点唯一，待确认与已拒绝的行不占约束；粒度：每个来源对象的每次对应一行（带生效区间的类别每个区间一行）';
COMMENT ON COLUMN platform_integration.source_references.source_reference_id IS '来源对照 ID（UUIDv7）';
COMMENT ON COLUMN platform_integration.source_references.source_system IS '来源系统：ORG_SOURCE 组织源 / LEGACY_SYSTEM 老系统 / ACCOUNTING_SYSTEM 核算系统；取值只在集成层出现';
COMMENT ON COLUMN platform_integration.source_references.source_object_kind IS '来源对象类别：EMPLOYEE 员工 / ORG_UNIT 组织单元 / POSITION 岗位 / LEGAL_ENTITY 法人主体；决定自有 ID 指向哪类自有对象，也是自然键的一部分';
COMMENT ON COLUMN platform_integration.source_references.source_id IS '来源标识：来源系统中该对象的 64 位有符号整数，可为负数，原值存放；核算系统的法人编码须大于 0；接口以十进制字符串传输，事件不带，领域模块不用，只在集成层与外部系统之间使用';
COMMENT ON COLUMN platform_integration.source_references.own_id IS '自有 ID：该对象在本系统中的 UUIDv7，类别决定指向哪类对象；跨 schema 只按 ID 引用，不建外键';
COMMENT ON COLUMN platform_integration.source_references.confirmation_status IS '确认状态：PENDING 待确认 / CONFIRMED 已确认 / REJECTED 已拒绝；来源同步与导入写入即为已确认，人工登记的核算组织编码先待确认，由他人确认或拒绝；出站与同步只读已确认行';
COMMENT ON COLUMN platform_integration.source_references.effective_from_date IS '生效日（含）：带生效区间的类别（法人主体）必填；为空表示该类别不带区间，对照在已确认期间一直有效';
COMMENT ON COLUMN platform_integration.source_references.effective_to_date IS '失效日（不含）：由确认后继的对照时关闭；等于生效日表示作废（同日更正，空区间不占排他约束）；为空表示至今有效或该类别不带区间';
COMMENT ON COLUMN platform_integration.source_references.created_by IS '录入人用户 ID：人工登记的人，确认人不得是此人；核算类别只由人工登记，为空只表示由来源同步分配';
COMMENT ON COLUMN platform_integration.source_references.decided_by IS '决定人用户 ID：确认或拒绝人工登记的人；为空表示尚未决定，或由来源同步分配而无需决定';
COMMENT ON COLUMN platform_integration.source_references.decided_at IS '决定时间；为空表示尚未决定，或由来源同步分配而无需决定';
COMMENT ON COLUMN platform_integration.source_references.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.source_references.updated_at IS '更新时间';
COMMENT ON COLUMN platform_integration.source_references.version IS '乐观锁版本';
COMMENT ON COLUMN platform_integration.source_references.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

CREATE FUNCTION platform_integration.guard_source_reference_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF NEW.source_reference_id IS DISTINCT FROM OLD.source_reference_id OR NEW.source_system IS DISTINCT FROM OLD.source_system
       OR NEW.source_object_kind IS DISTINCT FROM OLD.source_object_kind OR NEW.source_id IS DISTINCT FROM OLD.source_id
       OR NEW.own_id IS DISTINCT FROM OLD.own_id OR NEW.effective_from_date IS DISTINCT FROM OLD.effective_from_date
       OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'a source reference never has its source id, own id, kind, system, start or author rewritten' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.confirmation_status IS DISTINCT FROM OLD.confirmation_status AND OLD.confirmation_status <> 'PENDING' THEN
        RAISE EXCEPTION 'a source reference that is % cannot become %', OLD.confirmation_status, NEW.confirmation_status USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.effective_to_date IS DISTINCT FROM OLD.effective_to_date AND OLD.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'the end of a source reference period is set once' USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.decided_by IS NOT NULL AND (NEW.decided_by IS DISTINCT FROM OLD.decided_by OR NEW.decided_at IS DISTINCT FROM OLD.decided_at) THEN
        RAISE EXCEPTION 'a decision on a source reference cannot be changed or taken back' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_source_references_guard
    BEFORE UPDATE ON platform_integration.source_references
    FOR EACH ROW EXECUTE FUNCTION platform_integration.guard_source_reference_change();

CREATE FUNCTION platform_integration.reject_source_reference_deletion() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'platform_integration.source_references is closed, never deleted: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;
CREATE TRIGGER tg_source_references_never_deleted BEFORE DELETE ON platform_integration.source_references
    FOR EACH ROW EXECUTE FUNCTION platform_integration.reject_source_reference_deletion();
CREATE TRIGGER tg_source_references_never_deleted_truncate BEFORE TRUNCATE ON platform_integration.source_references
    FOR EACH STATEMENT EXECUTE FUNCTION platform_integration.reject_source_reference_deletion();
