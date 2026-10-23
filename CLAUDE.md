# FORCITIS one 现代化——会话须知

开场钩子注入「开工单」。没出现或显示不可用时，读 `docs/modernization/sessions/<会话名>.md`，再向 one-coordinator 要卡。

## 底线（详细条文与例外见 `docs/modernization/SESSION_PLAYBOOK.md`「1. 底线」）

1. 用中文：对话、报告、跨会话消息、子代理回复。
2. 老代码、老系统、外部系统只作证据，不照搬结构与命名；复刻老行为以生产数据为证。
3. 不写、不打印密钥，不调用会返回密钥的接口。
4. 生产只读：取证只用专用只读账号，明细与个人信息不进会话与仓库，不做任何生产写操作。
5. 须用户决定的事写收件箱等答复，不替用户拍板；用户口头决定当场用 `docs/modernization/new_intent.py` 记原话。
6. 不为过测试放宽或删断言；钩子或 CI 挡住按提示改，不绕过。

## 做什么就打开哪里

- 团队规则、派工、PR 与合并、消息：`docs/modernization/SESSION_PLAYBOOK.md`
- 写后端代码、DDL、迁移：先读 `backend/CLAUDE.md`
- 用语与确认页：先读 `docs/modernization/glossary/CLAUDE.md`
