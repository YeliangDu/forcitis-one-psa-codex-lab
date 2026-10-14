COMMENT ON TABLE cost_timesheet.consumed_events IS '已处理的事件：幂等记录；粒度：每个消费方每个事件标识一行，只追加；消费方按事件标识去重，事件至少投递一次';
