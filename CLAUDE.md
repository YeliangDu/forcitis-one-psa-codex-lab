# FORCITIS one 现代化——会话须知

开工先读 `docs/modernization/RULES.md` 与本角色文件 `docs/modernization/roles/<角色>.md`（会话对应哪个角色见其「过渡映射」）。

## 底线（条文与执行见 RULES ①）

1. 用中文。
2. 老代码、老系统、外部系统只作证据，不照搬；复刻老行为以生产数据为证。
3. 不写、不打印密钥，不调用会返回密钥的接口。
4. 生产只读：只用专用只读账号，明细与个人信息不进会话与仓库。
5. 用户已定的照做；须用户决定的写进决定库等答复，口头决定用 new_intent 记原话。
6. 不绕过钩子与 CI，不为过测试放宽断言，实现方不改验收测试。
7. 宣告完成须附可复现验证，转述先核原文。
8. 只在自己的工作区与分支干活，主检出不留改动。

## 入口

- 数值与配置：`policy.json`
- 后端代码、DDL、迁移：`backend/CLAUDE.md`
- 用语与决定页：`docs/modernization/glossary/CLAUDE.md`
