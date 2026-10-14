## 2026-10-14 · psa-change-governance · 变更治理后续：ADR-0024 与历史偏差登记
- 目标：落实用户对历史偏差、部分取代写法、ADR-0022 的决定（psa-coordinator 转达）。
- 用户决定（原话见 INTENT.md）：「只补必要的，其余承认既成事实 (Recommended)」；部分取代「认可 (Recommended)」；ADR-0022「要，补齐后再拿来审批」（由 psa-gl-finance-review 负责，本会话未改 ADR-0022）。
- 产出：ADR-0024（提议）「收入确认单迁入新系统、不设双源期」，部分取代 ADR-0016 后果第 4 条，四个必备小节齐全，决定第 5 条写明采纳时同一提交删除 ADR-0016 追加段并标注部分取代（本次未删）；adr/README.md 新增「历史偏差（规则生效前）」（ADR-0009 第 6 条两次、ADR-0016 追加段、ADR-0018 第 2 条记录、ADR-0020 第 7/8 条、ADR-0011 残留标记，含提交号与用户原话）；STATUS 新增「变更治理」行（完成）；INTENT 记原话。
- 验证：build_adrs.py 生成 24 份，ADR-0001..0023 无变化；ADR-0024 通过必备小节校验（提议状态不触发取代同步校验，采纳时会触发）。
- 下一步命令：用户审批 ADR-0024 后，在同一提交中改 ADR-0016 状态、删除追加段、改 ADR-0024 为已采纳，重跑 build_adrs.py。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-change-governance）
