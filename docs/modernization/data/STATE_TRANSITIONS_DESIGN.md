# state_transitions 数据设计（ADR-0058 落地；模板 DDL、登记、保留期、守护）

- 作者：one-data，2026-10-17。依据：ADR-0058（已采纳，用户 2026-10-17 批准，提交号 `ADR58-20261017-225508-gjl`，`claude/one-architecture-adr-governance@7cc6b31a`）的决定 1–8；输入 `data/STATE_TRANSITION_TABLES.md`。
- **状态：数据设计草稿，待独立复核后我再签发**（作者不自审自己的设计，门禁精神；请调度指定独立复核人，如 opus 子代理）。签发行出现前不开表结构 PR。
- 分工说明：我负责数据设计（本页）、门禁签发和表结构 PR 复核；kernel 值对象、六个 schema 的聚合改造与仓储落表、迁移脚本、守护代码是业务代码，按本会话画像由开发会话写，见末尾「派工建议」。

## 1. 模板 DDL（每个 schema 一份，同一份 Flyway 模板；`<schema>` 替换）

```sql
CREATE TABLE <schema>.state_transitions (
    id                      uuid        NOT NULL,
    aggregate_type          text        NOT NULL,
    aggregate_id            uuid        NOT NULL,
    sequence_no             bigint      NOT NULL,
    from_state              text,
    to_state                text        NOT NULL,
    occurred_on             date        NOT NULL,
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
    CONSTRAINT uk_state_transitions_aggregate_id_sequence_no UNIQUE (aggregate_id, sequence_no),
    CONSTRAINT fk_state_transitions_reverses_transition_id FOREIGN KEY (reverses_transition_id)
        REFERENCES <schema>.state_transitions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_state_transitions_aggregate_type CHECK (aggregate_type ~ '^[A-Z][A-Za-z0-9]{0,99}$'),
    CONSTRAINT ck_state_transitions_from_state CHECK (from_state IS NULL OR from_state ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_state_transitions_to_state CHECK (to_state ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    CONSTRAINT ck_state_transitions_sequence_no CHECK (sequence_no >= 1),
    CONSTRAINT ck_state_transitions_states_differ CHECK (from_state IS DISTINCT FROM to_state),
    CONSTRAINT ck_state_transitions_actor_type CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_state_transitions_cause_kind CHECK (cause_kind IN ('COMMAND', 'REVERSAL', 'CORRECTION', 'MIGRATION', 'SYSTEM_JOB')),
    CONSTRAINT ck_state_transitions_trigger CHECK (num_nonnulls(command_id, event_id) <= 1),
    CONSTRAINT ck_state_transitions_reversal CHECK ((cause_kind IN ('REVERSAL', 'CORRECTION')) = (reverses_transition_id IS NOT NULL)),
    CONSTRAINT ck_state_transitions_migration CHECK (cause_kind <> 'MIGRATION' OR from_state IS NULL)
);
CREATE INDEX ix_state_transitions_legal_entity_id_occurred_on ON <schema>.state_transitions (legal_entity_id, occurred_on) WHERE legal_entity_id IS NOT NULL;
CREATE INDEX ix_state_transitions_reverses_transition_id ON <schema>.state_transitions (reverses_transition_id) WHERE reverses_transition_id IS NOT NULL;
CREATE INDEX ix_state_transitions_recorded_at ON <schema>.state_transitions (recorded_at, id);   -- reporting 投影按水位增量取
-- 只追加（§14.1 D8、G34）：函数每个 schema 一份，触发器两个
CREATE FUNCTION <schema>.refuse_transition_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'state transitions are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_state_transitions_append_only BEFORE UPDATE OR DELETE ON <schema>.state_transitions
    FOR EACH ROW EXECUTE FUNCTION <schema>.refuse_transition_rewrite();
CREATE TRIGGER tg_state_transitions_append_only_truncate BEFORE TRUNCATE ON <schema>.state_transitions
    FOR EACH STATEMENT EXECUTE FUNCTION <schema>.refuse_transition_rewrite();
```
表注释、列注释（含「为空表示」）随模板；`created_at` 等于记录时刻、`updated_at` 恒等于 `created_at`（行不可改，同 F-1 子表口径）；时间列由应用传注入时钟，DDL 无 `now()`。运行角色的 `DELETE / TRUNCATE` 在 `one_db_roles_schemas.sql` §4b 回收（每 schema 一条）。

## 2. 与 ADR-0058 逐项对照

| ADR 条款 | 落点 |
|---|---|
| 1 主键 UUIDv7 | `id` |
| 1 aggregate_type / aggregate_id / sequence_no，(aggregate_id, sequence_no) 唯一 | 同名列与 `uk_…_aggregate_id_sequence_no` |
| 1 from_state（新建为空）、to_state | 两列；格式 CHECK 只定形不定值（值域由各 BC 的状态枚举与标记接口约束，模板同形） |
| 1 occurred_on 业务日期、recorded_at 记录时刻 | 两列；`recorded_at` 为注入时钟参数 |
| 1 legal_entity_id，无法人的登记理由 | 可空列；空时在 BC 注册表登记理由（`STATE_TRANSITION_TABLES.md`） |
| 1 actor_type / actor_id（系统操作者 UUIDv5，`system-actors.txt`） | 两列；`actor_id NOT NULL` |
| 1 cause 枚举（业务命令、冲销、更正、迁入、系统作业）、command_id 或 event_id、correlation_id、reverses_transition_id | `cause_kind` + `ck_…_cause_kind`；二选一 `ck_…_trigger`（至多一个）；`ck_…_reversal`（冲销/更正必有且仅它们有被抵消转换）；`fk_…_reverses_transition_id` 同表外键 |
| 2 只追加，回退也正向记 | 触发器；回退写新行，`cause_kind` 为 REVERSAL / CORRECTION |
| 5 索引（aggregate_id 加 sequence_no；legal_entity_id 加 occurred_on） | 唯一约束覆盖前者；`ix_…_legal_entity_id_occurred_on` 为后者（部分索引）。**我加了** `ix_…_recorded_at` 与 `ix_…_reverses_transition_id`（外键索引规则 O10；投影水位），请复核人确认 |
| 8 迁入写初始转换 | `ck_…_migration`：迁入的 `from_state` 必空 |

## 3. 需 one-architecture 回答的两点（不影响形状，影响 ADR 字面）

1. **一个事务内一个聚合产生多条转换**：`platform_approval` 里 `approval_requests` 与 `approval_tasks` 同属聚合 ApprovalRequest，同一次聚合版本变更可能既改请求状态又改任务状态，两条转换会撞 `(aggregate_id, sequence_no)` 唯一。请定：任务状态单独记成 `aggregate_type = ApprovalTask`、`aggregate_id = 任务 ID`、`sequence_no` 取任务自己的计数；或在唯一键里加一个「子对象」列。我倾向前者（次级实体有自己的 ID 与状态），但任务表有无自己的版本列要实现时核。同类情形还可能出现在其他聚合的次级实体（工时单无次级状态，目前只此一处）。
2. **`sequence_no` 取聚合的 `version`** 要求每次状态变更都使 `version` 加一，且同聚合不会有两条转换共用一个版本。`user_accounts`、`roles` 等用 `version` 做乐观锁，成立；`sync_runs` 已例外；`source_references`、`numbering_rule_versions` 实现时需核有无 `version` 列。

## 4. 登记

- `data-access-registry.yaml`，每个 schema 一条（新建，来源 NEW）：
  ```yaml
  <schema>.state_transitions:
    role: LOG_APPEND_ONLY
    mutability: APPEND_ONLY
    retention: <与所属聚合的业务记录相同；见第 5 节，待用户认可>
    lineage: {source: NEW}
    writers: [<本 BC 的模块>]
    readers: [reporting]      # 投影读，ADR-0058 第 7 条；cross_module 登记
    state_transitions_exempt: n/a   # 此表自己不适用
  ```
  有状态表本身按例外登记：`sync_runs`、`outbound_deliveries` 写 `state_transitions_exempt: <理由>`（`STATE_TRANSITION_TABLES.md`）。
- `data-classification.txt`：无新增列需登记（全是枚举、ID、时刻；无自由文本，无个人信息；`actor_id` 是代理键）。
- C4 允许写入内容：无自由文本、无 `detail`、无 `jsonb`；`from_state` / `to_state` 只能是状态枚举名。
- `aggregates.yaml`：不新增聚合；转换表归属其所服务的聚合所在 BC。

## 5. 保留期建议（D12，交用户认可；ADR-0058 第 6 条：与所属聚合的业务记录相同）

| schema | 所属聚合的保留期（现行） | 转换表建议 |
|---|---|---|
| cost_timesheet | 工时单 BUSINESS_RECORD（10 年，`RETENTION_PROPOSAL.md`） | 10 年 |
| platform_approval | 审批请求等 BUSINESS_RECORD（10 年） | 10 年 |
| platform_identity | 授权申请 10 年；账号 PERMANENT（含个人信息标记按 ADR-0043 R-2 须有限期） | 10 年；账号状态转换只含 ID 与状态，随账号存续，账号清理时一并 |
| platform_integration | `source_references` 人员类 10 年；`outbound_deliveries` 例外 | 10 年（`source_references`） |
| platform_numbering | `numbering_rule_versions` PERMANENT（配置） | 与规则版本同，永久（无个人信息） |
| organization_workforce | 法人 PERMANENT；员工/组织单元/岗位随自有化卡再定 | 先随法人永久，其余随自有化卡定 |

## 6. 守护（ADR 的「需同步修改的守护规则」）

- **结构守护**：实现了标记接口的聚合，所在 schema 必须有与模板一致的 `state_transitions`；例外逐行登记。扫描器 `scripts/ci/measurable_ddl_scan.py` 的 M3 已按「schema 有 `state_transitions` 或注册表 `state_transitions_exempt`」写好，可复用为 CI 检查；模板一致性（列、约束名、索引）另加一个比对 DDL 的守护。
- **行为守护**：每个 BC 仓储集成测试断言状态变化次数等于新增转换行数，含冲销路径（此为业务测试，开发会话写）。
- **只追加守护**：触发器 + §4b 回收；纳入 G34。

## 7. 派工建议（给 one-coordinator）

业务代码与迁移不归 one-data 写。建议按 ADR 分 7 张卡，每卡走门禁（本页签发后各卡引用）：①kernel `StateTransition` 值对象与标记接口；②cost_timesheet；③platform_approval；④platform_identity（授权申请、账号；roles 随 ADR-0034 PR-2）；⑤platform_integration（`source_references`）；⑥platform_numbering（`numbering_rule_versions`）；⑦organization_workforce（法人先做；员工、组织单元、岗位随自有化卡）。每卡 opus 复审。我负责：门禁签发、表结构 PR 复核（对照第 1 节逐字）、保留期交用户页。
