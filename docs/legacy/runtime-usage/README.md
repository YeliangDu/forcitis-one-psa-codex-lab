# 生产运行时证据（只读采集，2026-10-14）

> 历史证据（GOV-1023-FV2 DA3）：2026-10-14 的一次性采集，不再更新；生产主机标识已去掉，需要时查本机取证登记。

## 采集方式

- 通过 aliyun CLI 云助手，在生产 PSA 应用服务器 （主机标识不入仓，见取证登记） 上执行。
- 运行一个单文件 Java 程序：驱动取自 fst jar，数据源从本机 Nacos `prod` 命名空间的 `psa-datasource.yaml` 读取，以只读连接加 READ UNCOMMITTED 隔离级别访问 RDS `ruoyi-vue-pro`，只执行 SELECT。
- 凭据只在服务器进程内使用，没有打印、没有回传。临时目录 `/tmp/psa-ro-probe` 每次执行后都已删除。
- 程序源码见本会话 scratchpad `ro/Q.java`，查询语句的等价版见 `../runtime_usage_queries.sql`。

## 文件

| 文件 | 内容 |
|---|---|
| `prod_usage.tsv` | `##00` 访问日志时间窗；`##01` 近 180 天各接口调用量；`##02` 菜单与授权角色数；`##04` 各表行数；`##05/06` 活跃用户与登录 |
| `prod_bpm.tsv` | `##10` 流程定义与实例数；`##13` 审批人分配规则；`##14` 近 180 天任务数 |
| `prod_bpm2.tsv` | `##11` 各流程 BPMN 结构计数；`##12` 条件表达式样例 |

## 要点

### 规模
- 近 180 天活跃用户 335 人，API 调用约 176 万次。
- 817 个端点中实际被调用 405 个；启用且已授权的页面 89 个。
- 370 张表中 236 张有数据，共 3,524 万行。

### 部署
5 个 Java 服务连同 Nacos、Redis、RocketMQ、XXL-JOB，全部部署在**同一台 15G 内存的 ECS** 上。

### 审批（Flowable）
- 生产共 10 个流程定义。近 180 天的流程实例数：

  | 流程 | 实例数 | 说明 |
  |---|---|---|
  | TIMESHEET | 34,498 | 任务 66,766 个 |
  | PM_ETC | 103 | |
  | OPP_QUOTATION | 29 | |
  | PM_REVENUE | 20 | |
  | PM_BUDGET | 18 | |
  | TRAVEL | 18 | |
  | TRAVEL_REVISION | 1 | |
  | PM_DELIVERABLE、PM_STAGE_REVIEW、EXTERNAL_APPROVAL | 0 | |

- BPMN 结构：每个流程有 3–11 个用户任务，排他网关 0–5 个。**没有并行网关、服务任务**；子流程只在 TIMESHEET v15 出现（评审阶段在子流程中，见 `docs/data/timesheet-replay.md`）。包容网关只出现在 TRAVEL（4 个）和 TIMESHEET（2 个），用于按行类型分路。少量会签（多实例）。唯一的边界事件是驳回错误边界（`errorEventDefinition`，`Error_REJECT`），不是超时；生产 `ACT_RU_TIMER_JOB` = 0（2026-10-14 生产只读核实）。
- 条件表达式只读取领域预先算好的标志，例如 `needSeal`、`budgetGmNotBelowQuote`、`routeType == 'project'`、`firstPass`、`manHourTypeList.type`。
- 审批人分配规则 230 条，按类型分：

  | 类型 | 条数 | 含义 |
  |---|---|---|
  | 50 | 127 | 脚本，由代码计算审批人 |
  | 10 | 48 | 角色 |
  | 60 | 34 | 类型码含义未核实 |
  | 30 | 10 | 指定用户 |
  | 70 | 10 | 类型码含义未核实 |
  | 21 | 1 | 部门负责人 |

### 安全
- 安全组对 全网（任意来源） 开放网关端口 48088。
- 免密登录相关端点在 180 天内被调用约 2 万次，见 `SECURITY_FINDINGS.md` 中的 SEC-001。

### 目标环境
- 阿里云杭州已有 RDS PostgreSQL 17 实例 `FORCITIS-ONE-PG-production`（2c4g，到期时间 2026-10-18）。
- 本次只记录这个实例存在，没有访问其中的任何数据。
