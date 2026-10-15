# one-quality 速查（2026-10-15）

一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

## 在途

| 项 | 状态 | 出处 |
|---|---|---|
| FLAKY-SESS 会话清理与固定时钟不同源，整分钟偶发 401 | one-1a-next-3 按 H 修（会话时间基准例外） | 运行 38772041094 |
| Testcontainers 连接 PG 认证读无超时，CI 曾卡 54 分钟 | 待开卡（JDBC socketTimeout） | #197 运行 38746203340 |
| 覆盖率门槛是否恢复 | 待用户 | COORDINATOR_LEDGER「待用户」 |
| nightly-legacy-it 巡检 | 接手后设会话内定时 | `gh run list --workflow nightly-legacy-it.yml` |
