# ADR-0069 证据附件

状态：历史记录｜2026-10-21｜负责：one-architecture

本附件列出 ADR-0069（ADR-0055 补篇二，提议）各条的证据锚点、B 类代价、表结构要点、检索记录与守护明细。原第 1–6、15 条见 `ADR-0068-证据附件.md`。

- **基线与缩写**：同 ADR-0068 附件（origin/main `d7b805d8`；`M/`、`T/`、`R/`、`A/`、`D/`）。
- **基线 SQL**：
  - WF：`R/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql`
  - IG：`R/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql`
  - AU：`R/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql`
  - NB：`R/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql`
- **#491 评论**：owner 分档为评论 6309671304；B 类裁定稿为评论 6310172649。

## 第 7 条　同步列与 A8 登录名

- **`login_name`**：
  - 定义：WF:72、95；注释「为空即离职」（RULE-363）；
  - ADR-0033 第 4 项第 3 条（:141）与第 5 条（:143，原定随 DA-3 删除）；
  - ADR-0039:34；
  - W2 第 3 版 §3.1：随 D2 删除，进来源对照或迁入暂存，不进 `external_identities`（W-12）。
- **`separated_at`**：
  - 定义：WF:78、101；唯一写入方 `JdbcOrgSnapshotRepository.java:150-151`；
  - one-data 审查第 7 条：须与 `employee_versions` 同批或之后删除。
- **`last_sync_run_id`**：
  - 定义：WF:40、63、79、102；写入方 `JdbcOrgSnapshotRepository.java:117、126、143、152`；
  - 注册表：`A/data-access-registry.yaml:69、79、89`；
  - ADR-0039:48 位于其「影响面」，已被 ADR-0055 部分取代。
- **`wecom_user_id`**：W2 §3.1，随 DA-3 删除（one-data 审查第 7 条②）。
- **按 A8 登录名查人**：
  - `M/platform/integration/application/IntegrationOperationsCommands.java:38、47、55`；
  - `ProviderKind.java:5`；
  - `M/kernel/ExternalSubjectEmployees.java:6-14`；
  - `R/application-integration-operations.properties:6`；
  - ADR-0033 第 3 项第 6 条（:131）；
  - ADR-0067 决定 2：`ExternalSubjectEmployees` 属冻结例外（随 C1）。

## 第 8、9、10 条　B 类代价（摘自 #491 评论 6310172649）

### 第 8 条　部门简称

- **锚点**：根表 WF:38、61；版本表目前没有这一列（WF:319-340）。
- **代价**：
  - 前端 `org-units-page.tsx:9` 从只读改为可编辑；
  - 新增权限码（暂名 `organization.workforce:org-unit:update`）；
  - 简称视图在 `external-read-registry` 中的用途改为「迁入」。
- **受影响的 PR**：G、B 或 B2、PR-I、前端。

### 第 9 条　登录补开账号

- **锚点**：
  - `LoginAdmission.java:59`；
  - 补建分支 `OrgDirectoryService.java:59-73`；
  - `MUST_AUDIT_ACTIONS.yaml:33`（`AccountOpened` 必审计）。
- **代价**：`AccountOpened` 升一个 schema 版本，事件目录同步修改；新增一个告警。
- **受影响的 PR**：A、C、E。

### 第 10 条　致远 SSO

- **删除对象**：
  - `LoginWithSso`、`SeeyonTicketVerifier`；
  - `SessionController.java:95-100`；
  - `platform_identity.properties:36`；
  - 前端 `login-callback-page.tsx`。
- **依据**：ADR-0055:48。原裁定稿理由第 2 点（「日活是老系统的」）按 opus S7 改为「A8 门户随 ADR-0054 停用」。
- **H-2**：
  - 涉及 `ticket_issuers`、`sso_ticket_uses`、`SecurityInvariantsTest` I-2；
  - 触发条件为身份生命周期研究与 T-13 完成，负责人由 one-coordinator 指派（INVENTORY §8 第 19 条：目前没有卡）。
- **mssql 镜像**：`A/test-images.txt:4`。触发条件为迁入的退役用途达成（ADR-0055:113），负责人 one-1a-par。
- **登录方式取值**：AU:24 的 `login_method_kind` 含 `SSO`、`SIGNED_TICKET`；`M/platform/identity/application/LoginMethod.java:9、15`。

## 第 11 条　迁入适配器

- **现状**：`M/platform/integration/infrastructure/seeyon/` 下 19 个文件；`INVENTORY.md` §1.1、§1.5。
- **ADR 依据**：
  - ADR-0036 决定 1（:46-55）、决定 8（:119-123）；
  - ADR-0066:136-147（决定 8）；
  - ADR-0037 第 4 项第 4 条（:91）；
  - ADR-0067 决定 2（冻结例外）。
- **登记**：
  - `A/external-read-registry.yaml:22-55`；
  - `A/module-dependencies.txt:10`；
  - `A/test-images.txt:4`。

### 范围清单

改为迁入适配器：

- seeyon 的读取与映射：`SeeyonViewReader`、`SeeyonViewSql`、`*Row`、`SeeyonOrgTree`、`SeeyonMapping`；
- 连接与校验：`SeeyonConnections`、`SeeyonConfiguration`、`SeeyonStartupCheck`、`*SchemaInspector`；
- `SourceIdTranslation`：只在迁入批次内分配来源对照；
- 对账领域：`SnapshotReconciler`、`Reconciliation`、`IssueKind`，改为人事对象的迁入核对。

删除：

- 运行时端口 `OrgSource`、`StaffLookup`、`SourceOrgSnapshot` 系列；
- `SeeyonOrgSource` 的运行时实现、`DisabledOrgSource`、每日核对作业与 `/sync-runs:reconcile`；
- `legacy/` 全部代码（F、G）；
- `SeeyonTicketVerifier`（H-1）。

## 第 12 条　来源对照只读

- **用户原话**：ADR-0055:84（TRN-06「全表只读」）。
- **现有守卫**：IG:189-210（禁止改写两侧 ID、确认、区间、决定）；IG:212-222（拒绝 DELETE、TRUNCATE）。
- **只追加先例**：NB:70-79，`legacy_numbers` 的 `reject_legacy_number_change` 与两条触发器（原稿误写为 82-91，已订正）。
- **写入口**：`M/platform/integration/web/SourceReferenceController.java:56、67、73`。
- **U8C 对照**：IG:136-137、152（`ACCOUNTING_SYSTEM` + `LEGAL_ENTITY`）。
- **表结构要点**（one-data 审查第 12 条，供签发）：

  ```sql
  CREATE TABLE platform_integration.source_reference_seals (
      source_reference_seal_id uuid        NOT NULL,
      singleton                boolean     NOT NULL DEFAULT true,
      sealed_at                timestamptz NOT NULL,
      sealed_by_employee_id    uuid        NOT NULL,
      witnessed_by_employee_id uuid        NOT NULL,
      decision_ref             text        NOT NULL,
      created_at               timestamptz NOT NULL,
      CONSTRAINT pk_source_reference_seals PRIMARY KEY (source_reference_seal_id),
      CONSTRAINT ck_source_reference_seals_singleton CHECK (singleton),
      CONSTRAINT uk_source_reference_seals_singleton UNIQUE (singleton),
      CONSTRAINT ck_source_reference_seals_two_people CHECK (sealed_by_employee_id <> witnessed_by_employee_id),
      CONSTRAINT ck_source_reference_seals_decision_ref
          CHECK (char_length(decision_ref) BETWEEN 1 AND 200 AND btrim(decision_ref) <> '')
  );
  ```

  - 触发器拒绝 UPDATE、DELETE、TRUNCATE；
  - `source_references` 加 `BEFORE INSERT OR UPDATE` 触发器，查到封存行即抛 `restrict_violation`；
  - 登记：`data_category: 技术`、`APPEND_ONLY`、永久；`source_references` 的注释写明「封存后全表只读」。
- **属主守护**（opus S1）：表属主可以停用触发器，所以增加守护，断言运行时账号与迁入账号都不是表属主。迁入角色与表属主的关系在 main 上尚未核实（ADR-0036 决定 1，:55）。

## 第 13 条　勘误

- ADR-0055:98 原句；ADR-0055 头部第 25 行写「取代 ADR-0037 决定第 5、6、8 项（部分）」。
- ADR-0037 各决定的位置：
  - :50 决定 1；
  - :80 决定 4；
  - :97 决定 5；
  - :116、:129 决定 6；
  - :156、:163 决定 8。
- 勘误规则：`D/adr/src/_readme_lifecycle.md` §6。
- ADR-0036 状态行：INVENTORY §8 第 18 条。

## 第 14 条　审计对象类别

- **CHECK 常量串**：AU:12（原稿误写为 AU:11，已订正）；`login_method_kind` 在 AU:24。
- **用语**：terms.yaml `audit_subject_kind` 的中文名为「审计对象类别」（16 个对象类别加 3 个无对象类别）；原稿用的「审计主体种类」不在词表中，已改。
- **owner 原话**：#491 评论 6309671304 硬要求 4：「先改注册表（P1a=b 改造），SYNC_RUN 退休才能是一行 status = 'RETIRED'，不必再动一次 DDL」。该评论提到的 `platform_audit.subject_kinds` 在 main 上不存在（`git grep subject_kinds` 无命中），本篇是新建。
- **被取代的字面**：
  - ADR-0066:88-91（决定 4 第 5 条「平台自有的主体种类留在审计内部的枚举」）；
  - ADR-0033:166（决定第 7 项第 2 条，`subject_kind` 为 text + CHECK）。
- **REG 的归属**：ADR-0066 决定 4 第 7 条与 ADR-0067 决定 3 第 2 条（W0 收口窗口内完成单据类型注册表改造）。
- **写入方**：`RecordSyncRunRelease.java:38`。
- **样本聚合**：`T/platform/audit/AuditDomainEventsIT:16、18`、`T/kernel/EventSequenceContinuityTest:16、21`、`T/fixture/doubles/AggregateWriteGuard:86-87`，须换样本。
- **表结构要点**（one-data 审查第 14 条，供签发）：
  - 列：`audit_subject_kind_id uuid` 主键；`code text` 唯一，格式 `^[A-Z][A-Z0-9_]{0,49}$`；`owner_module text`；`status text`（`ACTIVE`、`RETIRED`）；`effective_from_date date`；`retired_on date`；`created_at`、`updated_at`。
  - 约束：`(status = 'RETIRED') = (retired_on IS NOT NULL)`；`audit_entries.subject_kind` 以外键指向 `code`（分区表可以引用非分区表）。
  - 权限：运行角色只读；触发器拒绝 DELETE、TRUNCATE，`retired_on` 只能由空变非空一次。
  - 种子：基线把 AU:12 的现有取值全部插入。
  - 数据类别：参照数据。

## 检索记录

命令（在 `docs/adr/` 执行，main `d7b805d8`）：

```
rg -n 'login_name|separated_at|last_sync_run_id|SYNC_RUN|subject_kind|source_references|SEEYON_LOGIN_NAME|short_name'
```

| 命中 | 结论 |
|---|---|
| ADR-0033:24、30、32（背景） | 无关 |
| ADR-0033:131（第 3 项第 6 条） | 本篇第 7 条取代「按 A8 登录名查」的一半 |
| ADR-0033:141、143（第 4 项第 3、5 条） | 第 3 条已被 ADR-0055 部分取代；第 5 条只涉删除时点的登记，本篇改为 D2 删除，仍在首次部署前，相容 |
| ADR-0033:166（第 7 项第 2 条） | 本篇取代 |
| ADR-0033:168（法人归属的 `subject_kind`） | 是另一张表的列，无关 |
| ADR-0033:68、88、189、216、219、260、296 | 相容 |
| ADR-0036:30、55、65、167 | 迁入写对照，封存在迁入之后，相容 |
| ADR-0038:86 | 描述过渡结构，相容 |
| ADR-0039:11、34、36、48 | 已被 ADR-0055 部分取代，或与本篇一致，相容 |
| ADR-0043:63、202、226、229（`login_name` 分级） | 列删除后，分级登记随 D2 更新，相容 |
| ADR-0066:88（决定 4 第 5 条） | 本篇取代部分（承载形态） |
| ADR-0066:180、249 | 相容 |

## 守护规则明细

1. **封存**：
   - 封存后 INSERT、UPDATE 的负向测试；
   - 封存表的单例与只读；
   - 有 `PENDING` 对照或未终结批次时拒绝封存；
   - 运行时账号与迁入账号都不是表属主。
2. **审计对象类别**：
   - 外键生效；
   - 退休的类别没有写入方（应用校验加守护）；
   - D2 与 REG 两种合并次序各有断言；
   - 登录方式中的 `SSO`、`SIGNED_TICKET` 在 H-1、H-2 后没有写入方。
3. **同步列**：
   - 删除后 `DdlNamingTest`、`data-classification.txt`、`ddl-naming-baseline.txt`、注册表同批更新；
   - 领域层不出现登录名判断。
4. **迁入适配器**：
   - `ExternalConnectionIsolationTest`；
   - PR-I 之前没有运行时调用方。
