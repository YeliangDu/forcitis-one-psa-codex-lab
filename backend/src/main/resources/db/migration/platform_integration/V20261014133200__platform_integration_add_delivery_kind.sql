ALTER TABLE platform_integration.outbound_deliveries
    ADD COLUMN kind       text NOT NULL DEFAULT 'CARD' CHECK (kind IN ('CARD', 'HANDLED_NOTE')),
    ADD COLUMN target_key text;
ALTER TABLE platform_integration.outbound_deliveries
    ADD CONSTRAINT outbound_deliveries_handled_note_target CHECK ((kind = 'HANDLED_NOTE') = (target_key IS NOT NULL));
COMMENT ON COLUMN platform_integration.outbound_deliveries.kind IS '投递种类：CARD 发送卡片 / HANDLED_NOTE 把已发送的卡片替换为已处理说明；两种共用同一套重试与死信';
COMMENT ON COLUMN platform_integration.outbound_deliveries.target_key IS '被替换卡片的去重键；仅 HANDLED_NOTE 投递有值，其说明文字存放在 description';
COMMENT ON COLUMN platform_integration.outbound_deliveries.attempt_count IS '已尝试发送次数；也是认领编号：状态变更只在编号与认领时一致时生效，过期的认领写入被拒绝';
