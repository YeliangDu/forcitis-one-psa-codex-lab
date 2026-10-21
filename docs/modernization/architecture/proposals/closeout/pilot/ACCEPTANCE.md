# 云端试点 VS-11 验收（RESULT.md 验收节）

状态：历史记录｜2026-10-17｜负责：one-architecture

试点结果见分支 `claude/arch-cloud-pilot` @ 1b914e4d 的 `RESULT.md`；本文是其验收节，单独成文以免与试点分支同名文件冲突。

## 1. 用 --extra 在本机重跑四项检查

输入：试点 `processes.vs11.yaml`（1b914e4d）；检查脚本为本分支 ebc52071 加 `--extra` 后的版本；三组分支取 origin 最新（people 379f2483）。不再拼 worktree。

| 检查 | 命令 | 结果 |
|---|---|---|
| 生成图 | `bpmn_gen.py <vs11> --out <tmp>` | 退出 0 |
| 图校验 | `bpmn_check.py <tmp>` | PRC-107：错误 0，警告 0 |
| 对应检查 | `closeout_check.py --qc --all --extra <vs11> --no-write` | 退出 0；PRC-107 无缺口 |
| 归段 | `stage_align_check.py --no-write --extra <vs11>` | 退出 0；VS-11 只剩基线已有的「流程稀少」；试点报告的归段第 6 项（stages_note 只读目录）已消失 |
| 对象层 | `object_layer_check.py --no-write --extra <vs11>` | 退出 0；①②④ 均为 0 |

## 2. 人员组合并后的 PRC-107 与试点比对

人员组 379f2483（含 0d220033 合并）对试点逐字段比对：触发（human、call、time）、步骤数 7、各步命令、退回去向、超时、通知、判断条件、职责分离、负担参数一致，第 1–12 条均已落实。

不同处，均认可：
- 结局多「已提醒」：按人员组「超时只提醒、不中断」约定（S-28）。
- 人员组骨架不写 stages_note：目录 PRC-107 已补（ebc52071），归段检查认目录。
- 说明性字段（human_note、merged_from、notes、迁入与访问范围措辞、角色说明）人员组更细，以其为准；PRC-182 保留人员组版本（第 13 条）。

## 3. 结论

验收通过。单个骨架文件可直接用 `--extra` 检查；铺开到其他价值流的技术前提已具备，是否铺开由 one-coordinator 定。
