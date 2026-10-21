# C8-people-VS14 评审包

Codex 起草（gpt-6.1-sol xhigh，约 11 分钟），one-arch-people 审稿通过，返工 0 轮（2026-10-17）。基线 `0d220033fc9abf94ec935837f74f49d0b1b20c43`，分支 `claude/one-arch-people-closeout`。本卡文档修改与限定检查已做，未提交、未推送。跨卡的状态与阶段条件差异保留并移交，不称业务语义已全部闭合。

## 依据与边界

- 权威快照 `2acfd46e`：`value-streams.v2.yaml` 的 VS-14 全段（CAPINFO C13/C14），`closeout/process-catalog.yaml` 七条 stages；优先于背景提议中尚未采纳的片段。
- 同快照的 `STEP1_INTERNAL_SERVICES_VS.md`、`ENTERPRISE_AGGREGATES_DRAFT.md` AGG-113–116、121、`CONVENTIONS.md` §5、`stage_align_check.py` 与阶段报告。VS-14.1 的 MS-24 是触发事实，未造对象状态；批准、办理可跳过，确认办结不可整体略掉。
- 本 worktree `CLAUDE.md`、编码与命名规范、SESSION_PLAYBOOK §5/§6.1 已读；本组 PRC-107 的负担字段及非中断「已提醒」、PRC-181 的 stages_note 作为写法样例。词表按 `docs/modernization/glossary/terms.yaml` 核对；原存未登记词保留在 b31.notes.md 新用语，本次无新对象、状态、命令或业务术语。
- PRC-140、141 不属 VS-14；不改其他分片、ADR、SSOT、数据模型、验收测试和 bpmn 样板。未运行网络、容器或安装依赖。

## 按段列流程与步骤

| 段 | 流程与步骤 | 结果与路径 |
|---|---|---|
| VS-14.1 提交申请 | PRC-139/142/143/144 各步 1 | 正式记录四类申请；常规进入批准，规则直通的差异见 A1/A2 |
| VS-14.2 批准申请 | PRC-139 步 2（驳回步 3）；PRC-142 步 2–3（驳回步 7）；PRC-143 步 2（驳回步 4）；PRC-144 步 2（驳回步 4） | IT 需求的批准和受理合起来到处理中；其余分别已批准／已通过。无需审批 IT 类别直接受理，标准配置内领用自动通过；来源已批准用印、福利活动沿原规则直通 |
| VS-14.3 办理事项 | PRC-139 步 4；PRC-142 步 4；PRC-143 步 3；PRC-155 步 1–2；PRC-157 步 1–2 | 用印、解决、交付、交接、维修登记。调拨和维修是已使用资产重入；员工活动跳过本段 |
| VS-14.4 确认办结 | PRC-139 步 5；PRC-142 步 5–7；PRC-144 步 3；PRC-156 步 1；PRC-157 步 2 | 用印规则关闭、IT 确认关闭／异常退回、活动完成、归还、当次维修登记；提醒分支不算服务办结 |

## 逐流程进出状态对照

「状态机」取 `parts/b31.state-machines.yaml`；合并产物中 AGG-115 三台子机加 `use_request:`、`transfer_request:`、`use_period:` 前缀，前缀不是新状态。下表「符合」仅指所列路径；例外逐项见交办表。

| 流程／阶段 | VS-14 进入 → 退出 | 本组承载与状态机证据 | 对照 |
|---|---|---|---|
| PRC-139／.1 | MS-24 事项触发 → OBJ-113 审批中 | 步 1 提交用印申请：草稿 → 审批中；有来源审批则草稿 → 已批准 | 无来源符合，直通 A1 |
| PRC-139／.2 | 审批中 → 已批准 | 步 2 批准用印申请；驳回步 3 到已驳回，撤回边到已撤回 | 正常符合；异常／重提 A3 |
| PRC-139／.3 | 已批准 → 已用印 | 步 4 登记用印；后续分次登记为已用印自环 | 符合 |
| PRC-139／.4 | 已用印 → 已关闭 | 步 5 关闭用印申请；份数到齐或经办人确认放弃剩余份数 | 符合，不另加人工关闭 |
| PRC-142／.1 | MS-24 → OBJ-114 已提交 | 步 1：常规草稿 → 已提交；需审批草稿 → 审批中 | 常规符合，顺序 A2 |
| PRC-142／.2 | 审批中（需审批类别）→ 处理中 | 步 2 审批中 → 已提交；步 3 已提交 → 处理中；常规跳过步 2 | 两步合起来符合；已提交不是处理中同义词 |
| PRC-142／.3 | 处理中 → 已解决 | 步 3 受理后先分流：不成立转步 6 由处理中到已退回，不执行解决、不发 IT 需求已解决；成立才到步 4 解决 IT 需求 | 符合（A3 已按 ARCH_VS14 改，2026-10-17） |
| PRC-142／.4 | 已解决 → 已关闭／已退回 | 步 5 已解决 → 已关闭，未解决回步 3 重开；步 6 处理中 → 已退回，步 7 审批中 → 已退回 | 关闭符合，退回入口 A3；期限 A7/A8 |
| PRC-143／.1 | MS-24 → OBJ-115 审批中／已通过（AGG-115） | 步 1 申请领用资产，超配置到审批中、标准配置内到已通过 | 符合；自动通过已存在 |
| PRC-143／.2 | 审批中 → 已通过 | 步 2 批准领用；步 4 驳回到已驳回，撤回到已撤回；标准配置内跳过 | 正常符合；异常 A3 |
| PRC-143／.3 | 已通过 → 已领用 | 步 3 登记领用，申请到已领用，新使用期间为在用 | 符合；本流程不等归还 |
| PRC-144／.1 | MS-24 → OBJ-116 审批中 | 步 1 需审批类型到审批中；福利型到已批准 | 需审批符合，直通 A1 |
| PRC-144／.2 | 审批中 → 已批准 | 步 2 批准员工活动申请；步 4 驳回到已驳回；福利型跳过 | 正常符合；异常 A3 |
| PRC-144／.4 | 已批准 → 已完成 | 步 3 完成员工活动；活动举办无独立状态，跳 skippable 的 .3 | 符合 |
| PRC-155／.3 | AGG-115 在用 → 已调拨 | 步 1 申请调拨资产到已申请；步 2 调拨申请到已调拨，旧期间到已结束、新期间为在用 | 符合；仅重入办理。无使用人转 PRC-143 为已转领用，撤销为已撤销 |
| PRC-156／.4 | AGG-115 已领用／已调拨 → 已归还 | 步 1 归还资产：在用 → 已结束，转换 process_outcome 已归还、emits 资产已归还 | 结局一致，状态字面不一致 A4；不挂 VS-13.6 |
| PRC-157／.3 | AGG-115 已通过／在用／已报修 → AGG-121 已维修登记 | 步 1 维修单草稿 → 已报修；步 2 已报修 → 已维修登记，使用记录仍在用 | 退出符合；已报修的聚合限定 A5 |
| PRC-157／.4 | AGG-121 已维修登记 → 已维修登记 | 步 2 产生登记结果即确认本次维修服务，同一事实承载两段，不另造状态转换 | 符合阶段同态写法；已修复／无法修复仅为结果属性 |

没有把不同概念作同义改名：审批中、已提交、处理中语义不同；已结束同时用于调拨和归还；已报修的差异是聚合归属。故本卡状态机改字面 **0 项**，疑项交 one-architecture，不在图或步骤中伪造状态。

## 逐流程改了什么

七条 stages 由空改为目录指定值，删除 support，逐段写专属 stages_note；25 步的命令、顺序和判断条件保持原值，补明状态事实。共同补 frequency_ref、20 个人工步骤 effort、12 处 wait、43 个出口 p；每处数值均为经验估值，每个判断概率合计 1。活动规模未知的 effort/wait、归还混合入口的 wait 写 null。所有频次为待取证，OA 模板累计不换算成年频次；来源线索在各流程 frequency_ref。

| 流程 | stages | 额外对齐 |
|---|---|---|
| PRC-139 | VS-14.1–4 | 来源审批直通补给印章保管人的待办；说明多次用印到已用印、规则关闭到已关闭；保留 AR-16 pending |
| PRC-142 | VS-14.1–4 | 明确无需审批类别直接受理及待办、批准后待受理与处理中区别、重开路径；非中断超时结局补已提醒，covers 用 P2D/P5D 精确覆盖 |
| PRC-143 | VS-14.1–3 | 明确标准配置内自动通过到已通过，通知管理员交付；已领用结束当前流程，归还另重入 |
| PRC-144 | VS-14.1、14.2、14.4 | 福利型自动通过后通知行政；跳办理段理由；活动完成负担未知，留空待取证 |
| PRC-155 | VS-14.3 | 说明在用资产重入；换使用人不等于服务使用结束；无使用人转领用 |
| PRC-156 | VS-14.4 | 保留「离职触发的归还待办不作 VS-13.6 退出条件」；说明已结束与已归还差异；补提醒分支结局与 P7D 精确覆盖 |
| PRC-157 | VS-14.3、14.4 | 说明独立维修聚合与当次服务确认；维修结果是属性，不将已修复或无法修复造为状态 |

没有缺少上述规则直通步骤，故无新增业务步骤或触发器。已补四类规则直通的通知属性；没有实际发送消息。涉及定时的现有期限参数仍是提议，登记平台调度与幂等口径交 A7，不新增周年自动生成触发。

## 已验证：改前 → 改后

scratch：`/private/tmp/c8-people-vs14-psnbp6to`。先用 `git archive 2acfd46e docs/modernization backend/architecture` 解出规范与脚本；before 覆盖 `git archive HEAD` 中的整个 people，after 覆盖本 worktree 整个 people。两次目录与脚本同版，没有写主仓库 `.git`。所有检查在各 scratch 的 `docs/modernization/architecture/proposals/closeout` 执行，解释器固定 `/Users/yeliang/.pyenv/versions/3.12.13/bin/python3 -I`。

| 命令／口径 | 改前 | 改后 | 结论与边界 |
|---|---|---|---|
| closeout_check.py --local --qc --all | people 缺口 0；判断 152（缺0）、超时18（缺0）、结局119（缺0）；无着落用语0 | 缺口0；判断152（缺0）、超时18（缺0）、结局121（缺0）；无着落用语0 | PASS；两条结局增加为提醒。启发式覆盖，不是业务执行测试 |
| converge_check.py --local --commands people/commands.yaml --states people/state-machines.yaml --all | 孤儿0、骨架未入目录1、无转换0、聚合无状态机1 | 0、1、0、1 | PARTIAL；均为原存 AGG-52／提交应付薪酬，卡外未改；B-31 新增缺口0 |
| bpmn_gen.py people/processes.people.yaml --out scratch（内置 bpmn_check） | 30/30 通过；失败0、警告0 | 30/30 通过；失败0、警告0 | PASS（图结构）；七流程7/7通过。生成图不代替期限业务语义验证 |
| stage_align_check.py --local --all：七流程目录标了但判据不支持（未说明） | 0 | 0 | PASS；全目录未说明同为0，不宣称本卡消除了原有未说明项 |
| 同上：已说明／目录缺段／待人工／参与者不在该段（全目录） | 4／7／138／1 | 4／7／138／1 | 未变化；其他组未加载，不能解释为全企业已闭合 |
| 同上：退出未匹配／进入无产生方／近似退出／近似进入 | 22／16／17／17 | 22／16／17／17 | 未变化；仅 people 47/190 骨架可读 |
| 同上：骨架阶段不一致（脚本） | 0 | 0 | 脚本只比较非空骨架阶段，改前空列表未被报出 |
| 独立逐条比对七流程 stages 与目录 | 不一致7；support7；专属 stages_note 0 | 不一致0；support0；专属 stages_note 7 | PASS；纠正脚本对空 stages 的盲区 |
| seam_check.py --local --all | 调用边缺失2、方向反0、方式不符0；事件对接0；跨组聚合无目录1；环0；孤儿事件36 | 同左 | PARTIAL；缺其他两组数据，未跨范围补边，见 A9 |
| name_id_check.py --local | 重号／删号／复用／去向不齐 0/0/0/0；其他提示 34/14/1/18/88/0/34/0/17/0 | 同左 | 编号项 PASS；名称提示保留，未获授权改流程名 |
| 场景 covers 与 decision.when、timeout.after、outcomes 精确对比（补充自查） | 超时长说明3处不是字段字面；提醒结局2处未登记 | 非字面0、漏覆盖0 | PASS；43出口、3超时、所有结局均逐字出现 |
| 生成一致性、卡外语义及 diff 空白 | 原工作区干净 | 固定 HEAD 底稿可重建；其他流程语义不变；git diff --check 通过。重复原地重建文本多1空行（A10） | PARTIAL；语义 PASS，文本幂等 FAIL |
| scripts/precheck.sh HEAD | 未运行（不属于卡第7节的改前六项） | 2 PASS、0 FAIL、13 SKIP，exit 0 | PASS；基线 HEAD 用于隔离本卡未提交的文档差异；每项 SKIP 理由见下 |

name_id 其他提示按脚本原顺序：流程名超12字、条件开头、动作超两个、含顿号、缺主对象名、阶段名超6字、价值流／阶段动词、能力名、活动式对象名、号段空缺。四个固定标识项和全部提示数字均未改变。

六项命令改前、改后退出码均为 0；其中 closeout/converge/stage/seam/name 以报告为主，不能只凭退出码称全通过。BPMN 只有 business 流程生成，共30张，不是对全部47个单元执行运行时验收。stage 对目录和状态机作推断，不能穷举每条分支；七流程仍有「仅近似支持、目录未标」的通用状态词噪声，未据此加挂其他价值流。

原始输出：scratch `logs/before-*.py.log`、`logs/after-*.py.log`，退出码与完整参数在 `before-results.json` / `after-results.json`；完整阶段和接缝报告在 before/after 的 closeout 目录；BPMN 放 before-bpmn/after-bpmn。未覆盖加载的 revenue/finance 各骨架、命令、状态机提示均保留（stage 6条、seam 6条数据提示），未以空白当通过。

## 待用户定（均为提议，归并 b31.notes.md B31-1–10）

| 项／流程 | 选项 | 推荐及理由 |
|---|---|---|
| B31-1 用印总经理加审范围／139 | A 授权委托书及无来源的公章、法定代表人名章；B 扩大到合同专用章、财务章；C 按制度另列范围 | 提议 A 为讨论起点，由用户确认正式制度；AR-16 已给号，不表示 PB31-01 已答复 |
| B31-2 IT 时限／142 | A 沿用受理2个工作日、解决后5日代关；B 改期限；C 不设 | 提议 A，先定日历与工作日算法及超时路由，再看上线数据；当前 P2D 不等于两个工作日 |
| B31-3 IT 受控类别／142 | A 权限提升审批、常规需求直接受理，软件采购另走采购；B 信息部门另列类别 | 提议 A，避免采购重复审批；类别表及采购流程号待信息部门／采购组给出 |
| B31-4 历史卡片对应／143、155–157 | A 按资产编号映射，缺项清单人工补；B 未关联卡片只读迁入；C 只迁能对应者 | 提议 A；先取证编号字段，B 改卡片必填约束，C 不符合全部迁入 |
| B31-5 岗位标准配置／143 | A 建配置清单，标准内自动通过、超配审批；B 全部只登记；C 全部审批 | 提议 A，符合规则可判的不设人审；配置归属和清单由信息部门确认 |
| B31-6 跨组织调拨／155 | A 只通知两侧负责人；B 调入方负责人批准 | 提议 A，B-31 只换使用人；B 会增加批准步骤与阶段承载，需另卡 |
| B31-7 离职归还提醒／156 | A 沿用离职后7日；B 改期限；C 不设 | 提议 A；仅提醒，不把归还作为离职退出条件，在职换机提醒另定 |
| B31-8 活动审批类型／144 | A 周年贺卡只登记，节日／创新发布审批；B 全审批；C 全只登记 | 提议 A；费用阈值由财务定，费用报销另走自身流程 |
| B31-9 停用感恩节在途／144 | A 按 U-4 审结或撤回后迁历史；B 全迁草稿 | 提议 A，避免停用类型在新系统恢复为待办；75单为原盘点数字，未在线刷新 |
| B31-10 周年贺卡触发／144 | A 保留员工申请；B 按入职周年自动生成 | 提议 A；B 需 B-13／调度触发设计与授权，未当作本卡必须新增步骤 |

## 交 one-architecture（保留缺口，不自行裁定）

| 编号 | 差异与证据 | 建议处理／边界 |
|---|---|---|
| A1 | VS-14.1 出口用印、活动仅写审批中，本组及既定规则有「来源审批直通已批准」「福利活动直接已批准」 | 提议在阶段出口明确规则直通的已批准分支；不能把已批准重命名审批中，也不能为凑出口增加人审。SSOT 未改 |
| A2 | VS-14.1 的 IT 出口为已提交，聚合稿顺序也是已提交 → 审批中；本组提交命令对受控类别直接草稿 → 审批中，已提交又用于批准后待受理 | 提议由架构确定「已提交」是提交事实还是排队态，以及需审批分支是否应显式经过已提交；不是同义改字面。VS-14.2 的处理中由步2+3共同产生已写清 |
| A3 | VS-14.4 进入仅已解决，IT 的主动退回来自处理中、审批驳回来自审批中；用印、领用、活动已驳回／已撤回均为本组终态，但 VS-14 notes 说驳回可重提 | 提议说明退回可提前结束，不要求先解决；区分原单重提和另建申请。另需核对步4解决之后转步6退回与「退回仅允许处理中」的守卫衔接。本组未新增重提命令或伪造状态，其他异常结局保持 |
| A4 | VS-14.4、ends_in 用 AGG-115 已归还；本组及聚合稿使用期间在用 → 已结束，归还转换带 process_outcome 已归还；调拨也结束旧期间 | 提议确定按「归还」命令的已结束记录推导已归还，或正式区分归还终态；本次未全局替换已结束，不能断言二者同义 |
| A5 | VS-14.3 进入叶把「已通过／在用／已报修」都限定 AGG-115；AGG-121 和本组明确已报修属于独立维修单，不能放回使用记录 | 提议拆出 OBJ-115／AGG-121 已报修进入条件，保留 AGG-115 在用的资产重入；出口和 .4 已正确限定 AGG-121，无需新增状态 |
| A6 | VS-14 目前能力 CAP-054、CAP-056、CAP-093 沿父链映射，背景稿注明行政能力拆分和名称未定；本组8个〔缺〕命令尚未回补 | 能力交架构／词表管家；缺命令为撤回用印申请、驳回 IT 需求、驳回领用、申请调拨资产、撤回 IT 需求、撤回领用、撤销调拨申请、撤回员工活动申请，沿原分片，不另造命令 |
| A7 | PRC-142 原稿称2个工作日而 timeout 为 P2D（日历时长）；PRC-156 同一任务有人工和 Separated 两入口，却统一挂 P7D、说明仅离职后提醒；CONVENTIONS 要 B-31 登记 platform.scheduling、幂等键=聚合ID+到期时点 | 提议经 B31-2/7 确定日历与起算点、人工入口是否提醒，再补平台登记及边（当前聚合稿未列 B-31 → platform.scheduling）；不可把负担估值当制度 |
| A8 | 生成器 bpmn_gen.py wire 的中断超时连回原任务后首个流目标。PRC-142 P5D 的 Flow_22 接「关闭 IT 需求？」网关，仍含无处理结论／未解决分支，不是一条显式代关路径；before/after 均如此 | 图结构校验通过不证明5日必代关。交架构决定生成器表达或另卡补明确系统关闭路径，本卡未改卡外生成器或声称运行时闭环 |
| A9 | 限定快照缺 revenue/finance 本地目录；converge 原存 AGG-52 提交应付薪酬缺命令／状态机，seam 边缺2、跨组无目录1、孤儿36 | 不扩大卡修改范围；由整合会话覆盖三组后复跑，再分派实际缺口。Separated 事件、维修报废和盘点读模型等原接缝保留在 b31.notes.md |
| A10 | rebuild.py 以旧合并文件头部作输入再拼接空行，每次重建 processes.people.yaml 多 1 个空行 | 已修（one-arch-people 审稿时，头部去尾换行后拼接）；连续两次重建逐字节相同，累积的 66 行空行已清除 |

## 改动文件与未运行

手改5份：`parts/b31.processes.yaml`、`parts/b31.scenarios.yaml`、`parts/b31.notes.md`、`STAGE_ALIGN.md`（只追加本卡节）、`REVIEW_VS14.md`（新建）。通过 `parts/rebuild.py` 生成（最终采用 A10 的固定底稿方式）并发生差异3份：`processes.people.yaml`、`scenarios.people.yaml`、`NOTES.md`；未手改生成产物。命令、状态机、RACI、事件分片及其合并产物无变化。重建使用卡指定的 Python 3.12.13（当前子目录默认 python3 没有 PyYAML，未安装依赖）。

未运行：生产频次与计时取证、真实审批和企业微信通知、流程引擎运行时验收、后端／前端／SQL Server 测试（本卡纯文档，禁容器／网络）；三组整合检查未具备完整输入；build_all --check 按 precheck 的未提交保护跳过。A1–A10 与 B31-1–10 仍待相应负责方处理。没有修改或放宽测试断言。

## precheck.sh 输出

基线选 HEAD 以仅检查派工卡本地改动；遵守本卡「不提交」。脚本要求 generated docs 提交后再检查的 SKIP 不以提交绕过。输出如下（每个 SKIP 都附脚本理由）：

```text
precheck: base HEAD (0d220033), HEAD 0d220033, 8 changed files (8 uncommitted)

SKIP  generated docs (build_all --check)     docs/modernization or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
SKIP  release scripts self-test, shellcheck  relevant_change.py: nothing the release scripts or the builds read changed
PASS  gitleaks (full history)                2775 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          relevant_change.py: nothing the builds read changed
SKIP  frontend verify                        relevant_change.py: nothing the builds read changed
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 2 PASS, 0 FAIL, 13 SKIP (base HEAD) — ready
```

## 后续修订

- 2026-10-17 按 one-architecture ARCH_VS14（638afdf0）A3：PRC-142 判断「需求不成立或无法实现」由步 4 解决之后移到步 3 受理之后，退回路径不再经过解决命令与 IT 需求已解决事件；状态机守卫保持处理中 → 已退回，不放宽为已解决可退回；场景 PRC-142-S06 路径同步。

## C9 B31 答复同步

**任务号 ARC-1017-APE-01（旧号 C9-people-B31）；返工 1 轮。原因：上一轮整体 YAML 序列化丢失原注释与版式，按审稿意见以 HEAD 379f2483 为底稿逐处文本修复。** 本节检查已于本轮全部重跑；恢复版式后语义与返工前工作区快照一致，无语义例外。

卡号 C9-people-B31；只改文件、未提交、未推送、未改分支。基线 `379f248396c2cede61c0f385bc1b573a61146339`，分支 `claude/one-arch-people-closeout`，审稿交 one-arch-people。**卡内七流程文档已同步；整体交付仍为 FAIL／未闭合**：聚合依赖环返回 1，不能用 precheck 的 0 FAIL 代替架构通过。本节是现行结果，C8 的待选人审、定时及周年人工入口仅留历史，不再待用户重复答复。

### 依据与改动文件

先读根 CLAUDE.md、编码/命名规范及 SESSION_PLAYBOOK §5、§6.1；权威按 `git show 03adf7eb:<路径>` 读取 ARCH_VS14.md（含 D15 表）、VS-14、七条流程目录、AGG-113–116/121、capabilities.v2.yaml、CONVENTIONS.md；同步清单及制度缺口按本卡 scratchpad 的 ARCH-B31-people.md / ARCH-B31-materials.md；用语核对 terms.yaml，参考 PRC-107 和本文件 C8 评审包。未修改 SSOT、ADR、B-03、其他分片、验收测试或任何仓库脚本；未联网、未起容器、未安装依赖、未写主仓库 `.git`。

改动文件（20 个）：

- `DATA_MODEL.md`
- `NOTES.md`
- `REVIEW_VS14.md`
- `STAGE_ALIGN.md`
- `commands.yaml`
- `data-model.people.yaml`
- `events.yaml`
- `parts/b31.DATA_MODEL.md`
- `parts/b31.commands.yaml`
- `parts/b31.data-model.yaml`
- `parts/b31.events.yaml`
- `parts/b31.notes.md`
- `parts/b31.processes.yaml`
- `parts/b31.raci.yaml`
- `parts/b31.scenarios.yaml`
- `parts/b31.state-machines.yaml`
- `processes.people.yaml`
- `raci.yaml`
- `scenarios.people.yaml`
- `state-machines.yaml`

`parts/rebuild.py` 生成七个合并产物，脚本未改。`data-model.people.yaml` / `DATA_MODEL.md` 为手工合并，本卡只替换 B-31 尾段，其他段逐字不变；既有 13 张表只补列族、D15 版本引用和历史口径，未新造对象、表、状态、术语或 DDL。原有未登记词留在 b31.notes.md 新用语，现行缺命令 5 条（撤回用印申请、驳回 IT 需求、申请调拨资产、撤回 IT 需求、撤销调拨申请），不将脚本通过视为术语批准。

### 返工 1 轮：注释、版式与语义核对

返工前语义快照：`/private/tmp/arc-1017-ape-01-r1/semantic-target/`；底稿：`git show HEAD:<path>`，保存于 `head/`。对底稿执行字段级文本替换、条目插入/删除及注释改写，未对任何分片调用 `yaml.dump` / `yaml.safe_dump`；定位用 `yaml.compose`，语义核对用 `yaml.safe_load`。沿用原缩进、流式字典、引号、折叠段落、行内注释及空行；新增条目按所在列表原写法插入。重建仍调用原封不动的 `parts/rebuild.py`，其既有合并序列化逻辑未改。

逐文件注释数（统计独立注释与行内注释所在行；与本卡七个文件的实际 YAML 注释数一致）：

- `parts/b31.commands.yaml`：HEAD 6 → 返工后 6。
- `parts/b31.data-model.yaml`：HEAD 7 → 返工后 7。
- `parts/b31.events.yaml`：HEAD 3 → 返工后 3。
- `parts/b31.processes.yaml`：HEAD 110 → 返工后 96。
- `parts/b31.raci.yaml`：HEAD 6 → 返工后 5。
- `parts/b31.scenarios.yaml`：HEAD 1 → 返工后 1。
- `parts/b31.state-machines.yaml`：HEAD 3 → 返工后 3。

`comment-inventory.json` 逐项核账通过，未列明的原注释均逐字保留。合计 136 → 121：删除 15 条、改写 12 条；另有 2 条仍适用的注释原样移到新对应位置，不计删除：HEAD `b31.processes.yaml:362`「规则自动通过，不设审批」移至 PRC-143 规则通过出口；`:723`「仅分析」移至 PRC-144 规则登记的同名 emits。B-13 回补新边、既有 platform.approval 边、OA 非节点事实边界、仍适用的〔推〕、经验估值、A3 分流及 PRC-156 非中断提醒说明均原样保留。

删除 / 改写清单（每行一条，行号均指 HEAD 379f2483，非返工后行号）：

- 改写 `b31.commands.yaml:3`「「name_status」非空的是〔缺〕命令：沿用分片提议名的 4 条（撤回用印申请、驳回 IT 需求、驳回领用、申请调拨资产），」 →「「name_status」非空的是〔缺〕命令：沿用分片提议名的 3 条（撤回用印申请、驳回 IT 需求、申请调拨资产），」；原因：B31-5 取消领用驳回现行命令；不删除历史数据。
- 改写 `b31.commands.yaml:4`「以及收敛时新增的 4 条（撤回 IT 需求、撤回领用、撤销调拨申请、撤回员工活动申请：分片只在审批步写了 withdraw 边，状态机需要命令落地）。」 →「以及收敛时新增的 2 条（撤回 IT 需求、撤销调拨申请）；B31-5、8 已撤领用/活动的审批期撤回（ARCH_VS14），历史状态与轮次保留。」；原因：B31-5、8 取消领用与活动审批期撤回，缺命令合计由 8 条减为 5 条。
- 改写 `b31.processes.yaml:2`「字段按 closeout/CONVENTIONS.md（含 §5）；归段按 2acfd46e 的 VS-14 与目录（C8-people-VS14），不沿用 S-31 的空 stages。」 →「字段按 closeout/CONVENTIONS.md（含 §5）；归段按 03adf7eb 的 VS-14 与目录、ARCH_VS14 的 B31 答复同步，不沿用 S-31 的空 stages。」；原因：归段依据更新为本卡指定的 03adf7eb；PRC-143/144 已按答复去掉 VS-14.2。
- 改写 `b31.processes.yaml:6`「用户新原则（修订，2026-10-16）：能由规则和不变量守卫的不设人工审批；保留的人审 approver 一律 cost_object_owner（申请人所在组织单元的负责人），」 →「用户原则：能由规则和不变量守卫的不设人工审批；保留的人审 approver 沿用 cost_object_owner 解析别名，责任按 ARCH_VS14 D15 用印人工批准责任、IT 受控类别与责任表配置，」；原因：ARCH_VS14 D15 要求正式制度决定责任归属，撤销默认申请人部门负责人的假定；只保留既有解析别名。
- 改写 `b31.processes.yaml:7`「加审只引用 APPROVAL_RULES.yaml 中 guard: approval 的规则（本组可命中 AR-01、AR-11、AR-12、AR-16），每个人审写 why_human。」 →「本人及缺位沿 AR-11、AR-12；加审范围须填 ARCH_VS14 D15 用印加审范围、IT 金额适用口径，AR-16 候选不能直接启用；每个人审写 why_human。」；原因：ARCH_VS14 D15 取消活动金额加审，保留两个人审的责任与加审配置前置；候选 AR-16 不直接启用。
- 改写 `b31.processes.yaml:47`「申请人所在组织单元的负责人（cost_object_owner）」 →「ARCH_VS14 D15 用印人工批准责任解析的负责人（cost_object_owner 别名）」；原因：用印责任按正式制度配置，不能固定为申请人部门负责人。
- 改写 `b31.processes.yaml:91`「申请人所在组织单元的负责人」 →「解析别名；责任依据 ARCH_VS14 D15 用印人工批准责任」；原因：同上；AR-11/AR-12 不替代制度责任归属。
- 改写 `b31.processes.yaml:178`「提出人所在组织单元的负责人（仅受控类别表标为需审批的类别）」 →「ARCH_VS14 D15 IT 受控类别与责任表指定的负责人（仅需人审类别）」；原因：IT 负责人按类别和正式制度配置，不默认提出人部门。
- 改写 `b31.processes.yaml:218`「提出人所在组织单元的负责人」 →「解析别名；责任依据 ARCH_VS14 D15 IT 受控类别与责任表」；原因：同上；保留既有解析别名，撤固定负责人假定。
- 删除 `b31.processes.yaml:243`「非中断超时分支结局为已提醒，原任务继续；提醒不改对象状态。」；原因：PRC-142 的 P2D 受理提醒已按 B31-2 撤销，不能残留提醒分支说明。
- 删除 `b31.processes.yaml:244`「〔推〕2 个工作日内受理，逾期提醒信息部门负责人（SH-M1 职能高管），待用户定 2」；原因：B31-2 撤销 P2D 受理提醒，不再征集 2 日参数或登记到期任务。
- 删除 `b31.processes.yaml:268`「〔推〕解决后 5 日未确认自动关闭，待用户定 2」；原因：B31-2 撤销 P5D 系统代关，仅提出人人工确认且满足 INV-120 才关闭。
- 删除 `b31.processes.yaml:331`「申请人所在组织单元的负责人（仅超出岗位标准配置时）」；原因：B31-5 取消全部领用人审及其 SH-M1 批准角色。
- 删除 `b31.processes.yaml:368`「经验估值；null 表示待取证」；原因：PRC-143 原步 2 批准领用已删除，其 wait 经验估值不再适用。
- 删除 `b31.processes.yaml:369`「经验估值；null 表示待取证」；原因：PRC-143 原步 2 批准领用已删除，其 effort 经验估值不再适用。
- 删除 `b31.processes.yaml:374`「申请人所在组织单元的负责人」；原因：PRC-143 原步 2 批准领用及审批人解析绑定已删除。
- 删除 `b31.processes.yaml:398`「仅超出标准配置、经审批的领用」；原因：B31-5 改为规则通过后签收登记，不再以审批通过事件为入口。
- 改写 `b31.processes.yaml:572`「〔推〕离职后 7 日仍未归还提醒原部门负责人，待用户定 7」 →「ARCH_VS14 D15 离职归还提醒：仅离职生效后 7 个日历日，完成取消；起点非任务启动/事件到达」；原因：B31-7 已定期限，删除推断及待选说法，限定离职入口和绝对起算点。
- 删除 `b31.processes.yaml:687`「申请人所在组织单元的负责人（仅受控活动类型表标为需审批的类型）」；原因：B31-8 取消全部活动人审及其 SH-M1 批准角色。
- 删除 `b31.processes.yaml:705`「经验估值；null 表示待取证」；原因：PRC-144 原步 1 改系统自动统一规则登记，原人工 effort 估值已删除。
- 删除 `b31.processes.yaml:721`「经验估值；null 表示待取证」；原因：PRC-144 原步 2 批准活动已删除，其 wait 经验估值不再适用。
- 删除 `b31.processes.yaml:722`「经验估值；null 表示待取证」；原因：PRC-144 原步 2 批准活动已删除，其 effort 经验估值不再适用。
- 删除 `b31.processes.yaml:728`「申请人所在组织单元的负责人」；原因：PRC-144 原步 2 批准活动及审批人解析绑定已删除。
- 删除 `b31.processes.yaml:752`「仅经审批的活动类型」；原因：B31-8 活动登记完成不再以审批通过事件为前提。
- 改写 `b31.raci.yaml:2`「口径：每个「对象×命令」恰一个 A。审批类命令（批准用印申请、批准 IT 需求、批准领用、批准员工活动申请）A = 实例级 cost_object_owner」 →「口径：每个「对象×命令」恰一个 A。现行审批类命令仅批准用印申请、批准 IT 需求，A = 按 ARCH_VS14 D15 正式责任配置解析的实例级 cost_object_owner」；原因：B31-5、8 取消领用/活动人审；剩余人审责任依 D15，不采用固定部门负责人。
- 改写 `b31.raci.yaml:3`「（申请人 / 提出人所在组织单元的负责人；批准用印申请仅无来源单据的申请有人审），与 b31.processes.yaml 骨架 approval.approver 一致，escalation_rules 对应 boundary.delegate=AR-11、escalate=AR-12。」 →「（用印人工批准责任、IT 受控类别与责任表；用印仅无来源单据有人审），与 b31.processes.yaml 的解析别名一致；AR-11、AR-12 只处理本人及缺位，不代替制度责任归属。」；原因：ARCH_VS14 D15 对负责人解析及 AR-11/AR-12 的职责边界已有新依据。
- 删除 `b31.raci.yaml:25`「无来源单据的高风险用印加总经理一级（S-41）；无金额限额」；原因：ARCH_VS14 D15 用印加审范围须按正式制度填定；AR-16 高风险用印加总经理候选及无金额限额不可直接启用。

`format-audit.json`：7/7 YAML 分片、6/6 YAML 重建产物及手工合并 `data-model.people.yaml` 的 `yaml.safe_load` 结果与返工前快照相同（共 14 份，0 例外）；未发现须改变语义目标的问题。1,367 个语义未变子树的原始文本片段逐字相同，版式漂移 0；35 个卡外分片逐字未变。两份手工模型的非 B-31 前段及 NOTES 非 B-31 段逐字未变；手工 YAML 的 B-31 尾段按修复分片合并，Markdown 的 B-31 小节恢复 HEAD 原有二级标题写法，保留本卡语义增补。

`focused-verification.json` 本轮独立复验：步骤 25→21、判断出口 43→47、超时 3→1、场景 47→60，covers 漏项/非字面项均 0→0；命令与 RACI 30→24 且一一对应，状态机 7→7、表 13→13；归段不一致 2→0、stages_note 7→7。双次重建的逐文件 SHA-256 见 `before-rebuild-result.json`、`after-rebuild-result.json`。本轮各主检查退出码及原始日志见本节下方验证说明；依赖环仍 FAIL、对象提示仍 129→131，不以格式返工掩盖未闭合项。

### 逐流程改动

| 流程 | 改前 → 改后及保留边界 |
|---|---|
| PRC-139 | 无来源用印固定部门负责人/AR-16 候选范围 → 用途、法人、归属对象的 D15 正式责任及加审配置；AR-11/12 仅处理本人/缺位，不替代责任归属；来源审批通过且 INV-138 满足仍直通。原单已驳回/已撤回补合法重提转换，新轮重新校验、旧轮次/意见/原编号保留。实际登记补报价版本、用印法人、拟发文件及盖章凭据可核验关系，关闭不抹凭据；INV-118/119/138 保持。B-03 发出前入口及依赖环未闭合 |
| PRC-142 | 受控类别/固定负责人候选 → 逐类 D15 是否人审、理由、负责人、提供/维护方、默认路由、采购交接；IT/采购金额先分承载，IT 自身承载才填两档阈值和币种。撤 P2D/P5D、到期策略及 system 代关权，保留人工关闭、催办、逾期查询；缺结论不关（INV-120）。A3 继续在步 3 受理后、步 4 解决前分流，不先发解决事件。原单重提沿既有提交命令保留旧轮 |
| PRC-143 | .1/.2/.3 → .1/.3；原步 2 批准、4 驳回、审批期撤回及 AR/SH-M1 绑定退出当前目录/状态机/权责/事件。所有领用规则通过草稿直达已通过，规则缺失/不满足拒绝告警，不恢复超配审批；现步 2（原 3）签收登记，申请提前检查、交付时 INV-121 兜底，同事务开在用期间。历史审批状态/轮次/原编号全保留 |
| PRC-155 | 调入方确认待选 → 同一核算主体内跨组织仅通知双方负责人，不新增批准/驳回命令。保留卡片、调入人在职及唯一使用人校验，双方实物交接与结束旧期间/开新期间同事务；旧期间结束不当归还，跨法人转让与账务交固定资产 |
| PRC-156 | 混合入口统一 P7D/期限待定 → 手工归还不套期限，仅已生效 Separated 生成待办；离职生效后 7 个日历日仍未归还非中断提醒原组织单元负责人，固定到期时点非事件到达/流程启动时点；归还完成取消，聚合 ID + 到期时点幂等，晚到/重试不重置。登记 B-31 → platform.scheduling；本次答复作 D15 版本依据，七天不再待选；不订阅离职登记已撤销、不阻塞 VS-13.6 |
| PRC-144 | .1/.2/.4 → .1/.4；全部活动规则登记到已批准（规则结果）后直接登记完成，原步 2/4、审批期撤回、AR-01/02 活动金额加审及 SH-M1 批准权删除。周年入口改系统读 B-13 入职日期/有效人员事实、platform.scheduling 按业务日自动生成，共用登记入口；员工 + 入职日期 + 周年日期 + 类型去重；适用、闰日、重新入职、补跑及维护责任须填。其他活动可人工登记；费用/报销/采购自身控制保留；触发器尚未实现 |
| PRC-157 | 保留独立 AGG-121、已报修 → 已维修登记，无新增审批；已修复/无法修复仅结果属性；卡片/折旧/报废账务归 B-24；全量明细及 ADR-0036 U-4 不因取消其他审批而改写历史或批量改草稿 |

配置一律按业务日期取已批准且生效的唯一版，逐版只追加并含生效日、作者、批准人、确认时间和依据，单据保存所用版本；缺失/重叠/无有效版/无法执行/规则不满足拒绝并告警，不设默认负责人、不补零或不限额。pending 均指 ARCH_VS14.md 的具体 D15 项或材料编号，不再指已答复 B31-n。离职七天与周年自动入口已定，未将尚缺的配置材料当作批准发布。

### 已验证：改前 → 改后

验证根目录 `/private/tmp/arc-1017-ape-01-r1`。两侧均先 `git archive 03adf7eb docs/modernization backend/architecture` 解包：before 覆盖 HEAD 379f2483 的整个 people，after 覆盖最终 worktree 整个 people；不写主仓库 `.git`。检查 cwd 为各侧 `docs/modernization/architecture/proposals/closeout`，全部用 `/Users/yeliang/.pyenv/versions/3.12.13/bin/python3 -I`；命令、退出码和原始日志分别在 `before-results.json` / `after-results.json` 与 `logs/`。复跑入口 `validate.py`、字面覆盖与状态目录检查 `focused.py`、版式审计 `audit.py` 均仅在该 scratch，未改仓库脚本。原始 stage/object/seam 等报告只在 scratch 生成。

| 检查（python3 -I 后的参数） | 改前 → 改后 | 判定 |
|---|---|---|
| closeout_check.py --local --qc --all | people 人审 14→12；用户任务93→90；出口152→158、超时18→16、结局121→116；各项缺口及无着落用语均0→0 | PASS；仅设计 QC，非运行时验收 |
| converge_check.py --local --commands people/commands.yaml --states people/state-machines.yaml --all | 孤儿0→0；骨架未入目录1→1；无转换0→0；聚合无状态机1→1 | PARTIAL；两项均原存 AGG-52/提交应付薪酬，卡外未改 |
| stage_align_check.py --local --all | 全目录未说明归段0→0；七流程未说明0→0；待人工138→137；参与者不在段3→1；其他退出不匹配22/进入无产生方16未变 | 本卡归段 PASS；全局仍 PARTIAL，余项含原存 PRC-83 等 |
| 独立七流程 stages 与 03adf7eb 目录逐条比对 | 不一致2→0（PRC-143/144）；专属 stages_note 7→7 | PASS；不靠脚本对非空阶段的盲区 |
| object_layer_check.py --local | 无主对象0→0；阶段承载对象无主流程0→0；主对象多流程23→23；能力不在段0→0 | 本卡无新增问题；全局 PARTIAL |
| object_check.py --local --states people/state-machines.yaml --all | 问题129→131；无人修改8→9、缺逆向14→15，其余未变；已豁免4→4 | PARTIAL；新增2项为 OBJ-116/AGG-116 员工活动取消审批后的通用生命周期提示，未为消提示造修改/撤回命令或改豁免 |
| seam_check.py --local --all | 边缺失2→2；反向0→0；方式不符0→0；事件对接0→0；跨组聚合无目录1→1；自身环1→1、加骨架后环1→1；孤儿事件37→37；同名不同义候选0→0 | FAIL／未闭合；报告型 exit 0 不能视为无环 |
| ../aggregates_dep_check.py（按脚本用法用默认聚合稿与 backend/architecture/module-dependencies.txt） | exit 1→1；128聚合、30业务BC、158规划边；同一强连通分量含 B-03/04/05/08/10/11/23/24/28/31 | FAIL；基线已成环，不称本卡新增或解决 |
| bpmn_gen.py --self-test | exit 0→0，全部通过 | PASS；03adf7eb 自测已读独立 bpmn_gen_fixtures/timeout.yaml，未复现卡文旧 KeyError，未改生成器/配置/断言 |
| bpmn_gen.py people/processes.people.yaml --out <各侧绝对输出目录> | business图30/30→30/30，失败0→0，警告0→0；七流程7/7→7/7 | PASS（图结构）；非运行时调度语义或业务验收 |
| 连续两次 python3 parts/rebuild.py | HEAD before 七产物字节相同→返工 after 七产物字节相同 | PASS；各文件 SHA-256 见 before-rebuild-result.json / after-rebuild-result.json；旧空行累积问题本版未复现 |
| B-31 covers 字面与索引/命令/RACI/状态自查 | 场景47→60；判断出口43→47、超时3→1；漏覆盖/非字面均0→0；现行命令30→24、权责30→24、状态机7→7、步骤25→21 | PASS；字段逐字匹配、goto合法、概率合计1、命令/RACI一一对应、现行取消审批路径无残留；focused-verification.json |
| 范围及差异 | 返工前20个卡内未提交文件→返工后仍20个；35个非B31分片逐字不变；合并产物1367个语义未变子树版式无漂移；两份模型非B31前段、NOTES非B31段逐字不变；git diff --check 通过 | PASS；未动测试断言 |
| scripts/precheck.sh HEAD | 本轮重跑：2 PASS、0 FAIL、13 SKIP，exit 0 | PASS（本卡文档范围）；逐项理由见下，不代表依赖环通过 |

closeout/stage/seam/object 只加载到 people（47/190 单元），revenue/finance 骨架、命令、状态机在该 archive 中缺失，原始警告未隐藏；不能将这一组合验证称作三组全覆盖。people 全目录 12 人审包含其他分片，本卡 B-31 现行仅 2 人审。BPMN 30 张仅 business 流程，不是 47 个单元的运行验收。双入口提醒在骨架描述中限定离职和绝对生效起点，通用 BPMN 边界图本身不能证明运行时不从任务开始计时。

场景变更依据是本次用户答复：PRC-142-S05/S07 撤旧提醒/代关期望并补人工超期不改状态；PRC-143-S02/S03/S04 撤超配审批/驳回/撤回期望，改规则通过/缺失/拒绝；PRC-144-S02–S06 撤人工审批/加审/撤回旧期望，改统一规则登记及周年规则/幂等。真实用印/IT 人审驳回、INV-118/119/120/121/138 的拒绝守卫保留。未删除或放宽任何仓库验收测试断言；本卡场景是 YAML 设计材料。

### 未闭合项（交 one-architecture）

1. **FAIL：报价实际用印与依赖环**。B-03 发出前必须核验实际用印，本卡的 API 校验方案及 ARCH_VS14 保留环未闭合。03adf7eb 聚合稿已出现事件“用印已办理”及 B-03 本地投影，和同提交 ARCH_VS14/同步卡冲突；实测依赖脚本也把该 B-03→B-31 事件边纳入同一环。须架构统一依赖口径、事件名/载荷/订阅及投影滞后拒绝，再另卡交收入组落实入口，不能标不校验或假称事件化已无环。
2. **PARTIAL：取消审批后的活动生命周期提示**。object_check 新增 OBJ-116 无修改/无逆向各1；现行已按用户撤批准、驳回及审批期撤回，不能为脚本加回。请架构按真实业务决定检查口径或另卡处理，本卡不改检查脚本/OBJECT_EXEMPTIONS/状态目录。
3. **PARTIAL：卡外历史合并底稿**。NOTES.keep.md 的旧聚合稿回补/方法验证仍含旧人审、空 stages、PB31 候选；它不在卡内，未修改；b31.notes.md 已明确这些是历史，C9 现行口径优先，交负责会话清理卡外源。当前命令/权责/状态机已清理六个审批命令，未把历史记录删除。
4. **未实现的后续实施**。D15 正式配置承载及审批责任接入、离职固定到期/取消/幂等调度、周年触发及补跑、B-03 前置核验均无运行时实现。本卡只给骨架/场景/列族；不报已运行。INV、历史全迁及 ADR-0036 U-4 保持；历史资产卡片对应、停用活动在途逐项处置及零份/完全不用细则仍需原负责方处理。
5. 原存 AGG-52 收敛缺口、缺其他两组输入及其他全局对象/阶段/接缝提示保持原样；未越界补齐，不以 exit 0 代替通过。卡文旧 BPMN 自测 KeyError 和 rebuild 空行问题在本次固定版本均未复现，不继续报成现行 FAIL。

仍缺的制度材料：`ARCH-B31-materials` **1、2、3、4（条件适用）、5、6、7、8**。仅引用材料编号，不视为已经收到或正式制度已经生效；九项选择不再重复询问。离职七天不再作为候选值征集，按本次答复登记所需版本元数据。

### 未运行

- 运行时周年触发器、离职提醒/取消/重试、真实消息发送、报价发出前核验及业务验收测试；本卡仅文档，且禁止容器/联网/代码修改。
- backend/frontend/SQL Server 测试及构建：本卡只改文档，precheck 判定不相关；未触发 CI、部署、账号创建或安装。
- build_all --check：precheck 按未提交文档规则 SKIP；卡明确禁止提交，未为通过擅自提交。验收测试目录和断言完全未改。
- 本轮 gitleaks 全历史扫描提交数见下方原始 precheck 输出，无泄露；它不覆盖未提交文档，未把该边界当已完成未提交差异扫描。

### precheck.sh 输出

命令 `scripts/precheck.sh HEAD`；完整输出如下。基线 HEAD 用于隔离本卡未提交文档范围，所有 SKIP 已由脚本逐项给理由；生成文档 SKIP 与本卡“不提交”约束一致。

```text
precheck: base HEAD (379f2483), HEAD 379f2483, 20 changed files (20 uncommitted)

SKIP  generated docs (build_all --check)     docs/modernization or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
SKIP  release scripts self-test, shellcheck  relevant_change.py: nothing the release scripts or the builds read changed
PASS  gitleaks (full history)                2820 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          relevant_change.py: nothing the builds read changed
SKIP  frontend verify                        relevant_change.py: nothing the builds read changed
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 2 PASS, 0 FAIL, 13 SKIP (base HEAD) — ready
```

## ARC-1017-APE-02

本卡文档同步和限定检查已执行，未提交、未推送；交 one-arch-people 审稿。整体架构仍为 PARTIAL，未把报告型检查退出 0 当作全局清零。基线 `6d928dc913c1a5533542b69d5506d619148b4ca0`，分支 `claude/one-arch-people-closeout`，worktree `zealous-shaw-1e4eba`。此节取代 C9 中关于依赖环、旧快照冲突及活动无逆向的现行判断；旧节原文完整保留作历史。 执行期间外部新增提交 `e9836b7d`（GOV-1017-COO-04），仅改人员组画像与 README；本会话未执行提交命令，未覆盖这两份外部改动。本卡19份改动文件在该提交中均无变化，改前证据仍固定初始6d928dc9，最终precheck的HEAD为e9836b7d。

### 改动与依据

- 已先读根 CLAUDE.md、CODING_STANDARD.md、NAMING_STANDARD.md、SESSION_PLAYBOOK §5 / §6.1；权威内容经本地 `git show 33e28272:<路径>` 读取聚合稿 B-31、AGG-113、AGG-116、REF-04 / B-32、`adr/ADR-0057.md`、ARCH_VS14.md 与 OBJECT_EXEMPTIONS.yaml。未联网、未安装依赖、未起容器或建账号。
- PRC-139：将旧 B-03→B-31 环未闭合、旧快照冲突和本地事件投影说法同步为 REF-04（暂名，图节点 B-32，无聚合流程模块）→B-31（api）核验；登记步新增 incoming api 接缝。匹配同一报价版本、法人、拟发文件及实际用印凭据，读不到、未用印、对应不符、缺凭据或核验失败拒绝发出并告警；已用印与关闭后的实际登记可核验。B-03 不依赖或订阅 B-31，状态仍叫已用印，事件同步为用印已办理、仅分析。ADR-0057 仍为提议稿待用户批；模块名称、标识与命名空间待 one-glossary，未改 ADR、SSOT 或收入骨架。
- PRC-144：新增第 3 步取消员工活动登记，已批准及已完成分别到提议终态已取消，两条转换均 `kind: reverse`。登记内容（含参与人）不可修改，记错或活动取消先留原因、人、时间、原状态及审计，再按需另建重登并关联原单；不删登记/完成历史、不设人审、不自动冲销费用等其他单据。原已完成仍是 VS-14.4 正常退出；已取消属 PRC-144 登记生命周期的逆向纠错，不算该段正常退出、不新增阶段。取消重试回放首次结果，已取消不能完成或恢复内容。
- 周年幂等边界补齐：自动重复/补跑仍返回原记录（含已取消），显式重登通过被取消登记引用识别并幂等返回后继登记，重新校验当前有效规则。PRC-144 新增分支后概率整组留 null 待取证，未自定取消发生率或制度值；其他流程经验估值未改。
- 命令目录、RACI、事件说明、场景及两份表级模型同步。取消只记内部结果与审计，无跨 BC 消费需求，不新增取消事件；既有 13 张表仅补取消原因/人/时间及重登原单引用列族。完整检索 terms.yaml 的 663 个 terms，未找到相应命令、状态或 organization.administration 词条；在 b31.notes.md 按领域 / organization.administration 登记 CancelEmployeeActivityRegistration（command）、Cancelled（state）提议，未修改正式词表。
- PRC-139、PRC-142 的 approver 保留 cost_object_owner，并行内注明“解析别名；实际责任人按 ARCH_VS14 D15 配置”；命令及 RACI 的对应 alias / approval 同步注明，未更改审批责任配置或 INV。
- 手工逐处文本替换 9 份 b31 分片；仅通过原 `parts/rebuild.py` 重建 7 份合并产物；手工模型 data-model.people.yaml / DATA_MODEL.md 只同步 B-31 段；本文件只追加本节。共 19 个卡内 tracked 文件。卡片登记文件 ARC-1017-APE-02.md 原字节保留、仍未跟踪；其他分片、脚本、验收目录、SSOT、OBJECT_EXEMPTIONS 均未修改。

### 已验证：改前 → 改后

固定 scratch `/private/tmp/ARC-1017-APE-02-9wvorqw6`。两侧规范与检查器均由 `git archive 33e28272 docs/modernization backend/architecture` 解出；before 覆盖 HEAD 的整个 people，after 覆盖本 worktree 的整个 people；所有指定检查 cwd 为各侧 closeout，解释器固定 `/Users/yeliang/.pyenv/versions/3.12.13/bin/python3 -I`。未写主仓库 `.git`。精确命令、退出码及原始日志见 before-results.json、after-results.json、logs/；复跑入口 validate.py 仅位于 scratch。

- **PASS（设计 QC）** `closeout_check.py --local --qc --all`：exit 0→0；people 人审12→12、用户任务90→91、判断出口158→163、超时16→16、结局116→117；无 why_human、approver 不符、加审引用无效、缺 permission/page、出口/超时/结局无场景、用语无着落均0→0。此为启发式设计检查。
- **PARTIAL（原存缺口）** `converge_check.py --local --commands people/commands.yaml --states people/state-machines.yaml --all`：exit 0→0；用到聚合×命令149→150，孤儿0→0、骨架未入目录1→1、改状态无转换0→0、聚合无状态机1→1；两处原存缺口均 AGG-52 / 提交应付薪酬，本卡新增命令与两条逆向转换匹配。
- **PASS（本卡归段）／PARTIAL（全局）** `stage_align_check.py --local --all`：exit 0→0；覆盖47/190，未说明归段0→0、已说明4→4、待人工137→137、参与者不在段1→1、退出不匹配22→22、进入无产生方16→16，完整输出未变。独立逐条核对七流程 stages 均与33e28272目录一致；取消不被作为VS-14.4正常退出。
- **PASS（OBJ-116逆向）／PARTIAL（默认全局权责）** `object_check.py --local --states people/state-machines.yaml --all`：exit 0→0、问题132→132、已豁免4→4；缺逆向15→14，明细确认OBJ-116缺逆向1→0；无人修改9→9，其中OBJ-116仍1→1，按B31-8交架构加豁免。本卡不改豁免。默认全局权责表缺对象×命令1→2，新增为AGG-116 / 取消员工活动登记；该表不在卡内，未冒改。
- **PASS（本组权责）／PARTIAL（原存全局项）** 另以相同 object_check 参数追加 `--matrix people/raci.yaml --write`：本组矩阵下问题132→131、权责缺项1→1，仅原存AGG-52；取消命令已有唯一A及R，不是本组漏登。默认矩阵与本组矩阵的完整明细分别见 before/after-object-detail.md、before/after-object-people-raci.md；辅助命令及日志见 logs/ 与对象证据清单。
- **PASS（依赖方向与无环）／PARTIAL（原存接缝）** `seam_check.py --local --all`：exit 0→0；交互124→125、通过119→120，REF-04→B-31 api接缝有已登记边；边缺2→2、反向0→0、方式不符0→0、事件对接0→0、跨组聚合无目录1→1、骨架外边1→1、孤儿事件36→36；§4.1自身环及加骨架后环均0→0。
- **PASS（设计查环）** `../aggregates_dep_check.py`：exit 0→0；128聚合、160规划边，含B-32→B-03/B-31/platform.audit三条api边，B-03无B-31依赖；“环：无”与§4.1分层一致均保持。无环来自33e28272的权威结构，不能把上一轮03adf7eb到本次的跨快照变化冒记为本卡消环成果。脚本标题计31个BC节点，B-32实际为流程模块图节点，非新增业务BC。
- **PASS（图结构）** `bpmn_gen.py --self-test`：exit 0→0，全部通过。`bpmn_gen.py people/processes.people.yaml --out <各侧绝对目录>`：exit 0→0、business图30/30→30/30、失败0→0、警告0→0，B-31七图7/7；PRC-144显示取消任务与已取消结局。图目录before-bpmn/、after-bpmn/，非47个单元的运行验收。
- **PASS（生成幂等）** 在 before 的 HEAD people 与本 worktree 分别执行两次 `python3 parts/rebuild.py`，解释器均3.12.13；七产物逐字节相同0差异，逐文件SHA-256及两次输出见 before-rebuild.json、after-rebuild.json。仓库生成器未改。
- **PASS（字面及范围）** audit.py / audit.json：B-31流程7→7、步骤21→22、人审2→2、命令/权责24→25、状态机7→7、表13→13、场景60→70、判断出口47→52、超时1→1、取消逆向0→2；covers漏项/非字面均0→0、索引/goto合法、已估概率合计1或整组null。命令与RACI一一对应，原有INV引用未减少。35个非B31分片（含rebuild.py）逐字不变，两份手工模型非B31前段、NOTES非B31块逐字不变，非目标流程/合并命令/状态/权责/事件语义不变；`git diff --check`通过。

归段、对象、接缝检查只加载people，archive没有revenue/finance骨架、命令及状态机，缺输入警告原样保留；默认权责表虽有586条，但不是同步后本组RACI。报告型exit 0不表示上述全局发现清零，也不证明运行时入口、实际发送或制度配置已生效。

### 注释与版式审计

计数口径：YAML含注释行数（整行及行内，每行至多计1）；Markdown计HTML注释，不把标题计成注释。每份卡内文件改前→改后如下，原注释未减少。

- `DATA_MODEL.md`：3→3。
- `NOTES.md`：0→0。
- `REVIEW_VS14.md`：0→0。
- `commands.yaml`：1→1。
- `data-model.people.yaml`：24→24。
- `events.yaml`：1→1。
- `parts/b31.DATA_MODEL.md`：0→0。
- `parts/b31.commands.yaml`：6→12。
- `parts/b31.data-model.yaml`：7→7。
- `parts/b31.events.yaml`：3→3。
- `parts/b31.notes.md`：0→0。
- `parts/b31.processes.yaml`：96→97。
- `parts/b31.raci.yaml`：5→11。
- `parts/b31.scenarios.yaml`：1→1。
- `parts/b31.state-machines.yaml`：3→3。
- `processes.people.yaml`：338→339。
- `raci.yaml`：1→1。
- `scenarios.people.yaml`：1→1。
- `state-machines.yaml`：1→1。

未改语义的最大YAML子树中，1059个原文本逐字一致；12个只差卡要求的D15别名注释，另2个只差解析器节点末尾对新增列表项缩进的截取边界；排除这两种已解释差异后，非注释版式漂移0。证据见audit.json与raw-changes.json，未整体dump任何分片；允许的合并产物由既有生成器生成。

### 仍未闭合项

1. one-architecture须将本卡新增命令及已取消提议状态回补AGG-116和全局权责表；按B31-8补OBJ-116“无人修改”豁免。本卡本组目录/RACI齐全、缺逆向已消，未改全局表、聚合SSOT或OBJECT_EXEMPTIONS。
2. ADR-0057仍待用户批；REF-04的正式名称、标识与命名空间，以及取消命令/状态提议交one-glossary定名。D15正式制度与配置材料ARCH-B31-materials 1–8仍须原负责方补齐，不重复询问九项已答选择、不猜制度值。
3. PRC-144“已批准/已完成直接进入取消步”的契约已写明；当前通用多入口BPMN生成器把开始事件统一接步1，不能证明已完成后可直接进入步3且不重做登记/完成。图结构通过只证明XML及引用有效；实现入口需另卡验证，未改卡外生成器或新增未约定字段藏入口。
4. 原存AGG-52、其他组缺输入、全局对象/接缝/阶段提示仍在；NOTES.keep.md中的旧聚合回补和方法段只保留历史，本卡现行b31.notes优先。历史资产卡片对应、停用活动在途、零份/完全不用等原存事项仍由原负责方处理，未放宽INV、历史全迁或ADR-0036 U-4。

### 未运行

- 业务运行时/验收测试：REF-04核验、审计失败次序及入口不可绕过、真实报价发出、取消/重登命令、周年触发补跑、离职绝对到期/取消/幂等、真实消息发送均未执行。本卡仅设计文档，禁止代码、容器和联网，未以设计场景当测试通过。
- 后端、前端、SQL Server构建及测试、CI、部署均未运行；没有修改验收目录或删改/放宽任何测试断言。one-arch-people独立审稿尚待负责会话执行。
- build_all --check受precheck的未提交保护SKIP；本卡禁止提交，未为规避保护而提交或改脚本。完整precheck及其余SKIP理由附下；全历史gitleaks不覆盖本卡未提交文档。

### precheck.sh 输出

命令 `scripts/precheck.sh HEAD`，最终实测退出码0；`SUMMARY: 2 PASS, 0 FAIL, 13 SKIP (base HEAD) — ready`。仅以本卡未提交文档为差异范围；每个SKIP理由由脚本逐项输出。先前外层zsh保存退出码时误用只读变量，已改为Python直接执行并记录脚本返回码，未改precheck；证据见precheck-result.json，最终输出如下。

```text
precheck: base HEAD (e9836b7d), HEAD e9836b7d, 20 changed files (20 uncommitted)

SKIP  generated docs (build_all --check)     docs/modernization or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
SKIP  release scripts self-test, shellcheck  relevant_change.py: nothing the release scripts or the builds read changed
PASS  gitleaks (full history)                2847 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          relevant_change.py: nothing the builds read changed
SKIP  frontend verify                        relevant_change.py: nothing the builds read changed
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 2 PASS, 0 FAIL, 13 SKIP (base HEAD) — ready
```

## 后续修订（PRC-144 取消入口）

- 2026-10-17 按 one-architecture CON-1017-ARC-08（fdfa9c3e）：PRC-144 取消触发加 `entry: 3`，其余触发仍从步 1；b31.notes 删「生成器统一连步 1」旧限制。上节第 3 条未闭合项（图上不能表达已完成后直达取消步）随之关闭，运行时入口仍未实现。
