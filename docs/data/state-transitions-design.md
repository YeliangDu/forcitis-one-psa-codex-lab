# state_transitions 数据设计（ADR-0058 落地；模板 DDL、登记、清理、守护）——第 2 版

> 冻结（2026-11-04，#1263）：第 2 版设计稿，不再更新；已落地内容见 ADR-0058、ADR-0075。

- 作者：one-data，2026-10-17。依据：ADR-0058（已采纳，用户 2026-10-17 批准，提交号 `ADR58-20261017-225508-gjl`）；输入 `data/STATE_TRANSITION_TABLES.md`；独立复核 `~/.modernize/psa/handoff/reviews/STATE_TRANSITIONS_DESIGN-review-1.md`（opus，基于第 1 版 d038b09c，结论修后可签发，阻断 B1–B3、应改 S1–S9、建议 N1、N3、N4）。
- **状态：第 2 版已改完复核意见，待 sonnet 复核后我签发**（见末尾「复核意见处理表」）。保留期一节在用户认可前不进表结构 PR（S9）。
- 分工：数据设计、签发、表结构 PR 复核归我；kernel、迁移、仓储、守护代码归开发会话（第 8 节派工）。

## 1. 模板 DDL（每个 schema 一份，同一份 Flyway 模板；`<schema>` 替换）

```sql
CREATE TABLE <schema>.state_transitions (
    id                      uuid        NOT NULL,
    aggregate_type_code          text        NOT NULL,
    aggregate_id            uuid        NOT NULL,
    transition_sequence_number             bigint      NOT NULL,
    from_status              text,
    to_status                text        NOT NULL,
    occurred_on_date             date        NOT NULL,
    recorded_at             timestamptz NOT NULL,
    legal_entity_id         uuid,
    actor_type              text        NOT NULL,
    actor_id                uuid        NOT NULL,
    cause_kind              text        NOT NULL,
    command_id              uuid,
    event_id                uuid,
    correlation_id          uuid        NOT NULL,
    reverses_transition_id  uuid,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT pk_state_transitions PRIMARY KEY (id),
    CONSTRAINT uk_state_transitions_aggregate_id_transition_sequence_number UNIQUE (aggregate_id, transition_sequence_number),
    CONSTRAINT uk_state_transitions_id_aggregate_id UNIQUE (id, aggregate_id),
    CONSTRAINT fk_state_transitions_reverses_transition_id FOREIGN KEY (reverses_transition_id, aggregate_id)
        REFERENCES <schema>.state_transitions (id, aggregate_id) ON DELETE RESTRICT,
    CONSTRAINT ck_state_transitions_aggregate_type_code CHECK (aggregate_type_code ~ '^[A-Z][A-Za-z0-9]{0,99}$'),
    CONSTRAINT ck_state_transitions_from_status CHECK (from_status IS NULL OR from_status ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_state_transitions_to_status CHECK (to_status ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_state_transitions_transition_sequence_number CHECK (transition_sequence_number >= 0),
    CONSTRAINT ck_state_transitions_states_differ CHECK (from_status IS DISTINCT FROM to_status),
    CONSTRAINT ck_state_transitions_first CHECK (from_status IS NOT NULL OR cause_kind = 'MIGRATION' OR transition_sequence_number <= 1),
    CONSTRAINT ck_state_transitions_actor_type CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_state_transitions_cause_kind CHECK (cause_kind IN ('COMMAND', 'REVERSAL', 'CORRECTION', 'MIGRATION', 'SYSTEM_JOB')),
    CONSTRAINT ck_state_transitions_trigger CHECK (num_nonnulls(command_id, event_id) <= 1
        AND (cause_kind <> 'COMMAND' OR command_id IS NOT NULL)
        AND (cause_kind <> 'SYSTEM_JOB' OR command_id IS NULL)
        AND (cause_kind <> 'MIGRATION' OR num_nonnulls(command_id, event_id) = 0)),
    CONSTRAINT ck_state_transitions_reversal CHECK ((cause_kind IN ('REVERSAL', 'CORRECTION')) = (reverses_transition_id IS NOT NULL)),
    CONSTRAINT ck_state_transitions_migration CHECK (cause_kind <> 'MIGRATION' OR from_status IS NULL),
    CONSTRAINT ck_state_transitions_times CHECK (created_at = recorded_at AND updated_at = recorded_at)
);
CREATE INDEX ix_state_transitions_legal_entity_id_occurred_on_date ON <schema>.state_transitions (legal_entity_id, occurred_on_date) WHERE legal_entity_id IS NOT NULL;
CREATE INDEX ix_state_transitions_recorded_at ON <schema>.state_transitions (recorded_at, id);   -- 投影按「水位回看窗口」读，见第 5 节
CREATE FUNCTION <schema>.refuse_transition_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- 清理只有一条通道：schema 属主角色经 SECURITY DEFINER 函数 <schema>.purge_expired_state_transitions 删除到期的整个聚合（第 4 节）；
    -- 在该函数里 current_user 是属主，其他任何角色（含运行角色）都被拒。
    IF TG_OP = 'DELETE' AND current_user = (SELECT pg_get_userbyid(c.relowner) FROM pg_class c WHERE c.oid = TG_RELID) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'state transitions are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_state_transitions_append_only BEFORE UPDATE OR DELETE ON <schema>.state_transitions
    FOR EACH ROW EXECUTE FUNCTION <schema>.refuse_transition_rewrite();
CREATE TRIGGER tg_state_transitions_append_only_truncate BEFORE TRUNCATE ON <schema>.state_transitions
    FOR EACH STATEMENT EXECUTE FUNCTION <schema>.refuse_transition_rewrite();
```
`TRUNCATE` 触发器函数里 `TG_OP = 'TRUNCATE'`，不进放行分支，永远拒。表与列注释（含「为空表示」）随模板；时间列全部由应用传注入时钟，DDL 无 `now()`；`created_at`、`updated_at`、`recorded_at` 三列同值，由 `ck_state_transitions_times` 防漂移（行不可改，N1）。

- `transition_sequence_number`（S2）：取**本次状态变更之后**聚合行的 `version`，与聚合行的更新在同一事务、同一语句版本递增。核实：目前 14 张表中 `version` 默认值都是 0（`bigint NOT NULL DEFAULT 0`），所以新建聚合的第一条转换 `transition_sequence_number` 为 0 或 1，取决于仓储插入时的版本值；模板 CHECK 允许 `>= 0`，`ck_state_transitions_first` 约束「原状态为空只能出现在新建（序号 0 或 1）或迁入」。`approval_tasks`、`numbering_rule_versions` **没有 `version` 列**：它们的转换用自己的计数，实现卡须给该表加 `version`（或用 `aggregate_type_code = ApprovalTask`、任务自己的序号列），在各自卡的数据设计里写。
- `reverses_transition_id` 的外键是复合的（S1），保证被抵消的转换属于同一聚合。
- `cause_kind` 与触发 ID（S1）：COMMAND 必须有 `command_id`；SYSTEM_JOB 不能有 `command_id`（可有 `event_id` 或都无）；MIGRATION 都无；REVERSAL / CORRECTION 与 `reverses_transition_id` 同有同无，并可带 `command_id` 或 `event_id`（至多一个）。
- 列名 `cause_kind` 与 ADR 字面的 `cause` 不一致（S6）：我按 NAMING_STANDARD（枚举列带 `_kind`）取 `cause_kind`，**交 one-architecture 定**；`terms.yaml` 查重已做：`cause`、`cause_kind`、`state_transition(s)` 都不在词表里（grep 0），须登记提议术语（领域，bc 按各 schema 同一术语，含 `transition_sequence_number` 的核对）。

## 2. 与 ADR-0058 逐项对照

| ADR 条款 | 落点 |
|---|---|
| 1 主键 UUIDv7 | `id` |
| 1 aggregate_type_code / aggregate_id / transition_sequence_number，(aggregate_id, transition_sequence_number) 唯一 | 同名列与 `uk_…_aggregate_id_transition_sequence_number` |
| 1 from_status（新建为空）、to_status | 两列；格式 CHECK 只定形不定值 |
| 1 occurred_on_date、recorded_at | 两列；`recorded_at` 为注入时钟参数 |
| 1 legal_entity_id | 可空；是否必填按聚合类型在注册表登记并由守护核（第 6 节 S5） |
| 1 actor_type / actor_id | 两列；系统操作者按 `system-actors.txt` |
| 1 cause 枚举、command_id 或 event_id、correlation_id、reverses_transition_id | `cause_kind`（待 one-architecture 定名）、`ck_…_trigger`、`correlation_id NOT NULL`、复合外键 |
| 2 只追加，回退也正向记 | 触发器 + 权限回收 + 回退写新行 |
| 5 索引（aggregate_id+transition_sequence_number；legal_entity_id+occurred_on_date） | 唯一约束；`ix_…_legal_entity_id_occurred_on_date`；另 `ix_…_recorded_at`（投影；复合外键自带唯一约束索引，不另建外键索引） |
| 8 迁入写初始转换 | `ck_…_migration` + `ck_…_trigger`（迁入无命令/事件 ID） |

## 3. 权限（B1）

`one_db_roles_schemas.sql` §4b 现有的数组只回收 `DELETE, TRUNCATE`（保留 UPDATE 用来关闭开放版本），**不适用**于转换表。转换表单独一段：
```sql
FOREACH t IN ARRAY ARRAY['cost_timesheet.state_transitions', '…']  -- 每 schema 一项
LOOP EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON %s FROM one_dev_app', t); END LOOP;
```
c3 / c9 两份运行时脚本同步。`DataArchitectureTest` 核对：注册表里角色为 `LOG_APPEND_ONLY` 且名为 `state_transitions` 的表必须在这一段（并入 G34）。

## 4. 保留期到期后的清理通道（B3）

- 清理由 schema 属主经函数 `<schema>.purge_expired_state_transitions(p_cutoff date) RETURNS bigint`（`SECURITY DEFINER`，属主 `one_*_owner`，`REVOKE EXECUTE … FROM PUBLIC`，只授予维护角色，做法同 `drop_archived_audit_entry_partition`）；函数里 `current_user` 是属主，触发器放行 DELETE（第 1 节），运行角色没有 EXECUTE，也删不了。
- 已知边界：触发器放行的是「当前用户是表属主」的 DELETE，不区分是不是经由清理函数；属主身份只在迁移与受控特权路径（运维命令、见证规则，ADR-0030）使用，运行角色不是属主，守护与 §4b 回收保证这一点。
- 一次只删「整个聚合都到期」的转换：该聚合所有转换的 `recorded_at < p_cutoff`，且聚合所属业务记录已按其保留期清理；同一语句删完整条冲销链，复合外键 `RESTRICT` 不触发。
- 作业归属：**不归本批卡**。保留期 10 年，清理作业与审计在线删除（ADR-0048）、ADR-0034 授权历史清理同批实现（见 ADR-0034-IMPL 的 D-1 登记），期限 = 首次生产部署后 30 个月内，负责人 one-1a-par 或其接班人；registry 先写 `{policy: PERMANENT}` + 注释四项（口径、清理落点、负责人、期限），与 role_versions 同法。本批卡只交付函数与放行触发器的**结构**，不交付作业。

## 5. 给度量设计的投影口径（B2、S4）

- **水位不能只看 `recorded_at`**：并发事务按提交顺序而不是 `recorded_at` 顺序可见，较早的 `recorded_at` 可能晚于水位才提交，增量漏行。投影必须二选一（或并用）：①**回看窗口**——每次从「水位 − 回看窗口」读起，按 `id` 去重（窗口 ≥ 最长事务时间，建议起步 1 小时，由度量设计定）；②**按聚合断号检测**——对每个 `aggregate_id` 检查 `transition_sequence_number` 连续，缺号则回补。建议并用：窗口读做日常，断号检测做每日对账。
- **左截断**：存量不回填，迁入聚合的第一条转换是迁入时的状态（`cause_kind = MIGRATION`，`from_status` 空）。投影里阶段时长必须把「第一条是 MIGRATION 的聚合，其迁入前的阶段」标为左截断，不得当作真实起点。
- **迁入行的 `occurred_on_date`**（S4）：迁入的初始转换用「状态业务意义上最后一次变化的业务日期」；取不到就用迁入作业的业务日期并在 `cause_kind = MIGRATION` 行上以左截断对待。具体取值口径各 schema 的迁入映射卡（E3）要有生产取证证据，本批卡不定。
- **迁入作业的系统操作者**须登记进 `system-actors.txt`（UUIDv5，名称以 `platform.` 或本 BC 前缀开头），由各 BC 卡的迁入部分办。

## 6. 登记与守护

- `data-access-registry.yaml`，每个 schema 一条：
  ```yaml
  <schema>.state_transitions:
    # 保留：与所属聚合的业务记录相同（用户认可后写期限）；清理通道与作业见 STATE_TRANSITIONS_DESIGN 第 4 节
    role: LOG_APPEND_ONLY
    mutability: APPEND_ONLY
    retention: {policy: PERMANENT}
    lineage: {source: NEW}
    writers: [<本 BC 的模块>]
    readers: [reporting]      # 投影读（ADR-0058 第 7 条），登记 cross_module
  ```
  有状态表例外用 `state_transitions_exempt: <理由>`（`sync_runs`、`outbound_deliveries`，理由见 `STATE_TRANSITION_TABLES.md`）。转换表自己不写该字段（N3，去掉了第 1 版的哨兵值）。
- **法人必填（S5）**：不按 schema 加 DDL CHECK（避免偏离 ADR 的「聚合没有法人归属的登记理由」），改为在注册表按聚合类型登记 `state_transitions_legal_entity: required | none:<理由>`，行为守护断言 `required` 的聚合每条转换 `legal_entity_id` 非空（来源不确定时失败，不填默认值）。若 one-architecture 要 DDL 层强制，须认可这个偏离。
- **统一落表点（S3）**：每个 BC 一个类实现转换落表（建议名 `Jdbc<Aggregate>Transitions` 或一个 schema 级 `JdbcStateTransitions`），聚合仓储的 `save` 只经它写转换；结构守护扫描：实现标记接口的聚合，其仓储源码必须调用该落表点；漏调用由行为守护（状态变化次数等于新增转换行数，含冲销路径）兜住。
- **守护两端（S7）**：Java 端「有状态枚举的聚合必须实现标记接口」（结构守护，防漏标）+ DDL 端 `measurable_ddl_scan.py` 的 M3（schema 必须有 `state_transitions`，或注册表例外）。`measurable_ddl_scan.py` 目前在分支 `claude/one-data-handoff-1`，不在 main，进 main 由「模板与守护」公共卡（第 8 节）带入。
- **模板一致性守护**：比对各 schema 的 `state_transitions` DDL 与本页第 1 节模板（列、类型、约束名、索引名、触发器名），只允许 `<schema>` 与函数属主差异。
- 触发器命名（N4）：按 `docs/data/trigger-naming-proposal.md`（`tg_<表名>_append_only` 与 `_truncate`），该条款提议稿尚未采纳，本表按此命名，采纳后无需改名。

## 7. 保留期建议（D12，交用户认可；S9：认可前不进表结构 PR）

| schema | 所属聚合的保留期（现行） | 转换表建议 |
|---|---|---|
| cost_timesheet | 工时单 BUSINESS_RECORD（10 年） | 10 年 |
| platform_approval | 审批请求等 BUSINESS_RECORD（10 年） | 10 年 |
| platform_identity | 授权申请 10 年；账号含个人信息标记须有限期（ADR-0043 R-2） | 10 年；账号转换只含 ID 与状态，随账号清理 |
| platform_integration | `source_references` 人员类 10 年 | 10 年 |
| platform_numbering | `numbering_rule_versions` 永久（配置，无个人信息） | 永久 |
| organization_workforce | 法人永久；员工、组织单元、岗位随自有化卡再定 | 法人永久；其余随自有化卡 |

## 8. 卡的拆分（S8）

0. **kernel**：`StateTransition` 值对象与标记接口（`kernel` 只放值对象）。
1. **「模板与守护」公共卡**（新增，排在 kernel 之后、各 BC 卡之前）：Flyway 模板与生成方式、模板一致性比对守护、§4b 回收段（B1）与 c3 / c9 同步、G34 并入、`measurable_ddl_scan.py` 进 main 并接 CI、注册表字段（`state_transitions_exempt`、`state_transitions_legal_entity`）与登记检查、清理函数模板（B3，不含作业）、结构守护与行为守护的公共测试夹具。
2. cost_timesheet。 3. platform_approval（**标阻塞**：`approval_tasks` 无 `version` 列，须先定任务转换的序号来源，见第 1 节；请 one-architecture 答聚合边界）。 4. platform_identity（授权申请、账号；**`roles` 不做**：随 ADR-0034-IMPL PR-2 把状态移入 `role_versions`，版本行本身就是历史，卡里写清这一矛盾的处理）。 5. platform_integration（`source_references`）。 6. platform_numbering（`numbering_rule_versions`，无 `version` 列，同 3 的前置）。 7. organization_workforce（先法人；员工、组织单元、岗位等自有化的卡；**前置**：自有化卡已定，且注册表补聚合 Employee、OrgUnit、Position）。
- 同时在开的代码 PR ≤ 3（one-coordinator 2026-10-17）；每卡 opus 复审；PR 标题带任务号。

## 9. 复核意见处理表

| 复核项 | 处理 |
|---|---|
| B1 回收 UPDATE | 第 3 节：转换表单独 `REVOKE UPDATE, DELETE, TRUNCATE`，并入 G34 核对 |
| B2 水位 | 第 5 节：回看窗口 + 按聚合断号检测；窗口长度度量设计定 |
| B3 清理通道 | 第 4 节：SECURITY DEFINER 函数 + 属主放行触发器，整聚合到期才删；作业另批 |
| S1 | 第 1 节：按 `cause_kind` 绑定 `command_id`、复合外键 `(reverses_transition_id, aggregate_id)`、`ck_…_first` 限制原状态为空 |
| S2 | 第 1 节：取变更之后的 version；核实默认 0；两张无 version 的表须加列 |
| S3 | 第 6 节：统一落表点 + 结构守护 + 行为守护兜底 |
| S4 | 第 5 节：左截断、迁入行 `occurred_on_date` 口径要证据、迁入操作者登记 |
| S5 | 第 6 节：不加 DDL CHECK，按聚合类型登记 + 行为守护；DDL 强制需 one-architecture 认可偏离 |
| S6 | 第 1 节：列名 `cause_kind` 交 one-architecture 定；词表查重已做 |
| S7 | 第 6 节：两端守护；`measurable_ddl_scan.py` 位置说明 |
| S8 | 第 8 节：增「模板与守护」公共卡；③标阻塞；④写清 roles；⑦写前置 |
| S9 | 第 7 节：认可前不进表结构 PR |
| N1 | 第 1 节 `ck_state_transitions_times` |
| N3 | 第 6 节：去掉 `n/a` 哨兵值 |
| N4 | 第 6 节：触发器命名条款提议稿 |

## 列名更正（2026-10-21，DdlNamingTest 首次建表时拦下）

模板列名按命名守护改为：`from_state`→`from_status`、`to_state`→`to_status`、`sequence_no`→`transition_sequence_number`、`occurred_on`→`occurred_on_date`、`aggregate_type`→`aggregate_type_code`（`_code` 列有格式 CHECK，G-DDL7）。理由：不放宽守护、不给模板表开豁免（CLAUDE.md 第 5 条）；`platform_file` 是第一个建表的 schema，现在改代价最小。约束名随列名（`ck_state_transitions_from_status` 等），其余形状不变。ADR-0058 正文中的旧列名由 one-architecture 在其修订中同步。

各 schema 的 `from_status`/`to_status` 带取值并集 CHECK（DdlNamingTest 要求 `_status` 列有取值 CHECK），`aggregate_type_code` 限定该 schema 的聚合类型，并可加按聚合成对限定状态的 CHECK；这三处取值由该 schema 的流转清单决定，其余列与约束逐字沿用模板（2026-10-21，platform_file 首次建表时定）。

**再更正（2026-10-21）**：`sequence_number` 与词条「流水号」（`platform.numbering`）同名，G39 要求列注释以「流水号」开头，语义不同会误导；本表的序号是聚合内第几次变更，列名定为 `transition_sequence_number`（约束名 `uk_state_transitions_aggregate_id_transition_sequence_number` 共 60 字符，在 63 以内）。不给守护开豁免。

## 子状态机聚合的记法（2026-10-21，B-31 AGG-115 起；与 one-architecture 对齐，ADR-0075 起草依据）

1. **每个有独立状态机的子实体各记各的转换**：`aggregate_type_code` 取子实体类型，`aggregate_id` 取该子行 ID，`legal_entity_id` 取该次命令的法人（根无法人归属时取命令请求上的法人）。注册表在 `aggregates.yaml` 里把这些子实体声明为「历史聚合」，并注明所属根。
2. **乐观锁与事务以根为准**：一个命令一个事务一个根（DATA_DESIGN_CHECKLIST §14 第 1 项不变）；**每条命令都推进根的 `version`**，写入时同时匹配根 `version` 与前置状态。
3. **转换序号取子行自己的 `version`**：子行每改一次状态自增 1；一条命令里同一子行状态变两次，`version` 增 2，写两条转换（序号 v+1、v+2）；无状态变化的写入不写转换，子行 `version` 是否推进以该子行是否变化为准，序号允许跳号，只要求每个 `aggregate_id` 内严格递增且唯一。
4. **目标版本由领域方法给出**：转换的 `transition_sequence_number` 与新状态由领域方法返回（子行新 `version`、新状态），仓储把它原样写入子行与转换行，不由仓储自行推算，也不在 SQL 里 `version + 1` 再回读。
5. 迁入初始转换（第 8 条）对每个子行各写一条。

## `status_of_aggregate` 的写法（2026-10-22，B-31 卡 A 验收测试抓出缺陷后补）

按聚合类型成对限定合法流转的 CHECK，**必须逐个 (from, to) 成对写**，并在外层用 `coalesce(… , false)` 包住：`from_status` 为空时，形如 `from_status = 'APPROVED' AND to_status IN (…)` 的分支求值为 NULL，而 CHECK 对 NULL 放行，`∅ → CONCLUDED` 之类的非法初始转换会被误接受；用 `coalesce(…, false)` 把未判定变成不通过。把"目标集合"与"来源集合"分开各写一个 `IN` 再相与（`to_status IN (…) AND (from_status IS NULL OR from_status IN (…))`）不够严：它会接受不在合法清单里的配对（例如 `∅ → CONFIRMED`、`PENDING → READ`）。迁入初始转换（`cause_kind = 'MIGRATION'`）可单列到任一允许的状态。platform_file 基线（#521）当前是后一种宽松写法，已请其以追加迁移改为成对写（见 B31-CARD-A-DDL 复核记录第三轮）。
