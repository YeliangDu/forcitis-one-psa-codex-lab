## 原话

「ops会话我不准备更换，所以他的历史会话在压缩前要有个hook，及时存到文档里，关于运维部署的关键context要保存在文件里。」

## 落点

one-ops 维护 phase1a/infra/OPS_CONTEXT.md（每个完成点更新）；SessionStart(compact) 钩子 postcompact_context.sh 按 session_context.map 在压缩后注入该文档与压缩前快照；SESSION_PLAYBOOK §2.1 第 13 条；调度 75% 巡检时提醒更新
