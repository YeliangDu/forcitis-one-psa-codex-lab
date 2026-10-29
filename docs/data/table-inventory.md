# table_inventory.csv：全库表清单（机器可读）

- 生成：`python3 docs/data/build_table_inventory.py --root <含 backend/ 与 scripts/ci/ 的检出或导出> --object-list docs/data/table-object-list.md --aggregates ENTERPRISE_AGGREGATES_DRAFT.md --finance … --people … --revenue … --out docs/data/table-inventory.csv`。作者 one-data，2026-10-17；输入快照：`origin/main` 的注册表与迁移（已建表 52 行，含 `act_*` 通配一行）、#476 两张、F-1 三张、ADR-0058 六张 `state_transitions`、三组提议 362 表项，共 425 行。
- 列：`schema, table, bc_module, bc_id, aggregate, obj, kind, has_status, legal_entity_column, source, registry_role`。
  - `kind`：业务记录 / 版本 / 关系 / 投影 / 技术。已建表按注册表 role 映射（技术：QUEUE、COUNTER、IDEMPOTENCY、FRAMEWORK、ONE_TIME、LINEAGE、LOG_APPEND_ONLY、PLACEHOLDER，外加我判为技术的 `sync_*` 三张；版本：DIMENSION_VERSIONED 或表名以 `_versions` 结尾；关系：五张法人归属/权限关系表；投影：SNAPSHOT 四张，自有化后会变）；提议表按 yaml 的 role（root、child→业务记录，version→版本，link→关系，staging→技术）。**`registry_role` 原样保留，映射可以重做**。
  - `obj`：已建表来自 `TABLE_OBJECT_LIST.md` 我的判断（平台类无对象，留空）；提议表取聚合稿中该聚合名称里括号标的 OBJ（没标则空）。**空 ≠ 没有对象**，是未判断或平台对象。
  - `has_status` / `legal_entity_column`：已建表来自 DDL 实扫（状态列：名为 status 或以 `_status` 结尾；法人列：列名含 `legal_entity`）；提议表只有列族名，按「族名含『状态』/『法人』」判 yes/no，**是关键字证据，不是 DDL**。
  - `bc_id`：聚合稿 BC 表的 B-NN；平台类无 B 编号则空。B-31（行政事务）尚无模块命名空间，`schema` 写 `tbd`（13 行）。
  - `source`：已建 / 提议·组·未签发 / 提议·F-1（已签发）/ 提议·ADR-0034-IMPL PR-1（#476，已复核）/ 提议·ADR-0058（设计第 2 版，待复核签发）。
- 提议表的 `schema` 由 BC 模块名把 `.` 换成 `_` 推出（如 `cost.costing` → `cost_costing`），尚无 DDL，表名 yaml 约定不含 schema，同名表在不同 schema 可能重复，读时用 `schema + table` 作键。
- 已知缺口：`REF-01`（外部引用登记维护单元）聚合名称在聚合稿查不到；三组提议表的 `obj` 只有聚合稿标了 OBJ 的才有值；技术表的最终归属等《业务对象界定标准》。
