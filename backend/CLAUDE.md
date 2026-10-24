# backend 须知

- 写代码：`docs/modernization/CODING_STANDARD.md`（Java/Spring 看「2. Java / Spring」，测试看「5. 测试」，交付前看「7. 交付前自查与 PR」）
- 命名与包结构：`docs/modernization/NAMING_STANDARD.md`「2. 命名规则」「3. 包结构」
- 接口：`docs/modernization/NAMING_STANDARD.md`「2.6 HTTP 接口约定」
- 写 DDL 或迁移（`src/main/resources/db/migration/`）：`docs/modernization/NAMING_STANDARD.md`「2.4」「2.4a DDL 设计原则」，`docs/modernization/CODING_STANDARD.md`「3. SQL 与迁移」，数据设计按 `docs/modernization/DATA_DESIGN_CHECKLIST.md`「14.1」（迁入「14.2」、外部集成「14.3」）；改表须先有 design 签发（见 `docs/modernization/roles/design.md`）
- 新词：先查 `docs/modernization/glossary/terms.yaml`，没有就走用语登记（`docs/modernization/glossary/CLAUDE.md`）
- 测试容器只用 `backend/architecture/test-images.txt` 登记的镜像
