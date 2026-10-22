# 派工卡 ARCH-C8：云端额度试点——一个价值流的流程骨架与描述级流程图

状态：草稿｜2026-10-17｜负责：one-architecture

- 类别：设计初稿（流程骨架）。执行者：云端会话（Codex 或 Claude，由 one-coordinator 按额度定），独立分支 `claude/arch-cloud-pilot`，基线为本分支最新提交。
- 试点对象：VS-11 沉淀复用知识（3 段、流程少、对象单一：知识条目 OBJ-110；人员组已有 PRC-107 骨架可作对照）。
- 依据：PROCESS_METHOD_DRAFT.md（§4 三份文件分工、§5 每个业务流程写清什么、§11 命名与编号）、CONVENTIONS.md 字段约定、ADR-0052（已批，待与 ADR-0054 同批生效）、ADR-0053（命名体系，已采纳）、value-streams.v2.yaml 中 VS-11 各段进出条件。
- 任务：①按 CONVENTIONS 为 VS-11 内全部流程写骨架（新文件 `closeout/pilot/processes.vs11.yaml`，不改三组文件）；②用 `bpmn_gen.py` 生成描述级图到 `closeout/pilot/bpmn/`；③跑 `closeout_check.py --qc`、`bpmn_check.py`、`stage_align_check.py --no-write`、`object_layer_check.py --no-write`，结果贴进结果文件。
- 验收：质检 0 缺口；每段退出条件有流程承载；图与骨架编号、用户任务一一对应；与人员组 PRC-107 现骨架的差异逐条列出。
- 记录：耗时、额度用量、返工轮次，供 one-coordinator 评估云端是否适合铺开。
- 禁止：不改三组骨架与目录、不联网、不提交到他人分支、不打印密钥。
