# 待建守护清单（新 PB 附表 C 详单，GOV-1022-COO-15 第二节第 11 条）

卡号为预留号，`tasks/` 里尚无对应片段（COO-10、COO-13 已派），由 one-coordinator 开卡；排序与「前 10」取舍归 one-quality（TEAM-84）。实现方：钩子、并发、命令文本解析类 one-quality 自写并经 opus 复审；其余可交 Codex（gpt-6.1-sol，强度按卡），产出照常 opus 首轮。守护上线的同一提交把对应 TEAM 条改为「守」（TEAM-83）。

## 落地前置（已随新 PB 同一 PR 完成：playbook_digest 下线；build_sessions 已无「当前状态」节，字段集严格校验，QUA-12 并入；开工单 COO-13 已合入 #627）

## 待建守护

| 卡号 | 规则 | 防什么 | 建议实现 | 实现方 |
|---|---|---|---|---|
| GOV-1022-QUA-11 | TEAM-82、83（①） | 规则标「守」但守护文件不存在或没注册（P-39 那种文件在、没接上）；TEAM 号引用悬空；废止条被引用 | build_all.py --check 新增 `check_rule_table.py`：解析 PB 规则表，「守」「半」行的守护名须能在 settings.json、.pre-commit-config.yaml、ci.yml、precheck.sh、workflows 中找到；全仓 grep `TEAM-\d+` 须在表内且未废止 | 自写（解析规则表与多份配置，属解析类） |
| GOV-1022-QUA-13 | TEAM-04（③） | 两个会话独占同一路径，并行改同一文件 | build_sessions.py：解析各画像「独占」中的反引号路径，按前缀两两求交，相交即失败；允许清单写在 `sessions/_overlaps.txt`（带调度裁定出处） | Codex |
| GOV-1022-QUA-14 | TEAM-20、24（⑤） | 任务卡状态词漂移、缺目标或完成标准、前置卡号；12 小时不更新 | 先跑一次性归一脚本把 141 份旧片段状态收为 5 值，再把 check_tasks.py 由列出改为阻断（build_all.py --check）：状态 ∉ 5 值、缺「目标」「完成标准」段即失败；陈旧仍只列出 | Codex（归一与校验均为结构化文本，非钩子） |
| GOV-1022-QUA-15 | TEAM-64（⑥） | 确认页引外部字体、状态值不在词表、缺 runtime（ArtifactData）自动保存 | 扩 check_pages.py：查 `fonts.googleapis`/`@import url(` 等外链字体、状态值白名单、页面声明 db capability；precheck 已注册，另加进 ci.yml | Codex |
| GOV-1022-QUA-16 | TEAM-46（⑦） | 数据登记文件被改却无 one-data 签发 | ci.yml 新步骤：PR diff 触及登记文件清单（由 one-data 给出路径表）时，PR 说明或提交 trailer 须有「数据设计：通过（one-data，日期）」；本地 precheck 同查 | 自写（判定签发真伪属权限边界；可让 Codex 起草测试） |
| GOV-1022-COO-17 | TEAM-87（⑧，COO-10 即 #616 已合入，余项） | `$'g\x69t'`、通配符、`bash <<EOF`、`env -S`、`git -c alias.`、`gh -R` 仍可绕过钩子命令匹配 | 分发器分词补这几类，差分自测 | 自写（钩子、解析类） |
| GOV-1022-QUA-17 | TEAM-25 | 卡交出不核 PR 与复审结论；节点自清无可靠信号 | 新 `scripts/finish.py <任务号>`：`gh pr list` 核 PR 已开、PR 评论有复审结论，改卡为「已交」，写 `.claude/node-marker`；context_handoff 只认标记文件 | 自写（与钩子联动；finish.py 本体可 Codex 起草） |
| GOV-1022-QUA-18 | TEAM-06 | 节点自清只在部分会话生效；清空前未交接 | context_handoff.py 读 node-marker 与用量 ≥20% 时注入固定自清文案，适用会话改为全员（除 coordinator、architecture） | 自写（钩子） |
| GOV-1022-QUA-19 | TEAM-31、67 | 派子代理不写 model，默认继承 opus；opus 用在复核、取数 | PreToolUse 分发器加 Agent/Task 检查：`model` 缺失即拒绝；Workflow 脚本由 precheck grep `agent(` 调用须带 model | 自写（钩子） |
| GOV-1022-QUA-20 | TEAM-47 | 会话绕过 mq.py 用 `gh pr merge` 或 API 合并 | 分发器拒绝 `gh pr merge`、`gh api .../merge`（one-merge 的 mq.py 进程内调用除外）；网页合并不加仓库规则集（调度定：合并只有用户账号与其会话有权限，靠 TEAM-47 自觉，不改仓库设置） | 自写（钩子） |
| GOV-1022-QUA-21 | TEAM-41 | PR 过大难复审、难回退 | ci.yml 步骤：非生成文件改动 >800 行或 >15 文件即失败，生成物按 .gitattributes `linguist-generated` 排除；超限须 PR 说明写理由并由调度批准评论放行 | Codex |
| GOV-1022-QUA-22 | TEAM-79 | 会话内 cron 随会话结束失效 | 把在跑的会话内定时任务逐个迁到 `.github/workflows/*.yml`（schedule）；涉及远端执行与额度，迁移清单先进收件箱由用户定 | Codex（工作流 YAML），one-quality 审 |
| GOV-1022-QUA-23 | TEAM-28、74 | 无主设计无人认领；周一出数靠手工 | `scripts/weekly_numbers.py`：跑守护①–③、列陈旧卡、超期 ADR 提议、在制 PR 数、Codex 返工率、无卡的设计稿与提议稿，输出给调度日报；可挂 schedule 工作流 | Codex |
| GOV-1022-MRG-01 | TEAM-45 | 入队依据靠跨会话消息转述，易错 head | mq.py：入队前读 PR 评论，须有调度账号发的「调度批准入队 head <sha>」且 sha 等于当前 head，否则拒绝 | one-merge（mq.py 单列 PR、opus 复审） |
| GOV-1022-MRG-02 | TEAM-49 | 合入后负责会话不知道，卡不关 | mq.py 合入后按 PR 标题任务号查 `tasks/` 负责会话，经 ccd send_message 发「#N 已合入（任务号）」给负责会话与调度 | one-merge（同上） |
| GOV-1022-MRG-03 | TEAM-09 | 钩子改动合入后旧会话仍跑旧钩子 | mq.py 合入触及 `scripts/hooks/`、`.claude/settings.json` 的 PR 后，列出启动早于合入时刻的会话报调度 | one-merge（同上） |
| GOV-1022-MRG-04 | TEAM-43 | 单会话在制代码 PR 超 3 个 | mq.py 或 precheck_before_pr：`gh pr create` 前数本会话（按 Session trailer 或分支前缀）开着的代码 PR，≥3 即拒绝 | 钩子侧自写；mq.py 侧 one-merge |

## 不建（写理由）

- TEAM-52 断言防漂移保持只告警（P-37：阻断误报率高），由 opus 首轮必查断言栏。
- TEAM-58–62 消息与收件箱规则无可靠机器信号，靠周一出数抽查。
