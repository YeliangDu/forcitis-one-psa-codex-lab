## 2026-10-14 · psa-repo-fragments · 公共记录片段化（一条记录一个文件 + ADR 拆开）
- 目标：INTENT / SESSION_LOG / STATUS 与 ADR 源改为一条记录一个文件，消除各会话 PR 在同一文件上的冲突（psa-coordinator 派工；本会话原为 psa-change-governance，复用）。
- 输入：最新 origin/main（含 #30–#33）；INTENT.md、SESSION_LOG.md、STATUS.md、adr/build_adrs.py、rules/build_brief.py、.github/workflows/ci.yml、SESSION_PLAYBOOK、CLAUDE.md。
- 用户决定（原话见 INTENT.md）：问「现在合并过程是不是有什么问题？」后选择「一条记录一个文件 + ADR 拆开 (Recommended)」。
- 产出：intent/、session-log/、status/（units、blockers、头部与当前位置段落）与 build_records.py；adr/src/（每份 ADR 一个源文件 + 3 个 README 片段）与重写的 build_adrs.py（读目录，保留状态集、必备小节、取代校验，新增小节顺序与文件名校验）；build_all.py（--check）；new_intent.py、new_log.py；CI gitleaks job 增加「generated docs match fragments」一步（三个 job 名称不变）；SESSION_PLAYBOOK §1、§3、§4、§6、§7、§8、§12、adr README 规则 4、CLAUDE.md 改为「只改片段，不直接编辑生成产物」。COORDINATOR_LEDGER.md 按调度会话要求保持单文件。
- 验证：迁移后重新生成的 INTENT.md、ADR-*.md、adr/README.md（除规则 4 的说明更新外）、MODERNIZATION_BRIEF.md 与迁移前逐字节一致；SESSION_LOG.md 与 STATUS.md 只有空白差异（条目或列表之间的多余 / 缺失空行，均为此前手工合并的痕迹，统一后去掉）。负向测试（临时副本）：单元格含竖线、工作单元缺键、日志两个标题、阻塞多行、ADR 缺小节、小节顺序错、状态不合法、新 ADR 缺必备小节、取代未同步标注，均生成失败；build_all.py --check 干净时通过、片段与产物不一致时失败。未使用子代理（迁移与比对脚本由本会话完成，可重复执行）。
- 未完成与阻塞：无。
- 下一步命令：合并后由 psa-coordinator 通知在途会话改用片段文件。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-repo-fragments）
