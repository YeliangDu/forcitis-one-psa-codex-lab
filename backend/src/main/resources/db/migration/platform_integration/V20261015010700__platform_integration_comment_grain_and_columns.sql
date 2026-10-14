-- 数据打分卡 K8、K9：表注释写明粒度，补全缺失的列注释（事件发布登记表由框架固定，不改）。
COMMENT ON TABLE platform_integration.outbound_deliveries IS '外发投递：企业微信文本卡片的待发队列与结果，事务提交后由投递作业发送，失败退避重试，超过次数进死信；粒度：每张待发或已发的卡片一行（按去重键唯一）';
COMMENT ON COLUMN platform_integration.outbound_deliveries.version IS '乐观锁版本';
