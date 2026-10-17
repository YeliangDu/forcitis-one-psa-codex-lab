---
卡号: C7-REV-1
状态: 进行中
负责会话: one-arch-revenue
前置:
---
# 派工卡 C7-REV-1：收入线组流程骨架按 SD-11～15 写回结果复核归段、补齐说明、铺开 BPMN 与价值流评审包

## 1. 卡号与类别
- 卡号：C7-REV-1（施工图 C7，one-coordinator 2026-10-17 派工）
- 类别：功能补做（应交付未做）——文档，不涉代码
- 批次与整合分支：Codex 在分支 `claude/one-arch-revenue-c7` 上提交；负责会话审后快进合入 `claude/one-arch-revenue-closeout`，不开 PR（2026-10-16 开 PR 新规：文档类不开 PR）

## 2. 依据
- 价值流定义：治理分支 `origin/claude/one-architecture-adr-governance` @ d7a12f41 的 `value-streams.v2.yaml`。SD-11～15 已按用户 2026-10-17 ANM 批准写回（提交 b4add5cb，注释「SD-1x（用户 2026-10-17 ANM 批准）」）：SD-12 补段参与者；SD-13 保证金进 VS-01.2、VS-04.1 段内状态，检查表模板版本进 VS-03.1，完工估算进 VS-02 各段，VS-04.5 退出改 any 并补「协议已终止」；SD-14 把只造进入状态的流程所造状态补为段内状态（VS-01.1 市场活动、客户活动、往来单位；VS-01.2 商机跟进中、服务产品启用、费率生效；VS-02.x 资料已确认 / 有缺陷；VS-03.1 项目执行中、检查计划生效；VS-04.3 付款条件项可结算；VS-05.1 往来单位）。
- 归段判据（用户 intent #421，见 `stage_align_check.py` 报告末尾「判据」）：① 结局或步骤到达的状态造出某段退出条件或 in_stage_states 所列状态，就归该段；② 事件策略看触发状态所在段；③ 参与者应在该段参与者中；进入条件不作归段依据；不按部门或时间先后归段。判据只给近似的不按近似改。
- 写法约定：`closeout/CONVENTIONS.md`（参考快照中）；本组现状 `closeout/revenue/README.md`、`STAGE_ALIGN.md`（第 5、6 节与末节已有逐条判断，沿用其口径）、`OBJECT_LAYER.md`。
- 已有裁定（不得推翻）：PRC-24 终止取消属价值流 ends_in，用户定不设终止段（SD-10），只需在 stages_note 点全段号；PRC-56、PRC-59 按判据②归进入所在段；贯穿活动（PRC-20、23、27、31、32）在 stages_note 开头逐个写全段号；检查器只认完整段号子串，不认区间写法与「贯穿」二字。

## 3. 范围与卡上文件
- 允许改动（只在本 worktree 内）：
  - `docs/modernization/architecture/proposals/closeout/revenue/processes.revenue.yaml`：只改 `stages`、`stages_note`、`outcomes` 与步骤 `outcome` / `decision[].outcome` 的用语（用语只为对齐价值流状态名，不改业务含义）
  - 同目录 `scenarios.revenue.yaml`：只随上一条结局用语同改 `outcome`
  - 同目录 `STAGE_ALIGN.md`：在末尾追加「第 7 节 SD-11～15 写回后复核」
  - 新建同目录 `VS_REVIEW.md`（价值流评审包，见第 8 节）
  - 新建同目录 `bpmn/` 下各流程 BPMN（已有 `bpmn/PRC-28.bpmn` 可覆盖重生成）
- 只读参考（可写，用于跑检查，不提交）：`--add-dir` 给出的参考快照目录 `/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/c7ref`（治理分支 docs/modernization 的副本），脚本在 `/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/c7ref/docs/modernization/architecture/proposals/closeout/`。
- 不碰：`primary_object` / `secondary_objects`、`roles`、`steps` 的结构、`commands.yaml`、`state-machines.yaml`、`data-model.revenue.yaml`、`raci.yaml`、`NOTES.md`、`Q04_STORAGE.md` 等其余文件；价值流定义与任何 SSOT；他组目录。需要动这些时停下，把理由写进结果报告。

## 4. 验收（文档卡，无测试代理；以下检查全部须跑并贴结果）
每次改完先同步到参考快照再跑：
```bash
REF=/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/c7ref; P=docs/modernization/architecture/proposals/closeout
rm -rf $REF/$P/revenue && cp -R $P/revenue $REF/$P/revenue
cd $REF/$P
python3 -I sync_catalog_stages.py --local        # 骨架 stages/stages_note/primary_object 同步进目录副本
python3 -I stage_align_check.py --local           # 写 STAGE_ALIGN_REPORT.md（快照内）
python3 -I closeout_check.py --qc --local
python3 -I bpmn_gen.py --out /private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/codex-c7/$P/revenue/bpmn revenue/processes.revenue.yaml
python3 -I bpmn_check.py /private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/codex-c7/$P/revenue/bpmn     # 若参数不同，以 --help 为准
```
验收标准：
- `stage_align_check` 第 6 项本组：6a「未说明」为 0；6a′「已说明」每条的 stages_note 依据与现行价值流一致（不得再写「单列交 one-architecture」「待 SD-1x」等已过时说法，SD-11～15 已定）；6b「判据支持但目录没标」本组每条都有处理：精确且业务上确属该段的补标，否则在 stages_note 写明不加的理由并点到该段号；6d 本组为 0。
- `closeout_check --qc` revenue 行与改前一致：无 why_human 0、approver 不符 0、加审引用无效 0、缺 permission/page 0、出口 / 超时 / 结局无场景均为 0；用语「无」列不得超过改前的 11。
- `bpmn_gen` 本组全部流程（业务流程、事件策略；单聚合与读模型按脚本支持范围）生成且检查通过。

## 5. 执行者与模型
- 执行者：Codex
- 模型与强度：`codex exec -m gpt-6.1-sol -c model_reasoning_effort=xhigh -s workspace-write --add-dir /private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/c7ref --ephemeral -o <结果文件>`
- 工作区：独立 worktree，分支 `claude/one-arch-revenue-c7`，基线 a06f4769
- 审稿：负责会话 one-arch-revenue（opus），逐条核对第 6 项改动

## 6. 禁止事项
- 不改价值流定义、阶段条件、SSOT 与他组文件；不改主对象、角色、步骤结构
- 判据只给「近似」的不补标；不按部门或时间先后补标；不为让检查器变绿而改业务含义或硬塞段号
- 不联网、不起容器、不建账号
- 系统称 FORCITIS one，老系统称「老系统」；新用语先查 `docs/modernization/glossary/terms.yaml`，查不到的写进结果报告，不自造进 yaml
- 只提交卡上文件；提交说明以 `docs(closeout-revenue):` 开头，中文，结尾加 `Co-Authored-By: Codex gpt-6.1-sol <noreply@openai.com>`；不推送

## 7. 预期表结构 diff
不涉及 DDL。

## 8. 交付物与自查
- 交付物：
  1. 骨架 stages / stages_note / 结局用语按第 4 节标准改完（逐条理由写进 STAGE_ALIGN.md 第 7 节的表：流程、报告项、处理〔补标 / 保留并改理由 / 改用语 / 不加并说明〕、依据原文）
  2. `bpmn/` 本组全部流程
  3. `VS_REVIEW.md`：按 VS-01、VS-02、VS-03、VS-04 四节，每节列：段 → 该段流程（编号、名称、类型、主对象）、各段退出条件由哪条流程造出（无则标缺口）、段参与者与流程角色对照、尚待用户或他会话定的事项（Q-04、SD-08 对象状态 MS-17、MS-23、Q09、Q21 等，照现有文件原文引，不新增判断）。供 one-architecture 出每个价值流的确认页
- 自查：第 4 节全部命令的输出原样贴进结果报告；分开写「已验证」与「未运行」

## 9. 审稿要求
- 负责会话逐条核对 6a / 6a′ / 6b 改动是否有价值流原文依据；BPMN 抽查；VS_REVIEW 与骨架一致
- 退回时写明条目与理由，Codex 按条改，记返工轮次

## 10. 报告去向
- 结果报告（`-o` 文件）交负责会话 one-arch-revenue；内容：提交号与分支、改动文件、第 4 节命令输出、未完成项、拿不准交负责会话定的条目、新用语
