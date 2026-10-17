# NAM-1017-GLO-07：交 N7 的 C 类问题

日期：2026-10-17。Codex 起草；返工第 1 轮（扩范围并处理 GLO-06 opus 复审）。仅列待裁定问题，不作业务结论。原分类源 [reconcile-decided.json](reconcile-decided.json) 未修改。

原 C 类 3 条保持原提议全部字段与状态不变；本轮另从 B 类识别 3 条业务口径问题，用 `keep_proposed` + `escalate_to: C` 留在原提议文件。共 6 个待裁定条目，后两项是同一组取消／终止边界问题。

| id | 分类来源与 bc | 白话问题 |
|---|---|---|
| `tender_project` | 原 C 类 · `masterdata.counterparty` | 招标项目是否需要在系统内独立登记、维护标段和采购方式，还是仅保存外部招标编号供投标、合同和工程引用？ |
| `output_invoice` | 原 C 类 · `receivables.wip` | 系统是否把每张销项发票作为独立单据，维护开票结果与红字更正，还是只引用外部开票结果？ |
| `supplier_agreement` | 原 C 类 · `procurement.subcontracting` | 分包、采购、租赁、保险、居间、劳务派遣是否统一使用供方协议，还是各自独立管理？ |
| `design_change_claim_status` | 原 B 类 · `delivery.acceptance` | 设计变更是否只需“待主张／不主张（附原因）”两种状态？已经向客户主张后，应在本处显示什么？ |
| `project_terminated` | 原 B 类 · `delivery.execution` | 项目提前终止是否必须已经发生成本？请与“取消”的判定口径一并确定。 |
| `project_cancelled` | 原 B 类 · `delivery.execution` | 项目在开工前取消与执行中终止的区分，是否一定以“尚无成本／已有成本”为界？若已发生成本仍取消，应使用哪个状态？ |

N7 应把问题与既有业务契约、用户原话逐条核对后提交用户；未得到裁定前不转正、不合并、不弃用、不进入 forbidden。C 类不进入本脚本的 A/B 定稿通道。

另有 4 条维持提议，不改归 C：`ui_external_system`、`login_lookup`、`registration`、`evidence`。原因见 [B 类完整对照](B_CLASS_DECISIONS.md)。其中 `registration` 待 platform.integration 改名；`login_lookup` 待 C1 改形；两条治理／技术用语等待适合的登记载体，均不伪造领域实体。
