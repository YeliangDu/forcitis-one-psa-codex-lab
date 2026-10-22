# 分析维度候选字面差异

由 extract_dimensions.py 生成，勿手改。

仅并列来源，不合并、不判同义。版本化主数据表，非分析维度。

按去除 Markdown 单元格外围空白后的完整字符串去重比对；保留括注、组合表达式与占位符，不拆分、不判同义。

总线矩阵包含 14 个原始表头（含业务过程事实、粒度、事实 BC（来源）、阶段）；每列保留全部 16 行，标记不解释。

## 仅总线矩阵列名出现（14）

- `业务过程事实`：`docs/modernization/BI_SCOPE.md:190`
- `事实 BC（来源）`：`docs/modernization/BI_SCOPE.md:190`
- `人员`：`docs/modernization/BI_SCOPE.md:190`
- `供应商`：`docs/modernization/BI_SCOPE.md:190`
- `合同 / 条款`：`docs/modernization/BI_SCOPE.md:190`
- `客户`：`docs/modernization/BI_SCOPE.md:190`
- `成本类型`：`docs/modernization/BI_SCOPE.md:190`
- `服务产品`：`docs/modernization/BI_SCOPE.md:190`
- `期间`：`docs/modernization/BI_SCOPE.md:190`
- `法人`：`docs/modernization/BI_SCOPE.md:190`
- `粒度`：`docs/modernization/BI_SCOPE.md:190`
- `组织`：`docs/modernization/BI_SCOPE.md:190`
- `阶段`：`docs/modernization/BI_SCOPE.md:190`
- `项目 / WBS`：`docs/modernization/BI_SCOPE.md:190`

## 仅指标基线 OAT 完整单元格出现（39）

- `AR`：`M-13 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:82`
- `AVG_HEADCOUNT`：`M-38 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:122`
- `BCR`：`M-18 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:87`
- `BILL`：`M-10 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:79`
- `BURN`：`M-47 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:131`
- `CA`：`M-17 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:86`
- `CASH 相关`：`M-12 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:81`
- `CM、CMR`：`M-29 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:103`
- `CM（层级变体）`：`M-30 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:104`
- `COLR`：`M-19 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:88`
- `CONV`：`M-04 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:68`
- `DC / COGS`：`M-27 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:101`
- `DE`：`M-25 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:99`
- `DE / OE`：`M-33 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:107`
- `DL`：`M-24 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:98`
- `DSO`：`M-16 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:85`
- `EMPLOYEE_SEPARATIONS ÷ AVG_HEADCOUNT`：`M-39 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:123`
- `HRS 拆分`：`M-43 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:127`
- `IMP`：`M-20 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:89`
- `NR`：`M-28 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:102`
- `NRPM（参照）`：`M-45 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:129`
- `OCF`：`M-36 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:115`
- `ODAR 的分子`：`M-15 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:84`
- `OH`：`M-31 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:105`
- `OP、OPM`：`M-32 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:106`
- `PIPE`：`M-01 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:65`
- `PIPE（近似）`：`M-07 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:71`
- `PLAN_TARGET_VALUE`：`M-40 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:124`
- `PTC / DC`：`M-26 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:100`
- `REV`：`M-08 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:77`
- `REV、P、OP（标准层）`：`M-35 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:109`
- `RPO`：`M-06 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:70`
- `SIGNED_NET_BOOKINGS 的变体（不扣转付）`：`M-05 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:69`
- `UR`：`M-42 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:126`
- `WO`：`M-21 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:90`
- `WON_PROPOSAL_VALUE、WR`：`M-02 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:66`
- `—`：`M-09 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:78`；`M-11 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:80`；`M-14 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:83`；`M-22 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:91`；`M-23 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:92`；`M-34 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:108`；`M-37 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:116`；`M-41 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:125`；`M-44 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:128`；`M-46 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:130`
- `—（待定扩展原语）`：`M-03 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:67`
- `启动会就绪检查、NPS 相关原语`：`M-48 docs/modernization/r0/METRIC_BASELINE_DRAFT.md:132`
