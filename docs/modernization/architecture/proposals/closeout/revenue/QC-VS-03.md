# VS03 质量检查（PRC-41、42、43、44、45、46）

## 脚本项

- 生成器：`bpmn_gen.py`（含只多读 `decision.outcome` 的本地补丁副本，见 NOTES 格式问题 1）对 PRC-41、43、44、45、46 全部「检查：通过」。剩余警告：PRC-41（2 处）、PRC-43、PRC-44「步骤缺 command，用缺省」，分别是 `read: true` 的系统读步骤与 PRC-44 第 5 步 `gap` 步骤（命令缺口，已列 NOTES）；PRC-45、PRC-46「超时升级任务与原任务同泳道」是布局提示，升级对象本就是同一检查负责人，不改。「按位置猜测」警告在补丁副本下清零，原版生成器仍有（已报）。PRC-42 为单聚合（type: single_aggregate），生成器不处理、不画图，仅过自查脚本。
- 自查脚本（selfcheck3.py，对两个 yaml 逐项）：问题数 0。覆盖：每个 approval 有 why_human 与 approver；aggregate 审批无 escalation_rules；每个 decision 末条 otherwise 且 p 之和为 1；每个 outcomes 有出口；in_system 步有 permission 与 page；offline 步有 evidence；trigger 类型只用 human / event；命令均在对应聚合 §2.2 命令列内；无命令步骤均为 read 或 gap；超时均为 ISO 8601；场景与 scenarios 列表一致；每个判断出口与超时至少一条场景；org_needs 四字段齐全；frequency_ref 非空。

## 八维度

1. 规范健全：符合。BPMN 检查通过；无死路（每个出口有结局，PRC-45 退回整改回到第 2 步）；无并行网关。
2. 完整覆盖：符合（出口、超时、结局）；撤回不适用于本批，因聚合稿无撤回类命令，已列 NOTES 新用语（取消检查等）。与 A8 同类模板及行业做法比对：不适用于本批改写，沿用 S3 的比对。
3. 一致追溯：符合。ContractSigned 由 B-05 发布、B-26 订阅；检查已锁定同 BC；cross_bc 方向对过 §4.1；新增「关联证据」B-26 → B-28 api 成立。角色→职位映射待权责对照表（org_needs 已写 position_hint）。用语：命令均取聚合稿原文，新用语已列。
4. 可理解：符合。PRC-45 元素最多（任务 21，另含网关 2、事件 5），在约 50 个元素以内；命名动词＋名词，结局为短语。
5. 业务正确：符合。口径未改：INV-50、51、55、56、57、58、P-28、P-30、intent 141725 均沿用；证据关联按 S-25 加挂检查与不符合项。「第 6 步前移证据缺失检查」是把人工审核里的机械核对前移为提交时拒绝，业务结果不变。
6. 内控合规：符合。segregation 保留（PRC-43、44）；三个审批写 why_human，approver 为专业角色；关键步骤留 audit；证据只追加、封存、哈希。关于「审批权只给责任人」：三者是专业复核而非费用审批，处置见 NOTES 接缝 1，待 one-architecture 裁定。
7. 效率：符合。审批层级为单级；证据关联改系统自动，避免重复录入（PRC-43、PRC-45）；检查项缺证据由提交时拒绝，不占审核人。不符（可改进，已列待用户定）：PRC-44 第 5 步送达回执无登记落点，线下完成后无系统记录，属命令缺口。
8. 可验收：符合。每个出口、超时、结局至少一条场景；PRC-43 全程路径 S01，新增 S08（缺证据拒绝提交）、S09（证据关联）；PRC-44 新增 S06、S07；PRC-45 新增 S08；PRC-46 超时场景随步骤移到第 2 步。

## 主会话复跑（2026-10-16，全组合并后，脚本取 #384 分支最新版）

- `closeout_check.py --local --qc --all`：revenue 人工审批 26，无 why_human 0、approver 不符 0、加审引用无效 0、缺 permission/page 0、判断出口无场景 0、超时无场景 0、结局无场景 0、用语无着落 0（主会话补：3 处客户方意见的 why_human、18 条场景、125 个命令名汇总交 one-glossary）。
- `build_catalog.py --check`：错误 0；警告为「未登记 bpmn」，待生成图入目录。
- `raci_build.py --local`：A 为固定角色 0、多个不同 A 0；外部（客户）审批不入 A 3；其余缺 A 为无审批节点的命令（预期）。
- `bpmn_gen.py`（含 R13–R15）：41 个流程 30 个通过；11 个（PRC-32、37、41、46、49、54、57、58、61、63、140）报 R14，原因是生成器把**不中断**期限的升级任务接回主线（`bpmn_gen.py` 第 369–371 行 `self.flow(esc, first["tgt"])`），产生双令牌——生成器缺陷，骨架无误；已报 one-architecture，建议不中断时升级任务接独立结束事件「已提醒」。另 17 条「按位置猜测结局」警告为生成器不读 `decision[].outcome`，建议补丁见 `tools/bpmn_gen.suggested.patch`。

## 当前状态（评审改稿后复跑，2026-10-16；本节取代上文各节中的「不符」与脚本结果）

- 改稿依据：opus 首轮评审 `REVIEW.md`（高 8、中 15、低 8）；sonnet 复核：已落实 27、部分 2、未落实 1、转交 1，复核新发现 5 条已全部改（PRC-148 驳回分支、PRC-149 事件触发的冲销额与原因来源、PRC-08 重新投标结局、PAY-R1 补「预付」、NOTES 阅读提示）。
- 脚本（取 `claude/one-architecture-adr-governance` 最新）：`closeout_check --qc` revenue 人工审批 39，无 why_human 0、approver 不符 0、加审引用无效 0、缺 permission/page 0、出口 / 超时 / 结局无场景 0、用语无着落 0；`bpmn_gen` 45 个业务流程全部「检查：通过」（含 R13 无死路、R14 无死锁、R15 元素数；生成器不中断期限缺陷已由 one-architecture 修复）；`build_catalog --check` 错误 0。
- 复审项仍开着的（不阻断交页，已有去向）：
  - 与 A8 同类模板逐项功能比对：本组没有 A8 覆盖对照输入，转 one-architecture（⑦ A8 覆盖对照）。
  - 审批回调的事件方式边（M11 后半）：one-architecture 在 §4.1 统一补。
  - `object_check`（对象 CRUD 矩阵）问题随下一步命令目录与状态机收敛处理。
