## 2026-10-14 · psa-oa-inventory · OA 流程盘点（迁入边界与顺序）
- 目标：只读盘点致远 A8 的流程与表单，给出迁入 FORCITIS one 的边界、顺序与阶段草案；按用户硬约束「确保权责业务事实全部在新系统」做权责事实完整性矩阵；起草 ADR-0021（提议）；A8 叫法登记为待确认用语（psa-coordinator 派工及补充）。
- 输入：SESSION_PLAYBOOK §3/§4/§5、STATUS.md、ADR-0013/0016/0017/0018、GLOSSARY.md、runtime/README.md、phase1a/COLUMN_OWNERSHIP.md、GUIDING_PRINCIPLES §9a（main ecd53f7）；psa 仓库（只读）与其 docs/analysis/OA收入开票回款流程迁移评估.md。基线 origin/main ecd53f7。
- 用户决定（原话见 INTENT.md，经 psa-coordinator 转达）：「是，同意只读查询」；「确保权责业务事实全部在新系统」。本会话未直接收到新的用户决定。
- 产出：OA_PROCESS_INVENTORY.md（125 个在用模板分类：迁入 39 / 视情况 48 / 留在 OA 35 / 已停用 3；权责事实完整性矩阵 23 行；36 处 A8 集成点逐处判定，权责相关 26 处；阶段草案 6a–6f 含入口 / 出口条件草稿；待决问题 8 个）；build_oa_inventory.py（分类表生成与校验）；oa_residual_check.sql（无遗留检查只读查询模板）；ADR-0021（提议，经 build_adrs.py 生成）；runtime/a8_templates.tsv、a8_forms.tsv、a8_u8c_vouchers.tsv、a8_collab_state.tsv、u8c_voucher_sources.tsv；glossary/oa_aliases.json + build_terms.py / build_confirmation.py 接入，用语确认表 A 组新增 20 项（含「产值」的 2 项按已有裁定归方言）；INTENT、STATUS 更新。
- 关键发现：A8 凭证插件按 16 种表单直接给 U8C 生成凭证（累计 402,285 行 / 32,339 张单据，近 180 天 99,179 行），A8 销项开票插件近 180 天开票 1,143 张——Phase 4 出口「U8C 推送权切换」须以 6a–6c 完成为前提；合同条款与收入行级耦合（PSA 作业回写 A8 条款累计字段），评估会话的顺序修正为合同与收入同阶段；U8C 自有手工、出纳、固定资产凭证须财务逐类判定。
- 验证：生产只读 7 次轻量查询（程序拒绝非 SELECT；单条超时 150 秒；凭据未打印未回传；临时目录 /tmp/psa-oa-ro 每次删除）；build_oa_inventory.py 校验 125 个活跃模板全部分类、无多余条目；build_adrs.py 重新生成后 ADR-0001..0020 无变化；build_terms.py 前后比对：293 条词条除别名外无变化，禁用写法只新增「客户产值确认单」「客户产值确认档案」；build_glossary.py 输出不变；architecture-guardrails GlossaryTest、NamingSurfaceTest 通过（注：该测试使用自带样例词表，不覆盖本次改动）。子代理：A8 调用点逐处列举 haiku；分类、矩阵、ADR 由本会话 opus 完成。
- 未完成与阻塞：ADR-0021 与 §9 八个问题待用户决定；6a–6f 未并入 brief（待审批）；oa_residual_check.sql 各段的查询形态已在本次采集中实测，但整份模板未带真实参数执行；U8C 手工凭证内容未读（只取计数）；A8 表单触发器（FORM_TRIGGER_RECORD_*）的隐式回写未抽规则。
- 下一步命令：用户决定后，psa-coordinator 把 6a–6f 并入 brief；6a 前新会话 `psa-6a-contract-revenue-extract` 运行 `/code-modernization:modernize-extract-rules psa contract`（含 A8 表单与触发器）。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-oa-inventory）
