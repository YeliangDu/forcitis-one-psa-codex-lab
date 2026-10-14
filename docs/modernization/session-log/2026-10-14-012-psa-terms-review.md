## 2026-10-14 · psa-terms-review · 用语确认第二轮
- 目标：确认 backend/glossary/proposed 的 58 条提议术语（含评估遗留的 cost、error-code、capacity、scheduler-lock，即 P30–P33）、OA 盘点登记的 20 个 A8 叫法、新 BC 命名 2 项；用户确认后入表。
- 输入：psa-coordinator 派工；SESSION_PLAYBOOK §3–§5、STATUS、ADR-0018、NAMING_STANDARD、terms.yaml、第一轮记录；main 4b3ed1d（含 PR #11）。
- 用户决定（原话见 INTENT.md）：确认页上 80 项全部按建议（P、N 同意建议，A 归方言）；「用语已经确认」；A5 项目委外「两种都有，按单据区分」；A11 应收坏账申请「计提」。
- 产出：glossary/round2-proposals.yaml（建议）、round2-source/（原提议冻结副本）、decisions-round2-2026-10-14.json、export_decisions_round2.py；build_terms.py / build_confirmation.py / build_confirm_page.py 支持第二轮；terms.yaml 293 → 348 条（全部已批准，方言别名 59 个）；backend/glossary/proposed 两份提议文件移除，4 个仍被代码使用的旧写法暂登记 pending-renames.yaml；确认页 https://claude.ai/artifact/L3bfFkbVDFHtm5ugfTbio8 （第二轮）；TERM_CONFIRMATION.md、INTENT.md、ADR-0018（含 build_adrs.py）更新。
- 验证：build_terms.py 输出 348 条 approved、待确认 0；backend `./mvnw verify` 通过，词表警告只剩 4 个过渡旧写法（declared / leader / web+scan / trail）。
- 未完成与阻塞：1a 会话改名 6 处（见报告的代码改名清单），改完删除 pending-renames.yaml、在 round2-proposals.yaml 的 code_renamed 登记 id 并重跑 build_terms.py 写入禁用；「坏账准备计提」词条待 6b 起草；新 BC 模块路径由 psa-gl-scope 统一。
- 下一步命令：1a 会话按改名清单改代码后运行 `cd docs/modernization && python3 glossary/build_terms.py && cd ../../backend && ./mvnw verify`。
- 会话 ID：本会话（worktree xin-4e9c83，分支 claude/psa-terms-review）
