## 原话

合入策略调整：PR 头经核对后自动入队（opus 首轮复审通过＋调度/one-quality 批准针对的 head 与当前 PR 头一致＋轻量 CI 绿＋无冲突，mq 记入队事件时间、批准人、head，批准后作者再推送自动出队重核），取代自动入队永久关闭、只能 one-merge 手动 enqueue；文档合集每小时一次；squash 不改仍用 --merge。one-merge 按序：合批测试（已批 2–3 个小 code PR 一列跑完整 CI 一起合，扩展 merge_train.py，整列红二分，目标 code 道每小时 6–8 个、每天 ≥120）；cancel_others 只取消已打 queued 的重测试不取消轻量 CI；OpenAPI 三个生成物不入仓由构建生成或 mq 自动重生成；补日志修 bug（入队事件、CONFLICT 带文件名、moved 空转、docker 类失败不算作者、secret scan 发布脚本自测只在相关文件变动时跑）。降冲突：rulings.yaml、COORDINATOR_LEDGER.md 改目录一条一文件、索引生成不入仓；sessions 画像只归会话本人、状态只写卡；代码 PR 尽量 4 小时内合完，作者平时不自己合 main，入队时交 mq 同步，gapscan 报冲突再合。保留 --match-head-commit、HEAD-CHANGED 退回、只认完整版必需检查、main 红两道暂停、只差文档复用 CI、生成物不入仓、失败只重跑一次

## 落点

规则表 TEAM-45、TEAM-47、TEAM-53；GOV-1023-MRG-07/08/10、GOV-1023-QUA-02；台账与裁定目录化

## 取代

- 2026-10-23-115815-one-coordinator-采纳-自动入队开关保持关闭-加入.md
