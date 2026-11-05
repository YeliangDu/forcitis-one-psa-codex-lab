-- 招聘 R1：#1450，逐表依据签署的数据设计 organization-recruitment-r1；只建招聘需求一张表，无应聘者表。
CREATE SCHEMA IF NOT EXISTS organization_recruitment;
COMMENT ON SCHEMA organization_recruitment IS '招聘；R1 只有招聘需求一张表，无业务行为';

-- state_columns：关闭列成对；草稿不带提交列；其余状态三列齐备；批准时刻只在批准后或批准后关闭（取消、录满）时有值；时刻先后。
CREATE TABLE organization_recruitment.recruitment_requisitions (
    id uuid NOT NULL,
    requisition_number text,
    position_id uuid NOT NULL,
    org_unit_id uuid NOT NULL,
    legal_entity_id uuid NOT NULL,
    requested_by_employee_id uuid NOT NULL,
    requested_count integer NOT NULL,
    expected_start_date date,
    description_text text,
    status text NOT NULL,
    close_reason_kind text,
    approval_instance_id uuid,
    submitted_at timestamptz,
    approved_at timestamptz,
    closed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_recruitment_requisitions PRIMARY KEY (id),
    CONSTRAINT uk_recruitment_requisitions_requisition_number UNIQUE (requisition_number),
    CONSTRAINT uk_recruitment_requisitions_approval_instance_id UNIQUE (approval_instance_id),
    CONSTRAINT ck_recruitment_requisitions_requisition_number CHECK (char_length(requisition_number) BETWEEN 1 AND 40),
    CONSTRAINT ck_recruitment_requisitions_requested_count CHECK (requested_count BETWEEN 1 AND 999),
    CONSTRAINT ck_recruitment_requisitions_description_text CHECK (char_length(description_text) BETWEEN 1 AND 500 AND btrim(description_text) <> ''),
    CONSTRAINT ck_recruitment_requisitions_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'CLOSED')),
    CONSTRAINT ck_recruitment_requisitions_close_reason_kind CHECK (close_reason_kind IN ('REJECTED', 'WITHDRAWN', 'CANCELLED', 'FILLED')),
    CONSTRAINT ck_recruitment_requisitions_state_columns CHECK (
        (status = 'CLOSED') = (close_reason_kind IS NOT NULL)
        AND (close_reason_kind IS NULL) = (closed_at IS NULL)
        AND CASE WHEN status = 'DRAFT'
                 THEN submitted_at IS NULL AND approval_instance_id IS NULL AND requisition_number IS NULL AND approved_at IS NULL
                 ELSE submitted_at IS NOT NULL AND approval_instance_id IS NOT NULL AND requisition_number IS NOT NULL END
        AND (approved_at IS NOT NULL) = (status = 'APPROVED' OR (status = 'CLOSED' AND close_reason_kind IN ('CANCELLED', 'FILLED')))
        AND (submitted_at IS NULL OR approved_at IS NULL OR submitted_at <= approved_at)
        AND (approved_at IS NULL OR closed_at IS NULL OR approved_at <= closed_at)
        AND (submitted_at IS NULL OR closed_at IS NULL OR submitted_at <= closed_at))
);

CREATE INDEX ix_recruitment_requisitions_org_unit_id_status ON organization_recruitment.recruitment_requisitions (org_unit_id, status);
CREATE INDEX ix_recruitment_requisitions_requested_by_employee_id ON organization_recruitment.recruitment_requisitions (requested_by_employee_id);

-- 全部合法写入：草稿插入或整表单直接提交插入；草稿只改描述列；草稿到已提交；已提交到已批准或已关闭；已批准到已关闭；已关闭的行不再变；只删草稿。
CREATE FUNCTION organization_recruitment.guard_requisition_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    descriptive_changed boolean;
    submit_columns_changed boolean;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status NOT IN ('DRAFT', 'SUBMITTED') THEN
            RAISE EXCEPTION 'a requisition is created as DRAFT or SUBMITTED, not %', NEW.status USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'only a DRAFT requisition is deleted' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.id <> OLD.id OR NEW.legal_entity_id <> OLD.legal_entity_id OR NEW.requested_by_employee_id <> OLD.requested_by_employee_id
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'id, legal entity, requester and creation time of a requisition never change' USING ERRCODE = 'restrict_violation';
    END IF;
    descriptive_changed := (NEW.position_id, NEW.org_unit_id, NEW.requested_count, NEW.expected_start_date, NEW.description_text)
        IS DISTINCT FROM (OLD.position_id, OLD.org_unit_id, OLD.requested_count, OLD.expected_start_date, OLD.description_text);
    submit_columns_changed := (NEW.requisition_number, NEW.approval_instance_id, NEW.submitted_at)
        IS DISTINCT FROM (OLD.requisition_number, OLD.approval_instance_id, OLD.submitted_at);
    IF OLD.status = 'DRAFT' AND NEW.status = 'DRAFT' THEN
        RETURN NEW;
    ELSIF OLD.status = 'DRAFT' AND NEW.status = 'SUBMITTED' THEN
        RETURN NEW;
    ELSIF (OLD.status = 'SUBMITTED' AND NEW.status = 'APPROVED')
       OR (OLD.status = 'SUBMITTED' AND NEW.status = 'CLOSED' AND NEW.close_reason_kind IN ('REJECTED', 'WITHDRAWN'))
       OR (OLD.status = 'APPROVED' AND NEW.status = 'CLOSED' AND NEW.close_reason_kind IN ('CANCELLED', 'FILLED')) THEN
        IF descriptive_changed OR submit_columns_changed THEN
            RAISE EXCEPTION 'a requisition edge changes only its state columns' USING ERRCODE = 'restrict_violation';
        END IF;
        IF OLD.status = 'APPROVED' AND NEW.approved_at IS DISTINCT FROM OLD.approved_at THEN
            RAISE EXCEPTION 'closing an approved requisition keeps its approval time' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    -- 终态没有任何出边，已关闭的行在这里被拒。
    RAISE EXCEPTION 'requisition edge % to % (close reason %) is not allowed', OLD.status, NEW.status, NEW.close_reason_kind USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_recruitment_requisitions_change BEFORE INSERT OR UPDATE OR DELETE ON organization_recruitment.recruitment_requisitions
    FOR EACH ROW EXECUTE FUNCTION organization_recruitment.guard_requisition_change();

CREATE FUNCTION organization_recruitment.guard_requisition_truncate() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'recruitment requisitions are not truncated' USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_recruitment_requisitions_truncate BEFORE TRUNCATE ON organization_recruitment.recruitment_requisitions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_recruitment.guard_requisition_truncate();

COMMENT ON TABLE organization_recruitment.recruitment_requisitions IS '招聘需求。粒度：每个组织单元每次用人申请一行；已关闭的行不再变化';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.id IS '主键（UUIDv7，由客户端生成，重放的提交据此幂等）';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.requisition_number IS '招聘需求单据编号，提交时由编号模块发出；为空表示仍是草稿';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.position_id IS '岗位 ID，跨上下文不建外键';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.org_unit_id IS '组织单元 ID，用人的组织单元，读取的数据范围键，跨上下文不建外键';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.legal_entity_id IS '法人主体 ID，申请人在创建当日的成本归属法人，审批发起需要；创建后不变，跨上下文不建外键';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.requested_by_employee_id IS '申请人员工 ID，创建后不变，跨上下文不建外键';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.requested_count IS '需求人数，单位：人，1 至 999';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.expected_start_date IS '期望到岗日；为空表示未填写';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.description_text IS '岗位需求说明，只写岗位所需，不写应聘者姓名或个人信息；为空表示没有说明';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.status IS '状态：DRAFT 草稿、SUBMITTED 已提交（审批中）、APPROVED 已批准、CLOSED 已关闭（终态）';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.close_reason_kind IS '关闭原因：REJECTED 驳回、WITHDRAWN 撤回、CANCELLED 取消、FILLED 录满；为空表示未关闭';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.approval_instance_id IS '审批实例 ID，一份需求一个审批流程，跨上下文不建外键；为空表示尚未发起审批';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.submitted_at IS '提交时刻；为空表示尚未提交';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.approved_at IS '批准时刻；为空表示尚未批准，被驳回或撤回的需求也为空';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.closed_at IS '关闭时刻；为空表示尚未关闭';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.created_at IS '创建时刻';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.updated_at IS '最后更新时刻';
COMMENT ON COLUMN organization_recruitment.recruitment_requisitions.version IS '乐观锁版本号';
