# 文件服务（platform.file）准入判断

状态：评审中｜2026-10-21｜负责：one-architecture

依据：ADR-0066 决定 6（准入九条）、决定 7（待准入到期条件）。触发：B-31 行政事务（organization.administration，one-1a-admin）2026-10-21 开工，是文件服务的首个使用 BC，到期条件「首个使用 BC 开工」成立（one-coordinator 2026-10-21 转达）。判定分工按决定 6：本会话写适配判断，one-coordinator 核对是否齐全，用户经 ADR 批准。

证据取自 origin/main（2026-10-21）。文件服务目前只落了 api 契约（`backend/src/main/java/com/forcitis/one/platform/file/api/`，25 个类型），没有实现、表与迁移。

## 逐条判断

| # | 条件 | 判定方式 | 结论 | 证据 |
|---|---|---|---|---|
| 1 | 使用面：≥2 个模块确认使用，其中 ≥1 个业务 BC | 过渡条款视同满足 | 满足 | 决定 6「过渡」：ADR-0060–0065 所设模块视同已满足第 1、8 条。另有实据：业务 BC——B-31 用印申请 AGG-113 含「用印事由与文件」，是最早使用方（ADR-0060 背景第 2 条，ADR-0054 先行期附件与历史版本保全）；平台模块——审批附件、签章服务送签与回收签后件（ADR-0060 决定「上传主体」「读取内容」两行、背景第 3 条，ADR-0064）。 |
| 2 | 不认识业务单据（决定 3 第 4 条、决定 4） | 人判，加复审 | 满足，待复审 | api 不枚举业务单据：绑定用 kernel `DocumentReference(DocumentTypeCode, DocumentId)`（`BindRequest.java:10`、`kernel/DocumentReference.java:7`），类型码经注册表校验；`FileSubjectKind` 只有上传主体种类 USER、ONE_TIME_LINK、PLATFORM_MODULE，不是单据类型。复审由 one-coordinator 指派。 |
| 3 | 依赖方向，无环 | 守护 | 满足 | `module-dependencies.txt:16` 只有 `platform.file -> platform.integration`（基础层可依赖 integration 的 api，决定 3 第 1 条）；同文件第 15 行注明 audit、identity、scheduling 随使用单元落地，三者都是基础层，允许。`ModuleDependencyTest`、`build_architecture.py` 在 main 上通过。 |
| 4 | 两面写清，策略项已登记 | 人判 | 满足 | 契约与数据形状面：api 10 个方法，表 `stored_files` 等与错误码 `FileError` 见 ADR-0060 决定表。策略面：大小与媒体类型白名单为配置项（ADR-0060 决定「写入内容」行）；下载链接时效默认 5 分钟、上限 15 分钟、超上限启动失败（「下载链接」行）；保存年限为版本化配置表 `retention_category_versions`（ADR-0061 决定 4，§14.1 D15、D17）；对象存储与检测引擎经 integration 端口（ADR-0060 决定 1 第 3 条、ADR-0070 决定 1 第 1 条）。三类载体都在决定 5 第 1 条允许的范围内。 |
| 5 | 契约先行，api 方法数 ≤12 | 守护（G4） | 满足 | api 先于实现入库（CS-1.10）；接口方法 `StoredFiles` 8、`FileImports` 1、`PlatformFileContent` 1，共 10 ≤ 12。 |
| 6 | 用语已登记 | 守护（G37） | 满足，主要概念待评审 | 模块名已在 `glossary/bcs.yaml:248`（文件服务，英文名 FileService，用户 2026-10-21 同意）；主要概念 46 条已登记为提议术语 `backend/glossary/proposed/platform-file.yaml`、`platform-file-areas.yaml`，待 one-glossary 评审；`GlossaryTest`（G37）在 main 上通过。 |
| 7 | 有 owner 与守护 | 守护 | 满足 | `contexts.yaml` 的 `owner: one-platform-file`，画像 `sessions/one-platform-file.md`；守护 `platform/file/guard/PlatformFileContentCallersTest`（只有平台模块能读内容）。 |
| 8 | 有已入库的 ADR | 过渡条款视同满足 | 满足 | ADR-0060、0061、0070 均已采纳并入库；`contexts.yaml` 的 `adr` 字段已列。 |
| 9 | 与第一个使用方同批建造，不预留 | 人判（CS-1.8） | 满足写入、关联、下载、检测；`FileImports` 视 B-31 迁入范围而定 | one-platform-file 已确认本批只建写入、关联、下载、检测（含 6b、5a、7），外部访问与迁入导入不提前建（one-coordinator 2026-10-21 转达）；one-1a-admin 的确认待到。**`FileImports` 判定**：其唯一调用方是迁入编排（接口注释与 ADR-0060 决定 7）。契约先行（CS-1.10）只许契约早于本批实现，不许为后续批次的使用方先放接口；第一个使用方不在本批，即使无实现也属预留。故：若 B-31 本批经迁入编排导入历史用印文件（由 B-31 迁入规格定，ADR-0060 决定 7 第 2 条），使用方同批，保留；否则由 one-platform-file 撤去 `FileImports` 及 `ImportRequest`、`ImportResult`、`ImportRejectionReason`，并同步 `PlatformFileContentCallersTest` 中的引用，待迁入编排开工时同批再建。不采用「标为待第一个使用方」：接口仍在 api 中，不能解决预留。 |

## 结论

九条中第 1、3、4、5、7、8 条满足；第 2、6、9 条实质满足，但各留一个待办：

1. 第 2 条：「不认识业务单据」按决定 6 须加复审。
2. 第 6 条：46 条提议术语待 one-glossary 评审。机器守护已通过，评审不阻塞准入，评审结论改名时随改。
3. 第 9 条：one-platform-file 已确认范围；待 one-1a-admin 确认 B-31 本批是否经迁入编排导入历史用印文件，据此保留或撤去 `FileImports`。

这三项闭合后，建议把 `contexts.yaml` 中文件服务的 `admission` 改为「已准入」。另提请 one-coordinator 注意：B-31 的 `organization.administration` 尚未在 `contexts.yaml` 登记，按该文件头部「新 BC 先在此登记再建模块」，须在 B-31 建模块前补登。
