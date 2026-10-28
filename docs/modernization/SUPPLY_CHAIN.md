# 依赖与供应链规则 — FORCITIS one

日期：2026-10-15 · 适用范围：后端（Maven）、前端（pnpm）、CI 工作流与自托管 runner、测试镜像、发布包

**出处**：用户 2026-10-15 对原则核对 B 的待决项答「全部按推荐 (Recommended)」（含 Dependabot、osv-scanner、新版本 7 天冷却、AG Grid 1b 前随表格选型定）；同日单独决定时未选「给 CI runner 建专用 macOS 用户」（见 S6）。

发布链已有的做法（发布包签名、动作固定 SHA、`pnpm install --ignore-scripts` 等）继续有效；本文补的是日常依赖进出的规则。守护见 `NAMING_STANDARD.md` §4「待实现的守护」G-S1–G-S6；runner 的日常维护见 `CI_RUNNERS.md`。

## S1 依赖来源与锁定

- Maven 只用 Maven Central，`pom.xml` 不声明其他仓库；构建用严格校验和（`-C`）。
- pnpm 一律 `--frozen-lockfile`；允许执行安装脚本的包只限 `pnpm-workspace.yaml` 的 `allowBuilds` 白名单，新增须在 PR 中写理由。
- 新发布的版本满 7 天才引入（pnpm `minimumReleaseAge`，Maven 依赖由 Dependabot 冷却期控制）；安全修复可例外，PR 中写明对应的漏洞编号。
- **文档生成用的 Python 依赖**（不进发布包与 SBOM，只在 CI 的 `build_all.py --check` 与本地生成时用）：
  - PyYAML 6.0.3（MIT）：`docs/modernization/glossary/build_terms.py` 生成正式术语 `glossary/terms.yaml`，`build_all.py --check` 据此校验可复现（#186）。来源为本机 pyenv Python 3.12.13 的 site-packages，CI runner（`CI_RUNNERS.md`）的 PATH 先走同一 pyenv，因此 CI 与本地是同一份。缺少时 `--check` 明确失败，不跳过。
- **架构图本机生成工具**（不进发布包与 SBOM，不进 `frontend` 依赖，CI 不使用；用户 2026-10-15 批准「允许安装，图在本机生成（推荐）」，经 one-coordinator 转达）：
  - `@mermaid-js/mermaid-cli` 11.4.2（MIT，`npm install --save-exact`，装在仓库外 `~/.modernize/psa/tools/mermaid-cli`），依赖 puppeteer 23.11.1（Apache-2.0），它使用本机缓存 `~/.cache/puppeteer/chrome-headless-shell/mac_arm-131.0.6778.204`。只由 `docs/modernization/architecture/build_architecture.py --svg` 调用，把 `generated/*.mmd` 渲染成入库的 SVG；在线图页只嵌静态 SVG，不加载外部脚本或字体。
  - CI 与 `build_all.py --check` 只校验 mmd 可复现、SVG 未过期（`generated/svg-sources.sha256`），不运行该工具。
  - 其余文档脚本只用标准库（如 `backend/glossary/build_proposed_index.py` 自带 YAML 子集解析器）；新增 Python 依赖照本条登记。

## S2 升级节奏

- Dependabot 每周一次，按生态分组（Maven、pnpm、GitHub Actions）开 PR，照常过 CI 与复审。
- 框架（Spring Boot、Spring Modulith、Flowable、React、antd、Taro）的次版本每月评估一次；主版本升级走 ADR（METHODS M3「规则调整三类」 ADR 类）。
- 可被利用的漏洞：Critical 72 小时内修复或缓解，High 7 天内。

## S3 漏洞扫描

- osv-scanner 在每个 PR 与每次发布时扫描后端与前端的锁定依赖；并入已有 CI 作业，不新增作业（`CI_RUNNERS.md`「并发与缓存」）。
- 有修复版本的 Critical / High 漏洞使检查失败；豁免登记在仓库中，写明理由与到期日，期限不超过 90 天，到期仍在即失败。

## S4 许可证

| 类别 | 许可证 | 处理 |
|---|---|---|
| 允许 | Apache-2.0、MIT、BSD、ISC、0BSD、CC0、Unicode-3.0、BlueOak；OFL 只用于字体；EPL-2.0、MPL-2.0 在不修改其源码时 | 直接使用 |
| 需评审 | LGPL、EPL-1.0、CDDL、未声明许可证 | PR 中说明用法，调度会话请用户决定 |
| 禁止 | GPL、AGPL、SSPL、BUSL、带 Commons Clause 的许可证、未购买的商业许可 | 不得引入 |

- 许可证检查与 S3 同一工具、同一作业。
- **构建期工具的 GPL 例外**：GPL-2.0 加 Classpath Exception、只在编译期使用、不进发布包和 SBOM 的工具可以用；进运行时的仍禁止。出处：用户 2026-10-15 选「接受并补条款（推荐）」（intent `2026-10-15-112158-one-coordinator-接受-Error-Prone-传.md`，经 one-coordinator 转达）。
- **已核登记的构建期工具**（经注解处理器路径运行，不进发布包与 SBOM）：
  - Error Prone 2.50.0（`error_prone_core`），Apache-2.0；2026-06 发布，满 7 天；R-1b P1 引入（ADR-0033 守护 14）。只开 `CollectionIncompatibleType`、`EqualsIncompatibleType` 两条检查；`backend/.mvn/jvm.config` 为 Maven 的 JVM 开放 javac 内部包，这是 JDK 17 以后 Error Prone 的运行条件。
  - 传递依赖 `io.github.eisop:dataflow-errorprone`：GPL-2.0 加 Classpath Exception（与 OpenJDK 同一许可证），仅编译期使用，产物不含它。**已接受**（依据上条）。
  - 其余传递依赖：Guava、auto-common、jspecify、jsr305、javax.inject、caffeine 的 pom 声明 Apache-2.0，pcollections 为 MIT；java-diff-utils、google-java-format、auto-value 自己的 pom 不写许可证，但各自的父 pom（`java-diff-utils-parent` 4.12、`google-java-format-parent` 1.35.0、`auto-value-parent` 1.9）声明 Apache-2.0。核实依据是 2026-10-15 对本机 Maven 仓库中这些 pom 的逐个检查。
- 商业组件（如 AG Grid）在 1b 前随表格选型决定，未购买许可前不得进入主干。

## S5 SBOM

- 每个发布包附 CycloneDX 格式的 SBOM（后端与前端各一份），SBOM 的摘要写入发布包的签名声明，与发布包一起校验。

## S6 CI 工作流与 runner

- 工作流中 `uses:` 一律固定 40 位提交 SHA（可附版本注释）；禁用 `pull_request_target`；工作流权限按作业最小授予。
- **目标**：runner 以专用的非管理员用户运行，该用户的 HOME 中没有任何凭据（部署密钥、云凭据、`gh` 令牌、SSH 私钥），也不能写系统级工具目录。
  - **用户 2026-10-15 本次未选，风险已登记，待决**：现状是 4 个 runner 以开发者账号常驻运行（`CI_RUNNERS.md`），依赖与安装脚本在 CI 中执行时能读到本机凭据目录、改写本机工具链。在用户决定之前，S1 的锁定与 `allowBuilds` 白名单、S3 扫描是主要缓解；G-S6 待决定后实现。

## S7 测试镜像

- Testcontainers 使用的数据库镜像只在一处常量定义，写成「标签 + 摘要」（`postgres:<小版本>@sha256:…`），不用浮动标签；小版本与生产 RDS 一致，RDS 升级小版本时同步更新。
