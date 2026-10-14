-- 数据打分卡 K9：表注释写明粒度。
COMMENT ON TABLE platform_audit.audit_entries IS '审计条目：只追加，数据库触发器禁止修改和删除（G34）；粒度：每个被审计的动作或领域事件一行';
