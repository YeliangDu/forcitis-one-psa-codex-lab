-- F-1 data scope block 2 (#729): the review items for authorizations (permission_adjustments).
-- 待调整权限（ADR-0032 第 4、5 项，ADR-0044 第 2、3 项，RULE-387）：只提醒，不授予不收回。
CREATE TABLE platform_identity.permission_adjustments (
    permission_adjustment_id          uuid        NOT NULL,
    kind                              text        NOT NULL,
    origin_kind                       text        NOT NULL,
    status                            text        NOT NULL,
    user_id                           uuid,
    role_id                           uuid,
    org_unit_id                       uuid,
    trigger_personnel_command_id      uuid,
    trigger_org_structure_command_id  uuid,
    trigger_field_kind                text,
    before_org_unit_id                uuid,
    after_org_unit_id                 uuid,
    before_position_id                uuid,
    after_position_id                 uuid,
    recipient_employee_id             uuid,
    original_permission_adjustment_id uuid,
    escalation_due_at                 timestamptz NOT NULL,
    escalated_at                      timestamptz,
    upheld_at                         timestamptz,
    upheld_by                         uuid,
    uphold_reason                     text,
    business_owner_employee_id        uuid,
    uphold_expires_at                 timestamptz,
    expired_at                        timestamptz,
    is_held_at_expiry                 boolean,
    regeneration_decided_at           timestamptz,
    adjusted_at                       timestamptz,
    adjusted_by                       uuid,
    adjusted_authorization_request_id uuid,
    adjusted_audit_entry_id           uuid,
    closed_at                         timestamptz,
    closure_personnel_command_id      uuid,
    version                           bigint      NOT NULL DEFAULT 0,
    event_count                       bigint      NOT NULL DEFAULT 0,
    created_at                        timestamptz NOT NULL,
    updated_at                        timestamptz NOT NULL,
    CONSTRAINT pk_permission_adjustments PRIMARY KEY (permission_adjustment_id),
    CONSTRAINT fk_permission_adjustments_user_id FOREIGN KEY (user_id)
        REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permission_adjustments_upheld_by FOREIGN KEY (upheld_by)
        REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permission_adjustments_adjusted_by FOREIGN KEY (adjusted_by)
        REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permission_adjustments_role_id FOREIGN KEY (role_id)
        REFERENCES platform_identity.roles (role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permission_adjustments_original_permission_adjustment_id FOREIGN KEY (original_permission_adjustment_id)
        REFERENCES platform_identity.permission_adjustments (permission_adjustment_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permission_adjustments_adjusted_authorization_request_id FOREIGN KEY (adjusted_authorization_request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT uk_permission_adjustments_original_permission_adjustment_id UNIQUE (original_permission_adjustment_id),
    CONSTRAINT ck_permission_adjustments_kind CHECK (kind IN ('SENSITIVE_ROLE_REVIEW', 'SCOPE_REVIEW')),
    CONSTRAINT ck_permission_adjustments_origin_kind CHECK (origin_kind IN ('ASSIGNMENT_CHANGE', 'UPHOLD_EXPIRY', 'ORG_UNIT_INACTIVE')),
    CONSTRAINT ck_permission_adjustments_status CHECK (status IN ('OPEN', 'UPHELD', 'EXPIRED', 'ADJUSTED', 'CLOSED_BY_SEPARATION')),
    CONSTRAINT ck_permission_adjustments_trigger_field_kind CHECK (trigger_field_kind IN ('ORG_UNIT', 'POSITION')),
    CONSTRAINT ck_permission_adjustments_kind_origin CHECK (
        (kind = 'SENSITIVE_ROLE_REVIEW' AND origin_kind IN ('ASSIGNMENT_CHANGE', 'UPHOLD_EXPIRY'))
        OR (kind = 'SCOPE_REVIEW' AND origin_kind IN ('ASSIGNMENT_CHANGE', 'ORG_UNIT_INACTIVE'))),
    CONSTRAINT ck_permission_adjustments_subject CHECK (CASE origin_kind
        WHEN 'ORG_UNIT_INACTIVE' THEN user_id IS NULL AND role_id IS NOT NULL AND org_unit_id IS NOT NULL
        ELSE user_id IS NOT NULL AND role_id IS NULL AND org_unit_id IS NULL END),
    CONSTRAINT ck_permission_adjustments_assignment_change CHECK (
        (origin_kind = 'ASSIGNMENT_CHANGE') = (trigger_field_kind IS NOT NULL)
        AND (origin_kind = 'ASSIGNMENT_CHANGE') = (before_org_unit_id IS NOT NULL AND after_org_unit_id IS NOT NULL)
        AND (origin_kind = 'ASSIGNMENT_CHANGE' OR (before_org_unit_id IS NULL AND after_org_unit_id IS NULL
                                                   AND before_position_id IS NULL AND after_position_id IS NULL))
        AND (trigger_field_kind IS DISTINCT FROM 'ORG_UNIT' OR before_org_unit_id <> after_org_unit_id)),
    CONSTRAINT ck_permission_adjustments_trigger_command CHECK (
        (origin_kind = 'ASSIGNMENT_CHANGE') = (trigger_personnel_command_id IS NOT NULL)
        AND (origin_kind = 'ORG_UNIT_INACTIVE') = (trigger_org_structure_command_id IS NOT NULL)),
    CONSTRAINT ck_permission_adjustments_recipient CHECK (
        (kind <> 'SCOPE_REVIEW' OR origin_kind <> 'ASSIGNMENT_CHANGE' OR trigger_field_kind = 'ORG_UNIT')
        AND (recipient_employee_id IS NULL OR (kind = 'SCOPE_REVIEW' AND origin_kind = 'ASSIGNMENT_CHANGE'))),
    CONSTRAINT ck_permission_adjustments_original CHECK (
        (original_permission_adjustment_id IS NOT NULL) = (origin_kind = 'UPHOLD_EXPIRY')
        AND original_permission_adjustment_id IS DISTINCT FROM permission_adjustment_id),
    CONSTRAINT ck_permission_adjustments_uphold CHECK (
        (status IN ('UPHELD', 'EXPIRED')) = (upheld_at IS NOT NULL)
        AND (upheld_at IS NULL) = (upheld_by IS NULL)
        AND (upheld_at IS NULL) = (uphold_reason IS NULL)),
    CONSTRAINT ck_permission_adjustments_uphold_terms CHECK (
        (kind = 'SENSITIVE_ROLE_REVIEW' AND upheld_at IS NOT NULL) = (business_owner_employee_id IS NOT NULL)
        AND (business_owner_employee_id IS NULL) = (uphold_expires_at IS NULL)),
    CONSTRAINT ck_permission_adjustments_uphold_reason CHECK (char_length(uphold_reason) <= 500 AND btrim(uphold_reason) <> ''),
    CONSTRAINT ck_permission_adjustments_uphold_expires_at CHECK (uphold_expires_at > upheld_at),
    CONSTRAINT ck_permission_adjustments_expiry CHECK (
        (status = 'EXPIRED') = (expired_at IS NOT NULL)
        AND (expired_at IS NULL OR (uphold_expires_at IS NOT NULL AND expired_at >= uphold_expires_at))
        AND (expired_at IS NOT NULL OR (is_held_at_expiry IS NULL AND regeneration_decided_at IS NULL))
        AND (expired_at IS NULL OR is_held_at_expiry IS NOT NULL)
        AND (expired_at IS NULL OR is_held_at_expiry OR regeneration_decided_at IS NOT NULL)),
    CONSTRAINT ck_permission_adjustments_adjusted CHECK (
        (status = 'ADJUSTED') = (adjusted_at IS NOT NULL)
        AND (adjusted_at IS NULL) = (num_nonnulls(adjusted_authorization_request_id, adjusted_audit_entry_id) = 0)
        AND num_nonnulls(adjusted_authorization_request_id, adjusted_audit_entry_id) <= 1
        AND (adjusted_by IS NULL OR adjusted_at IS NOT NULL)
        AND (adjusted_at IS NULL OR adjusted_by IS NOT NULL OR origin_kind = 'ORG_UNIT_INACTIVE')),
    CONSTRAINT ck_permission_adjustments_closed CHECK (
        (status = 'CLOSED_BY_SEPARATION') = (closed_at IS NOT NULL)
        AND (closed_at IS NULL) = (closure_personnel_command_id IS NULL)
        AND (status <> 'CLOSED_BY_SEPARATION' OR user_id IS NOT NULL)),
    CONSTRAINT ck_permission_adjustments_not_own CHECK (
        user_id IS NULL OR ((upheld_by IS NULL OR upheld_by <> user_id) AND (adjusted_by IS NULL OR adjusted_by <> user_id))),
    CONSTRAINT ck_permission_adjustments_escalation_due_at CHECK (escalation_due_at > created_at)
);
CREATE UNIQUE INDEX uk_permission_adjustments_assignment_trigger ON platform_identity.permission_adjustments
    (kind, user_id, trigger_personnel_command_id, trigger_field_kind) WHERE origin_kind = 'ASSIGNMENT_CHANGE';
CREATE UNIQUE INDEX uk_permission_adjustments_inactive_org_unit ON platform_identity.permission_adjustments
    (role_id, org_unit_id) WHERE origin_kind = 'ORG_UNIT_INACTIVE' AND status IN ('OPEN', 'UPHELD');
CREATE INDEX ix_permission_adjustments_user_id ON platform_identity.permission_adjustments (user_id);
CREATE INDEX ix_permission_adjustments_upheld_by ON platform_identity.permission_adjustments (upheld_by);
CREATE INDEX ix_permission_adjustments_adjusted_by ON platform_identity.permission_adjustments (adjusted_by);
CREATE INDEX ix_permission_adjustments_role_id ON platform_identity.permission_adjustments (role_id);
CREATE INDEX ix_permission_adjustments_adjusted_authorization_request_id ON platform_identity.permission_adjustments (adjusted_authorization_request_id);
CREATE INDEX ix_permission_adjustments_created_at ON platform_identity.permission_adjustments (created_at DESC, permission_adjustment_id DESC);
CREATE INDEX ix_permission_adjustments_recipient_employee_id ON platform_identity.permission_adjustments
    (recipient_employee_id, created_at DESC, permission_adjustment_id DESC) WHERE recipient_employee_id IS NOT NULL;
CREATE INDEX ix_permission_adjustments_uphold_expires_at ON platform_identity.permission_adjustments (uphold_expires_at) WHERE status = 'UPHELD';
CREATE INDEX ix_permission_adjustments_escalation_due_at ON platform_identity.permission_adjustments (escalation_due_at)
    WHERE status = 'OPEN' AND escalated_at IS NULL;
CREATE INDEX ix_permission_adjustments_regeneration_pending ON platform_identity.permission_adjustments (expired_at)
    WHERE status = 'EXPIRED' AND regeneration_decided_at IS NULL;
COMMENT ON TABLE platform_identity.permission_adjustments IS '待调整权限：任职变化、部门停用或撤销，或已维持的条目到期后，提醒有权的人复核授权的待办条目；只链接到标准授予流程，本身不授予也不收回（ADR-0032 第 4、5 项，ADR-0044 第 2、3 项，RULE-387）；不含权限码与范围。为什么是一张宽表而不拆明细：单一聚合，每个条目只经过一次维持、到期、调整或关闭，阶段有限，各阶段的列互斥由 CHECK 承担；以后新增阶段列时先回看这条。粒度：每个条目一行';
COMMENT ON COLUMN platform_identity.permission_adjustments.permission_adjustment_id IS '待调整权限 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.permission_adjustments.kind IS '待调整权限类型：SENSITIVE_ROLE_REVIEW 敏感角色复核 / SCOPE_REVIEW 范围复核';
COMMENT ON COLUMN platform_identity.permission_adjustments.origin_kind IS '条目来源：ASSIGNMENT_CHANGE 任职变化 / UPHOLD_EXPIRY 维持到期（重新生成）/ ORG_UNIT_INACTIVE 自定义部门停用或撤销';
COMMENT ON COLUMN platform_identity.permission_adjustments.status IS '状态：OPEN 待处理 / UPHELD 已维持 / EXPIRED 已到期 / ADJUSTED 已调整 / CLOSED_BY_SEPARATION 因离职关闭';
COMMENT ON COLUMN platform_identity.permission_adjustments.user_id IS '用户 ID：当事人的账号；为空表示条目关于角色（部门停用或撤销）';
COMMENT ON COLUMN platform_identity.permission_adjustments.role_id IS '角色 ID；为空表示条目关于人';
COMMENT ON COLUMN platform_identity.permission_adjustments.org_unit_id IS '组织单元 ID：被停用或撤销、仍被该角色版本引用的部门（跨 schema，不建外键）；为空表示不是部门停用或撤销产生的条目';
COMMENT ON COLUMN platform_identity.permission_adjustments.trigger_personnel_command_id IS '触发人事命令 ID：调动命令（EmployeeTransferred），跨 schema 不建外键（命令在 organization_workforce）；为空表示不是任职变化产生的条目';
COMMENT ON COLUMN platform_identity.permission_adjustments.trigger_org_structure_command_id IS '触发组织结构命令 ID：部门撤销或合并命令（OrgUnitDissolved、OrgUnitMerged），跨 schema 不建外键；为空表示不是部门停用或撤销产生的条目';
COMMENT ON COLUMN platform_identity.permission_adjustments.trigger_field_kind IS '触发字段：ORG_UNIT 部门 / POSITION 岗位，每个触发字段一条（ADR-0032 第 5 项第 2 条）；为 ORG_UNIT 时触发前后部门不同；为空表示不是任职变化产生的条目';
COMMENT ON COLUMN platform_identity.permission_adjustments.before_org_unit_id IS '触发前部门 ID（跨 schema）；为空表示不是任职变化产生的条目';
COMMENT ON COLUMN platform_identity.permission_adjustments.after_org_unit_id IS '触发后部门 ID（跨 schema）；为空表示不是任职变化产生的条目';
COMMENT ON COLUMN platform_identity.permission_adjustments.before_position_id IS '触发前岗位 ID（跨 schema）；为空表示不是任职变化产生的条目，或触发前没有岗位';
COMMENT ON COLUMN platform_identity.permission_adjustments.after_position_id IS '触发后岗位 ID（跨 schema）；为空表示不是任职变化产生的条目，或触发后没有岗位';
COMMENT ON COLUMN platform_identity.permission_adjustments.recipient_employee_id IS '收件人员工 ID：调动产生的范围复核给新部门负责人，取生成时的部门负责人（跨 schema）；为空表示不是调动产生的范围复核，或生成时新部门没有负责人——后者交组织数据管理员转交办理，保留还是调整仍由业务负责人确认（N1-09）';
COMMENT ON COLUMN platform_identity.permission_adjustments.original_permission_adjustment_id IS '原条目 ID：到期重新生成的条目引用被到期的条目，每个原条目至多一条（原条目只维持一次、有效至不变，故只按原条目去重）；外键 RESTRICT，清理按链整体进行；为空表示不是重新生成的条目';
COMMENT ON COLUMN platform_identity.permission_adjustments.escalation_due_at IS '升级期限：生成后第 5 个工作日结束（0032-Q3，RULE-394），生成时经 kernel 工作日端口按工作日历算出；到期仍待处理即升级给 account-role:update 持有人';
COMMENT ON COLUMN platform_identity.permission_adjustments.escalated_at IS '升级时刻；为空表示未升级（只升级一次，不收回）';
COMMENT ON COLUMN platform_identity.permission_adjustments.upheld_at IS '维持时刻；为空表示未维持';
COMMENT ON COLUMN platform_identity.permission_adjustments.upheld_by IS '维持人用户 ID（外键 user_accounts）；不得为当事人；为空表示未维持';
COMMENT ON COLUMN platform_identity.permission_adjustments.uphold_reason IS '维持原因：办理人填写，1 至 500 字；为空表示未维持';
COMMENT ON COLUMN platform_identity.permission_adjustments.business_owner_employee_id IS '业务负责人员工 ID：敏感角色复核维持时必填，须在职、不得为当事人，可以是办理人（ADR-0044 Q4 (b)，应用校验）；为空表示未维持或范围复核';
COMMENT ON COLUMN platform_identity.permission_adjustments.uphold_expires_at IS '有效至：维持后下一自然季度首日 00:00（Asia/Shanghai）的时刻，系统计算；为空表示未维持或范围复核（不设有效至）';
COMMENT ON COLUMN platform_identity.permission_adjustments.expired_at IS '到期时刻：每日到期作业按注入时钟转为已到期的时刻；为空表示未到期';
COMMENT ON COLUMN platform_identity.permission_adjustments.is_held_at_expiry IS '到期时是否在暂停期间：到期作业记下当事人当时是否被暂停（OrgDirectory.availabilityOn 的 separationHeld）；为空表示未到期；为真时重新生成等放行后的下一次作业判定';
COMMENT ON COLUMN platform_identity.permission_adjustments.regeneration_decided_at IS '重新生成判定时刻：判定是否重新生成条目的时刻（生成与否看原条目 ID 的引用）；为空表示未到期，或到期时在暂停期间、等放行后的下一次作业';
COMMENT ON COLUMN platform_identity.permission_adjustments.adjusted_at IS '标记已调整的时刻；为空表示未调整';
COMMENT ON COLUMN platform_identity.permission_adjustments.adjusted_by IS '标记已调整的用户 ID（外键 user_accounts）；为空表示未调整，或角色出新版本后由系统转为已调整';
COMMENT ON COLUMN platform_identity.permission_adjustments.adjusted_authorization_request_id IS '关联的授权申请 ID；与关联审计条目恰有一个；为空表示关联的是审计条目或未调整';
COMMENT ON COLUMN platform_identity.permission_adjustments.adjusted_audit_entry_id IS '关联的审计条目 ID（授予或角色版本的审计，跨 schema）；为空表示关联的是授权申请或未调整';
COMMENT ON COLUMN platform_identity.permission_adjustments.closed_at IS '关闭时刻（因离职）；为空表示未关闭';
COMMENT ON COLUMN platform_identity.permission_adjustments.closure_personnel_command_id IS '关闭人事命令 ID：使条目关闭的离职命令（EmployeeSeparated），跨 schema 不建外键；为空表示未关闭';
COMMENT ON COLUMN platform_identity.permission_adjustments.version IS '乐观锁版本（If-Match / ETag）';
COMMENT ON COLUMN platform_identity.permission_adjustments.event_count IS '事件序号：该聚合已发出的事件数，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON COLUMN platform_identity.permission_adjustments.created_at IS '创建时间（应用注入时钟）';
COMMENT ON COLUMN platform_identity.permission_adjustments.updated_at IS '更新时间（应用注入时钟）';
