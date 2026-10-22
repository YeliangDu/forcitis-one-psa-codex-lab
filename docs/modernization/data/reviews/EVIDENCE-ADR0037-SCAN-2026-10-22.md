# 取证文件留存核查（ADR-0037 底线①：明细与个人信息不进仓库）

- 核查：one-data，2026-10-22；对象：origin/main 上全部 `evidence/` 目录下 115 个文件 = `docs/modernization/phase1a/infra/handoff/B-5-dba/evidence/` 105 个（SQL 查询 98、结果与登记 7）+ `docs/modernization/data/evidence/` 10 个（SQL 查询 6、说明与汇总 3、扫描脚本 1）。注：`docs/modernization/evidence/` 目录不存在，调度所指应是这两处合计。
- 方法：脚本逐文件统计手机号、18 位证件号样式、邮箱、IP、凭据字样（`password`、`secret`、`jdbc:` 等）、姓氏开头的括号内 2–3 字片段、内含汉字的 SQL 字面量、`VALUES (`；只看计数；命中处再用「数字→#、汉字→字N」脱敏后看形状。没有把任何内容贴进会话。
- 结果：**115 个文件均可留；没有发现明细或个人信息。**
  - 手机号、邮箱、IP：0 命中。
  - 18 位数字命中全部是老系统表单模板 / 表单定义 ID、选项 ID（出现在 `IN (...)` 过滤、`### 名称（ID）` 标题、`field####｜类型｜…｜选项 ID` 行）；是定义元数据，不是人员标识。
  - 凭据字样：`evidence_login.sql` 在服务端用 `CRYPT_GEN_RANDOM` 生成一次性口令，文件里无字面值；`c14_f_msdb_effective.sql` 的 `token` 是 `sys.user_token` 视图名；`credential_columns.csv` 是凭据列的**列名清单**，无值。
  - 姓名样式：除 `name_pattern_scan.py` 自己的自测样例外，只有 `eb6_amounts.sql` 的 5 处，脱敏看是表单分组标签 `N'字字'`，不是人名。
  - 结果类文件（`*_result.md`、`B31P_*`、`migration_volume_result.md`、`credential_columns.csv`）内容是表单字段定义、枚举取值计数、行数和聚合，没有逐行业务数据。
- 做不到的：没有逐字通读 115 个文件；汉字字面量里仍可能有未被样式命中的人名。依据是它们都是查询脚本或元数据汇总，没有导出行。
- 建议（不是必须）：把 `evidence/` 下新增文件纳入提交前扫描——复用 `data/evidence/name_pattern_scan.py` 加手机号、证件号、邮箱样式，命中即阻断；由 one-quality 排期。
