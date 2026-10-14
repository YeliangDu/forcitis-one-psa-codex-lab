## 2026-10-14 · psa-oa-inventory · OA 盘点收尾：rebase 并落实用户决定
- 目标：rebase 到 origin/main 0f98e10，按用户决定（psa-coordinator 转达，原话已记 INTENT 0f98e10）改 ADR-0021、盘点与 brief / ADR-0016 修订稿。
- 输入：psa-coordinator 派工；INTENT 0f98e10 的 OA 盘点、H-1、H-3、M-5 决定。
- 用户决定（原话见 INTENT.md，本条不重复登记）：ADR-0021「采纳；6a–6c 并入 Phase 4 前」；「新建「应付与付款」BC」；非项目采购、员工借还款、合伙人注资退回「都算，都进新系统」；「新系统要自建整个hrm，算薪要迁入」；「主数据全部进新系统」；H-1「迁入新系统，不设双源期」。
- 产出：ADR-0021 改为已采纳（主数据、不设双源期、6e 自建 HRM、应付与付款 BC、6a–6c 为 Phase 4 出口前置）；ADR-0016 后果第 44 行后追加修订提议（未碰第 6 条）；brief 模板追加两条 `Proposed revision:`（Phase 4 入口的 A8 产值只读接入改为 6a 迁入；Phase 4 出口增加 6a–6c 前置）并重新生成 brief；OA_PROCESS_INVENTORY 分类改为迁入 80 / 视情况 30 / 留在 OA 12 / 已停用 3，矩阵增至 24 行并加主数据段（员工银行卡按敏感个人信息），§5、§6（6e 自建 HRM 范围草案）、§9（决定记录与待办）更新；build_adrs.py 补入 main 上 ADR-0009 的手改段落（此前重新生成会丢失）。rebase 冲突：STATUS、INTENT、SESSION_LOG 以 main 为底合并本单元的行。
- 验证：build_adrs.py 重新生成后，ADR 只有 0016（追加修订提议）、0021、README 有变化，ADR-0009 与 main 一致；build_brief.py 输出与 main 相同（P0 226、已定 103、阻塞 123、已评审 73），brief 只多两行；build_terms / build_confirmation / build_glossary 重新生成无差异（A 组 20 项）；build_oa_inventory.py 校验 125 个活跃模板全部分类。REPORT.html 未刷新（仓库无生成脚本）。未使用子代理。
- 未完成与阻塞：ADR-0016 修订提议与 brief 两条修订待用户批准；用语 A 组 20 项待确认；U8C 手工 / 出纳 / 固定资产凭证待财务判定；「应付与付款」BC 编号待 psa-gl-scope。
- 下一步命令：6a 前新会话 `psa-6a-contract-revenue-extract` 运行 `/code-modernization:modernize-extract-rules psa contract`（含 A8 表单与触发器）。
- 会话 ID：本会话（worktree xin-884358，分支 claude/psa-oa-inventory）
