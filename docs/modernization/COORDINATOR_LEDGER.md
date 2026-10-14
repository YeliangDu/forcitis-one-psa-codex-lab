# one-coordinator 调度台账（原 psa-coordinator）

调度会话的工作记忆。会话被压缩或重开时，先读本文件（调度会话名为 one-coordinator；工作会话向它报告）、`python3 docs/modernization/build_all.py` 生成的 STATUS.md（或 generated-docs 分支）、SESSION_PLAYBOOK §11–§14。只记「正在进行」的东西，完成即删；历史在 intent/、session-log/ 与 git 记录里。本文件只由调度会话维护，不拆片段。

更新：2026-10-15 20:50（one-coordinator）

## 用户授权调度会话自行决定的事
- 审核并合并 PR（「合 PR 交给你审核，决定权给你」）。main 受保护：只要求 backend / frontend / gitleaks 三项 CI 通过（另有 assertion drift 仅警告），管理员也受限。
- 是否新开会话、如何划分（「是否需要新会话这种决策权交给你」）。会话由用户新开，或复用已完成的会话并改名（`mcp__ccd_session_mgmt__set_session_title`）。新会话名用 `one-*` 前缀，已有 `psa-*` 不改。
- 用户在任何会话中的原话以会话记录为准；他人转达的决定先用会话记录搜索或当面复核。
- 不能替用户决定：部署、生产变更、购买、对外账号、已采纳 ADR 的变更（须新 ADR 取代并经用户批准）。

## 合并 PR 的操作要点
- 生成产物（INTENT.md、SESSION_LOG.md、STATUS.md、MODERNIZATION_BRIEF.md、adr/ADR-*.md、adr/README.md）冲突：取任一边后运行 `python3 docs/modernization/build_all.py`，再 `--check`。scratchpad 曾有脚本 update_pr.sh 做这件事（会话 scratchpad 不保留，必要时重写：worktree 检出 PR 分支 → merge origin/main → 生成产物取 theirs → build_all → push）。
- 非生成文件冲突：退回给作者会话合并（不要替作者手工合并业务文档）；作者推到本地分支后，由调度会话 `git push origin <commit>:refs/heads/<PR 分支>`（先确认是快进）。
- zsh 中 `"$H:claude/..."` 会被当成修饰符，写成 `"${H}:refs/heads/..."`。
- 审核要点：与 main 是否冲突；gitleaks 全历史；build_all --check；文档不含金额与个人信息；不得修改已合入的迁移（CI 有 migrations_immutable）；文档不用「PSA」指代系统（CI 有 check_legacy_name）。
- 代码 PR 在工作会话的 opus 复审结论出来之前不合并（#50 教训）；只有文档 PR 可凭 CI 合并。
- CI 偶发失败先看是否与 PR 内容无关（如测试抖动），`gh run rerun <id> --failed`，并让 1a 修成确定性。

## 在途工作会话
| 会话 | 在做 | 下一步 |
|---|---|---|
| psa-1a-platform-bridge-reimagine | 修复批六路（A 审批/工时、B1 身份授权、B2 数据架构、B2b 事件指标、B3 平台约定、C 前端）；A、C、B2b 可整合，B2 终核中，B1、B3 修复中；已按 §6.1 改为滚动整合、复核按严重度分流 | 整合分支逐路并入 → 整体 opus 复审 → 我核对合并 → 删除 status/blockers/080。紧接下一批（首次部署前必须）：全部聚合主键 UUIDv7（用户已定，B3 曾擅自推迟）、84 项改名、迁移基线（含「只增不删」守护）、OneApplication main 与 spring-boot-maven-plugin（ZIP layout，回放命令走 PropertiesLauncher）、G-T1..G-T4 守护。下一批按 §6.1 七条派工 |
| one-ops | 运维线长期会话。已做：C-3 dev 库与角色（one_admin 先建后轮换，builtin C.UTF-8）、B-5 A8 与老系统只读视图、C-4（Temurin 25、证书目录新布局、TLS 收紧、出站白名单、单元已装未启用）、C-6 角色与日历导出（回放导出进行中）、C-7 证书检查定时器、SLS 开通 | PR：C-3 / C-6 工具、nginx 模板、install-enable（#67 待解冲突）；DEV_DEPLOY_REMAINING 并入 #59 的 R-0..R-12；C-2 备份窗口改 02:00–03:00（先核门户夜间负载）；RDS 10-18 到期续费核对；RDS PG verify-full（CA 链） |
| forcitis-one-psa-36（design-sync 预演） | 已交 #64（字体令牌、预演项目同步） | 遗留：品牌字体入库、SelectField 出错样式、token 与 VI 标准色（#CF000E / #595757、Montserrat）对齐；正本同步前做 |

已完成、可复用：psa-repo-fragments、psa-olap-architecture、psa-terms-review（第三轮用语确认待启动）、psa-1a-process-review、psa-authz-model、one-docs-naming、psa-gl-finance-review、psa-review-*。旧调度会话 psa-coordinator-1 已交接。

## 重要阻塞
- 1a 修复批合并前禁止部署任何环境（status/blockers/080）。修复批合并前须 opus 复审。
- 首次发布前：OneApplication 无 main（build.sh 拒绝打包），由下一批修。

## 本机设施
- CI：本机自托管 runner mac-one-1..4（`~/actions-runners/one-N`，标签 forcitis-one）。部署 runner mac-one-deploy 已从 GitHub 注销，待用户执行 R-0 重建（/opt/forcitis-one-runner、root 所有、hook、--disableupdate，用户 forcitis-deploy 已建）。发布打包与签名在 GitHub 托管机。
- 代码 PR 合并前须有 opus 复审结论；文档 PR 凭 CI 合并。

## 待合并 PR
- #67 安装器首次 enable（one-ops 解冲突中）。已合并：#51–#61、#63–#66、#68（#62 与 #64 重复已关闭）。

## 采纳的规则待落地
- §6.1 修复批流程七条（2026-10-15）：滚动整合与复核分流已用于本批，其余下一批启用；记录复审轮次与耗时作对比。
- 守护 G-T1..G-T4、规格到测试对照、迁移基线开放标记 → 1a 下一批。
- brief 各阶段入口条件加「表结构评审已完成」「公共基础已冻结」→ brief 重新生成时一起审批。
- 项目技能 one-fix-loop（交付前自查）：已提议，用户未表态。

## 待用户 / 财务 / 运维决定
- 用户待办：RDS 控制台查日志备份频率（RPO；查不到则平台约定改为「主机故障不丢、误操作恢复精度取决于日志备份」）；ICP 备案主体核验；建企业微信「FORCITIS one 运维」群与机器人（A-5，Webhook 经 one-ops 安全窗口）；R-0 部署 runner 重建（sudo）；R-1..R-12；建归档 OSS bucket（国内、归档存储、本地冗余、WORM 2557 天，B-11）与法兰克福发布 bucket；向 A8 方提 v_org_member 加离职日期列。
- 发布流水线 U-1 / U-2 / U-3 / U-5：首次 prod 部署前定（建议接受 U-1、U-2、U-3；U-5 届时再定）。
- 生产前维护窗口（用户定时间）：RDS SQL Server rm-bp1690ep53t9778dp 开 SSL 并强制校验；RDS PG 证书重签（私钥曾进 one-ops 会话记录）。
- 老系统安全风险登记（用户「老系统先不管」）：SQL Server 公网白名单 0.0.0.0/0、psa-nacos 8848 对公司出口免登录可读生产库高权限口令、48088 对公网——这台 SQL Server 也承载新系统读 A8 的只读账号，1a 上线前须再请用户评估。
- 部署前清单：A 类与 N-1..N-3 已决定（用户「A 类全部按建议」「其他人选先空着，N-2、N-3 也采纳」）；平台负责人为用户本人，其余人选待定（本机私有记录 `~/.modernize/psa/infra/role_holders.md`，不入仓库）——生产前须补齐第二名超管（运维）、安全审计员（不能是超管）、组织数据管理员、DBA。B 类只能用户或运维执行（先做 B-1 `/design-sync`、B-2 企业微信应用）；C 类执行前须确认，C-5 等运维命令实现。CI 无发布流水线（清单遗漏项）——待安排。
- 财务：确认单 v2（`~/.modernize/psa/finance/FINANCE_CONFIRM_v2.html`，FQ-01..FQ-22 + 7 份 ADR），答复用 `r0/import_finance_answers.py` 导入；G1 开工前必须。FQ-18 需先跑 U8C 分类查询另行发送。
- 用户：B1–B5 与经营问题清单（`~/.modernize/psa/bi-inputs/questions/`）；OAT 4 个文件在 ddd-theory 提交后改引提交号；确认单末尾「会计类新术语由财务逐条确认」是否保留（未答）。
- 生产前：Phase 2 / 4 出口停用生产 SQL Agent 作业（A8_ACCESS_REGISTER #28–#31），属运维动作须用户批准。

## 排定的后续
- 第三轮用语确认（复用 psa-terms-review）：1a 下一批交付后启动；合并 1a 全部提议术语（按业务 / 技术分组）、R0 的 TERMS_ROUND3_INPUT（A 组 19、新词约 45、NRPM 与「人工成本产出倍数」对齐）、前端用语（含同步 / 放行 / 被中止与 held_run 重叠、待办与 todo / inbox 重叠）、工时类别（项目 / 售前 / OH，OH 疑似方言）、legacy_system（老系统，方言为旧名称）、TS / JV 单据类型代码。ADR-0018：阶段结束时不得留有会计类提议术语。
- 统一编排路线（1a 试点结束、brief 重新生成时一起审批）：Phase 1a–5、6a–6f（ADR-0021）、G1/G2（ADR-0022）、R0–R6（ADR-0023 / 0025）统一编号与依赖；6a 入口加「收付口径推送通道就绪」「过渡镜像就绪」；Phase 4、6a–6c、G1 合为业财一体阶段作为可选方案；客商主数据是否提前；6a–6f、G 与 R 阶段并入模板时补「流程梳理」入口条件。
- 1b 前必须：区域应收负责人指派类型按规格建；代理审批授权模型评审（RULE-270）；ADR-0026 列出的新规则先在 RULE_REVIEWS 登记（法人限定、审批参与只读访问、授予时职责分离组合）；组织数据管理员是否列敏感复核；Phase 1b 流程梳理（新会话 one-1b-process-review）。
- 新 ECS（i-bp122xlpar8w2uylmal2）机内加固随部署清单 C-4 执行。
