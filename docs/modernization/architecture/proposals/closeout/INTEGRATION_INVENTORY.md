# 集成层流程盘点（process-catalog 新形态 type: integration 登记草稿）

- 配套文件：`INTEGRATION_INVENTORY.yaml`（逐条字段）。id 留空，由 one-architecture 发号；不改 `process-catalog.yaml`。
- 口径：代码以本 worktree（origin/main 同源）为准，设计以已采纳 ADR 为准。「impl」列：built 已有代码，designed 仅设计文档，none 未见证据。「(拟)」表示生命期是起草者推断，文档未明文。
- 登记原则：领域模块不出现外部系统名；外部系统名只写在本册的 external_system 字段。

## 1. 条目一览（19 条，其中 1 条是 business、1 条仅占位）

| # | 名称 | 外部系统 | 方向 | 生命期 | impl | 相关目录流程 |
|---|---|---|---|---|---|---|
| 1 | 同步组织来源数据 | A8 组织视图 | in | 过渡(拟) | built | PRC-80 |
| 2 | 核对组织快照与来源 | A8 组织视图 | in | 过渡(拟) | built | 无 |
| 3 | 刷新部门简称 | 老系统视图 | in | 过渡 | built | 无 |
| 4 | 校验致远单点登录票据 | 致远 SSO | in | 过渡(拟) | built | 无 |
| 5 | 识别企业微信登录身份 | 企业微信 OAuth | in | 长期(拟) | built | PRC-137、138（相关） |
| 6 | 投递企业微信通知 | 企业微信 | out | 长期(拟) | built | 无 |
| 7 | 重投领域事件 | 无（内部） | out | 长期 | built | 无 |
| 8 | 归档数据到对象存储 | 阿里云 OSS | out | 长期 | built | 无 |
| 9 | 读取老系统待办 | 老系统视图 | in | 过渡 | built | 无 |
| 10 | 编排历史数据迁入 | 老系统、A8、U8C | in | 过渡 | designed | 无 |
| 11 | 推送凭证到U8C | U8C | out | 过渡(拟) | designed | PRC-135（相关） |
| 12 | 同步U8C主数据 | U8C | out | 过渡(拟) | designed | 无 |
| 13 | 对接数电发票平台 | 税务数电平台 | both | 长期 | designed | PRC-57、73（相关） |
| 14 | 同步差旅员工与组织 | 分贝通 | out | 长期(拟) | none | PRC-177 |
| 15 | 接收差旅订单与账单 | 分贝通 | in | 长期(拟) | none | PRC-171、173 |
| 16 | 推送出差申请并回写审批 | 分贝通 | both | 长期(拟) | none | PRC-172、173 |
| 17 | 导入银行流水（人工上传） | 无 | in | 长期 | none | PRC-64（已在目录） |
| 18 | 拉取银行流水 | 未见证据 | in | 未见证据 | none | PRC-64 |
| 19 | 拉取企业微信打卡记录 | 企业微信 | in | 长期(拟) | none | PRC-115 |

已在目录里的编号（与本册条目直接对应或相关）：PRC-64、PRC-115、PRC-135、PRC-137、PRC-138、PRC-171、PRC-172、PRC-173、PRC-177，另有相关的 PRC-57、PRC-73、PRC-80。目录里目前没有 type 为 integration 的条目。本册 19 条中，与现有编号一一对应的只有 #17（PRC-64）和 #14 的编排方（PRC-177），其余 integration 条目都需新发号。

## 2. 用户点名项的结论

- **组织来源同步（A8）**：#1，代码已有（SeeyonOrgSource，每 5 分钟）。ADR-0039 决定把同步机制搬进集成层，管理员离职暂停留 workforce。每日对账另列 #2（可并入 #1）。
- **企业微信投递**：#6，出站投递表加定时 worker 与重试，已有代码；登录识别 #5 是另一类（入向）。
- **领域事件投递与重投**：#7。它没有外部系统，只是内部事件发布登记的重试、死信告警和归档清理，属于技术保障作业，是否进流程目录需 one-architecture 判（见 §4 第 3 点）。
- **迁入编排**：#10。ADR-0036 提议模块 `platform.migration`，离线命令运行；main 中无该模块，只有读取登记里的迁入用途列。
- **银行流水拆分**：
  - 人工上传（#17）：目录 PRC-64 已是 type: business，主对象 OBJ-90。保持 business；若匹配与利息手续费制证拆出，PRC-64 可降为 single_aggregate。
  - 接口（#18）：**未见证据**。ADR-0022 第 55 行、FD-31（FINANCE_DECISIONS.md:293-297）、U8C_CAPABILITY_MAP.md:108 全都只写「导入」，没有银企直连或回单接口。如登记则为 integration，owner_bc 为 platform.integration；现在仅占位。

## 3. 目录现有条目的处置

按你给的关键词（导入、同步、推送、投递、重投）扫 `process-catalog.yaml`，命中如下，没有一条需要把 type 改成 integration，但有三条建议拆分：

| 编号 | 名称 | 现 type | 建议 |
|---|---|---|---|
| PRC-64 | 导入银行流水 | business | 保持（人工上传） |
| PRC-115 | 导入考勤记录并补卡 | business | 若打卡来源是企业微信接口，导入一步是 integration（#19），补卡仍 business，建议拆开；接口能力尚待信息部门核对（PPL_NOW.json:370） |
| PRC-135 | 处理会计分录待处理队列与推送重投 | single_aggregate | 保持：人对队列和死信的处置；自动推送与重试是 #11 |
| PRC-172 | 申请出差并同步差旅服务商 | business | 保持：业务流程里嵌入一步接口（#16） |
| PRC-177 | 向差旅服务商同步员工与组织 | event_policy | 保持：它存在的理由是集成层不能订阅业务事件（S-31 SQ-31），适配器另列 #14，不要改 type |
| PRC-137、138 | 入职开通/离职停用企业微信账号 | business | 保持：IT 人工过程，网关没有开通或停用账号的方法 |

PRC-57、73（开票、进项查验）、PRC-77、125（税务申报）：开具发票对接数电平台有明文（ADR-0021:39），列 #13；查验、红冲、增值税申报、个税申报未见走接口的证据，个税申报流程稿按人工导入客户端写（PPL_NOW.json:403），均不登记。

## 4. 需你决定的点

1. **登记粒度**：迁入编排（#10）整体一条还是按阶段 6a-6e 各一条；组织同步（#1、#2）、U8C（#11、#12）、分贝通（#14-16）是否合并。建议迁入、组织同步各一条，U8C 合一条，分贝通保持三条。
2. **银行流水接口**（#18）：是否要走接口。不走就删掉本条；走则先问财务取银行和接口方式。
3. **技术作业是否进流程目录**：#7 重投领域事件、#8 归档到对象存储没有外部业务对象，与 #6 的投递一样是平台自身保障。若流程目录只收业务可见的流程，这三条应改归「平台技术作业」另册。
4. **PRC-115 是否拆**：导入打卡与补卡分开，前提是企业微信打卡接口确认可用。
5. **生命期（拟）**：标「拟」的几条（A8 组织源、致远 SSO、U8C、企业微信、分贝通）文档没写退役或长期使用的明文，需业务方认可。

## 5. 未入册（已排除）

登记来源对照、确认与驳回（集成层内人工维护，不与外部交互）；按外部主体查找账号（一次性运维命令）；个税与增值税申报；PRC-137、138。理由见 YAML 的 `excluded` 段。

## 6. 查找来源与局限

- 已查：`ACL_BOUNDARY.md`、`ADR-0039`、`ADR-0036`、`ADR-0037`、`ADR-0021`、`ADR-0022`、`ddd/context-map.yaml`、`ddd/contexts.yaml`、`module-dependencies.txt`、`external-read-registry.yaml`、`platform/integration` 全部 api 与 infrastructure 类、`organization/workforce` 的调度作业、`process-catalog.yaml`、`object-layer.yaml`、S-31 裁定、决策页 PPL_NOW.json。
- 任务所说的各组骨架 `closeout/*/processes.*.yaml` 在本 worktree 中不存在（finance、people、revenue 目录为空），改查了 `proposals/processes.v1.yaml`（无外部系统步骤）与各组裁定文件；cross_bc 字段未见。
- 事件目录 `docs/modernization/events/integration.yaml` 未逐条读，故集成层发布的事件（来源对照登记等）未列入。
