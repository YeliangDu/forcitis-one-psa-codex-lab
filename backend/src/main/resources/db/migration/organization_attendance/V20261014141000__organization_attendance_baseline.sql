CREATE SCHEMA IF NOT EXISTS organization_attendance;
COMMENT ON SCHEMA organization_attendance IS '考勤与休假 BC · 1a 只含工作日历';

CREATE TABLE organization_attendance.work_calendar_years (
    id              uuid        NOT NULL,
    legal_entity_id uuid,
    year_number     integer     NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    event_count     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_work_calendar_years PRIMARY KEY (id),
    CONSTRAINT uk_work_calendar_years_legal_entity_id_year_number UNIQUE NULLS NOT DISTINCT (legal_entity_id, year_number),
    CONSTRAINT ck_work_calendar_years_year_range CHECK (year_number BETWEEN 2000 AND 2100)
);
COMMENT ON TABLE organization_attendance.work_calendar_years IS '工作日历年度：某个日历的一个已维护年份；没有这一行的年份视为未维护，依赖它的计算会失败；粒度：每个日历的每个年份一行';
COMMENT ON COLUMN organization_attendance.work_calendar_years.id IS '工作日历年度 ID（UUIDv7）';
COMMENT ON COLUMN organization_attendance.work_calendar_years.legal_entity_id IS '法人主体 ID；为空表示默认日历';
COMMENT ON COLUMN organization_attendance.work_calendar_years.year_number IS '日历年份';
COMMENT ON COLUMN organization_attendance.work_calendar_years.version IS '乐观锁版本';
COMMENT ON COLUMN organization_attendance.work_calendar_years.created_at IS '创建时间';
COMMENT ON COLUMN organization_attendance.work_calendar_years.updated_at IS '更新时间';
COMMENT ON COLUMN organization_attendance.work_calendar_years.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

CREATE TABLE organization_attendance.work_calendar_days (
    id                    uuid        NOT NULL,
    work_calendar_year_id uuid        NOT NULL,
    calendar_date         date        NOT NULL,
    day_kind              text        NOT NULL CHECK (day_kind IN ('HOLIDAY', 'MAKE_UP_WORKDAY')),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_work_calendar_days PRIMARY KEY (id),
    CONSTRAINT fk_work_calendar_days_work_calendar_year_id FOREIGN KEY (work_calendar_year_id)
        REFERENCES organization_attendance.work_calendar_years (id) ON DELETE CASCADE,
    CONSTRAINT uk_work_calendar_days_work_calendar_year_id_calendar_date UNIQUE (work_calendar_year_id, calendar_date)
);
COMMENT ON TABLE organization_attendance.work_calendar_days IS '工作日历日期：周一至周五上班、周末休息之外的例外日期；粒度：每个年份内每个例外日期一行';
COMMENT ON COLUMN organization_attendance.work_calendar_days.id IS '工作日历日期 ID（UUIDv7）';
COMMENT ON COLUMN organization_attendance.work_calendar_days.work_calendar_year_id IS '工作日历年度 ID：所属的已维护年份';
COMMENT ON COLUMN organization_attendance.work_calendar_days.calendar_date IS '例外日期';
COMMENT ON COLUMN organization_attendance.work_calendar_days.day_kind IS '类型：HOLIDAY 节假日 / MAKE_UP_WORKDAY 调休上班日（必须是周六或周日）';
COMMENT ON COLUMN organization_attendance.work_calendar_days.created_at IS '创建时间';
COMMENT ON COLUMN organization_attendance.work_calendar_days.updated_at IS '更新时间';
