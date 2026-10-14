-- 审批请求与发起轮次。Flowable 的 ACT_* 表由引擎自己管理（生产 flowable.database-schema-update=false，升级走专门手册）。
CREATE SCHEMA IF NOT EXISTS platform_approval;
COMMENT ON SCHEMA platform_approval IS '审批（权限流程与集成平台 BC · 流程引擎封装）';

CREATE TABLE platform_approval.approval_requests (
    id                  bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_type        text        NOT NULL,
    business_key        text        NOT NULL,
    initiator_id        bigint      NOT NULL,
    current_round       integer     NOT NULL CHECK (current_round >= 0),
    current_instance_id text,
    status              text        NOT NULL CHECK (status IN ('RUNNING', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    result_reason       text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_approval_requests_document UNIQUE (process_type, business_key)
);
COMMENT ON TABLE platform_approval.approval_requests IS '审批请求：每张业务单据一行，记录当前轮次、当前流程实例与状态';
COMMENT ON COLUMN platform_approval.approval_requests.id IS '主键';
COMMENT ON COLUMN platform_approval.approval_requests.process_type IS '流程类型（ProcessKey 枚举名）；与单据标识一起唯一，保证幂等发起（RULE-264/266）';
COMMENT ON COLUMN platform_approval.approval_requests.business_key IS '业务键：单据标识，由拥有该单据的业务模块给出';
COMMENT ON COLUMN platform_approval.approval_requests.initiator_id IS '发起人（人员 ID）：由拥有该单据的业务模块传入的当前用户；只有该人能再次发起或重新提交（I-5）';
COMMENT ON COLUMN platform_approval.approval_requests.current_round IS '当前轮次：从 1 开始，每次重新发起加 1（RULE-278）；0 表示刚建立、尚未发起';
COMMENT ON COLUMN platform_approval.approval_requests.current_instance_id IS '当前（或最近一轮）流程实例 ID';
COMMENT ON COLUMN platform_approval.approval_requests.status IS '审批状态：RUNNING / RETURNED / APPROVED / REJECTED / CANCELLED（RULE-289），用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_requests.result_reason IS '驳回、退回原因';
COMMENT ON COLUMN platform_approval.approval_requests.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.approval_requests.updated_at IS '更新时间';
COMMENT ON COLUMN platform_approval.approval_requests.version IS '乐观锁版本';

CREATE INDEX ix_approval_requests_instance ON platform_approval.approval_requests (current_instance_id);

CREATE TABLE platform_approval.start_rounds (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    process_type  text        NOT NULL,
    business_key  text        NOT NULL,
    round_number  integer     NOT NULL CHECK (round_number >= 1),
    initiator_id  bigint      NOT NULL,
    instance_id   text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_start_rounds_round UNIQUE (process_type, business_key, round_number)
);
COMMENT ON TABLE platform_approval.start_rounds IS '发起轮次：每次发起一行；唯一约束消除并发重号（RULE-278）';
COMMENT ON COLUMN platform_approval.start_rounds.id IS '主键';
COMMENT ON COLUMN platform_approval.start_rounds.process_type IS '流程类型（ProcessKey 枚举名）';
COMMENT ON COLUMN platform_approval.start_rounds.business_key IS '业务键：单据标识';
COMMENT ON COLUMN platform_approval.start_rounds.round_number IS '轮次：同一单据内从 1 递增';
COMMENT ON COLUMN platform_approval.start_rounds.initiator_id IS '发起人：该轮发起人（人员 ID）';
COMMENT ON COLUMN platform_approval.start_rounds.instance_id IS '该轮的流程实例 ID';
COMMENT ON COLUMN platform_approval.start_rounds.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.start_rounds.updated_at IS '更新时间';
