## 原话

关键 PR 规矩写进 SESSION_PLAYBOOK：PR 定为第一优先后冻结范围，新想法另开 PR、原 PR 只修复审意见（#668 现在就收口）；派出复审 20 分钟无结果立刻查是否在跑、不在就重派，巡检写明每个在开 PR 卡在哪步；mq 支持急标记，急 PR 排最前跑 CI、期间不被 CANCEL-RUN 取消；一个 PR 只做一件事。mq.py 首次合入失败率（24h 16%）归 one-merge 修：生成物冲突认 openapi.json 与 validators.ts 并在 CONFLICT 日志带文件名；update-branch 后轮询到新头且新 CI 已启动、moved 只在新 CI 完成后计数；环境失败（docker 未起等）不计作者失败、等恢复再跑、日志记 ENV，mq_selftest _signals 时间断言移出合并检查

## 落点

规则表 TEAM-45；GOV-1023-MRG-10；#668 收口
