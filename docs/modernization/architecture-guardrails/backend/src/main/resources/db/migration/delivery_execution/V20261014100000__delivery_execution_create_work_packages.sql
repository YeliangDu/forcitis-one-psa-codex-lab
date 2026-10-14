CREATE SCHEMA IF NOT EXISTS delivery_execution;
COMMENT ON SCHEMA delivery_execution IS '项目交付执行 BC';

CREATE TABLE delivery_execution.work_packages (
    id            bigint        PRIMARY KEY,
    planned_hours numeric(10, 2) NOT NULL,
    actual_hours  numeric(10, 2) NOT NULL,
    status        text          NOT NULL CHECK (status IN ('IN_PROGRESS', 'DONE', 'LOCKED', 'REWORK')),
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    version       bigint        NOT NULL DEFAULT 0
);
COMMENT ON TABLE delivery_execution.work_packages IS '任务包（WorkPackage）：WBS 最底层的工时承载单元';
COMMENT ON COLUMN delivery_execution.work_packages.id IS '任务包 ID（沿用老系统雪花 ID）';
COMMENT ON COLUMN delivery_execution.work_packages.planned_hours IS '计划工时（小时，2 位小数）';
COMMENT ON COLUMN delivery_execution.work_packages.actual_hours IS '实际工时（已审批，小时）';
COMMENT ON COLUMN delivery_execution.work_packages.status IS '状态：IN_PROGRESS / DONE / LOCKED / REWORK';
COMMENT ON COLUMN delivery_execution.work_packages.created_at IS '创建时间';
COMMENT ON COLUMN delivery_execution.work_packages.updated_at IS '更新时间';
COMMENT ON COLUMN delivery_execution.work_packages.version IS '乐观锁版本';
