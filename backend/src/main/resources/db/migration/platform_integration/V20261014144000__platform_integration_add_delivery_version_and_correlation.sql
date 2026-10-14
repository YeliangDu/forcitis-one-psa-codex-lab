-- 外发投递成为带状态机的聚合：写入带 version；correlation_id 让投递作业与发出的请求沿用产生它的请求、作业或事件处理的关联号。
ALTER TABLE platform_integration.outbound_deliveries
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN correlation_id uuid;
COMMENT ON COLUMN platform_integration.outbound_deliveries.correlation_id IS '关联号：产生这条投递的请求、作业或事件处理的关联号；投递作业恢复它，发出的请求带上它';
