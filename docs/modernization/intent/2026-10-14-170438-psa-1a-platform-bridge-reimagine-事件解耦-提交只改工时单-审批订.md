## 原话

事件解耦：提交只改工时单，审批订阅后发起 (Recommended)

## 落点

H-1：审批请求与任务合为一个聚合（任务为内部实体，共用 version 与事件序号）；工时单发布 TimesheetSubmitted，审批订阅后发起；提交接口不再同步返回 approvalInstanceId，前端看工时单状态
