## 2026-10-14 · psa-sec-hotfix · 老系统安全止血（第 7 轮：1a 新发现补录）
- 目标：psa-coordinator 派工——把 Phase 1a 规格挖掘中的 4 项新发现写入 SECURITY_FINDINGS，追加补丁，给出 SEC-116（验证码 9999）的无需发版止血方案，做对抗性复审。
- 输入：STATUS、SESSION_PLAYBOOK §3–§5、SECURITY_FINDINGS、security_remediation.notes.md / .patch（第 6 轮）、phase1a/INTERFACES.md、rules/IDENTITY_ORG_RULES.md（RULE-328、RULE-376）；基线 origin/main 4593583。
- 用户决定（原话）：本会话无新的用户决定（沿用 SEC-001「先网络层再补丁」等既有决定）。
- 产出：SECURITY_FINDINGS 新增 SEC-116 至 121 与第 7 轮复审记录；security_remediation.patch 67 → 84 个文件（凭据补丁不变）；notes 新增 SEC-116 至 121 章节、无需发版止血步骤、部署顺序、生产只读核实清单（含查询语句，只取计数）、未决事项 12–15、第 7 轮验证。
- 验证：老仓 `19d60d234` 全新副本上两份补丁 `git apply --check` 通过，应用后与编辑树哈希一致；老仓本身只做 `patch --dry-run`；Corretto 8 离线编译五个服务通过；56 个相关单测全部通过（`AdminAuthServiceImplTest` 在未打补丁的老仓上同样失败，与补丁无关）；泄密扫描 0 命中。对抗性复审：security-auditor 子代理（opus）——SEC-116、118 RESOLVES，117、119 PARTIAL，新发现 SEC-120（High）、121（Low），随后修复 120、121。机械查找未另派 haiku 子代理（由主会话直接完成）。
- 未完成与阻塞：运行时未在 dev 实测；生产 Nacos 是否覆盖 9999 未核实（需用户授权）；SEC-117 的首次提交 `paramsId` 绑定只能靠删除两个 HTTP 入口彻底修复（待用户决定）；SEC-119 依赖 SEC-006 轮换回调 Token / AESKey；`apiprod.forcitis.com` 由哪个 nginx server 提供未核实。
- 下一步命令：用户授权后只读核实生产 Nacos 与使用量（notes「Read-only checks needing user authorisation」）；运维按 notes「SEC-116 · Immediate stopgap」执行止血；dev 实测按各章节清单。
- 会话 ID：本会话（worktree xin-af304c，分支 claude/psa-sec-hotfix-r7）
