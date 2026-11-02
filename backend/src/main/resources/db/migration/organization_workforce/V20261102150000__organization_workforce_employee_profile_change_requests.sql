-- #980（设计 PR #996，用户 2026-11-02 Q1 a、Q2 a）：企业微信账号改绑的第二人复核申请表。只建表、触发器与索引，没有应用代码。
CREATE TABLE organization_workforce.employee_profile_change_requests (
    employee_profile_change_request_id uuid        NOT NULL,
    employee_id                        uuid        NOT NULL,
    requested_by                       uuid        NOT NULL,
    requested_at                       timestamptz NOT NULL,
    base_display_name                  text        NOT NULL,
    base_gender_kind                        text,
    base_email_address                         text,
    base_wecom_user_id                 text,
    display_name                       text        NOT NULL,
    gender_kind                             text,
    email_address                              text,
    wecom_user_id                      text,
    request_status                     text        NOT NULL,
    decided_by                         uuid,
    decided_at                         timestamptz,
    void_reason_kind                   text,
    decision_reason                    text,
    version                            bigint      NOT NULL DEFAULT 0,
    created_at                         timestamptz NOT NULL,
    updated_at                         timestamptz NOT NULL,
    CONSTRAINT pk_employee_profile_change_requests PRIMARY KEY (employee_profile_change_request_id),
    CONSTRAINT fk_employee_profile_change_requests_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT ck_employee_profile_change_requests_status CHECK (request_status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'VOIDED')),
    CONSTRAINT ck_employee_profile_change_requests_decision CHECK ((request_status = 'PENDING') = (decided_by IS NULL) AND (decided_by IS NULL) = (decided_at IS NULL)),
    CONSTRAINT ck_employee_profile_change_requests_two_people CHECK (decided_by IS NULL OR decided_by <> requested_by OR request_status = 'REJECTED'),
    CONSTRAINT ck_employee_profile_change_requests_wecom_moves CHECK (wecom_user_id IS DISTINCT FROM base_wecom_user_id),
    CONSTRAINT ck_employee_profile_change_requests_void_reason CHECK ((request_status = 'VOIDED') = (void_reason_kind IS NOT NULL)
        AND (void_reason_kind IS NULL OR void_reason_kind IN ('BASE_CHANGED', 'WECOM_USER_ID_TAKEN'))),
    CONSTRAINT ck_employee_profile_change_requests_decision_reason CHECK ((request_status = 'REJECTED') = (decision_reason IS NOT NULL)
        AND (decision_reason IS NULL OR (char_length(decision_reason) BETWEEN 1 AND 500 AND btrim(decision_reason) <> ''))),
    CONSTRAINT ck_employee_profile_change_requests_display_name CHECK (char_length(display_name) <= 500 AND btrim(display_name) <> ''
        AND char_length(base_display_name) <= 500 AND btrim(base_display_name) <> ''),
    CONSTRAINT ck_employee_profile_change_requests_gender CHECK ((gender_kind IS NULL OR gender_kind IN ('MALE', 'FEMALE')) AND (base_gender_kind IS NULL OR base_gender_kind IN ('MALE', 'FEMALE'))),
    CONSTRAINT ck_employee_profile_change_requests_email CHECK ((email_address IS NULL OR (char_length(email_address) <= 100 AND btrim(email_address) <> ''))
        AND (base_email_address IS NULL OR (char_length(base_email_address) <= 100 AND btrim(base_email_address) <> '')))
);
CREATE UNIQUE INDEX uk_employee_profile_change_requests_one_pending_per_employee
    ON organization_workforce.employee_profile_change_requests (employee_id) WHERE request_status = 'PENDING';
CREATE INDEX ix_employee_profile_change_requests_employee_id ON organization_workforce.employee_profile_change_requests (employee_id, requested_at);

COMMENT ON TABLE organization_workforce.employee_profile_change_requests IS '员工档案变更的第二人复核申请：改绑或清空已设企业微信用户 ID、且账号持有员工角色以外角色时，整份拟改档案等第二个人确认再生效；只追加，申请人可撤回。粒度：每次申请一行';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.employee_profile_change_request_id IS '申请 ID（UUIDv7，由应用层生成）';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.employee_id IS '员工 ID：被改档案的员工';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.requested_by IS '申请人（用户账号 ID，跨 schema 只存 ID）';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.requested_at IS '申请时刻';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.base_display_name IS '申请时的姓名：确认时与当前档案比对，不同则作废';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.base_gender_kind IS '性别：申请时的值，MALE / FEMALE；为空表示当时未维护';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.base_email_address IS '企业邮箱：申请时的值；为空表示当时未设';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.base_wecom_user_id IS '申请时的企业微信用户 ID；为空只在清空后 30 天内的再填入申请里出现';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.display_name IS '拟改的姓名';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.gender_kind IS '性别：拟改的值，MALE / FEMALE；为空表示清空';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.email_address IS '企业邮箱：拟改的值；为空表示清空';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.wecom_user_id IS '拟改的企业微信用户 ID；为空表示清空；必须与申请时的不同';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.request_status IS '状态：PENDING 待确认 / CONFIRMED 已确认并生效 / REJECTED 已拒绝或申请人撤回 / VOIDED 已作废';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.decided_by IS '处理人（用户账号 ID）：确认、拒绝或作废当次的操作人；除申请人撤回外不得是申请人；为空表示待确认';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.decided_at IS '处理时刻；为空表示待确认';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.void_reason_kind IS '作废原因：BASE_CHANGED 档案在申请后被改过 / WECOM_USER_ID_TAKEN 拟改的企业微信用户 ID 已被别的员工占用；为空表示未作废';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.decision_reason IS '拒绝原因（1 至 500 字）：拒绝与撤回必填；为空表示未被拒绝';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.version IS '乐观锁版本';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.employee_profile_change_requests.updated_at IS '更新时间';

-- 只追加：待确认只能转为已确认、已拒绝、已作废，三者之后不再变；处理人、处理时刻、作废原因、拒绝原因只能由空变非空一次，其余内容列除更新时间与版本外不变；DELETE 只放行表属主（经清除函数），TRUNCATE 拒绝。
CREATE FUNCTION organization_workforce.refuse_employee_profile_change_request_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'employee profile change requests are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'employee profile change requests are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.request_status <> OLD.request_status AND NOT (OLD.request_status = 'PENDING' AND NEW.request_status IN ('CONFIRMED', 'REJECTED', 'VOIDED')) THEN
        RAISE EXCEPTION 'employee profile change request status cannot move from % to %', OLD.request_status, NEW.request_status USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['decided_by', 'decided_at', 'void_reason_kind', 'decision_reason'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'request_status' - 'decided_by' - 'decided_at' - 'void_reason_kind' - 'decision_reason' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'request_status' - 'decided_by' - 'decided_at' - 'void_reason_kind' - 'decision_reason' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only the status and decision columns of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_employee_profile_change_request_rewrite() IS '档案变更申请只追加的触发器：待确认只转为已确认、已拒绝、已作废，处理人、处理时刻、作废原因、拒绝原因只放行由空变非空一次，其余内容列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_employee_profile_change_requests_append_only BEFORE UPDATE OR DELETE ON organization_workforce.employee_profile_change_requests
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_employee_profile_change_request_rewrite();
CREATE TRIGGER tg_employee_profile_change_requests_append_only_truncate BEFORE TRUNCATE ON organization_workforce.employee_profile_change_requests
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_employee_profile_change_request_rewrite();
