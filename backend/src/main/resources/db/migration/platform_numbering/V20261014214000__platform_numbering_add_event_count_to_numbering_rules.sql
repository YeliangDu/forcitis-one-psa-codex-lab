-- 聚合的乐观锁 version 与事件序号 event_count 分开持久化：version 每次保存加 1，event_count 每发一个事件加 1（信封的 aggregateVersion）。
-- 回填假设：此前聚合的每次保存恰好发出一个事件，所以已有行的 event_count 取其 version。
ALTER TABLE platform_numbering.numbering_rules ADD COLUMN event_count bigint NOT NULL DEFAULT 0;
UPDATE platform_numbering.numbering_rules SET event_count = version;
COMMENT ON COLUMN platform_numbering.numbering_rules.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
