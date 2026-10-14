-- 调度锁表：ShedLock（JDBC）用，一个作业一行
CREATE SCHEMA IF NOT EXISTS platform_scheduling;
COMMENT ON SCHEMA platform_scheduling IS '定时作业（权限流程与集成平台 BC · 平台运维）';

CREATE TABLE platform_scheduling.scheduler_locks (
    lock_name  text        PRIMARY KEY,
    lock_until timestamptz NOT NULL,
    locked_at  timestamptz NOT NULL,
    locked_by  text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE platform_scheduling.scheduler_locks IS '调度锁：ShedLock 作业互斥，一个作业一行';
COMMENT ON COLUMN platform_scheduling.scheduler_locks.lock_name IS '锁名 = 作业名 {subdomain}.{bc}.{name}';
COMMENT ON COLUMN platform_scheduling.scheduler_locks.lock_until IS '锁到期时间';
COMMENT ON COLUMN platform_scheduling.scheduler_locks.locked_at IS '加锁时间';
COMMENT ON COLUMN platform_scheduling.scheduler_locks.locked_by IS '持锁实例（主机名）';
COMMENT ON COLUMN platform_scheduling.scheduler_locks.created_at IS '创建时间';
COMMENT ON COLUMN platform_scheduling.scheduler_locks.updated_at IS '更新时间';
