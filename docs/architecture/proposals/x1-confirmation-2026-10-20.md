# X1 补确认：平台服务 6 份 ADR 的技术写法

状态：评审中｜2026-10-20｜负责：one-architecture

依据：用户批准页 X1 选 (a)「先批，架构会话恢复后补确认；补确认结论与草案不同的，再回来问你；在此之前相关代码不落地」（intent 提交 95c61e88）；one-coordinator 2026-10-20 派工（intent 提交 b58965a5，W0 第 2 件）。草案在分支 claude/one-coordinator-two-lanes 的 `architecture/research/platform-fit-2026-10-20/adr-drafts/`。

| # | 事项 | 结论 | 与已批草案 |
|---|---|---|---|
| 1 | AI 外发闸落点与无环 | 用户首批即 3 个场景、分属 3 个 BC（工时 B-06、报销 B-12、经营数据查询 reporting；intent 2026-10-20-223058），AI 底座稿「第二个场景时抽成平台模块」的条件在落地时已满足。建议第二份 AI ADR 直接设 `platform.ai`（暂名）持有出口闸、限额与审计，依赖 `platform.audit`、`platform.integration`，不被二者依赖；现有平台边只有 audit → integration（`module-dependencies.txt` 第 12 行），合并后无环。在第二份 ADR 采纳前不落代码（同已批稿）。 | **不同**：已批稿写「首个场景在其所属 BC 设唯一 AI 调用服务」。新增平台模块属结构选择，须回问用户 |
| 2 | 签章记录引用保存类别 | 签章记录（库表行）不新增「注册表引用类别代码」写法：在数据访问注册表登记 `retention: {policy: EXPIRY, job: <签章模块>.record.purge}`（现有写法，`DataArchitectureTest` D4 已支持 EXPIRY + job，不改守护）；作业经文件保存期 api 取所引签前件、签后件到期日的较晚者，任一未到期或起算未发生即不处置。文件保存期稿 §5 第 2 条相应改为「其他模块的库表记录在注册表登 EXPIRY + 清理作业，作业经本模块 api 取到期日后自行处置」。 | 措辞不同、业务不变（保存年限与起算不动） |
| 3 | ADR-0051 改写措辞 | 文件服务基础稿两处改写可行（原句经核在 ADR-0051 第 39 行与第 45 行）。建议表 platform 行（第 78 行）改为不逐个枚举：「`platform.*`（按 `contexts.yaml` 中 kind: platform 登记）」，免得每份新平台 ADR 都改 ADR-0051；第 174 行「10 个平台模块不改名」指已建模块，保留。 | 措辞细化 |
| 4 | 合入次序 | 6 份同一 PR 占号入库，编号按依赖：文件服务-基础（改写 ADR-0051，其余依赖它）→ 文件保存期 → 在线预览 → 外部访问 → 签章服务 → AI 底座；ADR-0054、0009、0051 的状态字段同 PR 改。平台分档 ADR 以文件服务基础稿已采纳为前提（见其复审）。 | 一致 |
| 5 | `platform.idempotency` 措辞 | 该模块无 api 包，只做按 Idempotency-Key 的 HTTP 应答缓存（package-info；web 经 kernel 端口使用）。签章服务稿决定 6 第 4 条「幂等键…（`platform.idempotency`）」改为「幂等键…，由本模块唯一约束去重；HTTP 写入另按 Idempotency-Key 经 web 层应答缓存」；外部访问稿「只依赖 platform.audit、文件服务、platform.idempotency」去掉 idempotency。 | 措辞订正（盘点 D-5），业务不变 |
| 6 | idempotency 是否并入 web | 不并（盘点 P6 条件式结论：收益小、要动 schema 与注册表），本轮只订措辞。 | 一致 |

需回问用户：第 1 项。第 2、3、5 项为技术订正，业务内容不变，拟在占号入库时写进 ADR 并在各份「批准证据」注明「X1 补确认订正」，请 one-coordinator 判定是否也须回问。
