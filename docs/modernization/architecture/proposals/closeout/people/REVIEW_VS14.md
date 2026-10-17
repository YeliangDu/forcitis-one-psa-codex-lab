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
