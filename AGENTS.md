# FORCITIS one 现代化——Codex 等代理须知

本仓库的规则以 [`CLAUDE.md`](CLAUDE.md) 为准。开工前完整阅读它、编码规范 [`docs/modernization/CODING_STANDARD.md`](docs/modernization/CODING_STANDARD.md)（怎么写代码的唯一入口）、`docs/modernization/NAMING_STANDARD.md`（命名与 DDL）和 `docs/modernization/SESSION_PLAYBOOK.md` TEAM-29–39、附 M5、附 M1。这些文件与本文件冲突时，以它们为准。

Codex 在本仓库只接受 one-coordinator 或负责会话派出的工作卡（SESSION_PLAYBOOK TEAM-32–38）：
- 只改卡上列出的文件；需要改卡外文件时停下，在结果里报告。
- 不修改验收测试目录（`backend/src/test/java/com/forcitis/one/acceptance/**`、`backend/src/test/resources/acceptance/**`、`frontend/**/__acceptance__/**`）。
- 不放宽、不删除断言；不联网；不起容器、不建账号。
- 用语以 `docs/modernization/glossary/terms.yaml` 为准；新词先登记提议术语。
- 交付前运行 `scripts/precheck.sh`，把输出附在结果里。通过标准：最后一行 `SUMMARY` 为 `0 FAIL`（退出码 0），每个 SKIP 都有理由；卡上验收测试转绿；有 FAIL 不算完成，在结果里写明原因。
- 结果里分开写「已验证」与「未运行」。
- 结果与报告用中文书写（用户要求，见 CLAUDE.md 第 0 条）。

进入 `backend/` 或 `docs/modernization/glossary/` 等目录工作前，先读该目录下的 `CLAUDE.md`（Codex 不会自动加载子目录 CLAUDE.md）。
