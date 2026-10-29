# N4：受控动词、状态与组合模板定稿初稿

日期：2026-10-17。派工卡：N4；工作分支：`claude/one-glossary-codex-n4`。**本稿仅供审定，业务用语未因本稿成为正式用语。** 除已引用户决定的条目外，表中归并、英文词形、权限候选与例子均待审定；「确认入账」已定，其余会计用语另经财务确认。已采纳原则以 `.codex-inputs/ADR-0053.md` 为准。本稿没有登记术语或改变运行行为。

**定稿状态（2026-10-17）**：用户已在命名第 1 批确认页提交 NM1-20261017-101116-a4n（22 题全按推荐，答复 `rules/decide/answers/NM1.json`，intent `2026-10-17-101709-one-glossary-NM1-决策页提交-NM1-20.md`）。据此：近义组与一词多义、状态特例按各题所选项定稿；NM1-10 定为「收款核销」与「核销无法收回款项」分开；NM1-22 认可其余动词整表的中文叫法；确认入账单立（AN-06）。本稿中仍标〔拟〕的英文代码名与权限映射不在此次认可范围，由设计方定。

## 1. 边界、来源与审阅方法

ADR-0053 决定 3 要求以槽位为源、由受控词渲染；名称反解只是迁移工具。本稿区分“已读到的存量证据”“命名建议”“业务裁定”，不把现有代码、候选文档或匹配成功当成业务正确性证明。命令的名称覆盖包括主动作和显式嵌套的事实动作；对象中的名词性“计划、申请、确认”等不自动升级为第二个命令。定义正文中举例提到的动作不属于名称动词的统计分母。

| 来源 | 本次读取范围 | 用途与限制 |
| --- | --- | --- |
| `CLAUDE.md`、`CODING_STANDARD.md`、`NAMING_STANDARD.md`，`METHODS.md` §5、§6.1 | 本地现文件 | 工作边界；已采纳 ADR 与本次用户指令优先 |
| `.codex-inputs/ADR-0053.md`；`.codex-inputs/PREREQ.md` 第一节 N1–N8 | 已采纳 ADR 与前提清单 | 概念不变、指称分离、槽位来源、N2 财务确认与 N4 用户批准的边界 |
| `NAMING_ATOMS_PREP.md`、`NAMING_ATOMS_DRAFT.md`、`ARCH_NAME_AUDIT.md` | 本目录三份全文 | 候选来源；其中关于存量的说法重新核对，不直接当裁定 |
| `.codex-inputs/revenue-commands.yaml` | `commands` 全部 256 行，250 个不同名称；`emits` 129 次、128 个不同字面名 | 收入链提议目录，不是已经实现的接口或已批准的事实目录 |
| `docs/glossary/terms.yaml` | command 46、domain_event 95、state 46 | 正式词表文件内的全部目标 kind；“正式”指文件来源，未按 status 筛除 |
| `backend/glossary/proposed/*.yaml` | 26 个文件；command 10、domain_event 21、state 14 | 提议与历史改名桥接词条全部保留；不因重复而从覆盖分母删除 |
| `backend/glossary/terms.yaml` | standard_actions 全部 10 项；本文件 terms 为空 | 与上面的正式业务词表是不同文件，权限动作在本文件 |
| `backend/src/main` 的 Java、SQL、YAML | 扫描权限字面量并读取控制器、权限目录 | 静态证据；没有登录运行系统，没有验证实际用户授予 |

附录 A 按目录原行顺序列 256 个命令；附录 B 按概念 id 列全部 172 个命令/事件词条；§3.3 列全部 60 个 state；附录 C 列全部 128 个 emits 名。行号 R001…R256 仅是本次目录行序，不是概念 id。词条引用使用 id，不依赖词表行号。

## 2. 受控动词初稿

### 2.1 权限动作核对与映射边界

当前标准动作只有 `read, create, update, delete, submit, approve, reject, export, import, execute`。候选稿的 `confirm, return, publish, disable, enable, close, determine, cancel, withdraw, void, revoke, release, reserve, lock, complete, terminate, review, seal, archive, reverse, issue, write_off, claim, restore, suspend, start` 等不能直接作为现行权限码后缀；本稿不新增 standard_actions。

`Permission.java` 的 Codes 声明与主代码/迁移静态字面量核对得到 27 个不同权限码，覆盖 8 种标准动作；`reject`、`import` 本次未检出权限码。**没有权限字面量不表示业务禁止该动作，也不表示运行时没有权限。**

| 标准动作 | 不同权限码数 | 静态证据 | 实际入口核对边界 |
| --- | --- | --- | --- |
| read | 9 | `Permission.Codes`；如 `organization.workforce:org-unit:read` | `OrgUnitController` 读取接口 |
| create | 1 | `organization.workforce:legal-entity-attribution-correction:create` | `LegalEntityAttributionController` 的修正入口先 isAuthenticated，`CorrectLegalEntityAttribution.execute` 再用 CallerPermissions.holds 检查 create |
| update | 7 | 如 `platform.identity:account-role:update`、`organization.attendance:locked-day:update` | `AuthorizationRequestController` 确认与拒绝均用 update；`RoleController` 创建/修订/废除角色也用 update；锁定日期维护用 update |
| delete | 1 | `platform.identity:session:delete` | `ForcedLogoutController` 强制登出 |
| submit | 1 | `cost.timesheet:timesheet:submit` | 只核到权限目录；本次未检出相应主代码 HTTP 绑定 |
| approve | 6 | 如 `platform.integration:source-reference:approve`、`platform.approval:task:approve` | `SourceReferenceController` 的 confirm/reject 共用 approve；任务相关仅核到目录 |
| reject | 0 | 标准动作已列；本次未检出权限字面量 | 不能由动词 Reject 推导当前已有 :reject 权限 |
| export | 1 | `platform.audit:audit-entry:export` | `AuditExportController` |
| import | 0 | 标准动作已列；本次未检出权限字面量 | 导入动作映射仅建议 |
| execute | 1 | `organization.workforce:sync-run:execute` | `OrgReconciliationController`、`SyncRunController`；放行还区分 separation-hold:approve |

权限映射是“资源 + 用例 + 主体职责”的授权设计，不能是动词的同义替换：例如确认编号规则用 approve，确认授权申请用 update；拒绝来源对照也用 approve。下表 `〔证〕` 只指已见相应资源的目录或绑定事实，入口边界以上表为准；`〔拟〕` 是沿用现标准动作的映射建议，须逐资源审定；`〔证/拟〕` 是已有一处证据、其他例子仍为建议。自动结果、kernel 原语或策略名写“无”，不凭名生成权限。事件与状态都不直接授予权限。

证据定位：[权限目录](../../../backend/src/main/java/com/forcitis/one/platform/identity/api/Permission.java)、[来源对照入口](../../../backend/src/main/java/com/forcitis/one/platform/integration/web/SourceReferenceController.java)、[编号规则入口](../../../backend/src/main/java/com/forcitis/one/platform/numbering/web/NumberingRuleController.java)、[授权确认入口](../../../backend/src/main/java/com/forcitis/one/platform/identity/web/AuthorizationRequestController.java)、[同步放行入口](../../../backend/src/main/java/com/forcitis/one/organization/workforce/web/SyncRunController.java)、[法人归属修正用例](../../../backend/src/main/java/com/forcitis/one/organization/workforce/application/CorrectLegalEntityAttribution.java)、[角色入口](../../../backend/src/main/java/com/forcitis/one/platform/identity/web/RoleController.java)。

### 2.2 动词表（含两例、近义并入条件）

原形及过去分词采用 PascalCase 的代码片段展示；snake 从原形机械转换，不另手写含义。示例是语法与语义边界示例，不代表相应命令已经登记、实现或授权。相同英文词形可对应不同受控概念，但必须由对象、BC 与定义消歧；表内 V 编号仅为本稿审阅索引，不提议为生产概念 id。多词动词的语序例外见 §4.2，不把形容词 Effective/Overdue 伪列为过去分词。

| 索引 | 中文/语义 | 英文原形 | 过去分词 | snake | 标准动作映射 | 并入谁/保留理由 | 例子 1；例子 2 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| V001 | 登记 | `Register` | `Registered` | `register` | create/update〔拟/证〕 | 记录、记→登记：仅指录入事实；Registration 是名词形；不把被记录的外部动作改为本系统执行 | 登记员工；登记检查计划 |
| V002 | 提交 | `Submit` | `Submitted` | `submit` | submit〔证〕 | 递交、填报→提交：仅提交已有材料；提出、申请、发起须区分首次建立申请；ForApproval 作目的补语 | 提交工时单；提交合同评审 |
| V003 | 编制 | `Compile` | `Compiled` | `compile` | create〔拟〕 | 起草→编制：仅制备草稿；不并入已经提交；预算 Preparation 建议本义并入 | 编制报价版本；编制交付计划版本 |
| V004 | 标记 | `Mark` | `Marked` | `mark` | update〔拟〕 | 只改变标识或记录判定结果；不以标记代替业务事实本身 | 标记付款条件项可结算；标记商票已逾期 |
| V005 | 驳回 | `Reject` | `Rejected` | `reject` | approve〔证〕 | 保留；不并退回。任务目录只有 approve；本次未核到任务驳回入口绑定；来源对照拒绝入口使用 approve | 驳回工时单；驳回合同评审 |
| V006 | 拒绝 | `Refuse` | `Refused` | `refuse` | approve〔证/拟〕 | 保留；来源对照现有 Rejected 与 Refused 分别指否定已登记对照、拒绝非法登记尝试，不合并概念 | 拒绝来源对照；拒绝非法来源对照登记 |
| V007 | 退回 | `Return` | `Returned` | `return` | approve〔拟〕 | 保留；要求修改后重提，与否定结论、取消认领分开 | 退回工时单；退回发包人资料 |
| V008 | 生效 | `TakeEffect` | `TakenEffect` | `take_effect` | execute〔拟〕 | 不与发布、批准、启用合并；TakenEffect 为原形的分词词组，事实自然语序特例见 §4 | 生效协议变更；生效费率版本 |
| V009 | 确认（核对无误） | `Confirm` | `Confirmed` | `confirm` | approve/update〔证〕 | 签认、GiveConfirmation/Given→确认仅在核对义；一次确认保留确认方补语；不并收到、会计确认、业务授权批准 | 确认最终价款；确认来源对照 |
| V010 | 确认收到 | `Acknowledge` | `Acknowledged` | `acknowledge` | update〔拟〕 | 与 Confirm 分立：仅知悉或收到，不蕴含同意或会计处理 | 确认收到发票；确认收到通知 |
| V011 | 确认入账（用户已定，AN-06；bc operations.facts） | `Recognise` | `Recognised` | `recognise` | execute〔拟〕 | 源 Recognize/Recognized 为美式证据；AN-06 用户已选单独立「确认入账」，不再转财务；不把中文入账等同 Book/Post | 确认入账收入；确认入账成本 |
| V012 | 发布 | `Publish` | `Published` | `publish` | execute〔拟〕 | 保留；发布≠批准≠生效；三者若同事务也分别命名事实 | 发布项目复盘；发布服务产品版本 |
| V013 | 停用 | `Deactivate` | `Deactivated` | `deactivate` | update〔证/拟〕 | Disable→Deactivate：仅业务可用性；不合并暂停；Disabled 为现存写法 | 停用法人主体；停用往来单位 |
| V014 | 启用 | `Activate` | `Activated` | `activate` | update〔证/拟〕 | Enable→Activate：仅业务可用性；生效基线的 Activated 应改生效谓词而非无条件合并 | 启用法人主体；启用用户账号 |
| V015 | 关闭 | `Close` | `Closed` | `close` | execute〔拟〕 | 结项→关闭：仅项目退出生命周期；关账另保留；不并完成、终止 | 关闭商机；关闭资源需求 |
| V016 | 批准 | `Approve` | `Approved` | `approve` | approve〔证〕 | 通过、核准→批准：仅作出同意决定；ApproveTimesheet 的中文审核按批准义；Review 不并 | 批准工时单；批准风险交付授权 |
| V017 | 认定（判定结论） | `Determine` | `Determined` | `determine` | execute〔拟〕 | 仅基于条件作出事实结论；视为同意成立、工时有效性属此候选，不并 Confirm | 认定视为同意成立；认定工时有效性 |
| V018 | 评估 | `Assess` | `Assessed` | `assess` | execute〔拟〕 | 保留；变更影响、返工归因可评估，不直接产生认可或债权结清 | 评估变更影响；评估返工影响 |
| V019 | 结算 | `Settle` | `Settled` | `settle` | execute〔拟〕 | 认定索赔/达成 ClaimSettled 依现定义候选→结算索赔；跨 BC 具体结算含义仍分立 | 结算协议；结算索赔 |
| V020 | 撤销（审批）；取消（活动） | `Cancel` | `Cancelled` | `cancel` | update〔拟〕 | 审批义保留「撤销」及概念 id；用户原话「已撤销可开新一轮」（intent `2026-10-14-126-1a流程-审批-重新发起.md`）；活动不再进行仍叫取消，按对象区分；不并 Revoke 的撤掉已授效力义 | 撤销审批；取消检查 |
| V021 | 撤回 | `Withdraw` | `Withdrawn` | `withdraw` | submit/approve〔拟〕 | 在途申请由发起人拉回；不是撤销已生效效力；权限目录有工时 submit 与任务 approve，未据此认定撤回入口已绑定 | 撤回工时单；撤回风险交付授权申请 |
| V022 | 作废 | `Void` | `Voided` | `void` | update〔拟〕 | 保留；单据不再有效；不等于删除或冲销已记事实 | 作废证据；作废开票申请 |
| V023 | 撤销 | `Revoke` | `Revoked` | `revoke` | update〔拟〕 | 保留；取消已授予效力；不由旧中文撤销机械推导 Revoke | 撤销返工认定；撤销红字信息确认 |
| V024 | 释放（额度占用） | `Release` | `Released` | `release` | execute〔拟〕 | 限额、预算占用释放；不并放行同步、解除暂停或取消认领 | 释放框架限额；释放收入额度占用 |
| V025 | 放行（同步） | `Release` | `Released` | `release` | execute/approve〔证〕 | 不同受控概念，共用英文代码词允许按对象消歧；sync-run 用 execute、separation-hold 用 approve | 放行同步记录；放行待放行离职 |
| V026 | 占用 | `Reserve` | `Reserved` | `reserve` | execute〔拟〕 | 保留；限额预占，不等于实际消耗、核销 | 占用收入额度；占用框架限额 |
| V027 | 锁定 | `Lock` | `Locked` | `lock` | update〔证/拟〕 | 保留；Freeze 指新增占用被冻结，不能一律并入 | 锁定项目预算；锁定工作包 |
| V028 | 完成 | `Complete` | `Completed` | `complete` | execute〔拟〕 | 结束活动→完成：仅正常完成；不并生命周期关闭或提前终止 | 完成阶段；完成客户活动 |
| V029 | 终止 | `Terminate` | `Terminated` | `terminate` | execute〔拟〕 | 叫停 Halt→终止：仅永久结束风险交付；若可恢复则用暂停，待业务核实 | 终止协议；终止项目 |
| V030 | 变更 | `Change` | `Changed` | `change` | update〔证/拟〕 | 更新、维护→变更：仅可编辑内容；Maintain 的整份维护用例保留可用写法；不把追加合并 | 变更银行账户；变更客户活动 |
| V031 | 追加 | `Append` | `Appended` | `append` | create〔拟〕 | 保留；只追加的事实/行，与更新既有记录严格分开 | 追加收入确认事实；追加冲销行 |
| V032 | 审核（审阅） | `Review` | `Reviewed` | `review` | approve〔拟〕 | 复核、评审、内审→审核：仅检查并形成审阅结果；批准结论另用 Approve | 审核检查记录；审核项目复盘 |
| V033 | 关联 | `Link` | `Linked` | `link` | update〔拟〕 | 挂接→关联：只建关联；登记协议项目关联的英语 Linked 与中文登记需人工核对 | 关联检查报告；关联证据 |
| V034 | 解除关联 | `Unlink` | `Unlinked` | `unlink` | update〔拟〕 | 保留；解除证据关联，不是释放额度或撤销原证据 | 解除证据关联；解除检查报告关联 |
| V035 | 封存 | `Seal` | `Sealed` | `seal` | update〔拟〕 | 保留；不可变证据留存，与普通归档分开 | 封存证据；封存交付物 |
| V036 | 归档 | `Archive` | `Archived` | `archive` | update〔拟〕 | 保留；与封存不同；归档不代表对象终止或数据删除 | 归档协议；归档项目 |
| V037 | 冲销 | `Reverse` | `Reversed` | `reverse` | submit〔证/拟〕 | kernel reverse 定义授权范围同提交；发票红冲不是本动作的无条件别名 | 冲销收入确认；冲销经营事实 |
| V038 | 开具 | `Issue` | `Issued` | `issue` | create〔拟〕 | 开票→开具发票；签发交付物为不同对象下同英语词；发号 IssueNumber 中 Number 是对象 | 开具销项发票；签发交付物 |
| V039 | 核销（坏账） | `WriteOff` | `WrittenOff` | `write_off` | execute〔拟〕 | 只认定应收不可收回并核销；不把正常收款核销 CashApplication 并入 | 核销坏账；核销应收坏账 |
| V040 | 核销（收款分配） | `Apply` | `Applied` | `apply` | execute〔拟〕 | CashApplication→Apply；清偿应收，非坏账；词组渲染特例见 §4 | 核销收款分配；核销到账款项 |
| V041 | 认领 | `Claim` | `Claimed` | `claim` | approve〔证/拟〕 | TaskClaim/ReceiptClaiming→Claim：对象区分；不得把合同索赔 Claim 当动词认领 | 认领任务；认领线索 |
| V042 | 取消认领 | `Unclaim` | `Unclaimed` | `unclaim` | approve〔拟〕 | TaskReleased、ReleaseOfTask→Unclaim：仅退回候选组；已有第三轮决定记载 | 取消认领任务；取消认领审批任务 |
| V043 | 恢复 | `Restore` | `Restored` | `restore` | update〔拟〕 | 保留；复工等是否同义按 BC；不以恢复代替冲销已经形成的事实 | 恢复项目；恢复线索 |
| V044 | 暂停 | `Suspend` | `Suspended` | `suspend` | update〔拟〕 | 挂起、Hold/Held→暂停：仅可解除的阻断；不并停用；同步按暂停类别留补语 | 暂停项目；暂停往来单位角色 |
| V045 | 创建 | `Create` | `Created` | `create` | create/update〔拟/证〕 | 建立、新建、设立、Establish→创建：仅新建对象；RoleController 创建角色实际用 update，不把 Create 自动映射 create | 创建应收；创建项目 |
| V046 | 开始 | `Start` | `Started` | `start` | execute〔拟〕 | 开展→开始：仅进入活动；开始执行保留目的/活动补语 | 开始现场检查；开始阶段 |
| V047 | 提出 | `Propose` | `Proposed` | `propose` | create〔拟〕 | 保留；首次形成变更或需求提案≠送审批；候选稿并入 Submit 过宽 | 提出设计变更；提出资源需求 |
| V048 | 申请 | `Request` | `Requested` | `request` | create〔拟〕 | 保留为命令；Submitted/Requested 事实区别见 §4；不把申请名词或 kind 当状态 | 申请往来单位角色；申请风险交付授权 |
| V049 | 发起 | `Initiate` | `Initiated` | `initiate` | create〔拟〕 | 保留；首次建立核查或流程；已经有材料再提交用 Submit | 发起资质核查；发起红字信息确认 |
| V050 | 应用 | `Apply` | `Applied` | `apply` | execute〔证/拟〕 | 执行变更集或基线更新；与收款核销 Apply 分立 | 应用组织变更；应用协议变更 |
| V051 | 计划（安排未来活动） | `Plan` | `Planned` | `plan` | create〔拟〕 | 保留动词；计划名词留对象，不是裸状态 | 计划市场活动；计划客户活动 |
| V052 | 安排 | `Schedule` | `Scheduled` | `schedule` | update〔拟〕 | 仅排定时间；与指派人员、编制计划区分 | 安排检查；安排现场检查 |
| V053 | 指派 | `Assign` | `Assigned` | `assign` | approve〔证/拟〕 | 分配任务→指派：仅任务处理人；资源、资金分配另列 Allocate | 指派检查人员；指派任务 |
| V054 | 建议 | `Recommend` | `Recommended` | `recommend` | create〔拟〕 | 保留；仅提出建议，不蕴含批准或实施 | 建议外部检测；建议整改方案 |
| V055 | 采信 | `Accept` | `Accepted` | `accept` | approve〔拟〕 | 只采纳证据；英语后置词组模板，见 §4；不并接受风险或验收认可 | 采信外部检测报告；采信核查证据 |
| V056 | 不采信 | `Reject` | `Rejected` | `reject` | approve〔拟〕 | 明确否定采信；未采信可能尚未判断，不能把二者当同一状态 | 不采信外部检测报告；不采信核查证据 |
| V057 | 接受 | `Accept` | `Accepted` | `accept` | approve〔拟〕 | 客户认可、验收通过→接受：仅肯定认可；接受风险与接受成果须对象和定义消歧 | 接受风险与问题；接受交付成果 |
| V058 | 让步接受 | `AcceptUnderConcession` | `AcceptedUnderConcession` | `accept_under_concession` | approve〔拟〕 | 与一般接受分立；补语 under concession，不凭该名推定免除义务 | 让步接受不符合项；让步接受检查结果 |
| V059 | 复查 | `Reinspect` | `Reinspected` | `reinspect` | execute〔拟〕 | 保留；再次实施检查，与文档复核不同 | 复查检查；复查整改结果 |
| V060 | 闭合 | `Resolve` | `Resolved` | `resolve` | execute〔拟〕 | 保留；证据证明问题解决≠关闭后停止跟踪；与关闭不符合项的关系待业务定 | 闭合不符合项；闭合整改项 |
| V061 | 停止跟踪 | `StopTracking` | `StoppedTracking` | `stop_tracking` | update〔拟〕 | 多词单一动作，保留；不假定问题已解决 | 停止跟踪不符合项；停止跟踪风险项 |
| V062 | 采集 | `Collect` | `Collected` | `collect` | create〔拟〕 | 保留；证据采集，不把催收 Collection 并入 | 采集证据；采集检查结果 |
| V063 | 上传 | `Upload` | `Uploaded` | `upload` | create〔拟〕 | 保留；技术传输动作，不能当作证据已审核 | 上传证据摘要；上传检查附件 |
| V064 | 回签 | `Countersign` | `Countersigned` | `countersign` | approve〔拟〕 | 合同双方回签；与审批会签共用词形但定义分立、BC 分立 | 回签协议；回签合同 |
| V065 | 会签 | `Countersign` | `Countersigned` | `countersign` | approve〔拟〕 | 审批多人各自作出结论；不合并合同回签概念 | 会签审批任务；会签合同评审 |
| V066 | 主张 | `Assert` | `Asserted` | `assert` | submit〔拟〕 | 提出索赔→主张索赔：已通知对方才用；仅内部提案仍为 Propose | 主张索赔；主张设计变更 |
| V067 | 转正（风险交付） | `Regularize` | `Regularized` | `regularize` | execute〔拟〕 | 源合同授权复核关闭，不是员工转正；保留 BC 限定 | 转正风险交付授权；转正风险交付记录 |
| V068 | 回冲 | `Restore` | `Restored` | `restore` | execute〔拟〕 | 仅恢复可用额度；不是凭证冲销，财务需核实是否另用名 | 回冲开票额度；回冲已占用额度 |
| V069 | 改写 | `Rewrite` | `Rewritten` | `rewrite` | update〔拟〕 | 存量技术动词证据；建议按实际业务结果改名，不推广为领域动作 | 改写 WIP；改写开票额度 |
| V070 | 红冲（发票） | `Issue` | `Issued` | `issue` | execute〔拟〕 | 建议动词开具、对象红字发票；正式中文与对象代码词待财务，不宣称法规已批准 | 红冲销项发票；开具红字发票 |
| V071 | 开放 | `Open` | `Opened` | `open` | update〔拟〕 | 仅开放证据类型能力；与开立账号 Open 同形不同义，限定对象 | 开放证据类型；开放核查类型 |
| V072 | 检测 | `Detect` | `Detected` | `detect` | execute〔拟〕 | 逾期识别；不把检测自动判为作出审批决定 | 检测应收逾期；检测时限超期 |
| V073 | 冲抵 | `Offset` | `Offset` | `offset` | execute〔拟〕 | 保留；员工借款抵账，与跨法人抵销 Eliminate 分开 | 冲抵员工借款；冲抵往来款项 |
| V074 | 冲减 | `Reduce` | `Reduced` | `reduce` | execute〔拟〕 | 保留；收入或应收减少，不推定坏账核销；RevenueReduction 建议生成事实名 | 冲减应收；冲减收入 |
| V075 | 转入 | `Transfer` | `Transferred` | `transfer` | execute〔拟〕 | 转预收或待退须分支；to 是补语介词；不与任务转交混用 | 转入预收；转入待退款项 |
| V076 | 分配 | `Allocate` | `Allocated` | `allocate` | execute〔拟〕 | 资源/款项分配；不把审批任务 Assigned 一律改 Allocate | 分配收款；分配项目资源 |
| V077 | 处置 | `Dispose` | `Disposed` | `dispose` | execute〔拟〕 | 宽泛动作暂保留迁移候选，落地须明确所作决定，不作任意执行权限依据 | 处置待认领流水；处置争议款项 |
| V078 | 扣减 | `Deduct` | `Deducted` | `deduct` | execute〔拟〕 | 保留；保证金余额减少，与没收法律效果不强并 | 扣减保证金；扣减借款余额 |
| V079 | 退还 | `Refund` | `Refunded` | `refund` | execute〔拟〕 | 保留；资金返还；不与退回材料 Return 混用 | 退还保证金；退还预收款 |
| V080 | 没收 | `Forfeit` | `Forfeited` | `forfeit` | execute〔拟〕 | 保留源词；适用条件需业务证据，本稿不认定有权没收 | 没收保证金；没收履约保证金 |
| V081 | 核实 | `Verify` | `Verified` | `verify` | execute〔拟〕 | 只读核实与档案核实共用词形；不并 Confirm 的双人确认 | 核实往来单位档案；只读核实外部连接 |
| V082 | 合并 | `Merge` | `Merged` | `merge` | update〔拟〕 | 保留；不得由合并词名推定自动重绑合同主体 | 合并往来单位；合并重复档案 |
| V083 | 延续 | `Renew` | `Renewed` | `renew` | update〔拟〕 | 保留；资质期限延续，与恢复已暂停状态不同 | 延续资质；延续资质证书 |
| V084 | 放弃 | `Abandon` | `Abandoned` | `abandon` | update〔拟〕 | 保留；主动放弃机会或投标，不等于丢单/未中标结果 | 放弃商机；放弃投标 |
| V085 | 赢得 | `Win` | `Won` | `win` | execute〔拟〕 | 中标、赢单按对象渲染；OpportunityAccepted 不由字面当作 Accept，待业务核对 | 赢得投标；赢得商机 |
| V086 | 失去 | `Lose` | `Lost` | `lose` | execute〔拟〕 | 未中标、丢单/输单按对象渲染，否定结果不写已失去投标；例外见状态词表 | 商机输单；投标未中标 |
| V087 | 通知 | `Notify` | `Notified` | `notify` | execute〔拟〕 | 只发送通知，不是对通知内容作出认定；索赔通知 Asserted 需核对行为 | 通知延期；通知整改 |
| V088 | 发出 | `Send` | `Sent` | `send` | execute〔拟〕 | 保留；设计变更发出与通知知悉分立，送达事实另用 Deliver | 发出设计变更；发出满意度调查 |
| V089 | 送达 | `Deliver` | `Delivered` | `deliver` | execute〔拟〕 | 保留；发出不等于送达；只登记外部送达时仍用 Register 命令 | 送达交付物；送达检查报告 |
| V090 | 声明 | `Declare` | `Declared` | `declare` | create〔拟〕 | 保留；声明无异议不等于发包人验收 | 声明无异议；声明利益冲突 |
| V091 | 提供 | `Provide` | `Provided` | `provide` | create〔拟〕 | 保留；登记资料已提供是记录事实，不代发包人执行提供动作 | 提供资料；提供检查证据 |
| V092 | 评分 | `Score` | `Scored` | `score` | update〔拟〕 | 保留；不把评分当作已完成整份调查 | 评分满意度调查；评分检查结果 |
| V093 | 跟进 | `FollowUp` | `FollowedUp` | `follow_up` | update〔拟〕 | 保留；登记跟进事实与执行跟进分开 | 跟进满意度调查；跟进风险问题 |
| V094 | 缴纳 | `Pay` | `Paid` | `pay` | execute〔拟〕 | 缴/缴纳同义限付款义；登记外部缴纳仍为 Register | 缴纳保证金；缴纳押金 |
| V095 | 开立 | `Open` | `Opened` | `open` | create/update〔拟/证〕 | 保留账号、保函开立；账号业务当前角色权限用 update；不与开放证据类型合概念 | 开立用户账号；开立保函 |
| V096 | 归还 | `Return` | `Returned` | `return` | update〔拟〕 | 归还借阅协议与退回单据共用词形，分立概念 | 归还协议；归还借阅文件 |
| V097 | 借出 | `Lend` | `Lent` | `lend` | update〔拟〕 | 保留；登记借出不等于系统执行实物交接 | 借出协议；借出归档文件 |
| V098 | 到期 | `Expire` | `Expired` | `expire` | 无（自动事实）/execute〔拟〕 | 到期事实保留；逾期不是到期的同义词；Due 词汇化例外 | 资质已到期；保函已到期 |
| V099 | 逾期 | `BecomeOverdue` | `BecomeOverdue` | `become_overdue` | 无（自动事实）/execute〔拟〕 | Overdue 是形容词；源 BecameOverdue 是过去式，不当作过去分词；事实特例见 §4 | 商票已逾期；任务已超时 |
| V100 | 满足 | `Satisfy` | `Satisfied` | `satisfy` | execute〔拟〕 | 保留；资源需求满足，不必等于资源需求关闭 | 满足资源需求；满足付款条件 |
| V101 | 达成 | `Fulfil` | `Fulfilled` | `fulfil` | execute〔拟〕 | 只用于条件实现；源 ClaimSettled/达成按索赔结算，不一律并入 Fulfil | 达成付款条件；达成协议条件 |
| V102 | 报告 | `Report` | `Reported` | `report` | create〔拟〕 | 保留；记录已报告时 Register 为主谓词，Report 为被记录事实 | 报告重大险情；报告检查问题 |
| V103 | 回收 | `Receive` | `Received` | `receive` | create〔拟〕 | 调查回收只指收回响应；回款/收到资金须对象定义，不把收到改为确认 | 回收满意度调查；收到收款 |
| V104 | 豁免 | `Waive` | `Waived` | `waive` | approve〔拟〕 | 保留；缺口风险接受，不删除缺口事实 | 豁免合同缺口；豁免完备度缺口 |
| V105 | 导入 | `Import` | `Imported` | `import` | import〔拟〕 | 历史→迁入为术语候选；正式业务导入和离线迁入边界另核，不沿用老系统名 | 导入合同基线；导入考勤记录 |
| V106 | 修正 | `Correct` | `Corrected` | `correct` | submit/create〔证/拟〕 | 更正→修正；kernel correct 定义同提交权限，法人归属修正当前 create；不并普通变更 | 修正合同主体；修正法人归属结束日 |
| V107 | 分摊 | `Allocate` | `Allocated` | `allocate` | execute〔拟〕 | 保留分摊概念，与一般资源分配同形；按权重拆分 Prorate 是 kernel 原语 | 分摊间接成本；分摊管理费用 |
| V108 | 勾稽 | `Reconcile` | `Reconciled` | `reconcile` | execute〔拟〕 | Reconciliation 名词→动作；不将差异自动核销 | 勾稽经营数据；勾稽期间结果 |
| V109 | 催收 | `Collect` | `Collected` | `collect` | execute〔拟〕 | Collection 名词→业务催收；与采集证据分立；代码词组候选 | 催收应收款；催收逾期账款 |
| V110 | 结转 | `CarryForward` | `CarriedForward` | `carry_forward` | execute〔拟〕 | PeriodEndClosing 在当前定义是成本入账与结转，不直接并关账 | 结转期末余额；结转成本 |
| V111 | 折算 | `Translate` | `Translated` | `translate` | execute〔拟〕 | CurrencyTranslation→动词，仅币种折算；不作语言翻译 | 折算外币余额；折算期间金额 |
| V112 | 抵销 | `Eliminate` | `Eliminated` | `eliminate` | execute〔拟〕 | 内部往来 Elimination；不与员工借款冲抵同义合并 | 抵销内部往来；抵销集团内部余额 |
| V113 | 关账 | `Close` | `Closed` | `close` | execute〔拟〕 | PeriodClose→Close+期间对象；须关账前置条件，不等于结转 | 关账会计期间；关账月度期间 |
| V114 | 重开 | `Reopen` | `Reopened` | `reopen` | execute〔拟〕 | 保留；已关账期间重开权限与政策须财务确认 | 重开会计期间；重开调整期间 |
| V115 | 审批 | `Process` | `Processed` | `process` | approve〔拟〕 | Approval 为过程名；不把审批整体等同批准结果 | 办理审批；处理审批轮次 |
| V116 | 办理 | `Handle` | `Handled` | `handle` | approve〔拟〕 | Act/Resulted→Handle 依旧词条 note；宽泛只用于审批任务，不推广为业务动作 | 办理任务；办理审批任务 |
| V117 | 作出决定 | `Decide` | `Decided` | `decide` | approve〔证/拟〕 | 保留总称；明确通过/驳回时优先 Approve/Reject | 作出审批决定；作出任务决定 |
| V118 | 升级 | `Escalate` | `Escalated` | `escalate` | approve〔拟〕 | Escalation 名词→动作；只交上级处理，不自动通过 | 升级审批任务；升级逾期任务 |
| V119 | 改派 | `Reassign` | `Reassigned` | `reassign` | approve〔证/拟〕 | Reassignment→动词；管理员改派，与当前人主动转交区分 | 改派任务；改派审批任务 |
| V120 | 转交 | `Transfer` | `Transferred` | `transfer` | approve〔拟〕 | 保留；当前办理人转给他人；与资金转入分立 | 转交任务；转交审批任务 |
| V121 | 跳过 | `Skip` | `Skipped` | `skip` | 无（自动事实）/approve〔拟〕 | 保留；任务无审批人跳过与投递无标识跳过需对象与原因限定 | 跳过审批节点；跳过通知 |
| V122 | 授予 | `Grant` | `Granted` | `grant` | update〔证〕 | 授权 Authorization→授予权限依第三轮 note；身份确认另属 ACL，不合并概念 | 授予权限；授予角色 |
| V123 | 登录 | `LogIn` | `LoggedIn` | `log_in` | 无（登录入口） | Login→LogIn 词形；不是业务权限码，登录前无调用者授予 | 登录用户账号；登录应急账号 |
| V124 | 登出 | `LogOut` | `LoggedOut` | `log_out` | 无（本人会话） | Logout→LogOut；强制登出另列 | 登出当前会话；登出用户账号 |
| V125 | 强制登出 | `ForceLogOut` | `ForcedLogOut` | `force_log_out` | delete〔证〕 | ForcedLogout 形容词形→动词；内部删除会话，不推导其他对象删除权限 | 强制登出用户账号；强制登出应急账号 |
| V126 | 维持 | `Uphold` | `Upheld` | `uphold` | update〔证/拟〕 | 仅维持授权结论；不等于延续期限或创建新授权 | 维持授权；维持角色授予 |
| V127 | 探测 | `Probe` | `Probed` | `probe` | 无（技术检查）/execute〔拟〕 | 探针名词→探测；无领域命令入口则不建权限 | 探测归档目标；探测写入可用性 |
| V128 | 保留 | `Retain` | `Retained` | `retain` | 无（策略名） | Keep 旧词 note 已指向数据保留期限；期限改 kind，不伪造 Retain 命令 | 保留在线数据；保留归档数据 |
| V129 | 按权重拆分 | `Prorate` | `Prorated` | `prorate` | 无（kernel 原语） | 不并业务分摊 Allocate；守恒计算不自行赋功能权限 | 按权重拆分金额；按权重拆分尾差 |
| V130 | 试提交 | `TrialCommit` | `TrialCommitted` | `trial_commit` | execute〔拟〕 | 保留；正式写库再回滚以校验，不渲染为已正式提交 | 试提交迁入批次；试提交合同批次 |
| V131 | 取代 | `Supersede` | `Superseded` | `supersede` | update〔拟〕 | 保留；后继版本生效替代旧版本；不等同删除旧记录 | 取代来源对照；取代费率版本 |
| V132 | 归属 | `Attribute` | `Attributed` | `attribute` | create/update〔证/拟〕 | 法人归属已确立中的确立依英文 Attribute；与经营事实归属跨 BC 分立 | 归属经营事实；确立法人归属 |
| V133 | 重述 | `Restate` | `Restated` | `restate` | execute〔拟〕 | 保留；引用原事实的受控更正，不更新原事实 | 重述经营事实；重述归属结果 |
| V134 | 识别 | `Identify` | `Identified` | `identify` | execute〔拟〕 | 保留；识别缺口与偏差，事实不含已批准处置 | 识别合同缺口；识别进度偏差 |
| V135 | 归集 | `Accumulate` | `Accumulated` | `accumulate` | execute〔拟〕 | 保留；成本累计，不等于分摊或会计入账 | 归集项目成本；归集人工成本 |
| V136 | 预警 | `Raise` | `Raised` | `raise` | 无（自动事实）/execute〔拟〕 | EclAlert、RiskRaised→发出预警：名词事件须人工补对象；不推定减值已计提 | 预警合同资产减值风险；预警应收减值风险 |
| V137 | 记账 | `Book` | `Booked` | `book` | execute〔拟〕 | Booked 只说明源事件的入账事实；与 Recognise、Post 分立，中文最终财务定 | 记账法定收入；记账减值分录 |
| V138 | 执行 | `Execute` | `Executed` | `execute` | execute〔证/拟〕 | RuleExecution 名词→执行规则；不合并所有业务谓词 | 执行规则；执行组织同步 |
| V139 | 消耗 | `Consume` | `Consumed` | `consume` | execute〔拟〕 | 预算占用转为实际消耗；不并预占 Reserve | 消耗预算占用；消耗资源预算 |
| V140 | 调整 | `Adjust` | `Adjusted` | `adjust` | update〔拟〕 | 保留业务调整，普通内容修改可 Change；已过账金额不因调整名更新 | 调整预算；调整权限 |
| V141 | 结束 | `End` | `Ended` | `end` | update〔证/拟〕 | 法人归属到期结束保留；正常活动结束可 Complete；不一律并 Close | 结束法人归属；结束归属区间 |
| V142 | 更名 | `Rename` | `Renamed` | `rename` | update〔证〕 | 只修改名称，保留有意义的窄动作 | 更名法人主体；更名组织单元 |
| V143 | 失败 | `Fail` | `Failed` | `fail` | 无（自动事实） | 保留结果动词，不建立用户发起失败命令 | 同步记录已失败；试提交已失败 |
| V144 | 成功 | `Succeed` | `Succeeded` | `succeed` | 无（自动事实） | 保留结果动词，不等于全业务已验收 | 同步记录已成功；试提交已成功 |
| V145 | 解除暂停 | `Release` | `Released` | `release` | execute/approve〔证/拟〕 | HoldReleased 只解除一种暂停原因；不保证同步已经放行 | 解除同步暂停；解除离职暂停 |
| V146 | 替换 | `Replace` | `Replaced` | `replace` | update〔证〕 | 保留整体替换角色集合，不把它合并为追加角色 | 替换账号角色；替换角色授予集合 |
| V147 | 离职收回 | `Revoke` | `Revoked` | `revoke` | 无（自动事实）/update〔拟〕 | Separated 中文实际是因离职收回角色；不得伪造本系统办理员工离职命令 | 因离职收回账号角色；因离职关闭授权 |
| V148 | 重置 | `Reset` | `Reset` | `reset` | update〔拟〕 | 保留；编号策略 ResetPolicy 为类别，不是每次发生重置事实 | 重置应急管理员凭据；重置流水号 |
| V149 | 修订 | `Revise` | `Revised` | `revise` | update〔证/拟〕 | 保留版本化修订，不把只追加版本当普通更新 | 修订角色；修订规则版本 |
| V150 | 重新设置 | `Reconfigure` | `Reconfigured` | `reconfigure` | update〔证〕 | 保留编号规则重配，未来可按业务含义改名，不硬并 Rename | 重新设置编号规则；重新设置发号范围 |
| V151 | 废除 | `Abolish` | `Abolished` | `abolish` | update〔拟〕 | 源 RoleDeleted 中文废除为业务失效；不能机械映射 delete 或物理删除 | 废除角色；废除角色定义 |
| V152 | 冻结 | `Freeze` | `Frozen` | `freeze` | update〔拟〕 | 账户禁止新增占用；与项目预算锁定分立 | 冻结预算账户；冻结预算占用 |
| V153 | 过账 | `Post` | `Posted` | `post` | execute〔拟〕 | 保留事实过账；提交过账候选不等于过账完成 | 过账经营事实；过账工时事实 |
| V154 | 分解 | `BreakDown` | `BrokenDown` | `break_down` | execute〔拟〕 | 预算 Breakdown→分解，子账户生成；不等同按权重拆分金额 | 分解预算；分解计划目标 |
| V155 | 处理 | `Process` | `Processed` | `process` | update〔拟〕 | 待处理的名词形 Processing；未明确动作只作为待办目标，不推广通用处理命令 | 处理待调整权限条目；处理待认领流水 |
| V156 | 签署 | `Sign` | `Signed` | `sign` | approve〔拟〕 | 保留；签署≠批准，也不必等于回签；ContractSigned 源事实已签署 | 签署合同；签署协议 |
| V157 | 重新提交 | `Resubmit` | `Resubmitted` | `resubmit` | submit〔拟〕 | 保留；退回后新一轮提交，前缀 re 必须来自受控词而非临时拼接 | 重新提交工时单；重新提交审批单据 |
| V158 | 计算 | `Calculate` | `Calculated` | `calculate` | execute〔拟〕 | 增补（NAM-1017-GLO-14）：用户在 NM6-08 认可名称「计算月度薪酬」，视为认可动词「计算」用于由规则算出结果的命令；不与「结算」「分摊」「归集」「核定」合并；其他对象使用须另核 | 计算月度薪酬 |

### 2.3 多义与近义组的裁定建议

| 原候选组/多义 | 本稿建议 | 依据、适用界限 |
| --- | --- | --- |
| 确认 | Confirm 核对无误；Acknowledge 确认收到；Recognise 单立「确认入账」（已定，AN-06；bc `operations.facts`）；明确同意批准用 Approve | 用户已定（intent `2026-10-17-091749`，本次派工卡明确重申）：确认入账单立，不再转财务；“收到”不等于“同意”；词条 `authorization_confirmed` 仍按授权语义核对，不由当前 Confirmed 推断只核对 |
| 认定 | Determine 作结论；Assess 评估影响；Settle 索赔达成/结算 | R071“认定索赔”对应 ClaimSettled，不能与 R082“认定视为同意成立”、R252“认定工时有效性”共用一个英文动作。`ClaimSettled` 是否确指达成结算，交用户确认 |
| 登记/记录/记 | 仅录入既成事实时合为 Register；保留嵌套事实动词 | R027“记录重大险情已报告”不表示本系统向外报告；R214“登记客户认可”不表示本系统替客户认可 |
| 提交/提出/申请/发起 | 不整体归并：已有材料送审 Submit；形成提案 Propose；请求授权 Request；启动核查 Initiate | 目录同时有申请授权、提交授权审批、批准授权，合并会抹去不同前置状态；Requested 事件仍受 §4.3 的事实要求约束 |
| 取消/撤销/撤回/作废 | 按语义分开；审批的 `cancel` 保留「撤销」，活动用「取消」，撤回与作废另立；撤掉已授效力也用「撤销」但须写明对象 | 用户原话「已撤销可开新一轮」（intent `2026-10-14-126-1a流程-审批-重新发起.md`）；`cancel` 结束整轮、单据回草稿；`withdraw` 拉回修改、本轮以退回结束，重提开新一轮（intent `2026-10-14-128-1a流程-审批-办理动作.md`）；Revoke 撤掉效力；Void 单据失效 |
| 驳回/退回/拒绝 | 分立结论、返修和拒绝登记；源 Rejected/Refused 要按事实对象核对 | `source_reference_rejected` 否定已登记对照；`source_reference_refused` 拒绝非法登记且不存储对照，不合并事件概念 |
| 批准/通过/审核/评审/复核 | 通过且给出同意结论→Approve；单纯审阅→Review；审批整体是过程 | `approve_timesheet` 的 en 是 ApproveTimesheet；审阅并不必然同意 |
| 关闭/结项/终止/完成/闭合 | 分立；项目结项可渲染 CloseProject；闭合是否并入解决须用户定 | 关闭不符合项、闭合不符合项、停止跟踪同时存在；不能假定同一种完成条件 |
| 停用/暂停/挂起/冻结/锁定 | Deactivate、Suspend、Freeze、Lock 分立；挂起仅在可恢复义并入 Suspend | 当前 sync_run_hold_released 只解除一种暂停原因，仍可能保持暂停；不能由解除暂停渲染“已恢复/已成功” |
| 更新/变更/维护/追加/修订 | 普通字段修改可 Change；Append、Revise 分立 | 追加事实与原地修改的持久化含义不同；角色修订有版本语义 |
| 释放/放行/解除暂停/取消认领 | 按额度、同步、暂停原因、任务候选组四种概念分开 | `task_released` 定义是取消认领；`sync_run_released` 与 `sync_run_hold_released` 定义不同；旧表的 Release 不能批量同义合并 |
| 核销/冲销/冲抵/抵销/冲减/红冲 | Apply、WriteOff、Reverse、Offset、Eliminate、Reduce、发票红字动作分立 | 正常收款核销不是坏账核销；kernel reverse 只说明冲销入口；ARCH_NAME_AUDIT 已提示发票红冲须按 BC 分立。所有会计中文与英文候选交财务 |

## 3. 状态词表

### 3.1 三种组合规则

| aspect | 中文取值模板 | 英文代码模板 | 使用条件与反例 |
| --- | --- | --- | --- |
| perfective（已） | `已 + V`；完整显示名可 `O + 已 + V + C` | 动词分词片段；完整名 `O + Participle + C` | 只有状态机定义确实保留该结果时才建状态，不能把每个过去事件都变成持久状态；完成事件可以发生但对象仍有别的主状态 |
| pending（待） | `待 + V + C` | `Pending + ActionNoun + C` | 已达到可等待动作的前置条件；`待处理` 不能机械变成 Open，`待调整权限类型` 是类别名，不能因待字当状态取值 |
| progressive（中） | `V + 中` | `In + ActionNoun + C` | 动作确实正在进行；`审批中` 不等于等待审批，也不等于在途的全部范围 |

ActionNoun 和进行形式须与动词一同受控，不靠英语临时加 ing。首批具体表：

| 动词 | 待：中文/英文 | 中：中文/英文 | 已：中文/英文 |
| --- | --- | --- | --- |
| Approve/审批过程 | 待审批 / PendingApproval | 审批中 / InApproval | 已通过 / Approved（同意结果） |
| Claim | 待认领 / PendingClaim | 认领中 / InClaiming（仅有可观察过程时） | 已认领 / Claimed |
| Confirm | 待确认 / PendingConfirmation | 确认中 / InConfirmation | 已确认 / Confirmed |
| Settle | 待结算 / PendingSettlement | 结算中 / InSettlement | 已结算 / Settled |
| Process | 待处理 / PendingProcessing | 处理中 / InProcessing | 已处理 / Processed |
| Review | 待审核 / PendingReview | 审核中 / InReview | 已审核 / Reviewed（不表示批准） |
| TakeEffect | 待生效 / PendingEffect | 不用“生效中”，除非业务定义过渡期 | 已生效 / Effective（词汇化实现） |
| Freeze | 待冻结 / PendingFreeze | 冻结中仅在业务可观察过渡时使用 | 已冻结 / Frozen |

上述是允许的渲染形态候选，不要求每个业务对象同时具备三类状态。ActionNoun 当前只对本表列出的组合给出候选；其他动词需要待/中形态时补表审定，缺失时失败，不能由模板臆造。

### 3.2 词汇化状态与非规则结果

| 名称 | 英文候选/现存词形 | 建议与边界 |
| --- | --- | --- |
| 在途 | Live | 现定义含“进行或退回待重提”，作为独立词汇化范围保留；不等于 InApproval |
| 草案/草稿 | Draft | 候选词汇化起始态；二者选一个首选中文交用户；本次 60 条 state 中无精确同名词条 |
| 拟派 | ProposedAssignment | 候选，只在有“已提名尚未正式指派”的状态机时登记；未检出同名 state |
| 可撤销、只读、可结算 | Revocable、ReadOnly、Settleable | 能力/限制条件，不强改为已撤销、已只读、已可结算；与生命周期状态正交时作属性，是否作为独立状态由业务定 |
| 已生效 | Effective | TakeEffect 的过去分词不是 Effective；将 Effective 作为词汇化状态形式，不混进动词分词列 |
| 已超时/逾期 | Overdue | 时间条件型；Overdue 不是过去分词，任务超时不自动通过或拒绝 |
| 无合同交付、争议应收、亏损合同 | UncontractedDelivery、DisputedReceivable、OnerousContract | 现存 state 是业务情境/性质，先核对是否应是条件维度或对象类别，不强加已/待/中 |
| 视为同意 | DeemedAcceptance | 当前提议定义把它当交付物状态；ARCH_NAME_AUDIT 指向 ADR-0049 的不同边界，本卡仅记录冲突，不以本稿宣告合同条件成立；认定记录与结果须用户重判 |
| 已中标、未中标、输单 | Won、Lost | 对象不同的结果词；“未中标”是落选结果时不能等同“还没中标”；与待评标分别定义 |
| 未采信 | NotAccepted / 未定 | 不能说明“尚待采信”还是“已决定不采信”；分别建议待采信、未获采信/已拒绝采信，须审题 |
| 视为批准 | DeemedApproved | 仅记录某业务条件下的结果候选，不臆造准许该结果的法规或合同条款 |

### 3.3 全部现存 state 词条逐项处置

“集合/维度”建议先撤出状态取值分类；集合可改为 value_object 并声明取值；目录项、标识、策略另按语义定 kind；只有 enum 类型名不需要词尾凑状态词。不是每个集合都表示生命周期，也不为凑三态而新增状态。以下均为建议，不修改 kind 或概念 id。

| 概念 id | BC | 现中文 / 英文 | 来源 | 处置建议 |
| --- | --- | --- | --- | --- |
| uncontracted_delivery | contract.agreement | 无合同交付 / UncontractedDelivery | 正式 | 词汇化/条件候选：条件型/业务情境；不强造已字 |
| acceptance_status | delivery.acceptance | 交付验收状态 / AcceptanceStatus | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| pending_intercompany_settlement | cost.costing | 待内部结算 / PendingIntercompanySettlement | 正式 | 可组合：pending；V019；建议 待内部结算；内部 |
| disputed_receivable | receivables.collection | 争议应收 / DisputedReceivable | 正式 | 词汇化/条件候选：条件型；争议不蕴含已处置 |
| onerous_contract | delivery.execution | 亏损合同 / OnerousContract | 正式 | 词汇化/条件候选：合同性质；由业务判定后挂条件，不是动作 |
| period_status | accounting.period | 期间状态 / PeriodStatus | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| document_type | platform.numbering | 单据类型 / DocumentType | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| reset_policy | platform.numbering | 流水号重置周期 / ResetPolicy | 正式 | 重置策略→建议 value_object；枚举策略/周期，非当前流水号的生命周期状态 |
| process_key | platform.approval | 流程标识 / ProcessKey | 正式 | 标识→建议 value_object；引用流程登记，不伪造生命周期枚举 |
| read_purpose | platform.integration | 读取用途 / ReadPurpose | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| source_employment_state | platform.integration | 来源在职状态 / SourceEmploymentState | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| source_record_state | platform.integration | 来源记录状态 / SourceRecordState | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| ui_pending_claim | platform.approval | 待认领 / PendingClaim | 正式 | 可组合：pending；V041；建议 待认领；— |
| ui_approval_running | platform.approval | 审批中 / ApprovalRunning | 正式 | 可组合：progressive；V115；建议 审批中；— |
| ui_approval_approved | platform.approval | 已通过 / ApprovalApproved | 正式 | 可组合：perfective；V016；建议 已通过；审批结果首选展示用已通过 |
| ui_approval_rejected | platform.approval | 已驳回 / ApprovalRejected | 正式 | 可组合：perfective；V005；建议 已驳回；— |
| ui_approval_returned | platform.approval | 已退回 / ApprovalReturned | 正式 | 可组合：perfective；V007/V096；建议 已退回；— |
| claimed | platform.approval | 已认领 / Claimed | 正式 | 可组合：perfective；V041；建议 已认领；— |
| live | platform.approval | 在途 / Live | 正式 | 词汇化/条件候选：审批进行或退回待重提，不能等同审批中 |
| notification_skipped | platform.approval | 通知已跳过 / NotificationSkipped | 正式 | 可组合：perfective；V121；建议 通知已跳过；对象=通知 |
| overdue | platform.approval | 已超时 / Overdue | 正式 | 词汇化/条件候选：形容词，绑定截止点；已不自动给出超时动词 |
| ui_locked_day | organization.attendance | 已锁定 / LockedDay | 正式 | 可组合：perfective；V027；建议 已锁定；对象=日历日期，去 LockedDay 中对象重复 |
| calendar_scope | organization.attendance | 日历适用范围 / CalendarScope | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| account_disabled | platform.identity | 账号停用 / AccountDisabled | 正式 | 可组合：perfective；V013；建议 账号已停用；对象=账号；当前词形 AccountDisabled |
| permission_adjustment_kind | platform.identity | 待调整权限类型 / PermissionAdjustmentKind | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| permission_adjustment_status | platform.identity | 待调整权限状态 / PermissionAdjustmentStatus | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| permission_adjustment_status__open | platform.identity | 待处理 / Open | 正式 | 可组合：pending；V155；建议 待处理；Open 是旧码，迁移不改 id |
| permission_adjustment_status__upheld | platform.identity | 已维持 / Upheld | 正式 | 可组合：perfective；V126；建议 已维持；— |
| permission_adjustment_status__adjusted | platform.identity | 已调整 / Adjusted | 正式 | 可组合：perfective；V140；建议 已调整；— |
| permission_adjustment_status__closed_by_separation | platform.identity | 因离职关闭 / ClosedBySeparation | 正式 | 可组合：perfective；V015；建议 已因离职关闭；reason=离职 |
| document_nature | kernel | 单据性质 / DocumentNature | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| reversal_kind | kernel | 冲销类别 / ReversalKind | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| counter_scope_kind | platform.numbering | 计数范围类别 / CounterScopeKind | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| ui_pending_confirmation_state | platform.numbering | 待确认 / PendingConfirmationState | 正式 | 可组合：pending；V009；建议 待确认；去除 State 技术尾缀仅候选 |
| ui_confirmed_state | platform.numbering | 已确认 / ConfirmedState | 正式 | 可组合：perfective；V009；建议 已确认；— |
| budget_freeze | operations.planning | 预算冻结 / BudgetFreeze | 正式 | 可组合：perfective；V152；建议 已冻结；账户管控状态；不并锁定预算 |
| target_type | operations.planning | 目标类型 / TargetType | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| settlement_method | receivables.collection | 回款方式 / SettlementMethod | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| contract_asset_risk_tier | receivables.wip | 合同资产风险分层 / ContractAssetRiskTier | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| org_unit_level | organization.workforce | 组织单元层级 / OrgUnitLevel | 正式 | 数值层级→建议 value_object；明确整数范围，不改成生命周期枚举 |
| customer_tier | masterdata.counterparty | 客户分级 / CustomerTier | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| business_line | organization.workforce | 业务线 / BusinessLine | 正式 | 分类目录项→撤出 state；由 N5 判参考实体还是值对象，不在本卡定对象主权 |
| delivery_purpose | platform.integration | 投递用途 / DeliveryPurpose | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| agreement_type | contract.agreement | 协议类型 / AgreementType | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| org_unit_type | organization.workforce | 组织单元类型 / OrgUnitType | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| audit_subject_kind | platform.audit | 审计对象类别 / AuditSubjectKind | 正式 | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| design_change_claim_status | delivery.acceptance | 设计变更主张状态 / DesignChangeClaimStatus | enterprise-aggregates.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| deemed_acceptance | delivery.acceptance | 视为同意 / DeemedAcceptance | enterprise-aggregates.yaml | 词汇化/条件候选：待改分类/定义：当前交付物状态与 ARCH_NAME_AUDIT 指向的 ADR-0049 冲突，按认定对象重判 |
| acceptance_evidence_type | delivery.acceptance | 客户认可证据类型 / AcceptanceEvidenceType | enterprise-aggregates.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| ui_released_state | organization.workforce | 已放行 / ReleasedState | fix-c.yaml | 可组合：perfective；V024/V025；建议 已放行；同步放行义 |
| provider_kind | platform.identity | 身份提供方类别 / ProviderKind | platform-identity.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| source_system | platform.integration | 来源系统 / SourceSystem | platform-integration.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| source_object_kind | platform.integration | 来源对象类别 / SourceObjectKind | platform-integration.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| rejected_state | platform.integration | 已拒绝 / RejectedState | platform-integration.yaml | 可组合：perfective；V006；建议 已拒绝；拒绝已登记对照，源代码 RejectedState 待与 Refused 事件对齐 |
| read_only_verification_result | platform.integration | 只读核实结果 / ReadOnlyVerificationResult | platform-integration.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| round3_pending__skipped | platform.approval | 已跳过 / Skipped | round3-pending-renames.yaml | 可组合：perfective；V121；建议 通知已跳过；旧词条已有 note 指向 notification_skipped，不新建概念 |
| round3_pending__account_disabled | platform.identity | 停用 / AccountDisabled | round3-pending-renames.yaml | 可组合：perfective；V013；建议 账号已停用；旧词条已有 note 指向 account_disabled |
| round3_pending__running | platform.approval | 进行中 / Running | round3-pending-renames.yaml | 可组合：progressive；V115；建议 审批中；旧词条已有 note 指向 ui_approval_running |
| bill_redemption_status | receivables.collection | 兑付状态 / BillRedemptionStatus | step3-objects.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |
| receivable_disposition_category | receivables.collection | 往来款项处置分类 / ReceivableDispositionCategory | step3-objects.yaml | 集合/分类维度→建议 value_object；保留概念，列明取值域，勿当单一状态取值渲染 |

本次分类：22 条可组合取值候选、6 条词汇化/条件候选、32 条集合、分类维度、标识或策略。分类是本稿判断，数量是逐行核对结果。`read_only_verification_result` 也作为结论集合，不以“核实通过、确认违规、判定不了”分别隐含一个动作权限。

### 3.4 “申请、增员、减员、公示、计划”核对结果

候选 DRAFT §2 称其为“抽样发现 5 个”。本次正式与提议的全部 60 条 state 中，五个中文精确名均未检出；因此不能将其写成当前已证实的五个错误词条。按派工要求保留五行审查与建议，现存确证问题见 §3.3。

| 被提及的词 | 当前证据 | 若后续找到相应词条，建议处置 |
| --- | --- | --- |
| 申请 | 未检出 zh=申请 的 state；命令 R073、R130 含申请；现有 `billing_request` 是 aggregate | Request 为动作；申请材料/请求对象保留名词；取值用待提交、待审批等，按真实前置状态定 |
| 增员 | 未检出同名 state | 若是变更类型，归变更类别枚举；若是动作，按录用/新增人员的实际业务定名；不能凭名称推断劳动关系成立 |
| 减员 | 未检出同名 state | 若是类别，归变更类别；离职、停用账号、撤销角色是不同动作，不整体归并 |
| 公示 | 未检出同名 state | Publish 为动作；确有过程用公示中、结果用已公示，须补公示对象、开始/结束条件 |
| 计划 | 未检出 zh=计划 的 state；R154、R183 的 Plan 是动词；现有 delivery_plan/operating_plan 是对象 | 编制计划是命令；计划是名词；已计划/计划中仅在真实状态机定义时采用，不能全局替换名词 |

## 4. 槽位、渲染与复合动作

### 4.1 槽位约束

| 槽位 | 类型与约束 | 例子 |
| --- | --- | --- |
| object | 登记的对象概念引用；命令/事件必填；状态由所属状态集合绑定对象，可不重复显示 | 工时单、来源对照；不能把整个未登记短语塞进对象槽绕过守护 |
| verb | 登记的受控动词概念引用；按 BC/定义选义，不从中文串或 id 拆词 | Confirm 与 Recognise 分立；Release 同形两义需不同概念引用 |
| aspect | 结构枚举 imperative / perfective / pending / progressive；命令 imperative，事件 perfective；状态后三类或显式词汇化例外 | 提交是 imperative；工时单已提交是 perfective |
| complement | 可空；非空只能是具名结构：purpose、outcome、reason、target、qualifier、recorded_fact；值引用已登记概念，语法模板逐类白名单 | purpose=审批；reason=离职；outcome=可结算；recorded_fact={object,verb,aspect} |

BC、scope、kind 仍属于所属概念，不压进上述四槽，也不由英文类名或 id 反解。状态集合归属、参与者、业务前置条件不靠 complement 暗含。目标结构源语言 zh-CN、只交付中文；英文界面名可与内部代码名不同（PREREQ N3、AN-05），本稿英文均为代码名候选。

### 4.2 中英文渲染

| 面/结构 | zh-CN | 英文代码（PascalCase） | 守护边界 |
| --- | --- | --- | --- |
| 命令，简单动词 | `V + O` | `Base + O` | 不自动以 Command/Request 为类型后缀；Request 若为业务对象词不因此删除 |
| 事件，及物动词 | `O + 已 + V` | `O + Participle` | 名称表示已发生事实，不能用事件要求别的 BC 做事 |
| 状态：结果 | `已 + V` | `Participle` | 类型/集合已经提供对象，不加 State 技术尾词；英文旧码迁移另走 N7 |
| 状态：待/中 | `待 + V` / `V + 中` | `Pending + ActionNoun` / `In + ActionNoun` | ActionNoun 必须登记；不对所有动词机械生成 |
| 目的 purpose | 命令 `V+O+目的`；事件 `O+已+V+目的` | `Base+O+For+Purpose` / `O+Participle+For+Purpose` | “提交审批”只表示已提交，不能生成 ApprovalCompleted |
| 结果 outcome | `V+O+结果` | `Base+O+As+Outcome` | MarkPaymentTermAsSettleable；结果必须受控，不能自由拼句 |
| 原因 reason | `V+O+原因`；事件 `O+已因+原因+V` | `Base+O+BecauseOf+Reason` / `O+Participle+BecauseOf+Reason` | 事件存量 ClosedBySeparation 保留迁移证据；规范片段待用户审定 |
| 定向 target | `V+O+至+目标` | `Base+O+To+Target` | Transfer 的 To 只渲染一次；不得生成 TransferOToTarget |
| 条件 qualifier | `限定词+V+O` 或 `V+O+限定词`，逐模板登记 | 如 `Accept+O+UnderConcession` | 不允许 complement 自带任意操作符或插入第二个命令 |
| 记录外部事实 recorded_fact | `登记+O+已+事实动作` | `Register+O+Participle`；登记结果事件 `O+Fact+Registered`（Fact 词待 N5） | 区分“登记认可”和“本系统认可”；嵌套一层，不能用登记伪装代执行 |
| 动词片段自带对象词的词组 | 按登记模板 | 收款核销：Apply+CashObject+To+Receivable；采信：Accept+O+AsEvidence；预警：Raise+RiskAlert；催收：Collect+Receivable | 受控词组规范化为动作头与补语后渲染；不得把词组内的 Cash/Evidence/Alert 再与对象重复；每个语义模板须用户审定 |
| 生效、逾期等不及物事实 | `O+已生效` / `O+已逾期` | `O+BecameEffective` / `O+BecameOverdue`，登记词汇化事件模板 | TakenEffect、BecomeOverdue 是分词形态，事件用自然的过去式事实；Effective/Overdue 留状态表；不能冒充规则及物模板 |

WriteOff→WrittenOff、CarryForward→CarriedForward、LogIn→LoggedIn、LogOut→LoggedOut、BreakDown→BrokenDown 按受控词组变形。 snake、camelCase、kebab-case 均由最终英文代码指称机械转换，例如 WriteOffReceivable→write_off_receivable / writeOffReceivable / write-off-receivable；snake 不是语言标签，也不登记成另一份手写首选名。

### 4.3 复合动作拆法

| 现名称 | 分析与建议 | 不可由名字决定的部分 |
| --- | --- | --- |
| 追加收入确认事实并占用转实；追加冲销行并占用转实 | Append、占用转实/Consume 为不同事实；外部用例可按主业务意图命名，内部保留两个已登记动作及一致性约束；是否对外拆命令看事务契约 | 不因命名要求把应原子提交的更新拆成两个独立用户请求；“转实”不是本稿已批准术语 |
| 开具销项发票并改写 WIP | IssueInvoice 为主意图；WIP 变化是独立事实/投影更新，Rewrite 不作最终领域名 | WIP 的具体变化、跨 BC 协调与失败补偿须规格证明，本稿不设计 |
| 释放占用、恢复应收 | ReleaseReservation 与 RestoreReceivable 两个动作；若本来是同一经济更正，按该主意图命名用例并分别记录结果 | 不擅自决定事务边界和授权主体 |
| 拒绝或撤回索赔；撤回或撤销投标 | 或表示互斥分支，分别列 Refuse/Withdraw、Withdraw/Revoke；不把二者当同义词 | 分支由谁、在哪个状态执行，须业务确认 |
| 标记付款条件项可结算 | 单一 Mark + outcome=Settleable，可保留补语；状态候选只叫可结算，不叫已可结算 | 可结算的业务条件须规则证明 |
| 登记合同—项目关联与范围挂 WBS | 可能是多个对象或两个动作，先恢复 Link 与范围挂接的对象关系，不把长字符串当 object | N5 名词补登与对象主权未定 |
| 登记重大险情已报告、登记客户认可、登记资料已提供 | Register 的嵌套 recorded_fact；本系统只记录外部已发生事实 | 对方实际报告/认可/提供的证据及日期需合同/流程规格 |
| 认定视为同意成立；标记待主张 | Determine 对认定对象作结论；Mark + outcome=pending Assert；不是 Determine 成为“会计确认”，也不是已经主张 | ARCH_NAME_AUDIT 提示定义冲突，N5/N7 审定后再生成 |

复合自然语言先判“多个动作/对象协调/目的补语/记录外部事实”，再选模板；有并、与不自动拆命令。旧 Requested 事件先看内容：若是要求别人执行则不得作为领域事实；若只是已提交申请，则候选 OSubmittedForApproval；本稿不放宽 G29。

### 4.4 二十个完整示例

下表是审阅用展开，O/V 使用可读名称以便人审；目标数据使用概念引用，不从名称解析 id。例中未登记对象或补语由 N5 审定，不能直接生成代码。

| # | kind | object | verb | aspect | complement | 中文渲染候选 | 英文代码渲染候选 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | command | 工时单 | Submit | imperative | — | 提交工时单 | SubmitTimesheet |
| 2 | domain_event | 工时单 | Submit | perfective | — | 工时单已提交 | TimesheetSubmitted |
| 3 | command | 工时单 | Approve | imperative | — | 批准工时单 | ApproveTimesheet |
| 4 | domain_event | 工时单 | Approve | perfective | — | 工时单已批准 | TimesheetApproved |
| 5 | state | 审批集合绑定 | Approve | perfective | 展示结果=通过 | 已通过 | Approved |
| 6 | state | 审批集合绑定 | Process | pending | — | 待审批 | PendingApproval |
| 7 | state | 审批集合绑定 | Process | progressive | — | 审批中 | InApproval |
| 8 | state | 任务集合绑定 | Claim | pending | — | 待认领 | PendingClaim |
| 9 | command | 来源对照 | Confirm | imperative | — | 确认来源对照 | ConfirmSourceReference |
| 10 | domain_event | 来源对照 | Confirm | perfective | — | 来源对照已确认 | SourceReferenceConfirmed |
| 11 | command | 发票 | Acknowledge | imperative | — | 确认收到发票 | AcknowledgeInvoice |
| 12 | domain_event | 收入 | Recognise | perfective | —（已定；bc `operations.facts`；intent `2026-10-17-091749`） | 收入已确认入账 | RevenueRecognised |
| 13 | command | 开票申请 | Void | imperative | — | 作废开票申请 | VoidBillingRequest |
| 14 | domain_event | 开票申请 | Void | perfective | — | 开票申请已作废 | BillingRequestVoided |
| 15 | command | 付款条件项 | Mark | imperative | outcome=可结算 | 标记付款条件项可结算 | MarkPaymentTermAsSettleable |
| 16 | command | 单据 | Submit | imperative | purpose=审批 | 提交单据审批 | SubmitDocumentForApproval |
| 17 | domain_event | 单据 | Submit | perfective | purpose=审批 | 单据已提交审批 | DocumentSubmittedForApproval |
| 18 | domain_event | 授权 | Close | perfective | reason=离职 | 授权已因离职关闭 | AuthorizationClosedBecauseOfSeparation |
| 19 | command | 交付成果 | AcceptUnderConcession | imperative | qualifier=让步 | 让步接受交付成果 | AcceptDeliverableUnderConcession |
| 20 | domain_event | 协议变更 | TakeEffect | perfective | 词汇化事件模板 | 协议变更已生效 | AgreementChangeBecameEffective |

## 5. 反解统计、可复算口径与边界

一次性工具：`/tmp/n4-build.py`、`/tmp/n4-input.json`、`/tmp/n4-audit.json`；不入仓库。工具只读输入，输出本稿允许文件；没有修改生成器或测试。

**自动成功定义（A）**：仅从现有名称恢复一个非空显式 object、一个受控 verb、kind 固定的 aspect；无补语则 complement=空；已知 `DocumentSubmittedForApproval` 用封闭 purpose 模板。中文目录取最长已知祈使前缀，遇“并、或、与、顿号、长横线”、标记结果或嵌套事实保守转人工；这包含对象协调的误拒，不能把拒绝数直接说成复合动作数。英文词条取最长受控命令前缀或事件分词后缀（含明确列出的存量别名）。这是一种**词形级反解成功率**，对象仍只是文本候选，不要求词表已经登记，也没有证明中英文同义。

**人工（M）**：无显式对象、名词化动作、后置动词、复合补语、词汇化事件、语义冲突或旧桥接词条，逐行列明动作线索；可提出候选但不计自动成功。D 表示错误分类为 command 的策略/期限名，当前没有可执行动词，建议改分类。没有用名称到对象的人工映射填高自动成功率。

| 分母 | 总数 | A 成功 | M/D 人工 | 成功率 |
| --- | --- | --- | --- | --- |
| 收入命令：行数 | 256 | 219 | 37 | 85.55% |
| 收入命令：不同名称 | 250 | 214 | 36 | 85.60% |
| 正式文件命令 | 46 | 9 | 37 | 19.57% |
| 提议目录命令 | 10 | 2 | 8 | 20.00% |
| 正式文件事件 | 95 | 87 | 8 | 91.58% |
| 提议目录事件 | 21 | 18 | 3 | 85.71% |
| 词表命令+事件合计 | 172 | 116 | 56 | 67.44% |
| 目录 emits：不同名称 | 128 | 105 | 23 | 82.03% |

emits 中文额外采用 `O已V`、受控结果尾词的封闭模板；包含“未/被拒/视为”或无对象、英语裸动词等保守转人工。该组只统计名字，不表示 emits 已入事件目录；重复出现的“应收已结清”只计一次。命令中的相同名称也可能属于不同聚合，250 个字面名不能当作250个用例概念。

**不能报告的成功率**：本卡没有做 N5 名词全登记、业务双语裁定、运行状态验证或渲染与正式指称的全量往返比对，故“业务语义正确率”“可直接迁移率”“生产可生成率”均未验证，不把 A 的百分比用作这些指标。原 DRAFT 的“约30%复合动作”是历史抽样，本次不沿用为全量结论。最终守护应从登记槽位正向渲染；反解失败逐条登记例外并注明原因，不能增加无限通配 complement 来追求100%。

## 6. 需用户裁定的问题

以下未定项为可整批审阅的建议，未在本稿代用户批准。「确认入账」已定（AN-06；intent `2026-10-17-091749`；bc `operations.facts`），原 Q02 删除，不再转财务或重问；其余会计用语仍须财务确认。

| 题号 | 需裁定 | 推荐 | 理由 |
| --- | --- | --- | --- |
| Q01 | 是否接受 §2 的“一个语义一个受控动词概念”，同形英语由对象和 BC 消歧？ | 建议接受；157项按语义分组审，不把同形 Release/Return/Open/Countersign 强合概念 | 同词形不保证同权限、同前置条件，保留语义比硬减数量可靠 |
| Q03 | 认定索赔是否确实表示达成索赔结算？ | 先核对 ClaimSettled 定义与业务场景；成立则用 Settle；其他认定用 Determine，影响评估用 Assess | 目录和事件名称不一致，单用 Determine 会丢结算事实 |
| Q04 | 提出、申请、发起是否全并提交？ | 建议不全并；按 §2.3 四种触发区分；同一材料的递交可并 Submit | 申请授权、提交审批、批准授权的现目录已有不同步骤 |
| Q05 | 通过/审核怎样统一；取消/撤销怎样区分？ | Approve 表同意，Review 表审阅；cancel 的现审批义保留「撤销」，活动用「取消」；撤回、作废及撤掉已授效力各按含义区分 | 用户原话「已撤销可开新一轮」（intent `2026-10-14-126-1a流程-审批-重新发起.md`）；不因命名改变业务状态机 |
| Q06 | 权限是否扩充到每个业务动词？ | 建议当前保持10项标准动作，另建按资源的具体用例映射，所有拟项批准前不生成权限 | 现有确认与拒绝可共用 approve 或 update，映射非一对一；本卡无改权限授权 |
| Q07 | 核销、冲销、红冲、回冲、记账、过账的中文与英文词组如何定？ | 建议财务整组确认；保留 Apply/WriteOff/Reverse/Offset/Eliminate/Reduce/Book/Post 的区别，红字发票代码词暂不定 | 不同经济事实不等价；Red红字发票对象代码词仍待财务，未核实法规译法 |
| Q08 | 已/待/中之外是否允许词汇化状态？ | 建议允许逐条带理由的例外；能力、期限条件与状态集合分开；不把每个事件自动建状态 | Live 含退回待重提；Effective/Overdue 为形容词，硬套会失真 |
| Q09 | 60条 state 中32个非取值条目如何处置？ | 建议按 §3.3 分集合/分类/标识/策略审 kind；不全改枚举；6条条件项单独审，22条取值按模板 | 集合名和取值的粒度不同，不能对集合名加已/待 |
| Q10 | 五个疑似误状态词如何处理？ | 建议先由原抽样提供具体 id/文件，再审对应处置；当前记未检出，不制造五个现存缺陷 | 本地全量60条查不到精确名，候选文档不是现存证据 |
| Q11 | 视为同意到底落哪个对象、属于何种结果？ | 建议随 N5/ADR-0049 相关对象重判；本稿只给 Determine 模板，暂停直接生成该状态词条 | ARCH_NAME_AUDIT 已指出交付物状态定义冲突，本卡未独立复核完整 ADR-0049 |
| Q12 | 复合动作必须拆两个对外命令吗？ | 建议按 §4.3 分清多事实与一个用例；保持必须的原子性；补语只保留封闭类型 | 命名规则不应单独改变跨聚合事务、职责或经济事实 |
| Q13 | 英语拼写、过往指称与显示名如何处理？ | 建议新代码词形先候选英式 Recognise/Recognised，旧 Recognized 作迁移证据；用户选定后统一，不在本卡禁用旧名 | ADR分代码与英文界面角色；命名变更影响既有事件，N7逐条审，不能静默替换 |
| Q14 | 动作表是否保留技术词 Rewrite、Dispose、Process 等宽泛名？ | 建议仅保留为迁移待判，不作为新领域命名范本；N7按明确业务结果改名 | 覆盖存量不等于推广存量写法；不为减少失败引入万能动词 |
| Q15 | 长词组动词和补语的英文自然语序如何定？ | 建议按 §4.2 逐模板审定；优先原子 Base 加受控补语，词组中对象成分只渲染一次 | 避免对象在动词词组中重复出现；未定模板缺失时失败 |

## 7. 本卡验证与交付边界

已验证与未运行的实际检查结果在文件末尾登记。设计初稿不包含业务执行实现；派工卡没有指定验收测试，不能声称“验收测试转绿”。原 N4 卡仅创建本文件；本次 N4P 只修改本文件并新增 `docs/decisions/decision-pages/sheets/nm1.json`，不改正式词表、proposed、命名标准、ADR、生成器、权限、安全代码或测试，不登记新术语；未提交、未推送、未联网、未起容器、未建账号。页面生成结果只放 `/tmp/nm1.html`；本次检查结果见 N4P 交付汇报。

## 附录 A：256 行收入命令动作覆盖及反解结果

动作列是覆盖线索，多个动词表示候选分解或嵌套事实，不宣告应有多个对外命令。A 的 object 只按名称切分；M 的对象与补语须人工恢复，不能直接交生成器。“在应收上核销占用”等带介词结构虽然识别了动作，仍须业务核对具体对象。

| 目录行序 | 现命令名 | 动作表引用 | 反解 | object 候选或人工原因 |
| --- | --- | --- | --- | --- |
| R001 | 登记检查计划 | V001:Register | A | 检查计划 |
| R002 | 生效检查计划 | V008:TakeEffect | A | 检查计划 |
| R003 | 标记不再受托 | V004:Mark | M | 结果或嵌套事实需补语 |
| R004 | 安排检查 | V052:Schedule | A | 检查 |
| R005 | 指派检查人员 | V053:Assign | A | 检查人员 |
| R006 | 开始现场检查 | V046:Start | A | 现场检查 |
| R007 | 提交检查记录 | V002:Submit | A | 检查记录 |
| R008 | 审核检查记录 | V032:Review | A | 检查记录 |
| R009 | 锁定检查 | V027:Lock | A | 检查 |
| R010 | 关联检查报告 | V033:Link | A | 检查报告 |
| R011 | 复查检查 | V059:Reinspect | A | 检查 |
| R012 | 取消检查 | V020:Cancel | A | 检查 |
| R013 | 发布检查表模板版本 | V012:Publish | A | 检查表模板版本 |
| R014 | 停用检查表模板版本 | V013:Deactivate | A | 检查表模板版本 |
| R015 | 建议外部检测 | V054:Recommend | A | 外部检测 |
| R016 | 登记业主委托外部检测 | V001:Register | A | 业主委托外部检测 |
| R017 | 登记外部检测报告 | V001:Register | A | 外部检测报告 |
| R018 | 采信外部检测报告 | V055:Accept | A | 外部检测报告 |
| R019 | 不采信外部检测报告 | V056:Reject | A | 外部检测报告 |
| R020 | 登记不符合项 | V001:Register | A | 不符合项 |
| R021 | 确认不符合项 | V009:Confirm | A | 不符合项 |
| R022 | 让步接受不符合项 | V058:AcceptUnderConcession | A | 不符合项 |
| R023 | 关闭不符合项 | V015:Close | A | 不符合项 |
| R024 | 复核不符合项 | V032:Review | A | 不符合项 |
| R025 | 发布不符合项 | V012:Publish | A | 不符合项 |
| R026 | 登记不符合项送达 | V001:Register | A | 不符合项送达 |
| R027 | 记录重大险情已报告 | V001:Register；V102:Report | M | 结果或嵌套事实需补语 |
| R028 | 开始整改 | V046:Start | A | 整改 |
| R029 | 登记整改申报 | V001:Register | A | 整改申报 |
| R030 | 闭合不符合项 | V060:Resolve | A | 不符合项 |
| R031 | 退回整改 | V007/V096:Return | A | 整改 |
| R032 | 登记客户接受风险 | V001:Register | A | 客户接受风险 |
| R033 | 停止跟踪不符合项 | V061:StopTracking | A | 不符合项 |
| R034 | 采集证据 | V062:Collect | A | 证据 |
| R035 | 上传证据摘要 | V063:Upload | A | 证据摘要 |
| R036 | 封存证据 | V035:Seal | A | 证据 |
| R037 | 作废证据 | V022:Void | A | 证据 |
| R038 | 关联证据 | V033:Link | A | 证据 |
| R039 | 解除证据关联 | V034:Unlink | M | 后置谓词/压缩名/未命中 |
| R040 | 起草协议 | V003:Compile | A | 协议 |
| R041 | 登记合同—项目关联与范围挂 WBS | V001:Register；V033:Link | M | 并列/析取/对象协调待判 |
| R042 | 登记视为同意参数与发包人代表 | V001:Register | M | 并列/析取/对象协调待判 |
| R043 | 提交合同评审 | V002:Submit | A | 合同评审 |
| R044 | 通过合同评审 | V016:Approve | A | 合同评审 |
| R045 | 驳回合同评审 | V005:Reject | A | 合同评审 |
| R046 | 回签 | V064/V065:Countersign | M | 名称缺显式对象 |
| R047 | 应用变更 | V050:Apply | A | 变更 |
| R048 | 提交任务订单 | V002:Submit | A | 任务订单 |
| R049 | 标记付款条件项可结算 | V004:Mark；V019:Settle | M | 结果或嵌套事实需补语 |
| R050 | 登记付款条件达成 | V001:Register | A | 付款条件达成 |
| R051 | 完成协议 | V028:Complete | A | 协议 |
| R052 | 编制结算书 | V003:Compile | A | 结算书 |
| R053 | 确认最终价款 | V009:Confirm | A | 最终价款 |
| R054 | 结算协议 | V019:Settle | A | 协议 |
| R055 | 终止协议 | V029:Terminate | A | 协议 |
| R056 | 归档协议 | V036:Archive | A | 协议 |
| R057 | 登记协议借出 | V001:Register | A | 协议借出 |
| R058 | 登记协议归还 | V001:Register | A | 协议归还 |
| R059 | 标记协议借阅逾期 | V004:Mark；V099:BecomeOverdue | M | 结果或嵌套事实需补语 |
| R060 | 占用框架限额 | V026:Reserve | A | 框架限额 |
| R061 | 释放框架限额 | V024/V025:Release | A | 框架限额 |
| R062 | 提出协议变更 | V047:Propose | A | 协议变更 |
| R063 | 评估变更影响 | V018:Assess | A | 变更影响 |
| R064 | 提交协议变更审批 | V002:Submit | A | 协议变更审批 |
| R065 | 通过协议变更审批 | V016:Approve | A | 协议变更审批 |
| R066 | 驳回协议变更审批 | V005:Reject | A | 协议变更审批 |
| R067 | 登记发包人书面同意 | V001:Register；V057:Accept | A | 发包人书面同意 |
| R068 | 生效协议变更 | V008:TakeEffect | A | 协议变更 |
| R069 | 确认现场签证 | V009:Confirm | A | 现场签证 |
| R070 | 提出索赔 | V047:Propose | A | 索赔 |
| R071 | 认定索赔 | V017:Determine；V019:Settle | A | 索赔 |
| R072 | 拒绝或撤回索赔 | V006:Refuse；V021:Withdraw | M | 并列/析取/对象协调待判 |
| R073 | 申请风险交付授权 | V048:Request | A | 风险交付授权 |
| R074 | 提交风险交付授权审批 | V002:Submit | A | 风险交付授权审批 |
| R075 | 批准风险交付授权 | V016:Approve | A | 风险交付授权 |
| R076 | 驳回风险交付授权 | V005:Reject | A | 风险交付授权 |
| R077 | 撤回风险交付授权申请 | V021:Withdraw | A | 风险交付授权申请 |
| R078 | 转正风险交付授权 | V067:Regularize | A | 风险交付授权 |
| R079 | 叫停风险交付授权 | V029:Terminate | A | 风险交付授权 |
| R080 | 更新开票与收入额度 | V030:Change | M | 并列/析取/对象协调待判 |
| R081 | 占用收入额度 | V026:Reserve | A | 收入额度 |
| R082 | 认定视为同意成立 | V017:Determine | A | 视为同意成立 |
| R083 | 追加收入确认事实并占用转实 | V031:Append；V026:Reserve；V011:Recognise | M | 并列/析取/对象协调待判 |
| R084 | 释放收入额度占用 | V024/V025:Release | A | 收入额度占用 |
| R085 | 冲销收入确认 | V037:Reverse | A | 收入确认 |
| R086 | 占用冲销额度 | V026:Reserve | A | 冲销额度 |
| R087 | 追加冲销行并占用转实 | V031:Append；V026:Reserve；V037:Reverse | M | 并列/析取/对象协调待判 |
| R088 | 开具销项发票并改写 WIP | V038:Issue；V069:Rewrite | M | 并列/析取/对象协调待判 |
| R089 | 回冲开票额度 | V068:Restore | A | 开票额度 |
| R090 | 提交过账候选 | V002:Submit | A | 过账候选 |
| R091 | 提交开票申请 | V002:Submit | A | 开票申请 |
| R092 | 开票申请准入 | V009:Confirm | M | 后置谓词/压缩名/未命中 |
| R093 | 驳回开票申请 | V005:Reject | A | 开票申请 |
| R094 | 作废开票申请 | V022:Void | A | 开票申请 |
| R095 | 开具销项发票并改写 WIP | V038:Issue；V069:Rewrite | M | 并列/析取/对象协调待判 |
| R096 | 提交过账候选 | V002:Submit | A | 过账候选 |
| R097 | 发起红字信息确认 | V049:Initiate；V009:Confirm | A | 红字信息确认 |
| R098 | 撤销红字信息确认 | V023:Revoke；V009:Confirm | A | 红字信息确认 |
| R099 | 红冲发票 | V070:Issue | A | 发票 |
| R100 | 开放证据类型 | V071/V095:Open | A | 证据类型 |
| R101 | 创建应收 | V045:Create | A | 应收 |
| R102 | 在应收上核销占用 | V040:Apply；V026:Reserve | M | 结果或嵌套事实需补语 |
| R103 | 检测应收逾期 | V072:Detect | A | 应收逾期 |
| R104 | 登记争议与处置分类 | V001:Register | M | 并列/析取/对象协调待判 |
| R105 | 核销坏账 | V039:WriteOff | A | 坏账 |
| R106 | 提交过账候选 | V002:Submit | A | 过账候选 |
| R107 | 释放占用、恢复应收 | V024/V025:Release；V043:Restore | M | 并列/析取/对象协调待判 |
| R108 | 冲抵员工借款 | V073:Offset | A | 员工借款 |
| R109 | 冲减应收 | V074:Reduce | A | 应收 |
| R110 | 转预收或待退 | V075:Transfer | M | 并列/析取/对象协调待判 |
| R111 | 收款认领 | V041:Claim；V103:Receive | M | 名称缺显式对象 |
| R112 | 记分配行与流水行 | V001:Register；V076/V107:Allocate | M | 并列/析取/对象协调待判 |
| R113 | 登记商票兑付 | V001:Register | A | 商票兑付 |
| R114 | 标记商票已逾期 | V004:Mark；V099:BecomeOverdue | M | 结果或嵌套事实需补语 |
| R115 | 处置待认领流水 | V077:Dispose | A | 待认领流水 |
| R116 | 提交过账候选 | V002:Submit | A | 过账候选 |
| R117 | 登记保证金应缴 | V001:Register | A | 保证金应缴 |
| R118 | 登记保证金缴纳 | V001:Register；V094:Pay | A | 保证金缴纳 |
| R119 | 登记保函开立 | V001:Register；V071/V095:Open | A | 保函开立 |
| R120 | 扣减保证金 | V078:Deduct | A | 保证金 |
| R121 | 退还保证金 | V079:Refund | A | 保证金 |
| R122 | 没收保证金 | V080:Forfeit | A | 保证金 |
| R123 | 登记保函到期或索赔 | V001:Register；V098:Expire；V066:Assert | M | 并列/析取/对象协调待判 |
| R124 | 提交过账候选 | V002:Submit | A | 过账候选 |
| R125 | 登记往来单位 | V001:Register | A | 往来单位 |
| R126 | 变更银行账户 | V030:Change | A | 银行账户 |
| R127 | 核实往来单位档案 | V081:Verify | A | 往来单位档案 |
| R128 | 合并往来单位 | V082:Merge | M | 并列/析取/对象协调待判 |
| R129 | 停用往来单位 | V013:Deactivate | A | 往来单位 |
| R130 | 申请往来单位角色 | V048:Request | A | 往来单位角色 |
| R131 | 核准往来单位角色合格 | V016:Approve | A | 往来单位角色合格 |
| R132 | 暂停往来单位角色 | V044:Suspend | A | 往来单位角色 |
| R133 | 停用往来单位角色 | V013:Deactivate | A | 往来单位角色 |
| R134 | 登记资质证书 | V001:Register | A | 资质证书 |
| R135 | 发起资质核查 | V049:Initiate | A | 资质核查 |
| R136 | 登记核查结果 | V001:Register；V023:Revoke | A | 核查结果 |
| R137 | 延续资质 | V083:Renew | A | 资质 |
| R138 | 标记资质已到期 | V004:Mark；V098:Expire | M | 结果或嵌套事实需补语 |
| R139 | 登记建设工程 | V001:Register | A | 建设工程 |
| R140 | 更新外部状态 | V030:Change | A | 外部状态 |
| R141 | 登记我方参与记录 | V001:Register | M | 并列/析取/对象协调待判 |
| R142 | 登记招标项目 | V001:Register | A | 招标项目 |
| R143 | 发布服务产品版本 | V012:Publish | A | 服务产品版本 |
| R144 | 停用服务产品版本 | V013:Deactivate | A | 服务产品版本 |
| R145 | 发布阶段类型版本 | V012:Publish | A | 阶段类型版本 |
| R146 | 停用阶段类型版本 | V013:Deactivate | A | 阶段类型版本 |
| R147 | 编制费率版本 | V003:Compile | A | 费率版本 |
| R148 | 批准费率版本 | V016:Approve | A | 费率版本 |
| R149 | 生效费率版本 | V008:TakeEffect | A | 费率版本 |
| R150 | 编制计费系数版本 | V003:Compile | A | 计费系数版本 |
| R151 | 生效计费系数版本 | V008:TakeEffect | A | 计费系数版本 |
| R152 | 编制协议价目版本 | V003:Compile | A | 协议价目版本 |
| R153 | 生效协议价目版本 | V008:TakeEffect | A | 协议价目版本 |
| R154 | 计划市场活动 | V051:Plan | A | 市场活动 |
| R155 | 开展市场活动 | V046:Start | A | 市场活动 |
| R156 | 结束市场活动 | V141:End | A | 市场活动 |
| R157 | 登记线索 | V001:Register | A | 线索 |
| R158 | 认领线索 | V041:Claim | A | 线索 |
| R159 | 挂起线索 | V044:Suspend | A | 线索 |
| R160 | 恢复线索 | V043:Restore | A | 线索 |
| R161 | 确认线索 | V009:Confirm | A | 线索 |
| R162 | 关闭线索 | V015:Close | A | 线索 |
| R163 | 新建商机 | V045:Create | A | 商机 |
| R164 | 变更商机负责人 | V030:Change | A | 商机负责人 |
| R165 | 维护商机参与团队 | V030:Change | M | 并列/析取/对象协调待判 |
| R166 | 商机赢单 | V085:Win | M | 后置谓词/压缩名/未命中 |
| R167 | 登记商机丢单 | V001:Register；V086:Lose | A | 商机丢单 |
| R168 | 放弃商机 | V084:Abandon | A | 商机 |
| R169 | 登记资格预审结果 | V001:Register | A | 资格预审结果 |
| R170 | 递交投标 | V002:Submit | A | 投标 |
| R171 | 登记中标 | V001:Register；V085:Win | A | 中标 |
| R172 | 登记未中标 | V001:Register；V086:Lose | A | 未中标 |
| R173 | 登记废标 | V001:Register；V022:Void | A | 废标 |
| R174 | 撤回或撤销投标 | V021:Withdraw；V023:Revoke | M | 并列/析取/对象协调待判 |
| R175 | 放弃投标 | V084:Abandon | A | 投标 |
| R176 | 编制报价版本 | V003:Compile | A | 报价版本 |
| R177 | 提交报价评审 | V002:Submit | A | 报价评审 |
| R178 | 通过报价评审 | V016:Approve | A | 报价评审 |
| R179 | 驳回报价评审 | V005:Reject | A | 报价评审 |
| R180 | 生效报价版本 | V008:TakeEffect | A | 报价版本 |
| R181 | 作废报价版本 | V022:Void | A | 报价版本 |
| R182 | 登记独立性检查结论 | V001:Register；V117:Decide | A | 独立性检查结论 |
| R183 | 计划客户活动 | V051:Plan | A | 客户活动 |
| R184 | 变更客户活动 | V030:Change | A | 客户活动 |
| R185 | 完成客户活动 | V028:Complete | A | 客户活动 |
| R186 | 取消客户活动 | V020:Cancel | A | 客户活动 |
| R187 | 建立项目 | V045:Create | A | 项目 |
| R188 | 开始执行 | V046:Start | A | 执行 |
| R189 | 暂停项目 | V044:Suspend | A | 项目 |
| R190 | 恢复项目 | V043:Restore | A | 项目 |
| R191 | 终止项目 | V029:Terminate | A | 项目 |
| R192 | 取消项目 | V020:Cancel | A | 项目 |
| R193 | 结项 | V015:Close | M | 名称缺显式对象 |
| R194 | 归档项目 | V036:Archive | A | 项目 |
| R195 | 建立项目 WBS | V045:Create | A | 项目 WBS |
| R196 | 开始阶段 | V046:Start | A | 阶段 |
| R197 | 完成阶段 | V028:Complete | A | 阶段 |
| R198 | 锁定工作包 | V027:Lock | A | 工作包 |
| R199 | 作废工作包 | V022:Void | A | 工作包 |
| R200 | 编制交付计划版本 | V003:Compile | A | 交付计划版本 |
| R201 | 批准交付计划 | V016:Approve | A | 交付计划 |
| R202 | 编制项目预算版本 | V003:Compile | A | 项目预算版本 |
| R203 | 锁定项目预算 | V027:Lock | A | 项目预算 |
| R204 | 提出完工估算 | V047:Propose | A | 完工估算 |
| R205 | 批准完工估算 | V016:Approve | A | 完工估算 |
| R206 | 填报产值估计 | V002:Submit | A | 产值估计 |
| R207 | 维护项目团队成员 | V030:Change | A | 项目团队成员 |
| R208 | 提出资源需求 | V047:Propose | A | 资源需求 |
| R209 | 确认资源分配 | V009:Confirm | A | 资源分配 |
| R210 | 编制交付物 | V003:Compile | A | 交付物 |
| R211 | 登记内部评审结论 | V001:Register | A | 内部评审结论 |
| R212 | 签发交付物 | V038:Issue | A | 交付物 |
| R213 | 登记提交轮次送达 | V001:Register；V089:Deliver | A | 提交轮次送达 |
| R214 | 登记客户认可 | V001:Register；V057:Accept | A | 客户认可 |
| R215 | 登记客户退回 | V001:Register；V007/V096:Return | A | 客户退回 |
| R216 | 登记项目经理的无异议声明 | V001:Register；V090:Declare | A | 项目经理的无异议声明 |
| R217 | 封存交付物 | V035:Seal | A | 交付物 |
| R218 | 认定返工 | V017:Determine | A | 返工 |
| R219 | 撤销返工认定 | V023:Revoke | A | 返工认定 |
| R220 | 提出设计变更 | V047:Propose | A | 设计变更 |
| R221 | 认定变更归因与影响 | V017:Determine | M | 并列/析取/对象协调待判 |
| R222 | 内审设计变更 | V032:Review | A | 设计变更 |
| R223 | 发出设计变更 | V088:Send | A | 设计变更 |
| R224 | 标记待主张 | V004:Mark；V066:Assert | M | 结果或嵌套事实需补语 |
| R225 | 关闭设计变更 | V015:Close | A | 设计变更 |
| R226 | 通知延期 | V087:Notify | A | 延期 |
| R227 | 提交延期详细说明 | V002:Submit | A | 延期详细说明 |
| R228 | 登记延期批准 | V001:Register；V016:Approve | A | 延期批准 |
| R229 | 登记延期拒绝 | V001:Register；V006:Refuse | A | 延期拒绝 |
| R230 | 登记延期视为批准 | V001:Register；V016:Approve | A | 延期视为批准 |
| R231 | 登记资料需求 | V001:Register | A | 资料需求 |
| R232 | 登记资料已提供 | V001:Register；V091:Provide | M | 结果或嵌套事实需补语 |
| R233 | 确认发包人资料 | V009:Confirm | A | 发包人资料 |
| R234 | 退回发包人资料 | V007/V096:Return | A | 发包人资料 |
| R235 | 编制项目复盘 | V003:Compile | A | 项目复盘 |
| R236 | 评审项目复盘 | V032:Review | A | 项目复盘 |
| R237 | 发布项目复盘 | V012:Publish | A | 项目复盘 |
| R238 | 登记风险与问题 | V001:Register | M | 并列/析取/对象协调待判 |
| R239 | 记录应对措施 | V001:Register | A | 应对措施 |
| R240 | 关闭风险与问题 | V015:Close | M | 并列/析取/对象协调待判 |
| R241 | 接受风险与问题 | V057:Accept | M | 并列/析取/对象协调待判 |
| R242 | 发出满意度调查 | V088:Send | A | 满意度调查 |
| R243 | 登记调查回收 | V001:Register；V103:Receive | A | 调查回收 |
| R244 | 评分满意度调查 | V092:Score | A | 满意度调查 |
| R245 | 登记满意度跟进 | V001:Register；V093:FollowUp | A | 满意度跟进 |
| R246 | 起草工时单 | V003:Compile | A | 工时单 |
| R247 | 提交工时单 | V002:Submit | A | 工时单 |
| R248 | 通过工时单 | V016:Approve | A | 工时单 |
| R249 | 退回工时单 | V007/V096:Return | A | 工时单 |
| R250 | 驳回工时单 | V005:Reject | A | 工时单 |
| R251 | 撤回工时单 | V021:Withdraw | A | 工时单 |
| R252 | 认定工时有效性 | V017:Determine | A | 工时有效性 |
| R253 | 提交过账候选 | V002:Submit | A | 过账候选 |
| R254 | 创建独立性检查 | V045:Create | A | 独立性检查 |
| R255 | 标记资源需求已满足 | V004:Mark；V100:Satisfy | M | 结果或嵌套事实需补语 |
| R256 | 关闭资源需求 | V015:Close | A | 资源需求 |

## 附录 B：正式与提议的全部命令、事件名称覆盖

所有来源均保留，不按 status 或桥接用途删分母；目标动作是审阅建议，不改原概念 id。A 按英文结构匹配，可能与中文含义不符（例如词尾 Activated 的实际中文是生效）；必须继续做语义裁定，不能仅凭 A 采用映射。

| kind / 来源 | 概念 id / BC | 现中文 / 英文 | 动作候选 | 反解 / 提取结果 |
| --- | --- | --- | --- | --- |
| 事件 / 正式 | business_fact_attributed / operations.facts | 事实归属 / BusinessFactAttributed | V132:Attribute | A：object=BusinessFact；C=— |
| 事件 / 正式 | business_fact_restated / operations.facts | 事实重述 / BusinessFactRestated | V133:Restate | A：object=BusinessFact；C=— |
| 命令 / 正式 | reconciliation / operations.policy | 经营勾稽 / Reconciliation | V108:Reconcile | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | waive_contract_gap / contract.agreement | 缺口豁免 / WaiveContractGap | V104:Waive | A：object=ContractGap；C=— |
| 事件 / 正式 | contract_gap_waived / contract.agreement | 合同缺口已豁免 / ContractGapWaived | V104:Waive | A：object=ContractGap；C=— |
| 命令 / 正式 | import_legacy_contract_baseline / contract.agreement | 历史合同导入 / ImportLegacyContractBaseline | V105:Import | A：object=LegacyContractBaseline；C=— |
| 事件 / 正式 | contract_signed / contract.agreement | 合同签署事实 / ContractSigned | V156:Sign | A：object=Contract；C=— |
| 命令 / 正式 | correct_contract_party / contract.agreement | 修正合同主体 / CorrectContractParty | V106:Correct | A：object=ContractParty；C=— |
| 事件 / 正式 | contract_gap_identified / contract.agreement | 合同缺口 / ContractGapIdentified | V134:Identify | A：object=ContractGap；C=— |
| 事件 / 正式 | claim_asserted / contract.agreement | 索赔通知 / ClaimAsserted | V066:Assert | A：object=Claim；C=— |
| 事件 / 正式 | scope_creep_identified / contract.agreement | 无签证范围蔓延 / ScopeCreepIdentified | V134:Identify | A：object=ScopeCreep；C=— |
| 事件 / 正式 | regularized / contract.agreement | 转正 / Regularized | V067:Regularize | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | halted / contract.agreement | 叫停 / Halted | V029:Terminate | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | scope_changed / contract.agreement | 范围变化 / ScopeChanged | V030:Change | A：object=Scope；C=— |
| 事件 / 正式 | change_order_approved / contract.agreement | 变更批准 / ChangeOrderApproved | V016:Approve | A：object=ChangeOrder；C=— |
| 事件 / 正式 | site_visa_confirmed / contract.agreement | 现场签证签认 / SiteVisaConfirmed | V009:Confirm | A：object=SiteVisa；C=— |
| 事件 / 正式 | claim_settled / contract.agreement | 达成 / ClaimSettled | V019:Settle | A：object=Claim；C=— |
| 事件 / 正式 | milestone_completion_submitted / delivery.execution | 完成申报 / MilestoneCompletionSubmitted | V002:Submit | A：object=MilestoneCompletion；C=— |
| 事件 / 正式 | schedule_variance_identified / delivery.execution | 进度偏差识别 / ScheduleVarianceIdentified | V134:Identify | A：object=ScheduleVariance；C=— |
| 事件 / 正式 | acceptance_returned / delivery.acceptance | 验收退回 / AcceptanceReturned | V007/V096:Return | A：object=Acceptance；C=— |
| 事件 / 正式 | milestone_accepted / delivery.acceptance | 验收通过 / MilestoneAccepted | V057:Accept | A：object=Milestone；C=— |
| 事件 / 正式 | cost_accumulated / cost.costing | 成本归集 / CostAccumulated | V135:Accumulate | A：object=Cost；C=— |
| 命令 / 正式 | allocate_indirect_cost / cost.costing | 间接成本分摊 / AllocateIndirectCost | V076/V107:Allocate | A：object=IndirectCost；C=— |
| 命令 / 正式 | submit_timesheet / cost.timesheet | 填报 / SubmitTimesheet | V002:Submit | A：object=Timesheet；C=— |
| 命令 / 正式 | approve_timesheet / cost.timesheet | 审核 / ApproveTimesheet | V016:Approve | A：object=Timesheet；C=— |
| 事件 / 正式 | invoice_issued / receivables.wip | 开票 / InvoiceIssued | V038:Issue | A：object=Invoice；C=— |
| 事件 / 正式 | invoice_eligibility_confirmed / receivables.wip | 开票准入 / InvoiceEligibilityConfirmed | V009:Confirm | A：object=InvoiceEligibility；C=— |
| 事件 / 正式 | cash_received / receivables.collection | 回款 / CashReceived | V103:Receive | A：object=Cash；C=— |
| 命令 / 正式 | receipt_claiming / receivables.collection | 到账认领 / ReceiptClaiming | V041:Claim | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | cash_application / receivables.collection | 核销 / CashApplication | V040:Apply | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | ar_written_off / receivables.collection | 坏账核销 / ArWrittenOff | V039:WriteOff | A：object=Ar；C=— |
| 命令 / 正式 | collection / receivables.collection | 催收 / Collection | V109:Collect | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | ar_overdue_detected / receivables.collection | 逾期应收 / ArOverdueDetected | V072:Detect | A：object=ArOverdue；C=— |
| 事件 / 正式 | ecl_alert / receivables.collection | ECL 预警 / EclAlert | V136:Raise | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | contract_asset_impairment_risk_raised / receivables.wip | 合同资产减值预警 / ContractAssetImpairmentRiskRaised | V136:Raise | A：object=ContractAssetImpairmentRisk；C=— |
| 事件 / 正式 | ar_impairment_risk_raised / receivables.wip | 应收账款减值预警 / ArImpairmentRiskRaised | V136:Raise | A：object=ArImpairmentRisk；C=— |
| 事件 / 正式 | retention_money_release_due / receivables.collection | 质量保证金到期返还 / RetentionMoneyReleaseDue | V098:Expire | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | accounting_period_closed / accounting.ledger | 会计期间已关闭 / AccountingPeriodClosed | V015:Close | A：object=AccountingPeriod；C=— |
| 事件 / 正式 | statutory_revenue_booked / accounting.ledger | 法定收入 / StatutoryRevenueBooked | V137:Book | A：object=StatutoryRevenue；C=— |
| 命令 / 正式 | period_end_closing / accounting.ledger | 期末结转 / PeriodEndClosing | V110:CarryForward | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | impairment_booked / accounting.ledger | 减值分录 / ImpairmentBooked | V137:Book | A：object=Impairment；C=— |
| 命令 / 正式 | currency_translation / accounting.ledger | 汇率折算 / CurrencyTranslation | V111:Translate | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | intercompany_elimination / accounting.ledger | 内部往来抵销 / IntercompanyElimination | V112:Eliminate | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | intercompany_settlement / accounting.ledger | 内部结算 / IntercompanySettlement | V019:Settle | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | approval / platform.approval | 审批 / Approval | V115:Process | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | rule_execution / platform.approval | 规则执行 / RuleExecution | V138:Execute | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | contract_modification / contract.agreement | 合同变更（会计口径） / ContractModification | V030:Change | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | revenue_recognized / receivables.wip | 收入已确认 / RevenueRecognized | V011:Recognise | A：object=Revenue；C=— |
| 命令 / 正式 | period_close / accounting.period | 期末关账 / PeriodClose | V113:Close | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | reopen_accounting_period / accounting.period | 期间重开 / ReopenAccountingPeriod | V114:Reopen | A：object=AccountingPeriod；C=— |
| 命令 / 正式 | approve / platform.approval | 通过 / Approve | V016:Approve | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | handle / platform.approval | 办理 / Handle | V116:Handle | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | task_assigned / platform.approval | 任务已分配 / TaskAssigned | V053:Assign | A：object=Task；C=— |
| 事件 / 正式 | approval_completed / platform.approval | 审批已完成 / ApprovalCompleted | V028:Complete | A：object=Approval；C=— |
| 命令 / 正式 | decide / platform.approval | 作出决定 / Decide | V117:Decide | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | return / platform.approval | 退回 / Return | V007/V096:Return | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | terminate / platform.approval | 终止 / Terminate | V029:Terminate | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | transfer / platform.approval | 转交 / Transfer | V120:Transfer | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | escalation / platform.approval | 升级 / Escalation | V118:Escalate | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | reassignment / platform.approval | 改派 / Reassignment | V119:Reassign | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | unclaim / platform.approval | 取消认领 / Unclaim | V042:Unclaim | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | task_claim / platform.approval | 认领 / TaskClaim | V041:Claim | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | reject / platform.approval | 驳回 / Reject | V005:Reject | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | task_handled / platform.approval | 任务已办理 / TaskHandled | V116:Handle | A：object=Task；C=— |
| 命令 / 正式 | resubmit / platform.approval | 重新提交 / Resubmit | V157:Resubmit | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | skip / platform.approval | 跳过 / Skip | V121:Skip | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | countersign / platform.approval | 会签 / Countersign | V064/V065:Countersign | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | withdraw / platform.approval | 撤回 / Withdraw | V021:Withdraw | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | cancel / platform.approval | 撤销 / Cancel | V020:Cancel | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | work_calendar_locked_days_changed / organization.attendance | 工作日历锁定日期被修改 / WorkCalendarLockedDaysChanged | V030:Change | A：object=WorkCalendarLockedDays；C=— |
| 命令 / 正式 | grant_permission / platform.identity | 授予权限 / GrantPermission | V122:Grant | A：object=Permission；C=— |
| 命令 / 正式 | login / platform.identity | 登录 / Login | V123:LogIn | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | logout / platform.identity | 登出 / Logout | V124:LogOut | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | uphold_permission_action / platform.identity | 维持授权 / Uphold | V126:Uphold | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | forced_logout / platform.identity | 强制登出 / ForcedLogout | V125:ForceLogOut | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | probe / platform.integration | 探针 / Probe | V127:Probe | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | data_retention_period / platform.integration | 数据保留期限 / DataRetentionPeriod | D：策略期限，无可执行动词 | D：建议改为策略/期限值对象 |
| 命令 / 正式 | prorate / kernel | 按权重拆分 / Prorate | V129:Prorate | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | reverse / kernel | 冲销动作 / Reverse | V037:Reverse | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | correct / kernel | 修正动作 / Correct | V106:Correct | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | issue_number / platform.numbering | 发号 / IssueNumber | V038:Issue | A：object=Number；C=— |
| 命令 / 正式 | budget_preparation / operations.planning | 预算编制 / BudgetPreparation | V003:Compile | M：对象/名词化/词组/语义需人工 |
| 命令 / 正式 | budget_breakdown / operations.planning | 预算分解 / BudgetBreakdown | V154:BreakDown | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | budget_reserved / operations.planning | 预算占用 / BudgetReserved | V026:Reserve | A：object=Budget；C=— |
| 事件 / 正式 | budget_released / operations.planning | 预算释放 / BudgetReleased | V024/V025:Release | A：object=Budget；C=— |
| 事件 / 正式 | budget_consumed / operations.planning | 预算消耗 / BudgetConsumed | V139:Consume | A：object=Budget；C=— |
| 事件 / 正式 | budget_adjusted / operations.planning | 预算调整 / BudgetAdjusted | V140:Adjust | A：object=Budget；C=— |
| 事件 / 正式 | revenue_reduction / receivables.wip | 收入冲减 / RevenueReduction | V074:Reduce | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | timesheet_approved / cost.timesheet | 工时单已批准 / TimesheetApproved | V016:Approve | A：object=Timesheet；C=— |
| 事件 / 正式 | timesheet_rejected / cost.timesheet | 工时单已驳回 / TimesheetRejected | V005:Reject | A：object=Timesheet；C=— |
| 事件 / 正式 | timesheet_returned / cost.timesheet | 工时单已退回 / TimesheetReturned | V007/V096:Return | A：object=Timesheet；C=— |
| 事件 / 正式 | timesheet_submitted / cost.timesheet | 工时单已提交审批 / TimesheetSubmitted | V002:Submit | A：object=Timesheet；C=— |
| 事件 / 正式 | work_calendar_year_maintained / organization.attendance | 工作日历年度已维护 / WorkCalendarYearMaintained | V030:Change | A：object=WorkCalendarYear；C=— |
| 事件 / 正式 | employee_registered / organization.workforce | 员工已登记 / EmployeeRegistered | V001:Register | A：object=Employee；C=— |
| 事件 / 正式 | legal_entity_activated / organization.workforce | 法人主体已启用 / LegalEntityActivated | V014:Activate | A：object=LegalEntity；C=— |
| 事件 / 正式 | legal_entity_attributed / organization.workforce | 法人归属已确立 / LegalEntityAttributed | V132:Attribute | A：object=LegalEntity；C=— |
| 事件 / 正式 | legal_entity_attribution_corrected / organization.workforce | 法人归属结束日已更正 / LegalEntityAttributionCorrected | V106:Correct | A：object=LegalEntityAttribution；C=— |
| 事件 / 正式 | legal_entity_attribution_ended / organization.workforce | 法人归属已结束 / LegalEntityAttributionEnded | V141:End | A：object=LegalEntityAttribution；C=— |
| 事件 / 正式 | legal_entity_code_corrected / organization.workforce | 法人主体代码已更正 / LegalEntityCodeCorrected | V106:Correct | A：object=LegalEntityCode；C=— |
| 事件 / 正式 | legal_entity_deactivated / organization.workforce | 法人主体已停用 / LegalEntityDeactivated | V013:Deactivate | A：object=LegalEntity；C=— |
| 事件 / 正式 | legal_entity_registered / organization.workforce | 法人主体已登记 / LegalEntityRegistered | V001:Register | A：object=LegalEntity；C=— |
| 事件 / 正式 | legal_entity_renamed / organization.workforce | 法人主体已更名 / LegalEntityRenamed | V142:Rename | A：object=LegalEntity；C=— |
| 事件 / 正式 | org_snapshot_changed / organization.workforce | 组织快照已变化 / OrgSnapshotChanged | V030:Change | A：object=OrgSnapshot；C=— |
| 事件 / 正式 | sync_run_failed / organization.workforce | 同步记录已失败 / SyncRunFailed | V143:Fail | A：object=SyncRun；C=— |
| 事件 / 正式 | sync_run_held / organization.workforce | 同步记录已被暂停 / SyncRunHeld | V044:Suspend | A：object=SyncRun；C=— |
| 事件 / 正式 | sync_run_hold_released / organization.workforce | 同步记录一类暂停已解除 / SyncRunHoldReleased | V145:Release | A：object=SyncRunHold；C=— |
| 事件 / 正式 | sync_run_released / organization.workforce | 同步记录已放行 / SyncRunReleased | V024/V025:Release | A：object=SyncRun；C=— |
| 事件 / 正式 | sync_run_succeeded / organization.workforce | 同步记录已成功 / SyncRunSucceeded | V144:Succeed | A：object=SyncRun；C=— |
| 事件 / 正式 | approval_round_started / platform.approval | 审批轮次已开始 / ApprovalRoundStarted | V046:Start | A：object=ApprovalRound；C=— |
| 事件 / 正式 | approval_task_escalated / platform.approval | 任务已升级 / ApprovalTaskEscalated | V118:Escalate | A：object=ApprovalTask；C=— |
| 事件 / 正式 | document_submitted_for_approval / platform.approval | 单据已请求审批 / DocumentSubmittedForApproval | V002:Submit | A：object=单据；C=purpose=审批 |
| 事件 / 正式 | task_claimed / platform.approval | 任务已认领 / TaskClaimed | V041:Claim | A：object=Task；C=— |
| 事件 / 正式 | task_closed / platform.approval | 任务已关闭 / TaskClosed | V015:Close | A：object=Task；C=— |
| 事件 / 正式 | task_reassigned / platform.approval | 任务已改派 / TaskReassigned | V119:Reassign | A：object=Task；C=— |
| 事件 / 正式 | task_released / platform.approval | 任务已退回候选组 / TaskReleased | V042:Unclaim | A：object=Task；C=— |
| 事件 / 正式 | task_transferred / platform.approval | 任务已转交 / TaskTransferred | V120:Transfer | A：object=Task；C=— |
| 事件 / 正式 | account_enabled / platform.identity | 用户账号已启用 / AccountEnabled | V014:Activate | A：object=Account；C=— |
| 事件 / 正式 | account_opened / platform.identity | 用户账号已开立 / AccountOpened | V071/V095:Open | A：object=Account；C=— |
| 事件 / 正式 | account_roles_replaced / platform.identity | 用户账号角色已替换 / AccountRolesReplaced | V146:Replace | A：object=AccountRoles；C=— |
| 事件 / 正式 | account_separated / platform.identity | 用户账号已因离职收回角色 / AccountSeparated | V147:Revoke | A：object=Account；C=— |
| 事件 / 正式 | authorization_confirmed / platform.identity | 授权申请已确认 / AuthorizationConfirmed | V009:Confirm | A：object=Authorization；C=— |
| 事件 / 正式 | authorization_opened / platform.identity | 授权申请已开立 / AuthorizationOpened | V071/V095:Open | A：object=Authorization；C=— |
| 事件 / 正式 | authorization_rejected / platform.identity | 授权申请已拒绝 / AuthorizationRejected | V005:Reject | A：object=Authorization；C=— |
| 事件 / 正式 | emergency_admin_credentials_reset / platform.identity | 应急管理员凭据已重置 / EmergencyAdminCredentialsReset | V148:Reset | A：object=EmergencyAdminCredentials；C=— |
| 事件 / 正式 | emergency_admin_disabled / platform.identity | 应急管理员已停用 / EmergencyAdminDisabled | V013:Deactivate | A：object=EmergencyAdmin；C=— |
| 事件 / 正式 | emergency_admin_enabled / platform.identity | 应急管理员已启用 / EmergencyAdminEnabled | V014:Activate | A：object=EmergencyAdmin；C=— |
| 事件 / 正式 | emergency_admin_registered / platform.identity | 应急管理员已登记 / EmergencyAdminRegistered | V001:Register | A：object=EmergencyAdmin；C=— |
| 事件 / 正式 | role_created / platform.identity | 角色已设立 / RoleCreated | V045:Create | A：object=Role；C=— |
| 事件 / 正式 | role_disabled / platform.identity | 角色已停用 / RoleDisabled | V013:Deactivate | A：object=Role；C=— |
| 事件 / 正式 | role_enabled / platform.identity | 角色已启用 / RoleEnabled | V014:Activate | A：object=Role；C=— |
| 事件 / 正式 | role_revised / platform.identity | 角色已修订 / RoleRevised | V149:Revise | A：object=Role；C=— |
| 事件 / 正式 | source_reference_confirmed / platform.integration | 来源对照已确认 / SourceReferenceConfirmed | V009:Confirm | A：object=SourceReference；C=— |
| 事件 / 正式 | source_reference_registered / platform.integration | 来源对照已登记 / SourceReferenceRegistered | V001:Register | A：object=SourceReference；C=— |
| 事件 / 正式 | source_reference_rejected / platform.integration | 来源对照已拒绝 / SourceReferenceRejected | V006:Refuse | A：object=SourceReference；C=— |
| 事件 / 正式 | source_reference_superseded / platform.integration | 来源对照已被取代 / SourceReferenceSuperseded | V131:Supersede | A：object=SourceReference；C=— |
| 事件 / 正式 | numbering_rule_confirmation_given / platform.numbering | 编号规则已获一次确认 / NumberingRuleConfirmationGiven | V009:Confirm | M：对象/名词化/词组/语义需人工 |
| 事件 / 正式 | numbering_rule_confirmed / platform.numbering | 编号规则已确认 / NumberingRuleConfirmed | V009:Confirm | A：object=NumberingRule；C=— |
| 事件 / 正式 | numbering_rule_reconfigured / platform.numbering | 编号规则已重新设置 / NumberingRuleReconfigured | V150:Reconfigure | A：object=NumberingRule；C=— |
| 事件 / 正式 | user_account_disabled / platform.identity | 用户账号已停用 / UserAccountDisabled | V013:Deactivate | A：object=UserAccount；C=— |
| 事件 / 正式 | deliverable_delivered / delivery.acceptance | 交付物已送达 / DeliverableDelivered | V089:Deliver | A：object=Deliverable；C=— |
| 事件 / 正式 | agreement_final_account_confirmed / contract.agreement | 协议竣工结算已确认 / AgreementFinalAccountConfirmed | V009:Confirm | A：object=AgreementFinalAccount；C=— |
| 命令 / architecture-acl.yaml | apply_org_change_set / organization.workforce | 应用组织变更 / ApplyOrgChangeSet | V050:Apply | A：object=OrgChangeSet；C=— |
| 事件 / code-aggregates-events.yaml | timesheet_approval_withdrawn / cost.timesheet | 工时单已撤回审批 / TimesheetApprovalWithdrawn | V021:Withdraw | A：object=TimesheetApproval；C=— |
| 事件 / code-aggregates-events.yaml | approval_task_became_overdue / platform.approval | 任务已超时 / ApprovalTaskBecameOverdue | V099:BecomeOverdue | A：object=ApprovalTask；C=— |
| 事件 / code-aggregates-events.yaml | role_deleted / platform.identity | 角色已废除 / RoleDeleted | V151:Abolish | A：object=Role；C=— |
| 事件 / code-aggregates-events.yaml | source_reference_refused / platform.integration | 来源对照登记已被拒绝 / SourceReferenceRefused | V006:Refuse | A：object=SourceReference；C=— |
| 命令 / data-migration.yaml | trial_commit / platform.migration | 试提交 / TrialCommit | V130:TrialCommit | M：对象/名词化/词组/语义需人工 |
| 事件 / enterprise-aggregates.yaml | subcontract_change_activated / procurement.subcontracting | 分包变更已生效 / SubcontractChangeActivated | V014:Activate | A：object=SubcontractChange；C=— |
| 事件 / enterprise-aggregates.yaml | no_objection_declared / delivery.acceptance | 无异议已声明 / NoObjectionDeclared | V090:Declare | A：object=NoObjection；C=— |
| 事件 / enterprise-aggregates.yaml | counterparty_bank_account_changed / masterdata.counterparty | 往来单位银行账户已变更 / CounterpartyBankAccountChanged | V030:Change | A：object=CounterpartyBankAccount；C=— |
| 事件 / enterprise-aggregates.yaml | bid_won / presales.opportunity | 投标已中标 / BidWon | V085:Win | A：object=Bid；C=— |
| 事件 / enterprise-aggregates.yaml | bid_lost / presales.opportunity | 投标未中标 / BidLost | V086:Lose | A：object=Bid；C=— |
| 事件 / enterprise-aggregates.yaml | opportunity_lost / presales.opportunity | 商机已输单 / OpportunityLost | V086:Lose | A：object=Opportunity；C=— |
| 事件 / enterprise-aggregates.yaml | opportunity_abandoned / presales.opportunity | 商机已放弃 / OpportunityAbandoned | V084:Abandon | A：object=Opportunity；C=— |
| 事件 / enterprise-aggregates.yaml | agreement_terminated / contract.agreement | 协议已终止 / AgreementTerminated | V029:Terminate | A：object=Agreement；C=— |
| 事件 / enterprise-aggregates.yaml | agreement_baseline_activated / contract.agreement | 协议新基线已生效 / AgreementBaselineActivated | V014:Activate | A：object=AgreementBaseline；C=— |
| 事件 / enterprise-aggregates.yaml | agreement_project_linked / contract.agreement | 协议项目关联已登记 / AgreementProjectLinked | V033:Link | A：object=AgreementProject；C=— |
| 事件 / enterprise-aggregates.yaml | project_established / delivery.execution | 项目已建立 / ProjectEstablished | V045:Create | A：object=Project；C=— |
| 事件 / enterprise-aggregates.yaml | project_closed / delivery.execution | 项目已结项 / ProjectClosed | V015:Close | A：object=Project；C=— |
| 事件 / enterprise-aggregates.yaml | project_terminated / delivery.execution | 项目已终止 / ProjectTerminated | V029:Terminate | A：object=Project；C=— |
| 事件 / enterprise-aggregates.yaml | project_cancelled / delivery.execution | 项目已取消 / ProjectCancelled | V020:Cancel | A：object=Project；C=— |
| 命令 / frontend-ui.yaml | release / platform.integration | 放行 / Release | V024/V025:Release | M：对象/名词化/词组/语义需人工 |
| 命令 / platform-integration.yaml | registration / platform.integration | 登记 / Registration | V001:Register | M：对象/名词化/词组/语义需人工 |
| 命令 / platform-integration.yaml | supersede / platform.integration | 取代 / Supersede | V131:Supersede | M：对象/名词化/词组/语义需人工 |
| 命令 / platform-integration.yaml | read_only_verification / platform.integration | 只读核实 / ReadOnlyVerification | V081:Verify | M：对象/名词化/词组/语义需人工 |
| 命令 / round3-pending-renames.yaml | round3_pending__act / platform.approval | 办理 / Act | V116:Handle | M：对象/名词化/词组/语义需人工 |
| 事件 / round3-pending-renames.yaml | round3_pending__assigned / platform.approval | 已分配 / Assigned | V053:Assign | M：对象/名词化/词组/语义需人工 |
| 事件 / round3-pending-renames.yaml | round3_pending__completed / platform.approval | 已完成 / Completed | V028:Complete | M：对象/名词化/词组/语义需人工 |
| 命令 / round3-pending-renames.yaml | round3_pending__release_of_task / platform.approval | 释放 / ReleaseOfTask | V042:Unclaim | A：object=OfTask；C=— |
| 事件 / round3-pending-renames.yaml | round3_pending__resulted / platform.approval | 已出结果 / Resulted | V116:Handle | M：对象/名词化/词组/语义需人工 |
| 命令 / round3-pending-renames.yaml | round3_pending__authorization / platform.identity | 授权 / Authorization | V122:Grant | M：对象/名词化/词组/语义需人工 |
| 命令 / round3-pending-renames.yaml | round3_pending__keep / platform.integration | 保留 / Keep | D：策略期限，无可执行动词 | D：建议改为策略/期限值对象 |

## 附录 C：目录 emits 全部不同字面名

保留英文原名及中英文混用，未自动转为正式事件名。A 仅说明封闭名称模式可切分；M 可能是省略对象、否定或词汇化结果，须按 §4 恢复槽位；不把不合式名字悄悄漏出分母。

| # | 现 emits 字面名 | 词形反解 |
| --- | --- | --- |
| 1 | 检查已锁定 | A |
| 2 | 检查表模板版本已生效 | A |
| 3 | 外部检测报告已采信 | A |
| 4 | 外部检测报告未采信 | M |
| 5 | 不符合项已确认 | A |
| 6 | 不符合项已关闭 | A |
| 7 | 不符合项已发布 | A |
| 8 | 不符合项已闭合 | A |
| 9 | 证据已封存 | M |
| 10 | 合同—项目关联已登记 | M |
| 11 | ContractSigned | A |
| 12 | 新基线已生效 | A |
| 13 | 付款条件项已可结算 | M |
| 14 | 协议已完成 | A |
| 15 | 协议已结算 | A |
| 16 | 协议已终止 | A |
| 17 | 协议借阅已逾期 | A |
| 18 | 限额已占用 | A |
| 19 | 框架限额占用被拒 | M |
| 20 | 限额已释放 | A |
| 21 | ChangeOrderApproved | A |
| 22 | SiteVisaConfirmed | A |
| 23 | ClaimAsserted | A |
| 24 | ClaimSettled | A |
| 25 | 风险交付授权已批准 | A |
| 26 | Regularized | M |
| 27 | Halted | M |
| 28 | 可开票额度已更新 | A |
| 29 | 视为同意已成立 | A |
| 30 | RevenueRecognized | A |
| 31 | 收入确认已冲销 | A |
| 32 | InvoiceEligibilityConfirmed | A |
| 33 | 开票申请已驳回 | A |
| 34 | 开票申请已作废 | A |
| 35 | InvoiceIssued | A |
| 36 | 发票已红冲 | A |
| 37 | 证据类型已开放 | A |
| 38 | 应收已结清 | A |
| 39 | ArOverdueDetected | A |
| 40 | ArWrittenOff | A |
| 41 | CashReceived | A |
| 42 | 收款已分配 | A |
| 43 | 商票已逾期 | A |
| 44 | 保证金已缴 | A |
| 45 | 保证金已退 | A |
| 46 | 保证金已没收 | A |
| 47 | 保函已到期 | A |
| 48 | 往来单位已登记 | A |
| 49 | 银行账户已变更 | A |
| 50 | 往来单位已核实 | A |
| 51 | 往来单位已合并 | A |
| 52 | 往来单位已停用 | A |
| 53 | 角色已合格 | A |
| 54 | 角色已暂停 | A |
| 55 | 角色已停用 | A |
| 56 | 资质已撤销 | A |
| 57 | 资质已到期 | A |
| 58 | 建设工程已登记 | A |
| 59 | 服务产品版本已发布 | A |
| 60 | 阶段类型版本已发布 | A |
| 61 | 费率版本已生效 | A |
| 62 | 费率版本已被取代 | M |
| 63 | 计费系数版本已生效 | A |
| 64 | 协议价目版本已生效 | A |
| 65 | 市场活动已结束 | M |
| 66 | 线索已确认 | A |
| 67 | 线索已关闭 | A |
| 68 | 独立性检查要求 | M |
| 69 | OpportunityAccepted | A |
| 70 | 商机已丢单 | A |
| 71 | 商机已放弃 | A |
| 72 | 投标已递交 | A |
| 73 | 投标已中标 | A |
| 74 | 投标未中标 | M |
| 75 | 投标已废标 | A |
| 76 | 投标已撤销 | A |
| 77 | 投标已放弃 | A |
| 78 | 报价版本已生效 | A |
| 79 | 报价版本已被取代 | M |
| 80 | 独立性检查已有结论 | M |
| 81 | 客户活动已计划 | A |
| 82 | 客户活动已变更 | A |
| 83 | 客户活动已完成 | A |
| 84 | 客户活动已取消 | A |
| 85 | 项目已建立 | A |
| 86 | 项目已开始执行 | M |
| 87 | 项目已暂停 | A |
| 88 | 项目已恢复 | M |
| 89 | 项目已终止 | A |
| 90 | 项目已取消 | A |
| 91 | 项目已结项 | M |
| 92 | 项目已归档 | A |
| 93 | 阶段已开始 | A |
| 94 | 阶段已完成 | A |
| 95 | 工作包已锁定 | A |
| 96 | 工作包已作废 | A |
| 97 | 交付计划已批准 | A |
| 98 | 项目预算已锁定 | A |
| 99 | 完工估算已批准 | A |
| 100 | 资源需求已提出 | M |
| 101 | 资源分配已确认 | A |
| 102 | 交付物已签发 | A |
| 103 | 交付物已送达 | A |
| 104 | 交付物已认可 | A |
| 105 | AcceptanceReturned | A |
| 106 | 无异议已声明 | A |
| 107 | 交付物已封存 | M |
| 108 | 返工已认定 | A |
| 109 | 返工认定已撤销 | A |
| 110 | 设计变更已发出 | A |
| 111 | 延期已批准 | A |
| 112 | 延期已拒绝 | A |
| 113 | 延期视为批准 | M |
| 114 | 发包人资料已确认 | A |
| 115 | 发包人资料已退回 | M |
| 116 | 项目复盘已发布 | A |
| 117 | 风险与问题已登记 | M |
| 118 | 风险与问题已关闭 | M |
| 119 | 风险与问题已接受 | M |
| 120 | 满意度调查已评分 | A |
| 121 | 满意度调查已跟进 | A |
| 122 | TimesheetSubmitted | A |
| 123 | TimesheetApproved | A |
| 124 | TimesheetReturned | A |
| 125 | TimesheetRejected | A |
| 126 | TimesheetApprovalWithdrawn | A |
| 127 | 工时已认定 | A |
| 128 | 工时已过账 | A |

## 附录 D：复算输入指纹与一次性工具

SHA-256 针对本次已读取的输入字节；不是审批或法律依据。正式/提议以本稿引用的文件与 id 回查；不以指纹解析概念名。

| 输入 | SHA-256 |
| --- | --- |
| .codex-inputs/revenue-commands.yaml | 29cc5587e7401405a0d9815d4e83ed606b42317f885959d726a8960bfc817f92 |
| docs/glossary/terms.yaml | c53845dbffa130b0bc9de2c5ae49891a848e2277c3b671ba82c7b197ba34b37d |
| backend/glossary/terms.yaml | fba3567021b226f30a3acdc12262903e9242d6fbe868a2b98cd5500937b12448 |
| backend/glossary/proposed/architecture-acl.yaml | 0f5839cd8ad7b50cfc774667a060781300ad200c27df90e8d8819c7c4aeb3a27 |
| backend/glossary/proposed/code-aggregates-events.yaml | 98b5e24d4ffa0970da53fa741769e5936f261fd2f84e83c147044402ea6abc62 |
| backend/glossary/proposed/contract-evolution.yaml | 6b353ff03e05abfe1a5acc3ba5145405122cb4ab801c351ead162de396eb7a93 |
| backend/glossary/proposed/cost-timesheet.yaml | 3390ccd9cb31d8f47ced391cbba640a0381d67b9488c8c088e6f46bd7b89bffb |
| backend/glossary/proposed/data-classification.yaml | 52e09d77ea541121006d76b647d01a4f59e82a0b1e5100e591abc76a43f5b955 |
| backend/glossary/proposed/data-migration.yaml | 91e3ff5cb8f55c68c4bff42d3d58b744c11704e4614c8e4fd9d61e9d8cad6c92 |
| backend/glossary/proposed/enterprise-aggregates.yaml | bfdf4d6d1d00af2213ab5b438e198de6793fc3f55cf681f6cca1b661760737eb |
| backend/glossary/proposed/finance-decisions.yaml | c178fca6ef94d9e20ec08223d5cb8c8cca40831216b0b240754091b7d0baab88 |
| backend/glossary/proposed/fix-b.yaml | 9012bba8d8a52e58a923f98c61ceb23e0c749029f570e695042abaaf1a8c1a31 |
| backend/glossary/proposed/fix-b1-identity.yaml | 09735f8bf8410468611cd41ca2a316e3ab59143db9172f85c46a6586c54d9376 |
| backend/glossary/proposed/fix-b2-data.yaml | ce8db5a94e03a8fa857b00b319290b74633a05184095c6dbf9b7b92503bc04aa |
| backend/glossary/proposed/fix-b2b-events.yaml | ad5556aebecb24a1676a282fa3267d56f12c2aaa40bcc99c06ff24aa37fc61cf |
| backend/glossary/proposed/fix-c.yaml | 583fbf55e1f1013e425f5c5dc1767df32933301719ef62fb1f99d3d65a38feb4 |
| backend/glossary/proposed/fix-time-zone.yaml | 4f1488f6b82c93b0eb176548e556170d65bc445afd2bf5751fae31c9eb34e70e |
| backend/glossary/proposed/frontend-ui.yaml | 55f99c95c8dc32e6588413847528e5a252918032050acf7231a04fb5db6a7bd2 |
| backend/glossary/proposed/kernel.yaml | e1af6c32e2c5eca69a78c11527bcbc07fdb752db8778401f9f23ac5c886a4e8e |
| backend/glossary/proposed/list-paging.yaml | 54e9139b04483cea4a0be2cbf51f93f1479ed97504b12f98fbd450fb9572d3a5 |
| backend/glossary/proposed/organization_attendance.yaml | 541285e91f35f9abb36c13192bf7f4730409f631242e277a970f797dde23efb9 |
| backend/glossary/proposed/organization_workforce.yaml | c3c1b69ac26c1b6854b8f66c395679d3dec5ea05fa1033f3107c33b10f64c4f8 |
| backend/glossary/proposed/platform-approval.yaml | 51533faeb99ce2a3df820f22bfc076c3cee251742b814b7bdf53ccd8d656c7c3 |
| backend/glossary/proposed/platform-identity.yaml | 1cd0fad26bd2c2b85ac1582b2c4794e8b149e44ad626460ec54faca282aeefab |
| backend/glossary/proposed/platform-integration.yaml | f0d87f0a8142a78ca0723cb97adff257d6386733fd415d71b65a3b35f1c117d0 |
| backend/glossary/proposed/platform-numbering.yaml | c70b8f5ff19aa11f21add361645f2bc226278e03509ca282033d13d04bbc8df1 |
| backend/glossary/proposed/platform-web.yaml | 1ed757a29b400f1e9fe25122743388622b00b6bc7844e15de1fca57eb4683e4c |
| backend/glossary/proposed/round3-pending-renames.yaml | 776e349088dd84712cb4eda4958d9f7254a76f0b99a96f593aa664af230900f5 |
| backend/glossary/proposed/step3-objects.yaml | 55231fdbd534d160f2600f409ef91e5d3c8bdc7e63dd6901ff6a5e1549f9d415 |

复算当前工具与快照（仅本机临时文件）：`python3 /tmp/n4-build.py`。脚本和 JSON 不入仓库且可能随临时目录清理；独立复核可直接按附录 A/B/C 的 A 行数重算本稿统计，规则见 §5。脚本未运行正式迁移，重跑会重建本稿，因此验证输出在最终重跑后附加。

## 附录 E：原 N4 卡检查记录（N4P 修改前的历史记录）

### 已验证

- 本卡全量覆盖与格式复核通过，包含输入快照逐行核对、本地证据链接、表格列数与统计复算；详细结果如下。
- `scripts/precheck.sh` 退出码 0，最后一行 `SUMMARY` 为 `4 PASS, 0 FAIL, 12 SKIP`。日志中的12个 changed files 含派工输入和本分支既有差异，不代表本卡改了12个文件；本卡仅新增本文件。
- gitleaks 8.30.1 的全历史扫描通过；另对本卡未提交 Markdown 单独扫描通过，避免把历史扫描当成未提交文件检查。

```text
PASS 256个目录行序及原命令名完整
PASS 172个命令/事件概念与60个状态概念完整
PASS 128个emits原名完整
PASS 157行动词各有两例
PASS 20个槽位示例
PASS 15个裁定问题含推荐理由
PASS 成功率可由附录重算
PASS Markdown表格每行列数一致
PASS 本地证据链接均存在
PASS 正式/提议输入与快照id及kind逐行相等
PASS 收入目录与快照名称逐行相等
```

### 未运行与每项 SKIP 的理由

| SKIP 项 | 理由与本卡处理 |
| --- | --- |
| generated docs / build_all --check | 脚本遇未提交文档而跳过；用户禁止提交，保持未提交状态，不擅自提交或生成卡外文件 |
| migrations immutable | 未改迁移、开放标记或 Flyway 配置 |
| system actors append-only | 未改系统执行者清单与其脚本 |
| acceptance guard | 未改验收测试；卡上未指定验收测试，不宣称验收转绿 |
| database scripts aligned | 未改数据库脚本或迁移 |
| docs read by builds are registered | 未改 backend、frontend 或脚本 |
| assertion drift | 未改测试文件，未修改任何断言 |
| glossary check:copy / test:scripts / term tests | terms.yaml、bcs.yaml、backend/glossary 均未改；本卡不切换词表结构 |
| backend guard tests | 未改守护测试或架构清单 |
| backend tests | 默认不执行；本卡没有实现代码，禁止起容器，未调用 --run-backend |
| frontend verify | 当前 worktree 未装前端依赖；本卡未改前端，未安装依赖、未联网 |
| SQL Server integration tests | 未改相关集成测试所覆盖内容；未起容器 |

未运行：业务语义验收、生产状态机/授权验证、N5 名词登记、N6 结构迁移、新旧指称往返比对、N7 改名、N8 守护实现；这些不属于本卡初稿交付。会计用语的财务确认、Q01–Q15 用户裁定均未代办或默认批准。

### precheck.sh 完整输出

```text
precheck: base origin/main (b9af6d11), HEAD b99f7aa9, 12 changed files (8 uncommitted)

SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2680 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
SKIP  frontend verify                        前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装（本次未改 frontend/，CI 仍会跑）
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 4 PASS, 0 FAIL, 12 SKIP (base origin/main) — ready
```

## NM2 会计动词定稿

2026-10-17：用户提交 `NM2-20261017-105418-xsj`（[答复](../../decisions/decision-pages/answers/nm2.json)，[题面](../../decisions/decision-pages/sheets/nm2.json)，[原话](../../decisions/2026-10-17-105923-one-glossary-NM2-决策页提交-NM2-20.md)），NM2-20 选“本批明确免转，由我直接确定”，本卡用户指令再次明确会计用语免转财务。本节取代本稿相关条目“待财务确认”的中文状态；其余历史草稿保留。

| 题号 | N4 片段 | 中文定稿 | 原形／过去分词／snake（沿用 N4） | 定稿含义与写回 |
| --- | --- | --- | --- | --- |
| NM2-13 | V037 | 冲销 | `Reverse` / `Reversed` / `reverse` | 更正已入账事实时保留原记录，追加反向记录。 `reversing_entry`、`reverse` |
| NM2-14 | V073 | 冲抵 | `Offset` / `Offset` / `offset` | 预付款抵付应付款等抵付场景；区别于更正错误记录。 决策源 verbs；不新增无对象命令 |
| NM2-15 | V074 | 冲减 | `Reduce` / `Reduced` / `reduce` | 价款调整等导致的收入减少；信用损失单独区分。 `revenue_reduction` |
| NM2-16 | V112 | 抵销 | `Eliminate` / `Eliminated` / `eliminate` | 按规定编制报表时相互抵去内部交易等金额；不批准任意净额列示。 `intercompany_elimination` |
| NM2-17 | V070 | 开具红字发票 | `Issue` / `Issued` / `issue` | 发票更正使用完整动作名；红冲发票仅保留为禁用检索指称。 决策源 verbs；不新增无对象命令 |
| NM2-18 | V068 | 恢复额度 | `Restore` / `Restored` / `restore` | 额度恢复可用；旧候选回冲不作为首选，也不表示凭证冲销。 决策源 verbs；不新增无对象命令 |
| NM2-19 | V137 | 记账 | `Book` / `Booked` / `book` | 一般将业务事项记入账簿，与正式过账分开。 决策源 verbs；不新增无对象命令 |
| NM2-19 | V153 | 过账 | `Post` / `Posted` / `post` | 通过期间与平衡检查后的正式处理环节；不重定确认入账或流程边界。 决策源 verbs；不新增无对象命令 |

中文及选项业务含义由用户定稿；英文片段沿用原表，不把本次中文认可扩大为英文命令、权限映射或槽位实现批准。“开具红字发票”是完整中文动作名，Issue 只是动词片段，原表未定的红字发票对象英文仍未补造。

V068 的“回冲”降为旧候选检索写法，首选为“恢复额度”；发票场景的“红冲／红冲发票”保持禁用旧指称。记账、过账、已单独决定的确认入账三者分立，不用一次中文改名重定流程边界。NM1 的收款核销与核销无法收回款项保留两种含义，NM2-20 只明确其免转路径，不在 N9 重做 NM1 的全批词条。

机器可读定稿在 [decisions-nm.yaml](../decisions-nm.yaml) 的 verbs，正式词条变更及未登记原因见 [N9_WRITEBACK.md](n9-writeback.md)。当前 DDD kind 没有 verb；对尚缺对象、BC 或双语名称的动作片段不伪造为正式命令，不新增 standard_actions，不据此新增业务功能。

N9R 第 1 轮（2026-10-17）：本节 8 个受控动作及答复原文保持不变；对应既有词条的 `confirmed` 改为保留原确认来源后追加本批来源，非答复字段漂移即失败。返工及验证证据见 [N9_WRITEBACK.md](n9-writeback.md)。
