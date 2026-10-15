# one-coordinator 调度台账（原 psa-coordinator）

调度会话的工作记忆。会话被压缩或重开时，先读本文件（调度会话名为 one-coordinator；工作会话向它报告）、`python3 docs/modernization/build_all.py` 生成的 STATUS.md（或 generated-docs 分支）、SESSION_PLAYBOOK §11–§14。只记「正在进行」的东西，完成即删；历史在 intent/、session-log/ 与 git 记录里。本文件只由调度会话维护，不拆片段。

更新：2026-10-15 23:10（one-coordinator）

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
| one-1a-next | 1a 下一批（NEXT_BATCH_PLAN）。已合并：W0 #97（基线开放标记、版本单调、验收测试守护）、文档更正 #91。ADR-0030 #93、0032 #94、0031 #95 用户已批准（2026-10-15），待其改状态行后合并；ADR-0029 #96 等 one-ops 生产只读计数 | W1：R-3a 纯合并基线（sonnet，opus 复核 pg_dump diff 为空）→ R-1 UUIDv7 → R-2 改名 → R-4；冻结窗口不派功能代理。再 W2 K-1..K-3、W3 F-1..F-14、W4 单独 PR 删基线标记 |
| one-ops | 运维线长期会话。已做：C-3 dev 库与角色（one_admin 先建后轮换，builtin C.UTF-8）、B-5 A8 与老系统只读视图、C-4（Temurin 25、证书目录新布局、TLS 收紧、出站白名单、单元已装未启用）、C-6 角色与日历导出（回放导出进行中）、C-7 证书检查定时器、SLS 开通 | PR：C-3 / C-6 工具、nginx 模板、install-enable（#67 待解冲突）；DEV_DEPLOY_REMAINING 并入 #59 的 R-0..R-12；C-2 备份窗口改 02:00–03:00（先核门户夜间负载）；RDS PG verify-full（CA 链） |
| forcitis-one-psa-36（design-sync 预演） | 已交 #64（字体令牌、预演项目同步） | 遗留：品牌字体入库、SelectField 出错样式、token 与 VI 标准色（#CF000E / #595757、Montserrat）对齐；正本同步前做 |

已完成、可复用：psa-repo-fragments、psa-olap-architecture、psa-terms-review（第三轮用语确认待启动）、psa-1a-process-review、psa-authz-model、one-docs-naming、psa-gl-finance-review、psa-review-*。旧调度会话 psa-coordinator-1 已交接。

## 后台任务与持久配置（SESSION_PLAYBOOK §2.1 第 9 条）
| 位置 | 项 | 性质 | 负责 | 清理 |
|---|---|---|---|---|
| ECS i-bp122xlpar8w2uylmal2 | /root/one-ops-backup-20261015020632（nginx 切换前配置与续期配置备份） | 临时（回滚用） | one-ops | 首次 dev 部署验证通过后删除（已同意） |
| ECS | /etc/forcitis-one/release-signers、release-source、github-release-token（空，OIDC 改造后删除）、one-release-token-check.{service,timer}（未启用，OIDC 改造后删除） | 长期 / 待撤 | one-ops | 随 OIDC 改造 PR |
| ECS | /data/forcitis-one/import/c6/20261015/（C-6 导出，root 0700） | 临时 | one-ops | 1a 生产切换后 30 天 |
| ECS | one-cert-check.timer、LoongCollector（ilogtaild / loongcollectord）、/etc/forcitis-one/ops-alert.env（群机器人 Webhook，root 0600）、/etc/forcitis-one/one.env、one-admin.pgpass、migrate/ | 长期 | one-ops | — |
| 本机 Mac | 自托管 runner mac-one-1..4（~/actions-runners/one-N，LaunchAgent，用户 yeliang）与 mac-one-deploy（/opt/forcitis-one-runner，LaunchDaemon，用户 forcitis-deploy，pre-job hook） | 长期 | one-coordinator / one-ops | — |
| 本机 Mac | ~/.modernize/psa/infra/role_holders.md（人名，0600）、~/.modernize/psa/finance/FINANCE_CONFIRM_v3.html（0600） | 长期私有 | one-coordinator | — |
| 阿里云 | SLS Project forcitis-one-observability（30 天）、云监控 OS 指标、ECS 快照策略 forcitis-one-daily | 长期付费（约 40 元/月以内） | one-ops | — |
| 阿里云 | OSS bucket forcitis-one-archive（杭州、归档、本地冗余、私有、SSE-OSS、仅 HTTPS；WORM 未锁，第一个写入方上线前重建并锁定）、RAM 策略 forcitis-one-archive-writer、RAM 角色 forcitis-one-ecs-archive（已绑定 ECS） | 长期 | one-ops | — |
| GitHub（用户账号） | fine-grained 令牌 forcitis-one-release-dev-2026-10-15（FORCITIS 组织、仅 forcitis-one-psa、Actions / Contents / Deployments / Metadata 只读，到期 2027-01-13）——OIDC 方案的后备，未安装到任何位置；令牌值不经会话 | 后备 | 用户 | 首次 dev 部署经 OIDC 成功后由用户删除（one-coordinator 提醒） |
| 阿里云 / GitHub | OIDC 提供商、RAM 角色 forcitis-one-deploy-dev、云助手固定命令、仓库 OIDC sub 定制（OIDC 改造 PR 合并前执行，逐条报 one-coordinator） | 长期（待建） | one-ops | — |
| GitHub | environments release / dev / prod、签名私钥 secret（release）、v* 标签规则（仓库级，组织级待用户定）、immutable releases | 长期 | one-ops | — |

## 重要阻塞
- 部署门禁只剩 status/blockers/090（数据范围整套，F-1）。基线开放标记存在时 deploy_gate 也拒绝部署（W4 单独 PR 删除）。
- 生产前（不阻塞 dev）：C-11 关单人模式（ADR-0030，进 PROD_PREREQUISITES）、C-12 四眼与财务确认、CODEOWNERS 再定。

## 本机设施
- CI：本机自托管 runner mac-one-1..4（`~/actions-runners/one-N`，标签 forcitis-one）。部署 runner mac-one-deploy 已重建（/opt/forcitis-one-runner、LaunchDaemon、用户 forcitis-deploy、pre-job hook）。main 分支保护已开 strict（2026-10-15 用户「开启」）：合并前须 `gh pr update-branch <n>` 并等 CI 重跑。发布打包与签名在 GitHub 托管机。
- 代码 PR 合并前须有 opus 复审结论；文档 PR 凭 CI 合并。

## 待合并 PR
- #93、#94、#95（ADR，待 one-1a-next 改状态行）；#96（ADR-0029，待取证）。已合并至 #97（#62 重复已关闭；#73 随 #89 合入）。
- 改守护文件（acceptance 守护、迁移守护、ci.yml、基线标记、ADR-0032 快照与迁移例外清单）的 PR：在 PR 审核中单列，须有 opus 复审（CODEOWNERS 生产前再定）。

## 采纳的规则待落地
- §6.1 修复批流程七条（2026-10-15）：滚动整合与复核分流已用于本批，其余下一批启用；记录复审轮次与耗时作对比。
- 守护 G-T1..G-T4、规格到测试对照、迁移基线开放标记 → 1a 下一批。
- brief 各阶段入口条件加「表结构评审已完成」「公共基础已冻结」→ brief 重新生成时一起审批。
- 项目技能 one-fix-loop（交付前自查）：已提议，用户未表态。

## 待用户 / 财务 / 运维决定
- （2026-10-15 用户「需要我做的，都让ops 会话自己做」：R-4 改 OIDC、R-13 免除、云助手命令与 B-13 由 one-ops 做；ICP 与备案用户「已经完成所有备案」。下列旧条目以此为准。）
- 用户待办（旧）：RDS 控制台查日志备份频率（RPO；查不到则平台约定改为「主机故障不丢、误操作恢复精度取决于日志备份」）；ICP 备案主体核验；建企业微信「FORCITIS one 运维」群与机器人（A-5，Webhook 经 one-ops 安全窗口）；R-0 部署 runner 重建（sudo）；R-1..R-12；建归档 OSS bucket（国内、归档存储、本地冗余、WORM 2557 天，B-11）与法兰克福发布 bucket；向 A8 方提 v_org_member 加离职日期列。
- 发布流水线 U-1 / U-2 / U-3 / U-5：首次 prod 部署前定（建议接受 U-1、U-2、U-3；U-5 届时再定）。
- 部署前清单：A 类与 N-1..N-3 已决定（用户「A 类全部按建议」「其他人选先空着，N-2、N-3 也采纳」）；平台负责人为用户本人，其余人选待定（本机私有记录 `~/.modernize/psa/infra/role_holders.md`，不入仓库）——生产前须补齐第二名超管（运维）、安全审计员（不能是超管）、组织数据管理员、DBA。B 类只能用户或运维执行（先做 B-1 `/design-sync`、B-2 企业微信应用）；C 类执行前须确认，C-5 等运维命令实现。CI 无发布流水线（清单遗漏项）——待安排。
- 财务：确认单 v2（`~/.modernize/psa/finance/FINANCE_CONFIRM_v2.html`，FQ-01..FQ-22 + 7 份 ADR），答复用 `r0/import_finance_answers.py` 导入；G1 开工前必须。FQ-18 需先跑 U8C 分类查询另行发送。
- 用户：B1–B5 与经营问题清单（`~/.modernize/psa/bi-inputs/questions/`）；OAT 4 个文件在 ddd-theory 提交后改引提交号；确认单末尾「会计类新术语由财务逐条确认」是否保留（未答）。
- 生产前：Phase 2 / 4 出口停用生产 SQL Agent 作业（A8_ACCESS_REGISTER #28–#31），属运维动作须用户批准。

## 已登记的现有事实（one-1a-next 2026-10-15 报告，待修）
- 生产主机上身份运维命令的「见证人」只是自报文本，有 shell 权限即可单人执行 → 生产前项，交 one-ops 写入 PREDEPLOY_CHECKLIST（与 C-11 同批定方案）。
- 运维手册用 `--spring.profiles.active=identity-operations` 会顶掉 dev/prod，生产运维命令跳过 VERIFIED_SERVER 校验 → 缺陷，随 ADR-0030 实现（F-12）改为 include 写法，加回归测试；one-ops 同步改手册。
- ADR-0028 第 1 条「可用超管少于 2 名即暂停」未实现（ApplySourceSnapshot 只在离职名单含超管 / 审计员时暂停）；第 4 条的运维放行命令不存在 → 缺陷卡，one-1a-next 按 §6.1 手术式修复。

- 用语缺陷：terms.yaml 的 job_grade（职级，T1–T7，「计费费率与折扣权限的档位依据」）与取证不符——A8 无独立职级字段，T1–T7 只是 ORG_POST 岗位名前缀，ORG_LEVEL 是管理职务；别名与 position 重叠。并入第三轮用语确认与岗位体系重梳；澄清前任何会话（1b 流程梳理、R 系列、F-8「职级标准费率」掩码）不得自行定职级来源。

## Codex 试点（用户 2026-10-15「用最合适的模型，你试试」）
- 调用：`codex exec -m gpt-6.1-sol -c model_reasoning_effort=high -s <read-only|workspace-write> --ephemeral -o <out> - < prompt`，在独立 worktree 中运行；Codex 读 AGENTS.md，不读 CLAUDE.md，提示词须要求先读 CLAUDE.md 与 SESSION_PLAYBOOK §5、§6.1。
- 冒烟（只读，R-4 方案，约 5 分钟）：清单 102 处 / 31 文件 / 49 表与计划一致，抽查属实，未改文件；指出框架管理表（Spring Session、Modulith 事件登记、ShedLock usingDbTime）、种子迁移、Flowable 边界等须先决定的问题。输出存 `~/.modernize/psa/codex/R-4-plan-codex-smoke.md`。
- 正式试点：R-4 实现（R-2 合并后），流程不变（测试代理先写验收、opus 复审、PR 注明 Codex 实现）；记录返工轮次与耗时，与 sonnet 卡对比。

- 离职日期：A8 人员表无离职日期；「员工离职申请」表单 formmain_0348 有预计离职日期 field0031（员工编号 field0012 关联），1b 规则卡由 one-1a-next 登记——预计离职日期与观察到的离职时间两字段，未结束申请属敏感。
- 首个 Release 0.1.0-rc.1：等 one-ops 的 OIDC 改造 PR 合并后由 one-coordinator 手动触发 release.yml（workflow_dispatch，version=0.1.0-rc.1，draft=false）。
- 公安联网备案：用户「已经完成所有备案」；登录页只展示 ICP 号（F-15，W3）。

## 排定的后续
- 规则文档瘦身（用户 2026-10-15「好，按这个计划做」）：前置——CODING_STANDARD（A–D）合并、原则核对 A / B 采纳；然后派 opus 出「规则 → 唯一位置」对照表与删除 / 归档清单，经用户确认后执行，加防重复检查。
- 待用户决定项集中问：DDL 基线审计、原则核对 A / B、CODING_STANDARD 冲突清单。
- 第三轮用语确认（复用 psa-terms-review）：1a 下一批交付后启动；合并 1a 全部提议术语（按业务 / 技术分组）、R0 的 TERMS_ROUND3_INPUT（A 组 19、新词约 45、NRPM 与「人工成本产出倍数」对齐）、前端用语（含同步 / 放行 / 被中止与 held_run 重叠、待办与 todo / inbox 重叠）、工时类别（项目 / 售前 / OH，OH 疑似方言）、legacy_system（老系统，方言为旧名称）、TS / JV 单据类型代码。ADR-0018：阶段结束时不得留有会计类提议术语。
- 统一编排路线（1a 试点结束、brief 重新生成时一起审批）：Phase 1a–5、6a–6f（ADR-0021）、G1/G2（ADR-0022）、R0–R6（ADR-0023 / 0025）统一编号与依赖；6a 入口加「收付口径推送通道就绪」「过渡镜像就绪」；Phase 4、6a–6c、G1 合为业财一体阶段作为可选方案；客商主数据是否提前；6a–6f、G 与 R 阶段并入模板时补「流程梳理」入口条件。
- 1b 前必须：区域应收负责人指派类型按规格建；代理审批授权模型评审（RULE-270）；ADR-0026 列出的新规则先在 RULE_REVIEWS 登记（法人限定、审批参与只读访问、授予时职责分离组合）；组织数据管理员是否列敏感复核；Phase 1b 流程梳理（新会话 one-1b-process-review）。
- 新 ECS（i-bp122xlpar8w2uylmal2）机内加固随部署清单 C-4 执行。
