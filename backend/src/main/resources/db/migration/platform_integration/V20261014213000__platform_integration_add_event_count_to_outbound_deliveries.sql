-- 出站投递聚合不发事件，所以序号保持 0，不按 version 回填。
-- 聚合的乐观锁 version 与事件序号 event_count 分开持久化：version 每次保存加 1，event_count 每发一个事件加 1（信封的 aggregateVersion）。
ALTER TABLE platform_integration.outbound_deliveries ADD COLUMN event_count bigint NOT NULL DEFAULT 0;
COMMENT ON COLUMN platform_integration.outbound_deliveries.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
