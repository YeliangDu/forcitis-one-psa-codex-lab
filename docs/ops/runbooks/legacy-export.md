# C-6 剧本：老系统数据只读导出（老角色与权限、TIMESHEET 回放、工作日历 2016–2026）

依据：ADR-0026 第 8 项与 D-9（老角色导出已授权，1a 切换前执行）、`docs/data/timesheet-replay.md`（回放导出需用户授权，Q-4）、D-1a-114（2016–2026 共 4,018 天迁入）。时点：dev 前。性质：读生产数据，**每一类导出执行前须用户单独确认**；只读，不写老库。

查询：`sql/legacy-readonly-export.sql`（STEP 1 列核对、STEP 2 角色、STEP 3 日历；STEP 4 回放按 TIMESHEET_REPLAY.md）。

## 执行前确认点

1. 用户逐项确认本次导出哪几类（角色 / 回放 / 日历），以及导出范围（回放：近期、当前定义版本 v15 的实例样本数量）。
2. 执行通道：与 2026-10-14 核实流程定义时相同的云助手只读通道；连接用只读身份（不使用 B-5 的两个视图账号——它们只能读各自视图；由老系统 DBA 提供临时只读账号或由 DBA 代为执行），用后由 DBA 停用。
3. 数据处理：导出文件含真实人员 ID，只存新 ECS 数据盘 `/data/forcitis-one/import/c6/`（见「执行方案」§3），不进仓库、不发聊天、不落运维机；老系统服务器临时目录用后删除；角色与日历导出不含姓名、手机号。
4. STEP 1 的列与查询不符时停止，修正查询并在执行记录中写明，不猜列。

## 步骤

1. STEP 1 列核对，结果留档。
2. 角色（STEP 2）：执行 2.1–2.3，各存 JSON；2.4 计数与文件行数一致。交 1a 代码会话做「老角色 → 新角色」对照与差异报告（ADR-0026 第 8 项第 2–5 条），差异由用户逐个决定。
3. 日历（STEP 3）：导出 CSV；3.1 期望 2016–2026 共 11 年、4,018 天、每年 365 / 366 天无重复；**每年 `holidays` 大于 0、`make_up_workdays` 与两类合计各有计数、`unexpected_workday_values` 为 0，任一不满足即停**（2026-10-17 加：此前 HOLIDAY 条件写错，导出里没有 HOLIDAY 却没被发现）。`day_kind` 以老系统 `workday` 标记（'1' 上班、'2' 休息）为准，节日编码印证：周一至五的 207 个休息日中 198 个带法定假日编码（春节、除夕、元旦、清明、劳动节、端午、中秋、国庆），另 9 个无编码、全在 2022–2025 年 1–2 月（春节假期尾部，推断）；非法定节日的周一至五均为上班日（取证明细见 `handoff/B-5-dba/evidence/cal_*.sql` 与 legacy-read 登记）。导入前抽查 3 个年份的春节与国庆，对照国务院公告。2016–2026 各年 HOLIDAY / MAKE_UP_WORKDAY 条数：2016 17/6、2017 16/5、2018 18/7、2019 17/6、2020 19/6、2021 18/7、2022 21/6、2023 22/4、2024 21/8、2025 19/5、2026 19/5（合计 207 / 65）。转换为每年一份 `{"days":[…]}`（只含 `day_kind` 非空的行），由组织数据管理员会话逐年 `PUT /api/v1/organization/work-calendars/{年}` 导入（或代码会话提供的导入命令），导入后逐年 GET 核对条数。
   - **导入结果（2026-10-21，会话 one-ops-calendar 报告，one-ops 记）**：dev 工作日历 2016–2026 已导入，逐年读回条数与预期一致，合计 272/272（HOLIDAY 207 + 调休 65），列表 11 年；**为临时数据**（按 `workday` 标记推出，投产前对照国务院公告核对）；2027 年未导，待组织数据管理员清单（B-7）。逐年明细在 one-coordinator 收到的报告里。
4. 回放（STEP 4）：按 TIMESHEET_REPLAY.md「导出文件」格式生成 JSON，运行回放命令，产出 `replay-report.md`；`REVIEWED_*` 请用户逐类接受，`DEFECT` 交代码会话修复（出口条件 D-3）。

## 执行方案（one-ops 起草；one-coordinator 2026-10-15 在授权内定：三类都导、回放样本按建议、通道 A、先做 0 计数报确认后再导明细）

### 1. 导出内容

| 类 | 内容 | 不含 | 用途与消费者 |
|---|---|---|---|
| 0 计数 | 三类各自的行数；TIMESHEET 当前定义版本实例按月计数 | 任何明细 | 先报 one-coordinator，确认后再导明细 |
| 角色 | STEP 2.1–2.4：角色定义、持有关系（用户 ID）、角色菜单权限串 | 姓名、手机号、账号名 | 1a 代码会话做「老角色 → 新角色」对照与差异报告，差异由用户逐个决定 |
| 日历 | STEP 3：2016–2026 每日 `workday` 与换算出的 `day_kind`，期望 4,018 行 | — | 组织数据管理员导入 dev（以及生产切换时导入生产） |
| 回放 | STEP 4：当前定义版本、近 90 天已办结的全部 TIMESHEET 实例（超过 500 个则按月均匀抽 500 个），及当时的组织快照、指派规则、流程变量、工时行类型集合、实际审批人序列（TIMESHEET_REPLAY.md 格式） | 姓名（报告只含 ID） | 在新 ECS 上运行 `TimesheetApproverReplayCommand`，产出 `replay-report.md`（出口条件 D-3） |

### 2. 通道（A：沿用探针）

- 与 psa-gl-scope 读 U8C 相同的做法（U8C_GL_PRACTICE「访问方式」）：经云助手在老系统应用服务器 `i-bp1b34hcl0g1cnkdtrb9` 上运行单文件 Java 探针，取老系统数据源，只读连接（`setReadOnly(true)`），拒绝非 SELECT 语句；不新建数据库账号；凭据只在服务器进程内使用，不打印、不回传。只读由探针保证，数据库服务端不强制。
- 老系统服务器上不落盘：每次调用只取一页（按主键键集分页，每页 ≤ 3,000 行），结果 gzip + base64 写到标准输出；探针放在 `mktemp -d` 建的 0700 临时目录，同一次调用结束时 `trap` 删除；云助手命令 `KeepCommand=false`。
- 搬到新 ECS：两台在同一 VPC，但新 ECS 入方向只开 80 / 443，没有接收端口，所以由运维机上的中转脚本把云助手结果（base64）在进程内存中直接交给云助手 `SendFile` 写到新 ECS。中转**不写本机磁盘、不打印内容**，只打印页号、行数与 SHA256。

### 3. 新 ECS 上的存放、权限、保留与删除

| 项 | 规定 |
|---|---|
| 目录 | `/data/forcitis-one/import/c6/<YYYYMMDD>/`（root 0700），文件 root 0600：`legacy-roles.json`、`legacy-calendar.csv`、`timesheet-export.json`；回放输出 `replay-out/` 同权限 |
| 清单 | 同目录 `manifest.json`：每个文件的行数、SHA256、源计数、执行时间、云助手 InvokeId；会话日志只抄行数与 SHA256，不抄内容 |
| 回放 | 在新 ECS 上以 root 运行回放命令（依赖 C-4 E-4 的 Java 25 与对应版本的发布包或构建产物）；`replay-report.md` 只含 ID，交用户查看时经云助手读取汇总表，不复制明细 |
| 不放 | 仓库、聊天、运维机磁盘、会话 scratchpad；`forcitis` 运行用户读不到（0700 root） |
| 保留 | 角色：到用户对差异报告逐项决定完、1a 生产切换完成；回放：到 D-3 验收（1a 出口）；日历：到生产导入核对通过。三类最迟都在 **1a 生产切换后 30 天**删除 |
| 删除 | `rm -rf /data/forcitis-one/import/c6/<YYYYMMDD>/`；删除时间写入会话日志，one-coordinator 台账里销项 |

## 执行记录（2026-10-15，one-ops）

工具：`tools/ops/ecs/scripts/LegacyReadOnlyProbe.java`（只读连接、READ COMMITTED、只许 SELECT、每次最多 3,000 行）、`tools/ops/ecs/scripts/legacy_probe_run.sh`（老系统服务器上取驱动与主数据源、运行探针、结束删除工作目录）、`tools/ops/ecs/scripts/c6_relay.py`（运维机上的中转：分页查询、内存拼装、SendFile 分块写新 ECS 并核对 SHA256）、`sql/legacy-export-step0-counts.sql`。执行账号即老系统应用的主数据源账号（实例上有建登录等权限，非 sysadmin）；只读由探针保证。

| 步骤 | 结果 |
|---|---|
| 0 计数（t-hz0701buw7j7fnk，READ UNCOMMITTED，只计数） | 角色 38、持有关系 1,359、菜单权限 1,386；日历 2016–2026 共 4,018 天、无重复；TIMESHEET 当前定义版本 v15；v15 已办结 2026-09 1,573 个、2026-10 1,827 个，未办结 147 个 |
| 样本（one-coordinator 确认） | v15 已办结（审批通过）实例，9 月、10 月各 250 个按结束时间均匀抽取；未办结 147 个不纳入（没有完整审批人序列） |
| 角色与日历（READ COMMITTED） | `legacy-roles.json`（SHA256 329cdb09f1fa5d8d0601a661519c11d1f0045a904f758ac547ed24f29352bccc）、`legacy-calendar.csv`（14ad4d12e0d0b1a839c974d8602d9adc22b5fa147ca3e3605d41fd939be45c43）、`manifest-roles-calendar.json`；与 0 计数对账全部一致 |
| 回放（READ COMMITTED） | `timesheet-export.json`（SHA256 0d505904dfb72b7a5d8544006f105c5975e2a6e05038ee3ab171a0e9d78d42a9）、`manifest-replay.json`（b673202567f676b547df5c7162b642e6db7e4fc2dad01b0fefca78d057f7064e）：样本 500 / 500，全部取到工时行类型，表单字段解码失败 0，审批节点 561 个；快照人员 1,258、部门 367。导出时 10 月已办结增至 1,828 个（多 1 个），不影响样本规则 |
| 存放 | 新 ECS `/data/forcitis-one/import/c6/20261015/`（root 0700，文件 root 0600）；运维机不落盘 |

回放导出的口径与局限（写入 `manifest-replay.json`，回放报告须引用）：
- 工时行类型取自流程变量 `manHourType`（老流程网关当时判断分支用的值，Java 序列化的整数列表，在中转进程内解码）；修订表 `psa_time_sheet_revision` 只有 851 行，覆盖不了样本，不用。
- 表单字段：项目经理取 `manager_id`、部门负责人（原区域负责人，P4 改名）取 `area_leader`（Java 序列化的字符串列表）。
- 实际办理人：`bpm_task_ext` 中三个审批节点结果为通过 / 不通过的任务的办理人；发起人节点不回放（M3）。
- 组织快照为导出当天的人员与部门（老表无历史），由此产生的差异归 `REVIEWED_*` 类。
- `processAdministratorUserId` 为回放专用替身 `-1`（流程管理员 A-4 未定），使「取不到人转流程管理员」路径可比对；不是真人，不进任何运行配置。
- `legacyFallbackUserIds` 为老代码中写死的兜底人 ID（RULE-254，用户已判「去掉写死兜底，取不到人转流程管理员」），只取 ID。
- 云助手会在阿里云侧保留调用输出一段时间（压缩后的页数据，含人员 ID），这是该通道的固有特性。

回放的运行：在新 ECS 上以 root 运行 `TimesheetApproverReplayCommand`，输入 `/data/forcitis-one/import/c6/20261015/timesheet-export.json`，输出到同目录 `replay-out/`。需要含该命令的构建产物（发布包或同版本的类与依赖）和 Java 25（已装）；首个发布包就绪后执行（2026-10-16 已执行，结果见下）。

### 回放结果（2026-10-16，one-ops）

在新 ECS 上用 v0.1.0-rc.2 的 jar（已验签）运行 `TimesheetApproverReplayCommand`，不连数据库（云助手 t-hz07058klbng3cw；汇总 t-hz0705bdjexee4g 等，只出计数与类别）。输出在 `/data/forcitis-one/import/c6/20261015/replay-out/`（root 0600：`replay-report.json`、`replay-report.md`、`run.log`），只含 ID，不复制明细。有 DEFECT 时命令按设计以 1 退出。

- **范围**：流程 v15，近 90 天内已办结且审批通过的实例（2026-09、2026-10），每月按结束时间均匀抽，最多 250 个：共 500 个实例、561 个审批节点。其中 supervisorReview 123 个（规则 INITIATOR_SUPERVISOR，level 1），projectManagerReview 389 个（FORM_FIELD_USERS，字段 projectManagerId），regionalHeadReview 49 个（FORM_FIELD_USERS，字段 regionalHeadId）。组织快照为导出当天（2026-10-15）的当前组织。
- **结论计数**：MATCH 474；REVIEWED_SELF_APPROVAL 80；REVIEWED_DISABLED_APPROVER 1；DEFECT 6。处理结果：RESOLVED 492，ESCALATED_SELF_APPROVAL 69。

| 分类 | 节点 | 数 | 新系统候选 | 老系统实际 | 说明 |
|---|---|---|---|---|---|
| REVIEWED_SELF_APPROVAL | projectManagerReview | 65 | 升级到发起人直属上级（RULE-307，ESCALATED_SELF_APPROVAL） | 发起人自己审批 | 表单项目经理就是发起人 |
| REVIEWED_SELF_APPROVAL | projectManagerReview | 10 | 表单中发起人以外的人（RESOLVED） | 发起人自己加另一人 | 表单字段有多人，含发起人 |
| REVIEWED_SELF_APPROVAL | 部门负责人节点（导出里仍叫 regionalHeadReview，回放命令按旧名映射） | 4 | 升级到发起人直属上级（ESCALATED_SELF_APPROVAL） | 发起人自己审批 | 表单区域负责人就是发起人 |
| REVIEWED_SELF_APPROVAL | 部门负责人节点（同上） | 1 | 表单中发起人以外的人（RESOLVED） | 发起人自己加另一人 | 同上，多人 |
| REVIEWED_DISABLED_APPROVER | supervisorReview | 1 | 发起人在快照中的直属上级（RESOLVED） | 发起人所在部门负责人 | 原审批人停用 |
| 回放方法限制（原 DEFECT，未取证坐实） | supervisorReview | 6 | 发起人在快照中的直属上级 | 同一人，是该直属上级的下属，不在发起人上级链上 | 涉及 2 名发起人；回放用的是导出日的组织快照，不是审批时点，老表没有汇报线历史，审批后上级若有调整就会出现这种差异（one-1a-par 的诊断，判断不是规则缺陷）；没有用取证去坐实 |

REVIEWED_SELF_APPROVAL 涉及 37 名发起人。REVIEWED_* 请用户逐类接受（one-rules 做决策页）。上表最后一行 6 条按 one-coordinator 的决定归为「回放方法限制」：不再为坐实原因做加密中转取证（成本与风险偏高，A8 又没有上级字段，只能间接判断），D-3 的结论由用户在出口评审时一并看；回放方法的改进（按提交时刻的组织快照重放）记为后续项。取证脚本草稿保留在 PR #254（草稿，暂停），需要时再启用。

## 校验

- 三类导出的计数与源一致，执行记录写明执行人、时间、行数、文件 SHA256（不写内容）。
- 日历导入后 `forcitis.organization.attendance.calendar.unmaintained` 不再因 2016–2026 增加。

## 回滚

只读导出无须回滚；ECS 上的导出文件按「执行方案」§3 的保留期删除。日历导入有误时重新 PUT 该年正确清单。
