# API 构建输入

`openapi.json` 和 `src/generated/{schema,validators,operations}.ts` 不入库，也不发布到 generated-docs 分支。后端 Controller、响应类型与契约断言是输入；生成文件只供本次构建使用。

在仓库根执行（Java 25、Node 24、固定 pnpm；完整导出需要 Docker，本机禁止起容器时不要执行导出步骤）：

```sh
# 先安装前端依赖；离线缓存缺包时失败，不联网补装。
(cd frontend && pnpm --offline --config.verify-deps-before-run=false install --frozen-lockfile)
bash scripts/codegen/generate_api.sh -o
bash scripts/ci/light.sh
(cd frontend && pnpm --offline --config.verify-deps-before-run=false verify)
```

顺序固定为 `test-compile → surefire:test@export-openapi → generate:api → 消费方`。`OpenApiExportTest` 对真实 springdoc 输出执行原有路径、必填性、ID 分类与组件唯一性断言。导出前删除旧四件产物；导出失败不继续生成 TypeScript。已有本次导出的 JSON 时，可在 `frontend/` 单独运行 `pnpm generate:api`。

后端标准生命周期的 `process-test-classes` 仍先导出 JSON，之后才进入 surefire/failsafe；因后端验收也读取 TypeScript，`./mvnw verify` 或 `scripts/ci/backend_verify.sh` 前仍须完成上述四件生成。无容器选测用 `test-compile surefire:test -Dtest=...`，不要使用 `test` 生命周期（它会触发容器导出）。缺生成物即失败并提示生成命令，不自动起容器、不跳过测试。

CI 的 `api-contract` 作业在每次运行中导出、断言、生成并上传四件产物；backend、frontend、gitleaks 作业显式等待并下载本次运行产物。文档全景和主干轻量测试同样需要契约，因此文档及 push 事件也执行该前置作业。generated-docs 工作流只在构建全景之前生成输入，发布清单不包含这四件。nightly-legacy-it 先生成再运行两种测试插件；发布构建也从指定源码重新导出并生成后才构建应用。

前端 `verify` 先生成，再校验可复现性、类型、测试与构建。`build` 也先生成；直接选包测试或类型检查前须先运行生成命令。

原 CI `git diff --exit-code -- frontend/packages/api/openapi.json` 检测的是入库快照是否与当前代码一致。移出后该比较不再适用，由导出断言、新生成 TypeScript 的检查、类型检查和测试覆盖当前契约。**尚未等价替代的缺口**：没有与 main 上一次契约的结构兼容性比对，无法仅凭上述检查证明未破坏旧客户端。`NAMING_STANDARD.md` 的 G-C1 仍是待实现项。
