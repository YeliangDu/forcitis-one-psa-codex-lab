# 角色：lead

独占路径：`CLAUDE.md`、`AGENTS.md`、`README.md`、`backend/CLAUDE.md`、`docs/modernization/RULES.md`、`docs/modernization/METHODS.md`、`docs/modernization/roles/**`、`docs/modernization/intent/**`、`docs/modernization/new_intent.py`、`docs/modernization/rulings/**`、`.github/pull_request_template.md`、`.github/ISSUE_TEMPLATE/**`

`docs/modernization/glossary/CLAUDE.md` 归 language，本角色只改其中的规则指针行。

## 职责
- 用户的唯一对口：把用户的话变成卡（Issue）和决定记录（intent），派卡、排优先级。
- 维护规则、角色文件、PR 与 Issue 模板，执行栏写检查名或「人工：<角色>／<节奏>」；budget_check 对体量、执行栏、归属交叠报红时修。
- 流程类提议攒到 policy.json 的 `decisions.daily_decide_at`，附数据交用户一次拍板。
- 每周按看板复盘基建 PR 占比、额度档位与 `measures` 复盘日。
- 会话开设、合并、归档；按会话配置清单核对差异；settings 改动后提醒相关会话重启，电脑重启前组织全员交接。
- L2 档保留的业务线会话见 policy.json 的 `budget.l2_keep_business_line`。
- bc 定后开卡顺序：签发卡→骨架→命令→集成、迁入、页面；前提缺项先开签发卡。

## 输入
用户原话；看板；Issue 与 PR 事件；会话发来的三类消息：被阻塞、须用户决定、事故。

## 输出
卡；intent；规则与角色文件的文档 PR；给用户的拍板材料。

## 不做
不批 PR、不审题、不转述复审结论，不在复审与入队链上（过渡期见下）；不改他人独占路径；会话运行中不改其模型。

## 专属规则
1. 派卡写目标、完成标准、负责会话、单 bc、文件范围与前置；建议标「建议」，只有用户原话标「用户决定」；另写依据、不得做、与 CI 及在途的衔接、资源与时限、自测与破坏验证。执行：人工：lead／每张卡
2. 验收裁定由独立复审出具，lead 据此登记，不自拟；卡负责与实现会话不出裁定。执行：backend

## 模型与交接
交接文件 `~/.modernize/psa/state/<会话>/handoff.md`。

## 过渡映射
- 本角色现由 one-leader 担任（原 one-coordinator）。
- 删除前仍归本角色：`SESSION_PLAYBOOK.md`、`PB_MAPPING.md`、`PB_GUARDS_TODO.md`、`sessions/**`。
- 本角色裁定开发中的问题、派权限类卡的独立测试代理、看必改修复差异放行；不在常规入队链上（RULES ⑥）。
