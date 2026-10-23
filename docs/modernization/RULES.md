# 规则

规则只有 `CLAUDE.md` 底线、本文件与 `roles/`，数值只在 `policy.json`；执行栏写检查名或「人工：<角色>／<节奏>」。

## ① 底线
执行：gitleaks、prod_write_gate、new_intent、main_checkout_gate、backend、人工：首轮复审／每个 PR

底线见 `CLAUDE.md`。

## ② 开卡与交接
执行：handoff、stop_check、start_sheet、人工：lead／周复盘

一卡一个可验收结果、一个负责会话、单 bc，二乙起一卡一个 Issue；入口条件不满足就停下报 lead。卡交出时先覆盖写交接文件（上限见 policy.json 的 `start.handoff_max_kb`），再结束会话或 `/clear`；交出前回测错题本，记录与事实不符以事实为准并更正；开工先核实况。上下文到阈值时 stop_check 每个会话一生最多挡一次。会话消息只用于唤醒，内容写卡或 PR 评论；只在被阻塞、须用户决定、事故时给 lead 发消息。

## ③ 分支与工作区
执行：main_checkout_gate、janitor

从最新 main 建工作区与分支，名为 `<会话>-<卡号>`，与卡同生同灭、不复用；改同一文件的卡串行；git 写操作用绝对路径。

## ④ 提交与推送
执行：commit_msg_gate、budget_check、人工：全员／每次

一项一提交，带 Session trailer；推送不跑测试，测试只在 CI。命令超过 policy.json 的 `shell.foreground_max_seconds`（待加）放后台，不在前台轮询，拿到退出码才报成功。

## ⑤ PR 与复审
执行：post_review、人工：首轮复审／每个 PR

PR 能单独合入与回退，按模板填，二乙起写 `Closes #<卡>`；守护、CI、钩子、跨 bc 接口、机械改名、部署阻塞各自单开 PR，合入后 main 仍绿。作者按 `scripts/review/PROMPT.md` 拉起复审子代理，子代理只写结论文件，由派工人运行 post_review 提交；复核由新子代理带上轮 review 全文只看必改项；安全、生产、钩子、解析类加 opus 终审。

## ⑥ 入队与合入
执行：mq、人工：lead／每个 PR

合入队列改造推迟，现行做法不变：复审通过后，调度在 PR 上留「调度批准入队 head <sha>」，由 one-merge 入队；只由合入队列合入，入队后不再推送。post_review 的 review 与「复审通过」标签在改造前只作记录，合入队列不读。

## ⑦ 文档快道
执行：relevant_change、mq

文档改动开小 PR；relevant_change 判为文档档的免复审，入队仍按⑥。

## ⑧ 什么进 git
执行：budget_check、build_all、gitleaks、janitor

CI 或别的会话要读的进；可由仓库生成的、一次性的不进；本机自用的放工具库；机密与个人信息永不进，报告前扫全历史。一类一份正本，归属见角色文件；只改源不改生成物。记忆只放用户偏好与指针（上限见 policy.json 的 `memory_max_kb`），规则回仓库，冲突以仓库为准。建常驻任务、持久配置或云资源即登记资源表，用完清理。

## ⑨ 须用户决定的事
执行：new_intent、人工：lead／每日拍板

brief 审批、ADR 采纳、规则评审结论；业务口径与用语、会计口径（经财务）、配置新版本；部署与生产变更、凭据轮换与历史清理；建账号、起容器（已登记镜像的本机临时测试容器除外，测完回收）、对外调用、购买与对外账号；敏感数据可见角色；关账后更正与期间重开。与已定决定字面不符不称「落地细化」，交 design 判；不在会话里向用户提问；流程类口头决定记为提议，按 policy.json 的 `decisions.daily_decide_at` 统一拍板。推送、开 PR、触发 CI 有长期授权；PR 的 Auto-fix 不开，被打开即报 lead；它、应用提示的常设授权、钩子留的口子都不算用户授权。

## ⑩ 模型分配
执行：delegate、budget_level

会话模型与配置见 policy.json 的 `sessions`。opus 只用于首轮复审、安全、守护实现与终审、设计判断，其余 sonnet。派子代理写明模型、范围、输出上限，不重复派同一件事。外部模型只经 delegate，准入见 `roles/platform.md`。

## ⑪ 改规则
执行：budget_check

改本文件、角色文件、policy.json 走⑦（是否文档档由 relevant_change 判），正文引用 intent；体量上限见 policy.json 的 `rules`。

## ⑫ 限流措施
执行：budget_check、人工：lead／周复盘

一个问题一种手段，换前先删旧的；限流与节奏措施在 policy.json 的 `measures` 写齐指标、基线、复盘日、撤销条件；冻结按日历并写最长期限；守护只因已发生的事故而加。
