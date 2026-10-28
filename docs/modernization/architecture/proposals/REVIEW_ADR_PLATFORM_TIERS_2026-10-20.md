# 复审：ADR 草案「平台模块分档、冻结与准入」

状态：历史记录｜2026-10-20｜负责：one-architecture

对象：分支 claude/one-coordinator-two-lanes 的 `architecture/research/platform-fit-2026-10-20/adr-drafts/ADR-草案-平台分档冻结与准入.md`，依据稿 `平台分档适配复核.md`、`平台模块盘点.md`。复审：opus 子代理首轮（只读），one-architecture 抽查事实（DocumentType 被 platform.web、platform.numbering 引用；contexts.yaml 中 scheduling、migration 为 kind: platform；integration/api 含 LegacyInbox、LegacyTodo、SourceObjectKind 等）后定稿。派工：one-coordinator 2026-10-20（intent 提交 b58965a5，W0 第 5 件）。

**结论：改后可采纳。** 框架与依据扎实；须先改 8 处，其中 4 处要用户定。

## 必须改

1. **拆份**（背景「一份一决定」、U1）：`adr/README.md` 生命周期第 7 节要求可分别采纳的选择拆成两份；草案自认三者可分别采纳，U1 不应作选择题。拆为「分档、依赖方向与准入退出」「冻结与变更通道」两份，后者引用前者。
2. **numbering 改判不成立**（决定 1 第 2 条、决定 3 第 4 条、U5）：「不枚举业务单据类型」对基础层与骨架层同样适用，改判骨架层不能豁免 `kernel.DocumentType`（QUOTE、TIMESHEET…）；`platform.web` 的 OpenApiProblems 也引用它。二选一：明文允许骨架层经登记读业务分类并说明理由，或把 DocumentType 列为有期限的登记例外；U5 选项据此重写。
3. **准入「同时满足 9 条」与已登记矛盾**（决定 7 对决定 2 第 2 条）：文件服务、签章服务、一次性链接第 6、7 条（用语、负责会话）仍不满足却已登记。加过渡条款：6 份已批稿视同满足第 8 条与适配判断，第 6、7 条登记时核对，未齐的登「待准入」，不倒逼重走评审。
4. **档位性质自相矛盾**（决定 2 第 2 条「参考性快照、不需新 ADR」对决定 8 第 2 条「改档一律正式 ADR」）：照 ADR-0051 决定 1 分法，档位为规范性，所含内容与使用方为参考性。
5. **与 ADR-0035 第 2 条的关系须在起草阶段定**（决定 6）：该条（用户已定）允许首次生产前直接改契约；T-A、T-B 在首次生产前加审批，实质限制它，「拟判相容」不成立。选 T-A/T-B 须在「取代」写「修订 ADR-0035 第 2 条（限平台模块）」；先行上线是否算首次生产须先定（背景写成事实、决定 6 第 3 条又写待核，自相矛盾）。
6. **T-C 与已定规则冲突**（决定 6 第 3 条）：「公共基础先行」、§13 第 4 条是用户采纳的规则，要求派功能代理前冻结；T-C 以首次生产为界会推翻它们，选项须写明。
7. **集成端口按基础层冻结的前提不成立**：`platform/integration/api` 含企业微信与老系统专用类型、`SourceObjectKind` 随迁入持续增长；ADR-0039 决定 6 仍留中性化项与 C1 登录拆分；ModuleDependencyTest 按模块判定，分不出端口与适配器。端口按 `api` 包界定，ADR-0039 未清项列冻结例外；identity 补「C1 联合身份定案前不冻结」（复核 §2.4 有、草案漏）。
8. **守护第 1 条采纳当天即失败**：scheduling、migration 在依赖表中无调用方，过不了「至少 2 个使用方出处」；`consumers` 手写 import 边与 `module-dependencies.txt` 重复（违 ADR-0041 决定 4）。import 类使用方由生成器从依赖表推出，只手写 ADR 与 intent 出处；scheduling 改 `kind: technical` 或给豁免；采纳 PR 以 U6 先答为前提。

## 建议改

1. 组织地基：`organization.workforce` 是 W0 正按 ADR-0055 重建的业务 BC，只冻结平台实际用到的接口。
2. U3 补后果：approval、identity、numbering、audit 已有第二使用方，选 T-B 等于采纳当天冻结，与 TRN-03 扩 approval 冲突；T-A 的代价是 W1 起每次兼容新增都要审核。
3. 取代：ADR-0051 决定 2 第 1 条写成「依赖文件服务基础稿先采纳」的硬前置；U6(a) 需命名空间例外，部分取代 ADR-0057 决定 3 第 6 条，应列入。
4. 用语：「组织地基」与 ADR-0051 决定 9「地基 BC」、横切矩阵「地基行」同名异义；「冻结」「准入」在词表已有「预算冻结」「开票准入」；`tier` 取值是标识符，采纳 PR 前定名。
5. 可查性：复核稿路径写作 `platform-review/…`，实际在 `architecture/research/platform-fit-2026-10-20/`；决定 2 所依 `ssot/01-stakeholders/SSOT.yaml` 不在 main 与本分支，宜写明字段定义；文件服务 api「已用 11/12」与已批稿「api 7 项」须核对。三条 intent 已核实存在。
6. 决定 7 每条准入条件标「守护」或「人判」：第 1 条「全局隐式使用」、第 2、9 条与「第二种变化原因」不可机判。
7. 决定 6 第 1 条：§6 的冻结对象含前端共用包，覆盖或声明不在范围。

## 可选

1. 回滚补「撤销冻结时快照基线一并删除」。
2. 决定 8 第 4 条：AI 外发闸条件已触发（用户首批 3 个场景，intent 2026-10-20-223058），可写「准入条件已触发，待 X1 补齐」。

## 需用户决定

1. 首次生产前冻结平台契约，是否作为对 ADR-0035 第 2 条的修订批准。
2. 冻结时点 T-A / T-B / T-C（按上面补齐代价后问；T-C 同时问是否改 §6、§13）。
3. 组织地基冻结全部 api，还是只冻平台用到的部分。
4. U1 改为告知「按规则拆为两份」，不作选择题。
