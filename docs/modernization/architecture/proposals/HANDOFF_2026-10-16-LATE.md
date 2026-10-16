# one-architecture 交接（2026-10-16 晚）

接续先读本文件，再读 `ARCH_CONTEXT.md`；深夜交接 `HANDOFF_2026-10-16-NIGHT.md` 只作历史。

## 现在在做（用户最新决定，经 one-coordinator）

- **阶段对齐（BIZBOK v11 §3.4：阶段 → 能力 → 信息 → 干系人）**：审核载体是 one-coordinator 生成的价值流对齐图 https://claude.ai/artifact/7M7q1KQTZ7XwaoXSELdETL 。第一步对齐阶段：未落阶段 28 条（finance 14、people 11、revenue 3）、跳段 12 条，已分派三组；`closeout/stage_align_check.py` 在写（退出条件、进入条件、稀少段）；VS-09、VS-11 漏项交 people。只改流程目录与价值流定义，改阶段定义或需用户判断的单列交 one-coordinator。
- **CRUD 缺口清零暂停**：S-43 对象划分裁定保留（`closeout/rulings/S-43_OBJECT_CLASSIFICATION.md`），object_check 已支持豁免表 `closeout/OBJECT_EXEMPTIONS.yaml`（理由与裁定号必填）；B3–B6 修复与豁免登记的子代理可能已完成未提交，接续时 `git status` 检查。受影响题清单 `closeout/PAGE_IMPACT.md`。
- **确认页**：收件箱 7 页（人员先答、人员续答、收入先答、收入余题、阈值、财务先答、财务余题），用户暂不作答，清零与对齐后按变动改题，过 check_pages（one-quality PR #419 分支 claude/one-quality-check-pages）与独立审题再一次交用户。规矩见记忆 decision-page-review-first。

## 今天定下的（台账 `closeout/STRUCTURAL_ITEMS.md` S-29–S-44）

- 结构裁定 S-29–S-44；新号到 PRC-180、AGG-134（预留）、INV-139；AR-16 无来源高风险用印；AR-11/12 代批；AR-13/14 按项目级别取审核主体（不叠加）。
- 权责对照表 `closeout/RESPONSIBILITY_MATRIX.yaml`（raci_build 合并三组片段，586 条，冲突 0）；`ORG_NEEDS.md`（org_needs_build.py）。
- 跨组接缝 `closeout/SEAM_REPORT.md` 已清零（seam_check.py）；依赖边 156，无环。
- 非工作日工时与加班补休接缝 S-44（本期不对接）。

## 脚本（closeout/）

closeout_check（--qc）、bpmn_gen / bpmn_check、converge_check（stateless、perpetual）、raci_build（合并片段）、org_needs_build、object_check（豁免表）、seam_check、build_catalog（support）、stage_align_check（在写）。

## 分支

本会话 `claude/one-architecture-adr-governance`（PR #365）。推送前检查有个偶发失败的后端测试 DeadLetterJobMetricsTest（单跑通过），可交 one-quality。
