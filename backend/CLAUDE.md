# backend 须知

- 先读：`docs/rules/coding-core.md` 全文，再按下列指向查细则
- 写代码：`docs/rules/coding-standard.md`（Java/Spring 看「2. Java / Spring」，测试看「5. 测试」，交付前看「7. 交付前自查与 PR」）
- 命名与包结构：`docs/rules/naming-standard.md`「2. Naming rules: English, industry-idiomatic, predictable」「3. Package structure: where code goes」
- 接口：`docs/rules/naming-standard.md`「2.6 HTTP interface conventions」
- 写 DDL 或迁移（`src/main/resources/db/migration/`）：`docs/rules/naming-standard.md`「2.4 Column-name class words, constraints and indexes」「2.4a DDL design principles」，`docs/rules/coding-standard.md`「3. SQL 与迁移」，数据设计按 `docs/rules/data-design-checklist.md`「14.1」（迁入「14.2」、外部集成「14.3」）；改表须先有 design 签发（见 `docs/rules/roles/design.md`）
- 新词：先查 `docs/glossary/terms.yaml`，没有就登记提议术语（`docs/glossary/CLAUDE.md`）
- 测试容器只用 `backend/architecture/test-images.txt` 登记的镜像
