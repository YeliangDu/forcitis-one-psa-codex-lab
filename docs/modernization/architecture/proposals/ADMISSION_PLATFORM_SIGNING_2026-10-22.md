# 签章服务（platform.signing）准入判断

状态：已确认｜2026-10-22｜负责：one-architecture

依据：ADR-0066 决定 6（准入九条）、决定 7（待准入到期条件）。触发：one-1a-admin 的 opus 复审（one-coordinator 2026-10-22 转来，Medium）指出，签章服务的待准入在 B-31 开工时已到期，main 上却写「准入判断在 one-platform-file 开工签章第一段时做」。

证据取自 origin/main（2026-10-22）。签章服务目前只有登记：`contexts.yaml` 中 status planned、admission 待准入、owner one-platform-file；`glossary/bcs.yaml:236` 有模块名；没有 api、实现、表、迁移，也没有 `module-dependencies.txt` 行。

## 到期判断

ADR-0066 决定 7 的到期条件是「W1 结束，或该模块的首个使用 BC 开工，两者取早」。B-31 行政事务（`organization.administration`）是签章服务的首个使用 BC（ADR-0064 决定 1 第 3 条、决定 3 第 4 条：用印申请引用印章台账条目），2026-10-21 开工。**到期条件已在 2026-10-21 成立。**`contexts.yaml` 中「准入判断在 one-platform-file 开工签章第一段时做」与决定 7 不符，本裁定删去。

## 逐条判断

| # | 条件 | 判定方式 | 结论 | 证据与缺口 |
|---|---|---|---|---|
| 1 | 使用面 | 过渡条款视同满足 | 满足 | 决定 6「过渡」：ADR-0060–0065 所设模块视同已满足第 1、8 条。实据：B-31 用印申请引用台账条目（ADR-0064 决定 3 第 4 条）；后续 `contract.agreement` 签署证据、`delivery.acceptance` 图纸签章登记（决定 1 第 3 条）。 |
| 2 | 不认识业务单据 | 人判，加复审 | 设计满足，待 api 复审 | ADR-0064 决定 1 第 3 条：不依赖调用方、不回调。签章记录写「请求方与申请 ID」（决定 7 第 1 条），api 须用 kernel `DocumentReference`，不得枚举业务单据；api 入库时复审。 |
| 3 | 依赖方向，无环 | 守护 | 设计满足，未落地 | 设计只依赖 `platform.audit`、`platform.integration`（决定 1 第 3 条），都在基础层。`module-dependencies.txt` 无行，随 api 一并登记。 |
| 4 | 两面写清，策略项已登记 | 人判 | 部分满足 | 数据形状面：台账粒度、排他约束、版本化已写（决定 3）。策略面：印章类型取值、电子印章就绪开关（A1b 两段切换）的载体未登记（决定 5 第 1 条三选一）。 |
| 5 | 契约先行，api 方法数 ≤12 | 守护（G4） | **未满足** | 无 api。 |
| 6 | 用语已登记 | 守护（G37） | 满足（待 #564 合入） | one-glossary #564（`claude/one-glossary-signing`）：模块标识 `platform.signing`、schema `platform_signing`、权限码前缀 `platform.signing`；提议术语印章台账、印章台账条目、印章台账条目版本、印章类型、签章记录；`seal` 只指印章，文件封存用 `hold`。 |
| 7 | 有 owner 与守护 | 守护 | **未满足** | owner 有（one-platform-file）；无守护落点。 |
| 8 | 有已入库的 ADR | 过渡条款视同满足 | 满足 | ADR-0064 已采纳。 |
| 9 | 与第一个使用方同批建造，不预留 | 人判（CS-1.8） | **未确认** | 第一段只建先行上线所需：实体章的印章台账（含版本表）与用印登记所用的台账查询；电子用印、CA、图纸签章不在本批（A1a、A1b）。使用方已确认（one-1a-admin 2026-10-22）：只用①台账有效版本查询（按台账条目 ID 与用印时点取版本，缺版本或早于最早版本即失败告警）、②用印申请与用印登记对台账条目 ID 与所用版本的引用，面向实体章；不用电子用印、签章记录、签前件与签后件派生、证书元数据、图纸签章，不开服务商回调与公开入口；台账维护权限归签章服务。待 one-platform-file 确认建造范围。 |

## 裁定

1. **已到期、未补齐**：第 4、5、6、7、9 条未齐，签章服务仍为「待准入」，`contexts.yaml` 的 `admission_due` 改写为到期事实，不再推迟到「签章第一段」。
2. **用印 7.3、7.4 不得开工**，直至签章服务准入（决定 7「到期未补齐：首个使用 BC 不得开工」）。B-31 中不使用签章服务的施工卡不受本条影响。
3. **补齐路径**（按此次序）：
   1. one-glossary：登记模块标识、schema、权限码前缀，以及台账、台账条目、台账条目版本、签章记录的提议术语（第 6 条）——已登记，#564。
   2. one-platform-file：按第 9 条范围出 api 契约（方法数 ≤12，用 `DocumentReference`）、`module-dependencies.txt` 行与守护落点，登记印章类型与电子印章开关的载体（第 4、5、7 条）；api 入库时请 opus 复审第 2 条。
   3. one-1a-admin：确认 B-31 第一段只用台账查询与实体章的用印登记引用（第 9 条）——已确认，2026-10-22。
   4. 以上齐后，one-architecture 复核九条，改 `admission: 已准入`。
4. **报用户（决定 7）**：B-31 已于到期当日开工，与决定 7「首个使用 BC 不得开工」的字面不一致；本裁定把限制落到实际用到签章服务的 7.3、7.4。是否认可这一落法，由 one-coordinator 报用户（进收件箱）；用户不认可时，B-31 其余施工卡也一并停下。

## 用户认可与护栏（SGN，2026-10-22）

用户选「只停 7.3、7.4，其余照常」（提交 `SGN-20261022-065336-7ox`，intent `2026-10-22-065356`），并要求写成可核对的判据：

1. **判据**：一张已开工卡的验收项与命令、接口清单里都不含签章服务调用，才算不依赖签章服务；出现一项即视为该卡有依赖。
2. **护栏**：任一张已开工卡若要加签章（如资产领用改电子签收、维修单盖章验收），该卡就地停下，并入 7.3、7.4 队列等准入；不得以「只加了一点」为由继续施工。
3. **核对线下盖章**：由 one-1a-admin 确认其余卡没有未建模、但线下实际要盖实体章的动作；有的归 7.4。

核对记录（one-1a-admin 核查，one-coordinator 抽查 #561 依赖表无指向 `platform.signing` 的边，2026-10-22）：

- 已开工的卡都没有调用签章服务。
- 7.5 至 7.9 与合同原件借阅中，没有未建模的线下盖章动作。
- 归入 7.4 队列：用印审批人表、预盖空白页登记、过渡通道及其季度与月度作业、历史用印迁入映射。


## 九条复核（2026-10-22 晚，one-architecture）

证据：PR #587（`claude/one-platform-signing-api` @1f7ae51a6）与其中的 `phase1a/signing/CONTRACT_DRAFT.md`；opus 复审第 2 条通过（one-platform-file 转来）。

| # | 结论 | 依据 |
|---|---|---|
| 1 | 满足 | 过渡条款，同上表 |
| 2 | 满足 | api 4 个方法只收台账条目 ID、`LegalEntityId`、印章类型码、时刻或日期，不收任何业务单据参数；`DocumentReference` 随电子用印批的签章记录使用，第一段不预留。opus 复审按此口径通过 |
| 3 | 满足 | `module-dependencies.txt` 增 `platform.signing ->`（第一段无依赖）；`ModuleDependencyTest` 按传递闭包断言：可达的业务模块只许 `organization.workforce`，直接依赖不超出 ADR-0064 所列加 `platform.identity`（见下附），`platform.approval` 不可达签章服务 |
| 4 | 满足 | 契约 §4：印章类型取值与电子印章就绪开关都放 D15 版本化配置表（开关列随电子印章批建）；条目载体是 D17 版本表上的事实列 |
| 5 | 满足（随 #587 合入） | `platform.signing.api.SealLedger` 4 个方法 ≤12 |
| 6 | 满足 | #564 已合；错误码中文名另交 one-glossary |
| 7 | 满足 | owner one-platform-file；守护落点见契约 §5（本批落 1、3、5、6 项） |
| 8 | 满足 | ADR-0064 |
| 9 | 满足 | 建造范围：one-platform-file 契约 §1；使用方确认：one-1a-admin 2026-10-22，见上 |

**裁定**：#587 合入 main 当时，九条全部满足，`contexts.yaml` 改 `admission: 已准入`（one-architecture 在 main 上改，证据指本节）；改后用印 7.3、7.4 即可开工。

**附：`platform.identity` 依赖。**ADR-0064 决定 1 第 3 条所列依赖里没有 `platform.identity`。台账与印章类型清单的维护端点要做权限校验，这是每个带 web 端点的模块都有的依赖；identity 属基础层，签章服务属公共服务层，按 ADR-0066 决定 2 的分层是向下依赖，不成环。本裁定认可签章服务在维护命令落地时依赖 `platform.identity`，只用于当前用户与权限；ADR-0064 该条随下一批 ADR 订正补列。`context-map.yaml` 中 `platform.identity>platform.signing` 的 basis 应改为「ADR-0066 决定 2；本节」，不写 ADR-0064。
