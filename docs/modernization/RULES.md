# 规则

规则只有本文件与 `roles/`，数值只在 `policy.json`；「执行」写检查名或「人：<会话>」。

## ① 底线
1. 用中文：对话、报告、跨会话消息、子代理回复。执行：人：全员
2. 老代码、老系统、外部系统只作证据，不照搬结构与命名；复刻老行为以生产数据为证。执行：LegacyShapeTest
3. 代码与测试不写密钥字面量；不打印、不调用会返回密钥的接口；原值不落盘，不进会话、共享文件、仓库与报告；泄露按安全事件报 one-lead；报告前扫全历史。执行：gitleaks
4. 生产只读：只用专用只读账号取证并登记，口径按 ADR-0037 第 8 项；明细与个人信息不进会话与仓库，只出计数或聚合，或落受限目录（保留见 policy.json 的 `evidence.retention_days`）；不做任何生产写；取证子代理不改不包装运维脚本、不装依赖，缺凭据即停。执行：prod_write_gate
5. 用户已定的照做，有异议停下报告，不推迟不改变；与已定决定字面不符不称「落地细化」，交 one-design 判。⑨所列写进决定库等答复，不替用户拍板，不让别的会话代做被拒操作；口头决定当场用 new_intent 记原话。执行：new_intent
6. 钩子或 CI 挡住按提示改，不用 `--no-verify`、不自批绕过，有口子也不算授权；不为过测试放宽或删断言；验收测试由独立测试代理先写，实现方不改，裁定由独立复审出具。执行：acceptance_guard；人：首轮复审
7. 宣告完成、可用、通过须附可复现验证；转述结论先核原文，标已核实、已抽查或未核实。执行：人：首轮复审
8. 只在自己的工作区与分支干活，主检出不留改动；git 写操作用绝对路径，先核对目录与分支。执行：main_checkout_gate

## ② 开卡与交接
一卡一个 Issue、一个可验收结果、一个负责会话、单 bc；入口条件不满足就停下报 one-lead。卡交出时先覆盖写交接文件（上限见 policy.json 的 `handoff.max_bytes`），再结束会话或 `/clear`；开工先核实况。执行：handoff；stop_check；start_sheet --check

## ③ 分支与工作区
从最新 main 建工作区与分支，名为 `<会话>-<卡号>`，与卡同生同灭、不复用；改同一文件的卡串行。执行：main_checkout_gate；janitor

## ④ 提交与推送
一项一提交，带 Session trailer；推送不跑测试，测试只在 CI。命令超过 policy.json 的 `shell.foreground_max_s` 放后台，不在前台轮询，拿到退出码才报成功。执行：commit_msg_gate；budget_check；人：全员

## ⑤ PR 与复审
PR 能单独合入与回退，写 `Closes #<卡>`，按模板填。作者按 `scripts/review/PROMPT.md` 拉起复审子代理，用 post_review 提交带机读头的普通 review，通过打「复审通过」标签，必改去标签。复核由新子代理带上轮 review 全文只看必改项。安全、生产、钩子、解析类加 opus 终审。执行：post_review；mq

## ⑥ 入队与合入
mq 机械入队，无开关：有「复审通过」标签，且其 review 的 commit 等于当前 head（只多了合 main 的提交视为一致），轻 CI 绿，无冲突，无 hold。只由 mq 合入；入队后不再推送。执行：mq

## ⑦ 文档快道
文档改动开小 PR；relevant_change 判为文档档的免复审，轻 CI 绿即由 mq 合入。执行：relevant_change；mq

## ⑧ 什么进 git
CI 或别的会话要读的进；可由仓库生成的、一次性的不进；本机自用的放工具库；机密与个人信息永不进。一类一份正本，归属见角色文件；只改源不改生成物。执行：budget_check；build_all --check

## ⑨ 须用户决定的事
brief 审批、ADR 采纳、规则评审结论；业务口径与用语、会计口径（经财务）、配置新版本；部署与生产变更、凭据轮换与历史清理；建账号、起容器（已登记镜像的本机临时测试容器除外）、对外调用、购买与对外账号；敏感数据可见角色；关账后更正与期间重开。流程类口头决定记为提议，按 policy.json 的 `decisions.ruling_time` 统一拍板。推送、开 PR、触发 CI 有长期授权。执行：new_intent；人：one-lead

## ⑩ 模型分配
会话模型与配置见 policy.json 的 `sessions`。opus 只用于首轮复审、安全、守护实现与终审、设计判断，其余 sonnet。外部模型只经 delegate，准入见 `roles/platform.md`。执行：delegate；budget_level

## ⑪ 改规则
改本文件、角色文件、policy.json 走⑦，正文引用 intent；体量上限见 policy.json 的 `rules`。执行：budget_check

## ⑫ 限流措施
一个问题一种手段，换前先删旧的；限流与节奏措施在 policy.json 的 `measures` 写齐指标、基线、复盘日、撤销条件；冻结按日历并写最长期限；守护只因已发生的事故而加。执行：budget_check；人：one-lead
