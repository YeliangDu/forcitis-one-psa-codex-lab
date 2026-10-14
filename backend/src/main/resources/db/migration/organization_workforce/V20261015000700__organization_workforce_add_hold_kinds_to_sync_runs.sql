-- ADR-0028：暂停原因类型化，按类解除；暂停时的离职名单留作放行时比对。
ALTER TABLE organization_workforce.sync_runs
    ADD COLUMN hold_kind_codes     text[] NOT NULL DEFAULT '{}' CHECK (hold_kind_codes <@ ARRAY['SHRINK_GUARD', 'ADMINISTRATOR_SEPARATION']),
    ADD COLUMN released_kind_codes text[] NOT NULL DEFAULT '{}' CHECK (released_kind_codes <@ ARRAY['SHRINK_GUARD', 'ADMINISTRATOR_SEPARATION']);
COMMENT ON COLUMN organization_workforce.sync_runs.hold_kind_codes IS '暂停类型集合：SHRINK_GUARD 缩水保护 / ADMINISTRATOR_SEPARATION 管理员离职；状态为 HELD 时才有值，held_reason 只是给人看的说明';
COMMENT ON COLUMN organization_workforce.sync_runs.released_kind_codes IS '已解除的暂停类型：全部解除后才按最新源数据重跑并应用；源数据变化使暂停类型或离职名单改变时清空';
UPDATE organization_workforce.sync_runs SET hold_kind_codes = ARRAY['SHRINK_GUARD'] WHERE status = 'HELD';

CREATE TABLE organization_workforce.sync_run_held_separations (
    sync_run_id bigint      NOT NULL,
    employee_id bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_sync_run_held_separations PRIMARY KEY (sync_run_id, employee_id),
    CONSTRAINT fk_sync_run_held_separations_sync_run_id FOREIGN KEY (sync_run_id) REFERENCES organization_workforce.sync_runs (id) ON DELETE CASCADE
);
COMMENT ON TABLE organization_workforce.sync_run_held_separations IS '被暂停的同步运行当时的离职名单：放行重跑时比对是否出现新的管理员离职，并供有放行权限的人只读查看。粒度：每个暂停的运行的每个待离职员工一行';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.sync_run_id IS '同步记录 ID：被暂停的同步';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.employee_id IS '员工 ID：待离职';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.updated_at IS '更新时间';
