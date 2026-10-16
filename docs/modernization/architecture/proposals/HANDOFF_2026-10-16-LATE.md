# one-architecture 交接（2026-10-16 晚，/clear 前）

接续先读本文件，再读 `ARCH_CONTEXT.md`；更早的交接（NIGHT、PM）只作历史。

## 进行中：阶段对齐（BIZBOK v11 §3.4：阶段 → 能力 → 信息 → 干系人，第一层）

- 审核载体：one-coordinator 生成的价值流对齐图 https://claude.ai/artifact/7M7q1KQTZ7XwaoXSELdETL ；数据来自 `closeout/process-catalog.yaml` 与 `value-streams.v2.yaml`。
- 已完成：
  - 未落阶段 0，跳段 0；
  - 目录阶段由 `closeout/sync_catalog_stages.py` 从三组骨架同步，骨架为准，组改骨架后跑它；
  - VS-02.7 改名「收尾」；
  - 新增 VS-04.6 履约、VS-08.0 事前申请；
  - SD-01–10 已裁定落地（d51de5e8）。
- 检查器 `closeout/stage_align_check.py`（报告 `STAGE_ALIGN_REPORT.md`）：
  - 用户归段判据：结局造出退出条件；事件策略看触发状态所在段；参与者在该段；不按部门或时间。
  - 本会话的细化：
    - 段内状态也算支持；
    - 进入条件不算支持；
    - 跨价值流事件策略归本价值流以其为进入的段；
    - 自环与 non_state 不算到达；
    - 一拆多对象可带 `agg` 限定；
    - stages_note 点到该段的列为「已说明」。
  - 最近一轮：退出匹配不上 0、进入找不到 1；第 6 项未说明不支持 19、已说明 37。
  - 后台子代理在修 PRC-33（单聚合状态缺对象名）、PRC-49（索赔已认定）两处匹配，接续时 `git status` 看有无未提交的 `stage_align_check.py`。
- 待交用户（`closeout/STAGE_DEFINITION_ISSUES.md`）：
  - SD-11 VS-10.0 记账与结转段；
  - SD-12 各段参与者缺人（finance 9、revenue 20、people 1）；
  - SD-13 阶段条件缺对象（保证金、检查表模板、完工估算、协议已终止）；
  - SD-14 只造进入状态的 10 条流程（PRC-01、02、03、05、12、13、17、26、41、152）：补段内状态或改支撑；
  - SD-15 VS-09.3 退出补「经营复盘已闭环」；
  - SD-09 随 Q09。
- 下一步：
  1. 检查器修复提交后，请三组复跑；
  2. 把 SD-11–15 一并报 one-coordinator，由它重出对齐图交用户审；
  3. 用户审过阶段层后再开能力层，之后是信息层（回补 MS-17–20 缺对象状态）和干系人层。
- 未定的口径：people 的 9 条配置与主数据流程（只造进入条件）按判据维持 support（J-11），这会让 VS-09 的流程数变少，已在 SD-14 一并说明。

## 暂停：CRUD 缺口清零（用户决定，待阶段与能力对齐后续做）

- S-43 对象划分裁定保留（`closeout/rulings/S-43_OBJECT_CLASSIFICATION.md`）。
- object_check 已修 B1–B6，豁免表 `closeout/OBJECT_EXEMPTIONS.yaml` 已登 15 条（理由与裁定号必填）；问题现为 67 条。
- 待裁项：PRC-33、PRC-123 的「单聚合维护含多个聚合」；AGG-119 补流程（已给 PRC-180）；OBJ-81 收入确认随 VS04-01 的答复。
- 受影响题清单 `closeout/PAGE_IMPACT.md`；三组的分拣在各组 `QC-LIFECYCLE.md` 或 `QC-OBJECTS.md`。

## 确认页（用户暂不作答；清零与对齐后按变动改题，过 check_pages 与独立审题再一次交用户）

- 收件箱 7 页：人员先答、人员续答、收入先答、收入余题、阈值、财务先答、财务余题；题源在 `architecture/decision-page/*.json`。
- 页面检查脚本 check_pages：one-quality PR #419，分支 claude/one-quality-check-pages。
- 待改题：
  - VS04-00 保持「Q24 与 Q-10 两次答复冲突」：Q-10 在 intent 134134，one-coordinator 已撤回更正；
  - 收入线开票链审批人现写「协议承担部门负责人」，随 VS04-26 答复改；
  - 补休有效期等随答复写回。
- 规矩见记忆 decision-page-review-first。

## 在途 PR

- #365 本会话分支 `claude/one-architecture-adr-governance`（里程碑合入）。
- #425 intent 片段 18 条，只含 intent，已报调度入队。
- 推送前检查偶发后端测试失败（DeadLetterJobMetricsTest、SingleAggregateGuardTest，后者 one-quality #392 在加固），重推即过，不绕过。

## 今天的台账

结构裁定 S-29–S-44（`closeout/STRUCTURAL_ITEMS.md`）；新号到 PRC-182、AGG-134（预留）、INV-139；AR-13/14 按项目级别取审核主体（不叠加）；AR-16 无来源高风险用印。

## 脚本（closeout/）

- 质检：`closeout_check`（--qc）；
- 流程图：`bpmn_gen`、`bpmn_check`；
- 收敛：`converge_check`；
- 权责：`raci_build`（合并三组片段），`org_needs_build`；
- 对象与接缝：`object_check`（豁免表），`seam_check`；
- 目录与阶段：`build_catalog`，`sync_catalog_stages`，`stage_align_check`。
