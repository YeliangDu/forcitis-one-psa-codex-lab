# 设计正本差距清单 — Forcitis One Design System

正本：Claude Design「Forcitis One Design System」（用户 2026-10-14 决定）。用户要求：「还不完善在现代化过程中不断完善」。写入正本已获授权（用户 2026-10-14：「授权你往正本写入设计」），补全方式为**代码为源**（用户 2026-10-14 选定）：在 FORCITIS one 仓库的 `shared/ui` 中实现，再经 `/design-sync` 同步回正本。规则见 ADR-0013 第 7 条：**先补正本，再写页面**；每个阶段开始时列差距、结束时清零。

正本现有（只读读取，2026-10-14）：
- 基础：令牌 palette / semantic / typography / spacing / elevation / motion，Icon（Lucide 语义名），Wordmark，AiMark；
- 组件：Button、StatusBadge、Panel、StatCard、ApprovalPath、AiAssistCard、CommandPalette、AppShell、DataGrid、LedgerTable、WbsGrid、MetricBar、WeekStrip、MonthHeatCalendar，表单件（含 MoneyInput、Money 万 / 亿、ValueHelp、TreeSelect），反馈件（Dialog、ConfirmDialog、Toast、Alert、Steps、Tabs），导航件，BottomSheet，TimesheetRow、TimesheetKpiBar；
- 套件 / 模板：外壳概览、过账批次与修复抽屉、关账驾驶舱、工时（PC + H5）、费用采集工作台；
- 正本自列路线图：对账工作台、发票 / WIP（D8）、应收与回款（D9）、跨法人结算（D11）、文件上传、时间选择、富文本。

| 阶段 | 需要而正本缺失 | 状态 |
|---|---|---|
| 1a 平台桥接 | 合并待办收件箱（新旧系统待办合并、行级审批）；登录页（企业微信 OAuth / 致远 SSO 入口）；权限不足 / 会话过期页 | 待补 |
| 1b 报价 | 报价单文档视图（条款拆分、毛利、印章）；成本估算树（可基于 WbsGrid 扩展）；毛利低于阈值的说明范式 | 待补 |
| 2 交付 + 工时 | 项目记录页（页头、页签、关键指标）；甘特 / 进度与 ETC；交付物提交与验收；预算修订对比 | 待补（工时已有） |
| 3 差旅报销 | 分贝通订单与对账单视图 | 待补（费用工作台已有） |
| 4 成本、收入与核算 | WIP 与开票工作台；收入确认三轨（已确认 / 已开票 / WIP）；对账工作台；分摊结果与凭证预览 | 待补（过账、关账已有） |
| 5 身份与下线 | 用户、角色、权限、数据范围管理页 | 待补 |
| 通用 | 文件上传、时间选择、富文本（周会纪要，须配合 XSS 净化） | 待补 |

「FORCITIS one（Storm）」中有可参考的交互范式（ApprovalInbox、RevenueRecognition、WipWorktable、CollectionWorktable、ChangeOrderList），补正本时只借鉴交互，按正本令牌重新设计。
