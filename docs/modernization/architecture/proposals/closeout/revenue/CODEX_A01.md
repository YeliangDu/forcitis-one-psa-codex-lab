# 派工卡 ARC-1017-ARE-01：[架构·presales] 报价发出编排接入骨架

## 1. 类别
功能补做（文档，骨架与目录）；工作区 `claude/one-arch-revenue-a01`（独立 worktree），**不要 git add / commit**（沙箱写不了共享 git 目录），改完留在工作区，由负责会话审后提交。

## 2. 依据（one-architecture 2026-10-17 裁定，原文要点）
1. 新增下游流程模块「报价发出编排」（无聚合，沿 B-29 / REF-02 先例；名称待 one-glossary 定，暂记 REF-03）：先向 B-31 核验实际用印（同一报价版本、法人、拟发文件；读不到或失败一律拒绝并告警），再调用对应命令。
2. 投标路径：「递交投标」（AGG-11，PRC-08 第 7 步）改由该模块调用，命令入参带用印登记 ID。
3. 非投标路径：在 PRC-07「生效报价版本」后加人工步「登记报价发出」，命令归 AGG-12（只记录发出方式、日期、客户联系人、用印登记 ID，不改状态、不校验真伪），由该模块调用。
4. 报价聚合与投标聚合都不依赖 B-31（核验只在模块里）。聚合稿与 ADR 由 one-architecture 改，本卡不碰。
参考：`ENTERPRISE_AGGREGATES_DRAFT.md` 的 REF-02 行与 B-29 行（治理分支），本组 PRC-36（B-29 编排调用他 BC 命令的写法）；AGG-113 用印申请的「用印登记 ID」。

## 3. 改动范围（只改下列文件，目录 `docs/modernization/architecture/proposals/closeout/revenue/`）
- `processes.revenue.yaml`
  - PRC-07：在「生效报价版本」步把 decision 拆为「投标场景（发出经 PRC-08 递交投标）」→ end（outcome 报价已生效）与 otherwise（非投标：询价、议标、直接委托）→ 新步。新增两步：
    a. system 步（读）：报价发出编排（REF-03）经 api 向 B-31 核验实际用印（reads AGG-113；cross_bc api out B-31「核验实际用印」）；decision：未用印、版本或法人或拟发文件不符、读不到或调用失败 → end，outcome「报价发出被拒（未用印）」，失败并告警（写 notify 给 SH-E5）；otherwise → b。
    b. SH-E5 人工步：command「登记报价发出」，aggregate AGG-12，where in_system，desc 写明由报价发出编排调用、入参（发出方式、日期、客户联系人、用印登记 ID）、不改报价状态、不校验真伪；emits 无或按目录；permission「报价.登记发出」；page「报价发出登记页」；audit true；outcome「报价已发出」。
    更新 PRC-07 的 outcomes（加「报价已发出」「报价发出被拒（未用印）」）、scenarios 列表、notes（一句说明 B31-16 与 REF-03）。
  - PRC-08 第 7 步「递交投标」前插一步同 a 的核验（失败 → end，outcome「递交被拒（未用印）」，告警）；第 7 步 desc 补「由报价发出编排调用，入参带用印登记 ID」，evidence 补用印登记 ID。注意插步后**所有 goto 步号要整体顺延**（含 decision、withdraw、on_reject、他步引用），并检查 desc / notes / human_note / scenarios 里引用 PRC-08 步号的文字（如「第 15–20 步」「步 11→12」）同步改。PRC-07 新步在末尾「作废报价版本」之前插入时同理。
- `scenarios.revenue.yaml`：PRC-07 补「非投标发出成功」「未用印被拒」「B-31 读不到被拒」「投标场景生效即止」；PRC-08 补「递交前用印核验失败」；既有场景 path 里的步号随顺延改。
- `commands.yaml`：新增「登记报价发出」（AGG-12；params 报价版本、发出方式、日期、客户联系人、用印登记 ID；preconditions 报价版本为「已生效」；idempotency 报价版本 ID + 发出序号；emits []）；「递交投标」params 加「用印登记 ID」。
- `state-machines.yaml`：AGG-12 加 已生效 → 已生效「登记报价发出」（note：只记录，不改状态）。
- `raci.yaml`：新增 AGG-12「登记报价发出」一条，照同目录 AGG-12 其他命令的写法。
- `NOTES.md`：末尾加「新用语·报价发出（B31-16）」表：报价发出编排（REF-03 暂名，待 one-glossary）、登记报价发出、用印登记 ID、报价已发出、报价发出被拒（未用印）、递交被拒（未用印）；bc presales，范围领域。
- 不碰：其余文件、他组目录、价值流与 SSOT、主对象与角色定义（PRC-07、08 若新步引入新角色，先停下写进报告）。

## 4. 验收（每次改完同步到参考快照再跑；参考快照 `/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/c7ref` 可写）
```bash
REF=/private/tmp/claude-501/-Users-yeliang-Projects-FORCITIS-forcitis-one-psa--claude-worktrees-practical-hodgkin-3f1b8a/5b3bed7f-a626-42ed-9450-2e65ae5a3124/scratchpad/c7ref; P=docs/modernization/architecture/proposals/closeout
rm -rf $REF/$P/revenue && cp -R $P/revenue $REF/$P/revenue && cd $REF/$P
python3 -I closeout_check.py --qc --local
python3 -I converge_check.py --local --commands revenue/commands.yaml --states revenue/state-machines.yaml
python3 -I bpmn_gen.py --out /tmp/a01-bpmn --only PRC-07 revenue/processes.revenue.yaml   # --only 不支持时去掉
python3 -I bpmn_gen.py --out /tmp/a01-bpmn --only PRC-08 revenue/processes.revenue.yaml
python3 -I stage_align_check.py --local --no-write | grep '^6 '
```
标准：qc revenue 行无 why_human / approver / 加审 / permission-page / 出口 / 超时 / 结局无场景均 0；converge 本组孤儿命令 0、改状态步骤无对应转换 0（只剩既有的 AGG-52、53、59）；PRC-07、08 BPMN 检查通过；第 6 项 6a 未说明仍为 0、6d 仍为 0。另把 PRC-07、08 的 BPMN 覆盖写入工作区 `revenue/bpmn/`。

## 5. 禁止
不联网、不起容器；不改卡外文件；不改业务含义以外的东西；用语先查 `docs/modernization/glossary/terms.yaml`；系统称 FORCITIS one。

## 6. 报告（最后一条消息，中文）
改动文件、PRC-08 步号顺延对照表（旧 → 新）、第 4 节命令输出、未完成项与拿不准的条目。
