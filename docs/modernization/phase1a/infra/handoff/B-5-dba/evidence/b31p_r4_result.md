# B31P 第四批取证结果（one-ops，2026-10-17，one_evidence_ro 只读；one-data 草稿 @4638ceb9，one-ops 审后调整）

来源：`b31p_r4_q5a.sql`、`b31p_r4_q5b.sql`、`b31p_r4_q6a.sql`、`b31p_r4_q6b.sql`（SQL 调整见各文件头注释）。字段定义、枚举项、流程定义属性取值画像与节点权限名都是空白配置，不含实例、成员或标识；Q6-A 对「有 2–3 个汉字取值的属性」（可能是人名）整属性不出值，含 8 位以上连续数字的取值不出，出值的汉字取值经人工核对为角色名、节点名或条件表达式；**其中新增报价流程条件表达式里有 1 处硬编码姓名，已在本文件隐去为「〔具体人员〕」（one-data 2026-10-17 发现，见 legacy-read 登记补记），此前「无人名」的说法不成立**。

## Q5-A 表单字段定义（模板 → 字段：标签｜键｜物理类型｜长度｜可空标志｜枚举引用 ID）

### 需求工单（模板 -803037765431705053，表单 2872786304158091915「IT需求工单」，31 个字段）

- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 流水号｜field0117｜VARCHAR/text｜100,0｜null｜—
- 员工编号｜field0021｜VARCHAR/text｜100,0｜null｜—
- 员工姓名｜field0018｜VARCHAR/text｜1000,0｜null｜—
- 所在部门｜field0025｜VARCHAR/department｜20,0｜null｜—
- 需求类型｜field0105｜DECIMAL/select｜20,0｜null｜2326016881727851812
- 详细分类｜field0119｜DECIMAL/select｜20,0｜null｜2326016881727851812
- 优先级｜field0110｜DECIMAL/select｜20,0｜null｜1534654868854620891
- 问题截图｜field0109｜VARCHAR/attachment｜20,0｜null｜—
- 详细描述｜field0112｜VARCHAR/textarea｜2000,0｜null｜—
- 处理结果｜field0123｜DECIMAL/select｜20,0｜null｜7089441405752199241
- 处理人｜field0124｜VARCHAR/member｜20,0｜null｜—
- 处理时间｜field0121｜DATETIME/datetime｜255,0｜null｜—
- 解决方案｜field0125｜VARCHAR/textarea｜2000,0｜null｜—
- 历史需求工单｜field0090｜VARCHAR/5902128098173592526｜255,0｜null｜—
- 创建人1｜field0114｜VARCHAR/member｜20,0｜null｜—
- 创建时间1｜field0115｜DATETIME/datetime｜255,0｜null｜—
- 是否已归还｜field0126｜VARCHAR/checkbox｜5,0｜null｜—
- 归还时间｜field0127｜DATETIME/datetime｜255,0｜null｜—
- 归还人｜field0128｜VARCHAR/member｜20,0｜null｜—

### 用印申请（模板 16074125293700，表单 3996442246046958045「用印申请」，40 个字段）

- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 申请人｜field0029｜VARCHAR/member｜20,0｜null｜—
- 申请部门｜field0032｜VARCHAR/department｜20,0｜null｜—
- 申请单位｜field0034｜VARCHAR/department｜20,0｜null｜—
- 用印日期｜field0054｜TIMESTAMP/date｜255,0｜null｜—
- 印章类型｜field0041｜VARCHAR/select｜20,0｜null｜-3472409557441036019
- 商机名称｜field0056｜VARCHAR/text｜400,0｜null｜—
- 项目名称｜field0058｜VARCHAR/text｜600,0｜null｜—
- 项目级别｜field0059｜VARCHAR/select｜20,0｜null｜-7391496778928405449
- 备注｜field0044｜VARCHAR/text｜500,0｜null｜—
- 用印文件上传｜field0062｜VARCHAR/attachment｜20,0｜null｜—
- 创建人1｜field0036｜VARCHAR/member｜20,0｜null｜—
- 创建时间1｜field0037｜DATETIME/datetime｜255,0｜null｜—
- 项目编号｜field0060｜VARCHAR/text｜100,0｜null｜—
- 商机编号｜field0057｜VARCHAR/text｜100,0｜null｜—
- 份数｜field0043｜DECIMAL/text｜20,0｜null｜—
- 原复印件｜field0042｜VARCHAR/select｜20,0｜null｜-2454450906200063743
- 交付部门｜field0066｜VARCHAR/department｜20,0｜null｜—
- 协助投标联系人｜field0081｜VARCHAR/text｜100,0｜null｜—
- 内审查询｜field0068｜VARCHAR/5902128098173592526｜20,0｜null｜—
- 是否为投标配合用印｜field0070｜DECIMAL/radio｜20,0｜null｜-3494224496022304687
- 用印分类｜field0072｜DECIMAL/select｜20,0｜null｜-7342233378416668993
- 文件类型｜field0073｜DECIMAL/select｜20,0｜null｜-7342233378416668993
- 印章归属公司｜field0075｜DECIMAL/select｜20,0｜null｜-2667337719256758192
- 用印模式｜field0076｜DECIMAL/select｜20,0｜null｜6039499715297166506
- 项目总监｜field0077｜VARCHAR/member｜20,0｜null｜—
- 协助投标单位｜field0078｜VARCHAR/text｜100,0｜null｜—
- 协助投标项目名称｜field0079｜VARCHAR/text｜100,0｜null｜—
- 协助投标费用｜field0080｜DECIMAL/text｜20,2｜null｜—

### 新增报价（模板 16486247748910，表单 -736266135085169353「新增报价」，76 个字段）

- 成本未填校验｜field0206｜DECIMAL/text｜20,0｜null｜—
- 阶段任务校验｜field0106｜DECIMAL/text｜20,2｜null｜—
- 流程退回校验｜field0205｜DECIMAL/text｜20,0｜null｜—
- M4岗人力成本｜field0158｜DECIMAL/text｜20,2｜null｜—
- 人力成本小计｜field0159｜DECIMAL/text｜20,2｜null｜—
- 安评委外小计｜field0163｜DECIMAL/text｜20,2｜null｜—
- 光评委外小计｜field0161｜DECIMAL/text｜20,2｜null｜—
- 擦窗机委外小计｜field0162｜DECIMAL/text｜20,2｜null｜—
- 施工图签委外小计｜field0164｜DECIMAL/text｜20,2｜null｜—
- 其他委外小计｜field0165｜DECIMAL/text｜20,2｜null｜—
- 信息服务费小计｜field0201｜DECIMAL/text｜20,2｜null｜—
- 差旅费小计｜field0222｜DECIMAL/text｜20,2｜null｜—
- 印制费小计｜field0223｜DECIMAL/text｜20,2｜null｜—
- 税额合计｜field0116｜DECIMAL/text｜20,2｜null｜—
- 不含税总金额合计｜field0117｜DECIMAL/text｜20,2｜null｜—
- 折后含税总金额合计｜field0035｜DECIMAL/text｜20,2｜null｜—
- 尾差调整｜field0181｜DECIMAL/text｜20,2｜null｜—
- 最终报价｜field0184｜DECIMAL/text｜20,2｜null｜—
- 报价情况说明｜field0041｜VARCHAR/text｜100,0｜null｜—
- BU预估成本｜field0039｜DECIMAL/text｜20,2｜null｜—
- 预估毛利率｜field0040｜DECIMAL/text｜20,2｜null｜—
- 低利润率原因阐述｜field0203｜VARCHAR/textarea｜2000,0｜null｜—
- 创建人1｜field0061｜VARCHAR/member｜20,0｜null｜—
- 创建时间1｜field0062｜DATETIME/datetime｜255,0｜null｜—
- 报价单号校验｜field0195｜VARCHAR/text｜100,0｜null｜—
- 商机编号校验｜field0193｜VARCHAR/text｜100,0｜null｜—
- 报价单是否已关联合同｜field0125｜DECIMAL/select｜20,0｜null｜3783704123441168666
- 成本测算附件｜field0226｜VARCHAR/attachment｜20,0｜null｜—
- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 申请人｜field0227｜VARCHAR/member｜20,0｜null｜—
- 所属部门｜field0038｜VARCHAR/department｜20,0｜null｜—
- 商机所属区域｜field0037｜VARCHAR/department｜20,0｜null｜—
- 报价单编号｜field0029｜VARCHAR/text｜100,0｜null｜—
- 报价单名称｜field0033｜VARCHAR/text｜200,0｜null｜—
- 销售负责人｜field0036｜VARCHAR/member｜20,0｜null｜—
- 营销所属战区｜field0123｜VARCHAR/select｜20,0｜null｜8169289548988237789
- 商机编号｜field0109｜VARCHAR/text｜100,0｜null｜—
- 商机名称｜field0034｜VARCHAR/text｜400,0｜null｜—
- 商机级别｜field0092｜VARCHAR/select｜20,0｜null｜-7391496778928405449
- 客户编号｜field0111｜VARCHAR/text｜100,0｜null｜—
- 客户名称｜field0086｜VARCHAR/text｜100,0｜null｜—
- 商机类别｜field0199｜DECIMAL/select｜20,0｜null｜-4385749188867961438
- 项目交付部门｜field0105｜VARCHAR/department｜20,0｜null｜—
- 模板编码｜field0225｜VARCHAR/text｜100,0｜null｜—
- 模板名称｜field0127｜VARCHAR/text｜100,0｜null｜—
- 销售团队｜field0113｜VARCHAR/multimember｜4000,0｜null｜—
- 是否用印｜field0121｜VARCHAR/radio｜20,0｜null｜-3494224496022304687
- 报价单附件｜field0099｜VARCHAR/attachment｜20,0｜null｜—
- CEO是否已参加｜field0212｜DECIMAL/select｜20,0｜null｜3783704123441168666
- 定价沟通会备忘录｜field0208｜VARCHAR/attachment｜20,0｜null｜—
- T1岗人天｜field0173｜DECIMAL/text｜20,2｜null｜—
- T2岗人天｜field0174｜DECIMAL/text｜20,2｜null｜—
- T3岗人天｜field0175｜DECIMAL/text｜20,2｜null｜—
- M3岗人天｜field0177｜DECIMAL/text｜20,2｜null｜—
- M4岗人天｜field0178｜DECIMAL/text｜20,2｜null｜—
- 人天合计｜field0179｜DECIMAL/text｜20,2｜null｜—
- T1岗人天单价｜field0167｜DECIMAL/text｜22,2｜null｜—
- T2岗人天单价｜field0168｜DECIMAL/text｜22,2｜null｜—
- T3岗人天单价｜field0169｜DECIMAL/text｜22,2｜null｜—
- M3岗人天单价｜field0170｜DECIMAL/text｜22,2｜null｜—
- M4岗人天单价｜field0171｜DECIMAL/text｜22,2｜null｜—
- T1岗人力成本｜field0154｜DECIMAL/text｜20,2｜null｜—
- T2岗人力成本｜field0155｜DECIMAL/text｜20,2｜null｜—
- T3岗人力成本｜field0156｜DECIMAL/text｜20,2｜null｜—
- M3岗人力成本｜field0157｜DECIMAL/text｜20,2｜null｜—

### 新增报价（模板 16620851075370，表单 -736266135085169353「新增报价」，76 个字段）

- M3岗人力成本｜field0157｜DECIMAL/text｜20,2｜null｜—
- T3岗人力成本｜field0156｜DECIMAL/text｜20,2｜null｜—
- T2岗人力成本｜field0155｜DECIMAL/text｜20,2｜null｜—
- T1岗人力成本｜field0154｜DECIMAL/text｜20,2｜null｜—
- M4岗人天单价｜field0171｜DECIMAL/text｜22,2｜null｜—
- M3岗人天单价｜field0170｜DECIMAL/text｜22,2｜null｜—
- T3岗人天单价｜field0169｜DECIMAL/text｜22,2｜null｜—
- T2岗人天单价｜field0168｜DECIMAL/text｜22,2｜null｜—
- T1岗人天单价｜field0167｜DECIMAL/text｜22,2｜null｜—
- 人天合计｜field0179｜DECIMAL/text｜20,2｜null｜—
- M4岗人天｜field0178｜DECIMAL/text｜20,2｜null｜—
- M3岗人天｜field0177｜DECIMAL/text｜20,2｜null｜—
- T3岗人天｜field0175｜DECIMAL/text｜20,2｜null｜—
- T2岗人天｜field0174｜DECIMAL/text｜20,2｜null｜—
- T1岗人天｜field0173｜DECIMAL/text｜20,2｜null｜—
- 定价沟通会备忘录｜field0208｜VARCHAR/attachment｜20,0｜null｜—
- CEO是否已参加｜field0212｜DECIMAL/select｜20,0｜null｜3783704123441168666
- 报价单附件｜field0099｜VARCHAR/attachment｜20,0｜null｜—
- 是否用印｜field0121｜VARCHAR/radio｜20,0｜null｜-3494224496022304687
- 销售团队｜field0113｜VARCHAR/multimember｜4000,0｜null｜—
- 模板名称｜field0127｜VARCHAR/text｜100,0｜null｜—
- 模板编码｜field0225｜VARCHAR/text｜100,0｜null｜—
- 项目交付部门｜field0105｜VARCHAR/department｜20,0｜null｜—
- 商机类别｜field0199｜DECIMAL/select｜20,0｜null｜-4385749188867961438
- 客户名称｜field0086｜VARCHAR/text｜100,0｜null｜—
- 客户编号｜field0111｜VARCHAR/text｜100,0｜null｜—
- 商机级别｜field0092｜VARCHAR/select｜20,0｜null｜-7391496778928405449
- 商机名称｜field0034｜VARCHAR/text｜400,0｜null｜—
- 商机编号｜field0109｜VARCHAR/text｜100,0｜null｜—
- 营销所属战区｜field0123｜VARCHAR/select｜20,0｜null｜8169289548988237789
- 销售负责人｜field0036｜VARCHAR/member｜20,0｜null｜—
- 报价单名称｜field0033｜VARCHAR/text｜200,0｜null｜—
- 报价单编号｜field0029｜VARCHAR/text｜100,0｜null｜—
- 商机所属区域｜field0037｜VARCHAR/department｜20,0｜null｜—
- 所属部门｜field0038｜VARCHAR/department｜20,0｜null｜—
- 申请人｜field0227｜VARCHAR/member｜20,0｜null｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- ID｜id｜null/text｜20｜true｜—
- 成本测算附件｜field0226｜VARCHAR/attachment｜20,0｜null｜—
- 报价单是否已关联合同｜field0125｜DECIMAL/select｜20,0｜null｜3783704123441168666
- 商机编号校验｜field0193｜VARCHAR/text｜100,0｜null｜—
- 报价单号校验｜field0195｜VARCHAR/text｜100,0｜null｜—
- 创建时间1｜field0062｜DATETIME/datetime｜255,0｜null｜—
- 创建人1｜field0061｜VARCHAR/member｜20,0｜null｜—
- 低利润率原因阐述｜field0203｜VARCHAR/textarea｜2000,0｜null｜—
- 预估毛利率｜field0040｜DECIMAL/text｜20,2｜null｜—
- BU预估成本｜field0039｜DECIMAL/text｜20,2｜null｜—
- 报价情况说明｜field0041｜VARCHAR/text｜100,0｜null｜—
- 最终报价｜field0184｜DECIMAL/text｜20,2｜null｜—
- 尾差调整｜field0181｜DECIMAL/text｜20,2｜null｜—
- 折后含税总金额合计｜field0035｜DECIMAL/text｜20,2｜null｜—
- 不含税总金额合计｜field0117｜DECIMAL/text｜20,2｜null｜—
- 税额合计｜field0116｜DECIMAL/text｜20,2｜null｜—
- 印制费小计｜field0223｜DECIMAL/text｜20,2｜null｜—
- 差旅费小计｜field0222｜DECIMAL/text｜20,2｜null｜—
- 信息服务费小计｜field0201｜DECIMAL/text｜20,2｜null｜—
- 其他委外小计｜field0165｜DECIMAL/text｜20,2｜null｜—
- 施工图签委外小计｜field0164｜DECIMAL/text｜20,2｜null｜—
- 擦窗机委外小计｜field0162｜DECIMAL/text｜20,2｜null｜—
- 光评委外小计｜field0161｜DECIMAL/text｜20,2｜null｜—
- 安评委外小计｜field0163｜DECIMAL/text｜20,2｜null｜—
- 人力成本小计｜field0159｜DECIMAL/text｜20,2｜null｜—
- M4岗人力成本｜field0158｜DECIMAL/text｜20,2｜null｜—
- 流程退回校验｜field0205｜DECIMAL/text｜20,0｜null｜—
- 阶段任务校验｜field0106｜DECIMAL/text｜20,2｜null｜—
- 成本未填校验｜field0206｜DECIMAL/text｜20,0｜null｜—

### 感恩节活动（模板 2916490644621970991，表单 8853836939692625881「感恩节活动」，29 个字段）

- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 赠与者员工编号｜field0031｜VARCHAR/text｜255,0｜null｜—
- 赠与者员工姓名｜field0165｜VARCHAR/member｜20,0｜null｜—
- 赠与者姓名｜field0149｜VARCHAR/text｜100,0｜null｜—
- 赠与者性别｜field0171｜DECIMAL/select｜20,0｜null｜1716024204443730308
- 赠与者部门｜field0146｜VARCHAR/department｜20,0｜null｜—
- 年度｜field0029｜VARCHAR/text｜100,0｜null｜—
- 被赠与者编号｜field0148｜VARCHAR/text｜255,0｜null｜—
- 被赠者姓名｜field0163｜VARCHAR/member｜20,0｜null｜—
- 被赠与者部门｜field0150｜VARCHAR/department｜20,0｜null｜—
- 赠物｜field0153｜VARCHAR/text｜100,0｜null｜—
- 祝福语｜field0154｜VARCHAR/text｜100,0｜null｜—
- 创建日期｜field0135｜TIMESTAMP/date｜255,0｜null｜—
- 创建人1｜field0134｜VARCHAR/member｜20,0｜null｜—
- 重复赠与校验｜field0161｜VARCHAR/checkbox｜5,0｜null｜—
- 互相赠与校验｜field0167｜VARCHAR/member｜20,0｜null｜—
- 已被赠与校验｜field0169｜VARCHAR/checkbox｜5,0｜null｜—
- 已发送｜field0173｜VARCHAR/checkbox｜5,0｜null｜—

### IT资产领用申请（模板 5324409057402766767，表单 -325283934409505282「IT资产领用单」，20 个字段）

- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 领用申请编号｜field0004｜VARCHAR/text｜100,0｜null｜—
- 资产所属部门｜field0005｜VARCHAR/department｜20,0｜null｜—
- 资产管理员｜field0006｜VARCHAR/member｜20,0｜null｜—
- 采购日期｜field0007｜TIMESTAMP/date｜255,0｜null｜—
- 供应商编号｜field0008｜VARCHAR/text｜100,0｜null｜—
- 供应商名称｜field0009｜VARCHAR/text｜100,0｜null｜—
- 创建人1｜field0016｜VARCHAR/member｜20,0｜null｜—
- 创建时间1｜field0017｜DATETIME/datetime｜255,0｜null｜—

### 用印申请（模板 5779151688961574431，表单 3996442246046958045「用印申请」，40 个字段）

- 协助投标联系人｜field0081｜VARCHAR/text｜100,0｜null｜—
- 协助投标费用｜field0080｜DECIMAL/text｜20,2｜null｜—
- ID｜id｜null/text｜20｜true｜—
- 协助投标项目名称｜field0079｜VARCHAR/text｜100,0｜null｜—
- 协助投标单位｜field0078｜VARCHAR/text｜100,0｜null｜—
- 项目总监｜field0077｜VARCHAR/member｜20,0｜null｜—
- 用印模式｜field0076｜DECIMAL/select｜20,0｜null｜6039499715297166506
- 印章归属公司｜field0075｜DECIMAL/select｜20,0｜null｜-2667337719256758192
- 文件类型｜field0073｜DECIMAL/select｜20,0｜null｜-7342233378416668993
- 用印分类｜field0072｜DECIMAL/select｜20,0｜null｜-7342233378416668993
- 是否为投标配合用印｜field0070｜DECIMAL/radio｜20,0｜null｜-3494224496022304687
- 审核状态｜state｜null/text｜10｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 申请部门｜field0032｜VARCHAR/department｜20,0｜null｜—
- 申请人｜field0029｜VARCHAR/member｜20,0｜null｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 内审查询｜field0068｜VARCHAR/5902128098173592526｜20,0｜null｜—
- 交付部门｜field0066｜VARCHAR/department｜20,0｜null｜—
- 原复印件｜field0042｜VARCHAR/select｜20,0｜null｜-2454450906200063743
- 份数｜field0043｜DECIMAL/text｜20,0｜null｜—
- 商机编号｜field0057｜VARCHAR/text｜100,0｜null｜—
- 项目编号｜field0060｜VARCHAR/text｜100,0｜null｜—
- 创建时间1｜field0037｜DATETIME/datetime｜255,0｜null｜—
- 创建人1｜field0036｜VARCHAR/member｜20,0｜null｜—
- 用印文件上传｜field0062｜VARCHAR/attachment｜20,0｜null｜—
- 备注｜field0044｜VARCHAR/text｜500,0｜null｜—
- 项目级别｜field0059｜VARCHAR/select｜20,0｜null｜-7391496778928405449
- 项目名称｜field0058｜VARCHAR/text｜600,0｜null｜—
- 商机名称｜field0056｜VARCHAR/text｜400,0｜null｜—
- 印章类型｜field0041｜VARCHAR/select｜20,0｜null｜-3472409557441036019
- 用印日期｜field0054｜TIMESTAMP/date｜255,0｜null｜—
- 申请单位｜field0034｜VARCHAR/department｜20,0｜null｜—

### 创新项目发布申请（模板 6389177723207763537，表单 6167072424176533726「创新项目发布申请」，27 个字段）

- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 项目名称｜field0004｜VARCHAR/text｜100,0｜null｜—
- 项目类型｜field0005｜DECIMAL/select｜20,0｜null｜8915756690597432062
- 创新类型｜field0006｜VARCHAR/select｜20,0｜null｜8915756690597432062
- 开始时间｜field0007｜TIMESTAMP/date｜255,0｜null｜—
- 结束时间｜field0008｜TIMESTAMP/date｜255,0｜null｜—
- 资料上传｜field0009｜VARCHAR/attachment｜20,0｜null｜—
- 项目描述｜field0010｜VARCHAR/textarea｜2000,0｜null｜—
- 是否可做为创新项目申请｜field0042｜DECIMAL/radio｜20,0｜null｜-3494224496022304687
- 基础分｜field0029｜DECIMAL/text｜20,0｜null｜—
- 最终得分｜field0027｜DECIMAL/text｜20,0｜null｜—
- 评分附件｜field0035｜VARCHAR/attachment｜20,0｜null｜—
- 评分备注｜field0036｜VARCHAR/text｜500,0｜null｜—
- 创建人1｜field0011｜VARCHAR/member｜20,0｜null｜—
- 创建时间1｜field0012｜DATETIME/datetime｜255,0｜null｜—
- 项目编号｜field0014｜VARCHAR/text｜100,0｜null｜—

### 周年贺卡（模板 7187395059800230659，表单 -6481979378378584165「周年贺卡」，18 个字段）

- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 周年匠人｜field0001｜VARCHAR/member｜20,0｜null｜—
- 姓名｜field0002｜VARCHAR/text｜100,0｜null｜—
- 入职日期｜field0003｜TIMESTAMP/date｜255,0｜null｜—
- 入职年限｜field0004｜DECIMAL/text｜20,0｜null｜—
- 发起年｜field0006｜DECIMAL/text｜20,0｜null｜—
- 发起月｜field0007｜DECIMAL/text｜20,0｜null｜—

### 新增报价（模板 8339262726610963687，表单 -736266135085169353「新增报价」，76 个字段）

- 预估毛利率｜field0040｜DECIMAL/text｜20,2｜null｜—
- 低利润率原因阐述｜field0203｜VARCHAR/textarea｜2000,0｜null｜—
- 创建人1｜field0061｜VARCHAR/member｜20,0｜null｜—
- 创建时间1｜field0062｜DATETIME/datetime｜255,0｜null｜—
- 报价单号校验｜field0195｜VARCHAR/text｜100,0｜null｜—
- 商机编号校验｜field0193｜VARCHAR/text｜100,0｜null｜—
- 报价单是否已关联合同｜field0125｜DECIMAL/select｜20,0｜null｜3783704123441168666
- 阶段任务校验｜field0106｜DECIMAL/text｜20,2｜null｜—
- 人力成本小计｜field0159｜DECIMAL/text｜20,2｜null｜—
- 安评委外小计｜field0163｜DECIMAL/text｜20,2｜null｜—
- 光评委外小计｜field0161｜DECIMAL/text｜20,2｜null｜—
- 擦窗机委外小计｜field0162｜DECIMAL/text｜20,2｜null｜—
- 施工图签委外小计｜field0164｜DECIMAL/text｜20,2｜null｜—
- 其他委外小计｜field0165｜DECIMAL/text｜20,2｜null｜—
- 信息服务费小计｜field0201｜DECIMAL/text｜20,2｜null｜—
- 差旅费小计｜field0222｜DECIMAL/text｜20,2｜null｜—
- 印制费小计｜field0223｜DECIMAL/text｜20,2｜null｜—
- 税额合计｜field0116｜DECIMAL/text｜20,2｜null｜—
- 不含税总金额合计｜field0117｜DECIMAL/text｜20,2｜null｜—
- 折后含税总金额合计｜field0035｜DECIMAL/text｜20,2｜null｜—
- 尾差调整｜field0181｜DECIMAL/text｜20,2｜null｜—
- 最终报价｜field0184｜DECIMAL/text｜20,2｜null｜—
- 报价情况说明｜field0041｜VARCHAR/text｜100,0｜null｜—
- BU预估成本｜field0039｜DECIMAL/text｜20,2｜null｜—
- 成本测算附件｜field0226｜VARCHAR/attachment｜20,0｜null｜—
- 流程退回校验｜field0205｜DECIMAL/text｜20,0｜null｜—
- 成本未填校验｜field0206｜DECIMAL/text｜20,0｜null｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 申请人｜field0227｜VARCHAR/member｜20,0｜null｜—
- 所属部门｜field0038｜VARCHAR/department｜20,0｜null｜—
- 商机所属区域｜field0037｜VARCHAR/department｜20,0｜null｜—
- 报价单编号｜field0029｜VARCHAR/text｜100,0｜null｜—
- 报价单名称｜field0033｜VARCHAR/text｜200,0｜null｜—
- ID｜id｜null/text｜20｜true｜—
- 销售负责人｜field0036｜VARCHAR/member｜20,0｜null｜—
- 营销所属战区｜field0123｜VARCHAR/select｜20,0｜null｜8169289548988237789
- 商机编号｜field0109｜VARCHAR/text｜100,0｜null｜—
- 商机名称｜field0034｜VARCHAR/text｜400,0｜null｜—
- 商机级别｜field0092｜VARCHAR/select｜20,0｜null｜-7391496778928405449
- 客户编号｜field0111｜VARCHAR/text｜100,0｜null｜—
- 客户名称｜field0086｜VARCHAR/text｜100,0｜null｜—
- 商机类别｜field0199｜DECIMAL/select｜20,0｜null｜-4385749188867961438
- 项目交付部门｜field0105｜VARCHAR/department｜20,0｜null｜—
- 模板编码｜field0225｜VARCHAR/text｜100,0｜null｜—
- 模板名称｜field0127｜VARCHAR/text｜100,0｜null｜—
- 销售团队｜field0113｜VARCHAR/multimember｜4000,0｜null｜—
- 是否用印｜field0121｜VARCHAR/radio｜20,0｜null｜-3494224496022304687
- 报价单附件｜field0099｜VARCHAR/attachment｜20,0｜null｜—
- CEO是否已参加｜field0212｜DECIMAL/select｜20,0｜null｜3783704123441168666
- 定价沟通会备忘录｜field0208｜VARCHAR/attachment｜20,0｜null｜—
- T1岗人天｜field0173｜DECIMAL/text｜20,2｜null｜—
- T2岗人天｜field0174｜DECIMAL/text｜20,2｜null｜—
- T3岗人天｜field0175｜DECIMAL/text｜20,2｜null｜—
- M3岗人天｜field0177｜DECIMAL/text｜20,2｜null｜—
- M4岗人天｜field0178｜DECIMAL/text｜20,2｜null｜—
- 人天合计｜field0179｜DECIMAL/text｜20,2｜null｜—
- T1岗人天单价｜field0167｜DECIMAL/text｜22,2｜null｜—
- T2岗人天单价｜field0168｜DECIMAL/text｜22,2｜null｜—
- T3岗人天单价｜field0169｜DECIMAL/text｜22,2｜null｜—
- M3岗人天单价｜field0170｜DECIMAL/text｜22,2｜null｜—
- M4岗人天单价｜field0171｜DECIMAL/text｜22,2｜null｜—
- T1岗人力成本｜field0154｜DECIMAL/text｜20,2｜null｜—
- T2岗人力成本｜field0155｜DECIMAL/text｜20,2｜null｜—
- T3岗人力成本｜field0156｜DECIMAL/text｜20,2｜null｜—
- M3岗人力成本｜field0157｜DECIMAL/text｜20,2｜null｜—
- M4岗人力成本｜field0158｜DECIMAL/text｜20,2｜null｜—

### IT资产采购申请（模板 9068384877204753833，表单 6009314549831779163「IT资产采购申请」，30 个字段）

- ID｜id｜null/text｜20｜true｜—
- 审核状态｜state｜null/text｜10｜true｜—
- 创建人｜start_member_id｜null/member｜20｜true｜—
- 创建时间｜start_date｜null/datetime｜｜true｜—
- 审核人｜approve_member_id｜null/member｜20｜true｜—
- 审核时间｜approve_date｜null/datetime｜｜true｜—
- 流程状态｜finishedflag｜null/text｜10｜true｜—
- 核定状态｜ratifyflag｜null/text｜10｜true｜—
- 核定人｜ratify_member_id｜null/member｜20｜true｜—
- 核定时间｜ratify_date｜null/datetime｜｜true｜—
- 修改人｜modify_member_id｜null/member｜20｜true｜—
- 修改时间｜modify_date｜null/datetime｜｜true｜—
- 订单编号｜field0001｜VARCHAR/text｜100,0｜null｜—
- 申请日期｜field0003｜TIMESTAMP/date｜255,0｜null｜—
- 申请人｜field0006｜VARCHAR/member｜20,0｜null｜—
- 申请部门｜field0005｜VARCHAR/department｜20,0｜null｜—
- 采购需求｜field0031｜VARCHAR/text｜500,0｜null｜—
- 供应商编号｜field0032｜VARCHAR/text｜100,0｜null｜—
- 供应商名称｜field0010｜VARCHAR/text｜100,0｜null｜—
- 公司户头名称｜field0011｜VARCHAR/text｜100,0｜null｜—
- 开户行名称｜field0012｜VARCHAR/text｜100,0｜null｜—
- 银行账号｜field0013｜VARCHAR/text｜100,0｜null｜—
- 联行号｜field0014｜VARCHAR/text｜100,0｜null｜—
- 成本中心｜field0007｜VARCHAR/department｜20,0｜null｜—
- 采购订单总价｜field0052｜DECIMAL/text｜20,2｜null｜—
- 采购合同附件｜field0009｜VARCHAR/attachment｜20,0｜null｜—
- 创建人1｜field0029｜VARCHAR/member｜20,0｜null｜—
- 创建时间1｜field0030｜DATETIME/datetime｜255,0｜null｜—
- 订单类型｜field0053｜VARCHAR/text｜100,0｜null｜—
- 采购类型｜field0054｜DECIMAL/select｜20,0｜null｜5117652334517701119

## Q5-B 受控值：enumInfo 的数字前缀确实是 CTP_ENUM.ID（16 个枚举全部对上）

### 枚举 -7391496778928405449「商机级别」（3 项）

2.V=1(状态1)；3.S=2(状态1)；4.N=0(状态1)

### 枚举 -7342233378416668993「用印分类」（19 项）

1.项目用章=0(状态1)；1.图纸交付=0(状态1)；1.人事用印=0(状态1)；1.财务用印=0(状态1)；1.供应商入库=0(状态1)；2.行政用印=1(状态1)；2.销售用章=1(状态1)；2.巡查报告=1(状态1)；2.投标回执=1(状态1)；3.约谈记录=2(状态1)；3.人事行政用章=2(状态1)；3.项目变更单=2(状态1)；4.结算书=3(状态1)；4.投标配合=3(状态1)；4.财务用章=3(状态1)；5.图纸送审=4(状态1)；5.终止协议=4(状态1)；6.项目诉讼=5(状态1)；7.必捷结算专用=6(状态0)

### 枚举 -4385749188867961438「商机类别」（6 项）

1.战略框架=0(状态1)；2.战略子项=5(状态1)；3.非战略=1(状态1)；3.超高层=2(状态0)；4.海外项目=3(状态0)；5.新业务=4(状态0)

### 枚举 -3494224496022304687「是否」（2 项）

1.是=0(状态1)；2.否=1(状态1)

### 枚举 -3472409557441036019「印章类型」（17 项）

1.公章=4(状态1)；2.合同章=1(状态1)；3.法人章=5(状态1)；4.公章和法人章=6(状态1)；5.人事专用章=10(状态1)；5.财务专用章=8(状态1)；6.图纸专用章（不带编号-方型红色）=9(状态1)；7.技术专用章（建筑科技-圆形红色）=7(状态1)；8.上海市设计出图章（方形红色）=11(状态1)；9.江苏省工程勘察设计出图专用章（甲级资质章）=3(状态1)；10.山西省工程设计出图专用章（山西资质章-椭圆红色）=16(状态0)；11.（出图章）山西隆达通=13(状态0)；12.（公章和出图章）山西隆达通=14(状态0)；13.（公章）弗思特建筑科技（山西）有限公司=15(状态0)；15.（公章和出图章）弗思特建筑科技（山西）有限公司=17(状态0)；98.（公章）山西隆达通=12(状态0)；99.法人章-废=0(状态0)

### 枚举 -2667337719256758192「财务核算组织」（6 项）

1.弗思特建筑科技有限公司=0(状态1)；2.必捷建筑技术（南京）有限公司=1(状态1)；3.雷蒙沃能源科技（上海）有限公司=2(状态1)；4.弗思特建筑科技（山西）有限公司=4(状态1)；5.弗思特建筑科技（山西）有限公司南京分公司=3(状态1)；6.山西隆达通建筑工程有限公司=5(状态1)

### 枚举 -2454450906200063743「原/复印件」（2 项）

1.原件=0(状态1)；2.复印件=1(状态1)

### 枚举 1534654868854620891「优先级」（4 项）

1.立即解决=0(状态1)；2.高度重视=1(状态1)；3.正常处理=2(状态1)；4.低优先级=3(状态1)

### 枚举 1716024204443730308「性别」（2 项）

1.男=0(状态1)；2.女=1(状态1)

### 枚举 2326016881727851812「需求类型」（24 项）

1.BPM系统=0(状态1)；1.信息系统需求=0(状态1)；1.办公软件=0(状态0)；1.笔记本电脑=0(状态1)；2.台式机=1(状态1)；2.PSA系统=1(状态1)；2.IT设备故障=1(状态1)；2.电脑硬件=1(状态1)；3.公司网络=2(状态1)；3.U8C财务系统=6(状态1)；3.显示器=2(状态1)；3.IT资产借用=2(状态1)；4.内存=3(状态1)；4.BI决策分析平台=4(状态1)；4.打印机=4(状态1)；4.KM知识管理平台=5(状态1)；5.企业邮箱=3(状态1)；5.加密系统=2(状态1)；5.显卡=4(状态1)；6.硬盘=5(状态1)；6.投影仪=5(状态1)；7.U盘=6(状态1)；7.电视机=6(状态1)；8.其他=3(状态1)

### 枚举 3783704123441168666「是否」（2 项）

1.是=0(状态1)；2.否=1(状态1)

### 枚举 5117652334517701119「采购类型」（2 项）

1.一次性采购=0(状态1)；2.日常采购=1(状态1)

### 枚举 6039499715297166506「用印模式」（2 项）

1.纸质鲜章=0(状态1)；2.电子章=1(状态1)

### 枚举 7089441405752199241「处理结果」（3 项）

1.已处理=0(状态1)；2.暂时挂起=1(状态1)；3.取消=2(状态1)

### 枚举 8169289548988237789「营销分组」（5 项）

1.弗思特=1(状态1)；2.必捷=0(状态1)；3.南区=2(状态0)；3.雷蒙沃=3(状态1)；5.总部=4(状态0)

### 枚举 8915756690597432062「创新项目类型」（29 项）

1.创新项目=0(状态1)；1.工作方法创新=0(状态0)；1.工作方法创新=0(状态0)；1.工作方法创新=0(状态0)；2.成果表达方式创新=1(状态0)；2.成果表达方式创新=1(状态0)；2.成果表达方式创新=1(状态0)；2.专利项目=1(状态1)；3.众包项目=2(状态1)；3.项目操作流程创新=2(状态0)；3.项目操作流程创新=2(状态0)；3.项目操作流程创新=2(状态0)；4.产品开发=3(状态0)；4.产品开发=3(状态0)；4.产品开发=3(状态0)；5.开发插件或软件=4(状态0)；5.开发插件或软件=4(状态0)；5.开发插件或软件=4(状态0)；6.技术领先=5(状态0)；6.技术领先=5(状态0)；6.技术领先=5(状态0)；7.发明=6(状态1)；7.技术推广=6(状态1)；7.图集模型绘制=6(状态1)；8.实用新型=7(状态1)；8.技术创新=7(状态1)；8.软件测试=7(状态1)；9.外观设计=8(状态1)；9.产品研发=8(状态1)

## Q6-A 流程定义属性取值画像（模板｜元素.属性：出现次数，不同取值数；低基数取值：值×次数）

### 需求工单（-803037765431705053）

- a.b：4 次，1 种取值，含 8 位以上数字取值
- a.c：5 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：4 次，4 种取值，含短汉字取值（整属性不出值）
- a.f：4 次，4 种取值，含 8 位以上数字取值；BlankNode×1；Member@field0124#处理人×1
- a.g：4 次，4 种取值；Department×1；FormField×1；Node×1；Team×1
- a.h：5 次，1 种取值；false×5
- a.i：5 次，1 种取值；false×5
- a.j：5 次，1 种取值；false×5
- a.k：4 次，1 种取值；roleadmin×4
- l.a：1 次，1 种取值；currentNode×1
- l.b：1 次，1 种取值，含短汉字取值（整属性不出值）
- l.c：1 次，1 种取值；{需求类型} == "IT资产借用"×1
- l.d：1 次，1 种取值；与当前分支同级的其他分支条件都不满足时，则执行排他分支×1
- l.e：2 次，2 种取值，含 8 位以上数字取值
- l.h：8 次，3 种取值，含短汉字取值（整属性不出值）
- l.i：8 次，8 种取值，含 8 位以上数字取值
- l.j：8 次，7 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：8 次，7 种取值，含 8 位以上数字取值；start×1
- l.m：1 次，1 种取值，含 8 位以上数字取值
- l.t：8 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：8 次，8 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：4 次，1 种取值；1000×4
- n.n：8 次，8 种取值，含短汉字取值（整属性不出值）
- n.ncn：2 次，2 种取值；IT部门负责人×1；流程信息部×1
- n.ncn_i18n：2 次，2 种取值；IT部门负责人×1；流程信息部×1
- n.p：2 次，2 种取值；false×1；true×1
- n.t：8 次，4 种取值，含短汉字取值（整属性不出值）
- n.x：8 次，7 种取值，含短汉字取值（整属性不出值）
- n.y：8 次，3 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值，含 8 位以上数字取值
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.ts：1 次，1 种取值；×1
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；false×1
- s.aa：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.cy：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.dfexn：1 次，1 种取值；<点击选择人员>×1
- s.ds：5 次，2 种取值，含短汉字取值（整属性不出值）
- s.f：5 次，1 种取值，含 8 位以上数字取值
- s.ft：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：5 次，5 种取值，含 8 位以上数字取值
- s.g：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：5 次，3 种取值；approve×3；inform×1；newCol×1
- s.j：8 次，1 种取值；single×8
- s.k：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：8 次，1 种取值；false×8
- s.md：4 次，2 种取值，含短汉字取值（整属性不出值）
- s.mu：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：5 次，3 种取值，含短汉字取值（整属性不出值）
- s.na：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.na_b：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.na_i：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.oe：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.otmn：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.q：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：4 次，1 种取值，含短汉字取值（整属性不出值）
### 感恩节活动（2916490644621970991）

- a.b：1 次，1 种取值，含 8 位以上数字取值
- a.c：2 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：1 次，1 种取值；被赠者姓名×1
- a.f：1 次，1 种取值；Member@field0163#被赠者姓名×1
- a.g：1 次，1 种取值；FormField×1
- a.h：2 次，1 种取值；false×2
- a.i：2 次，1 种取值；false×2
- a.j：2 次，1 种取值；false×2
- a.k：1 次，1 种取值；roleadmin×1
- l.h：2 次，1 种取值，含短汉字取值（整属性不出值）
- l.i：2 次，2 种取值，含 8 位以上数字取值
- l.j：2 次，2 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：2 次，2 种取值，含 8 位以上数字取值；start×1
- l.t：2 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：3 次，3 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：1 次，1 种取值；1000×1
- n.n：3 次，3 种取值，含短汉字取值（整属性不出值）
- n.t：3 次，3 种取值，含短汉字取值（整属性不出值）
- n.x：3 次，3 种取值，含短汉字取值（整属性不出值）
- n.y：3 次，1 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值；×1
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；false×1
- s.cy：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.f：2 次，1 种取值，含 8 位以上数字取值
- s.ft：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：2 次，2 种取值，含 8 位以上数字取值
- s.g：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：1 次，1 种取值；inform×1
- s.j：3 次，1 种取值；single×3
- s.k：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：3 次，1 种取值；false×3
- s.md：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.mu：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.na：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.na_b：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.na_i：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.q：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：1 次，1 种取值，含短汉字取值（整属性不出值）
### IT资产领用申请（5324409057402766767）

- a.b：3 次，1 种取值，含 8 位以上数字取值
- a.c：4 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：3 次，3 种取值；JS-流程信息部负责人×1；资产管理员×1；资产使用人×1
- a.f：3 次，3 种取值，含 8 位以上数字取值；Member@field0006#资产管理员×1；Multimember@field0023#资产使用人×1
- a.g：3 次，2 种取值；FormField×2；Team×1
- a.h：4 次，1 种取值；false×4
- a.i：4 次，1 种取值；false×4
- a.j：4 次，1 种取值；false×4
- a.k：3 次，1 种取值；roleadmin×3
- l.h：4 次，1 种取值，含短汉字取值（整属性不出值）
- l.i：4 次，4 种取值，含 8 位以上数字取值
- l.j：4 次，4 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：4 次，4 种取值，含 8 位以上数字取值；start×1
- l.t：4 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：5 次，5 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：3 次，1 种取值；1000×3
- n.n：5 次，5 种取值，含短汉字取值（整属性不出值）
- n.t：5 次，3 种取值，含短汉字取值（整属性不出值）
- n.x：5 次，5 种取值，含短汉字取值（整属性不出值）
- n.y：5 次，1 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值；×1
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.ts：1 次，1 种取值；×1
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；false×1
- s.aa：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.cy：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.dfexn：2 次，1 种取值；<点击选择人员>×2
- s.ds：4 次，3 种取值，含短汉字取值（整属性不出值）
- s.f：4 次，1 种取值，含 8 位以上数字取值
- s.ft：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：4 次，4 种取值，含 8 位以上数字取值
- s.g：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：4 次，3 种取值；approve×2；inform×1；newCol×1
- s.j：5 次，2 种取值，含短汉字取值（整属性不出值）
- s.k：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：5 次，1 种取值；false×5
- s.md：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.mu：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：4 次，3 种取值，含短汉字取值（整属性不出值）
- s.na：4 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_b：5 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_i：5 次，2 种取值，含短汉字取值（整属性不出值）
- s.otmn：2 次，1 种取值，含短汉字取值（整属性不出值）
- s.q：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：4 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：4 次，1 种取值，含短汉字取值（整属性不出值）
### 用印申请（5779151688961574431）

- a.b：22 次，1 种取值，含 8 位以上数字取值
- a.c：23 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：22 次，17 种取值，含短汉字取值（整属性不出值）
- a.f：22 次，17 种取值，含 8 位以上数字取值
- a.g：22 次，4 种取值；FormField×4；Node×4；Post×1；Team×13
- a.h：23 次，1 种取值；false×23
- a.i：23 次，1 种取值；false×23
- a.j：23 次，1 种取值；false×23
- a.k：22 次，1 种取值；roleadmin×22
- l.a：12 次，2 种取值；currentNode×10；start×2
- l.b：12 次，1 种取值，含短汉字取值（整属性不出值）
- l.c：12 次，11 种取值；{文件类型} == "财务用印" or {文件类型} == "项目诉讼"×1；{文件类型} == "人事用印"×1；{文件类型} == "投标配合"×1；{文件类型} == "行政用印"×1；{印章类型} == "财务专用章"×1；{印章类型} == "技术专用章（建筑科技-圆形红色）"×1；{用印分类} == "销售用章"×2
- l.d：3 次，1 种取值；与当前分支同级的其他分支条件都不满足时，则执行排他分支×3
- l.e：18 次，18 种取值，含 8 位以上数字取值
- l.h：47 次，3 种取值，含短汉字取值（整属性不出值）
- l.i：47 次，47 种取值，含 8 位以上数字取值
- l.j：47 次，35 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：47 次，35 种取值，含 8 位以上数字取值
- l.m：12 次，11 种取值，含 8 位以上数字取值
- l.t：47 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：36 次，36 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：22 次，1 种取值；1000×22
- n.n：36 次，21 种取值，含短汉字取值（整属性不出值）
- n.ncn：5 次，5 种取值，含短汉字取值（整属性不出值）
- n.ncn_i18n：5 次，5 种取值，含短汉字取值（整属性不出值）
- n.p：12 次，2 种取值；false×6；true×6
- n.t：36 次，4 种取值，含短汉字取值（整属性不出值）
- n.x：36 次，24 种取值，含短汉字取值（整属性不出值）
- n.y：36 次，9 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值，含 8 位以上数字取值
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.ts：1 次，1 种取值；×1
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；false×1
- s.aa：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.cy：11 次，1 种取值，含短汉字取值（整属性不出值）
- s.dfexn：3 次，1 种取值；<点击选择人员>×3
- s.ds：22 次，2 种取值，含短汉字取值（整属性不出值）
- s.f：23 次，1 种取值，含 8 位以上数字取值
- s.ft：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：23 次，5 种取值，含 8 位以上数字取值
- s.g：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：22 次，2 种取值；approve×15；inform×7
- s.j：36 次，2 种取值，含短汉字取值（整属性不出值）
- s.k：11 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：11 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：36 次，1 种取值；false×36
- s.md：11 次，1 种取值，含短汉字取值（整属性不出值）
- s.mu：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：22 次，2 种取值，含短汉字取值（整属性不出值）
- s.na：11 次，1 种取值，含短汉字取值（整属性不出值）
- s.na_b：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.na_i：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.otmn：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.q：11 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：36 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：15 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：11 次，1 种取值，含短汉字取值（整属性不出值）
### 创新项目发布申请（6389177723207763537）

- a.b：8 次，1 种取值，含 8 位以上数字取值
- a.c：9 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：8 次，4 种取值，含短汉字取值（整属性不出值）
- a.f：8 次，4 种取值，含 8 位以上数字取值；BlankNode×3；Multimember@field0017#项目成员姓名×1；Sender×1
- a.g：8 次，3 种取值；FormField×1；Node×4；Team×3
- a.h：9 次，1 种取值；false×9
- a.i：9 次，1 种取值；false×9
- a.j：9 次，1 种取值；false×9
- a.k：8 次，1 种取值；roleadmin×8
- l.a：5 次，1 种取值；currentNode×5
- l.b：5 次，1 种取值，含短汉字取值（整属性不出值）
- l.c：5 次，4 种取值；{是否可做为创新项目申请} == "否"×1；{是否可做为创新项目申请} == "是"×2；{项目类型} == "众包项目"×1；{项目类型} == "专利项目"×1
- l.e：7 次，7 种取值，含 8 位以上数字取值
- l.h：19 次，3 种取值，含短汉字取值（整属性不出值）
- l.i：19 次，19 种取值，含 8 位以上数字取值
- l.j：19 次，15 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：19 次，15 种取值，含 8 位以上数字取值；start×1
- l.m：5 次，4 种取值，含 8 位以上数字取值
- l.t：19 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：16 次，16 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：8 次，1 种取值；1000×8
- n.n：16 次，7 种取值，含短汉字取值（整属性不出值）
- n.ncn：5 次，5 种取值；结果反馈×1；内部审核×1；知会发起人×1；知会小组成员×1；专利深化×1
- n.ncn_i18n：5 次，5 种取值；结果反馈×1；内部审核×1；知会发起人×1；知会小组成员×1；专利深化×1
- n.p：6 次，2 种取值；false×3；true×3
- n.t：16 次，4 种取值，含短汉字取值（整属性不出值）
- n.x：16 次，12 种取值，含短汉字取值（整属性不出值）
- n.y：16 次，6 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值；×1
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.ts：1 次，1 种取值；×1
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；false×1
- s.aa：16 次，1 种取值，含短汉字取值（整属性不出值）
- s.cy：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.ds：8 次，2 种取值，含短汉字取值（整属性不出值）
- s.f：9 次，1 种取值，含 8 位以上数字取值
- s.ft：16 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：16 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：9 次，7 种取值，含 8 位以上数字取值
- s.g：16 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：8 次，2 种取值；approve×3；inform×5
- s.j：16 次，2 种取值，含短汉字取值（整属性不出值）
- s.k：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：16 次，1 种取值；false×16
- s.md：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.mu：16 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：8 次，2 种取值，含短汉字取值（整属性不出值）
- s.na：5 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_b：16 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_i：16 次，2 种取值，含短汉字取值（整属性不出值）
- s.q：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：16 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：5 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：5 次，1 种取值，含短汉字取值（整属性不出值）
### 周年贺卡（7187395059800230659）

- a.b：1 次，1 种取值，含 8 位以上数字取值
- a.c：2 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：1 次，1 种取值；周年匠人×1
- a.f：1 次，1 种取值；Member@field0001#周年匠人×1
- a.g：1 次，1 种取值；FormField×1
- a.h：2 次，1 种取值；false×2
- a.i：2 次，1 种取值；false×2
- a.j：2 次，1 种取值；false×2
- a.k：1 次，1 种取值；roleadmin×1
- l.h：2 次，1 种取值，含短汉字取值（整属性不出值）
- l.i：2 次，2 种取值，含 8 位以上数字取值
- l.j：2 次，2 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：2 次，2 种取值，含 8 位以上数字取值；start×1
- l.t：2 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：3 次，3 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：1 次，1 种取值；1000×1
- n.n：3 次，3 种取值，含短汉字取值（整属性不出值）
- n.t：3 次，3 种取值，含短汉字取值（整属性不出值）
- n.x：3 次，3 种取值，含短汉字取值（整属性不出值）
- n.y：3 次，1 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值；×1
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.ts：1 次，1 种取值；×1
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；false×1
- s.aa：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.cy：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.dfexn：1 次，1 种取值；<点击选择人员>×1
- s.ds：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.f：2 次，1 种取值，含 8 位以上数字取值
- s.ft：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：2 次，2 种取值，含 8 位以上数字取值
- s.g：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：1 次，1 种取值；read×1
- s.j：3 次，1 种取值；single×3
- s.k：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：3 次，1 种取值；false×3
- s.md：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.mu：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.na：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.na_b：3 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_i：3 次，2 种取值，含短汉字取值（整属性不出值）
- s.otmn：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.q：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：1 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：1 次，1 种取值，含短汉字取值（整属性不出值）
### 新增报价（8339262726610963687）

- a.b：14 次，1 种取值，含 8 位以上数字取值
- a.c：15 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：14 次，9 种取值，含短汉字取值（整属性不出值）
- a.f：14 次，9 种取值，含 8 位以上数字取值；BlankNode×5
- a.g：14 次，3 种取值；FormField×3；Node×6；Team×5
- a.h：15 次，1 种取值；false×15
- a.i：15 次，1 种取值；false×15
- a.j：15 次，1 种取值；false×15
- a.k：14 次，1 种取值；roleadmin×14
- l.a：5 次，1 种取值；currentNode×5
- l.b：5 次，1 种取值，含短汉字取值（整属性不出值）
- l.c：5 次，5 种取值；{是否用印} == "是"×1；{项目交付部门} in [08华中区域(包含子部门)]×1；{销售负责人} == 〔具体人员〕×1；{信息服务费小计} > 0 or {预估毛利率} < 0.45×1
- l.d：4 次，1 种取值；与当前分支同级的其他分支条件都不满足时，则执行排他分支×4
- l.e：10 次，10 种取值，含 8 位以上数字取值
- l.h：30 次，3 种取值，含短汉字取值（整属性不出值）
- l.i：30 次，30 种取值，含 8 位以上数字取值
- l.j：30 次，25 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：30 次，25 种取值，含 8 位以上数字取值
- l.m：5 次，5 种取值，含 8 位以上数字取值
- l.t：30 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：26 次，26 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：14 次，1 种取值；1000×14
- n.n：26 次，13 种取值，含短汉字取值（整属性不出值）
- n.ncn：2 次，2 种取值；财务负责人×1；印章管理员×1
- n.ncn_i18n：2 次，2 种取值；财务负责人×1；印章管理员×1
- n.p：10 次，2 种取值；false×5；true×5
- n.t：26 次，4 种取值，含短汉字取值（整属性不出值）
- n.x：26 次，21 种取值，含短汉字取值（整属性不出值）
- n.y：26 次，3 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值，含 8 位以上数字取值
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.ts：1 次，1 种取值；×1
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；true×1
- s.aa：26 次，1 种取值，含短汉字取值（整属性不出值）
- s.cy：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.dfexn：3 次，1 种取值；<点击选择人员>×3
- s.ds：14 次，2 种取值，含短汉字取值（整属性不出值）
- s.f：15 次，1 种取值，含 8 位以上数字取值
- s.ft：26 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：26 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：14 次，7 种取值，含 8 位以上数字取值
- s.g：26 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：14 次，2 种取值；approve×7；inform×7
- s.j：26 次，1 种取值；single×26
- s.k：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：26 次，1 种取值；false×26
- s.md：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.mu：26 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：14 次，2 种取值，含短汉字取值（整属性不出值）
- s.na：8 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_b：26 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_i：26 次，2 种取值，含短汉字取值（整属性不出值）
- s.otmn：3 次，1 种取值，含短汉字取值（整属性不出值）
- s.q：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：26 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：8 次，1 种取值，含短汉字取值（整属性不出值）
### IT资产采购申请（9068384877204753833）

- a.b：9 次，1 种取值，含 8 位以上数字取值
- a.c：10 次，1 种取值，含短汉字取值（整属性不出值）
- a.d：9 次，8 种取值，含短汉字取值（整属性不出值）
- a.f：9 次，8 种取值，含 8 位以上数字取值；Department@field0007#成本中心#DepManager×1
- a.g：9 次，3 种取值；FormField×3；Team×5；user×1
- a.h：10 次，1 种取值；false×10
- a.i：10 次，1 种取值；false×10
- a.j：10 次，1 种取值；false×10
- a.k：9 次，1 种取值；roleadmin×9
- l.a：1 次，1 种取值；start×1
- l.b：1 次，1 种取值，含短汉字取值（整属性不出值）
- l.c：1 次，1 种取值；[部门(主岗部门)] == "0602流程信息"×1
- l.d：1 次，1 种取值；与当前分支同级的其他分支条件都不满足时，则执行排他分支×1
- l.e：2 次，2 种取值，含 8 位以上数字取值
- l.h：13 次，3 种取值，含短汉字取值（整属性不出值）
- l.i：13 次，13 种取值，含 8 位以上数字取值
- l.j：13 次，12 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- l.k：13 次，12 种取值，含 8 位以上数字取值；start×1
- l.m：1 次，1 种取值，含 8 位以上数字取值
- l.t：13 次，1 种取值，含短汉字取值（整属性不出值）
- n.b：1 次，1 种取值；normal×1
- n.i：13 次，13 种取值，含短汉字取值（整属性不出值），含 8 位以上数字取值
- n.l：9 次，1 种取值；1000×9
- n.n：13 次，12 种取值，含短汉字取值（整属性不出值）
- n.ncn：3 次，3 种取值；财务管理部负责人×1；成本中心主管×1；运维经理×1
- n.ncn_i18n：3 次，3 种取值；财务管理部负责人×1；成本中心主管×1；运维经理×1
- n.p：2 次，2 种取值；false×1；true×1
- n.t：13 次，4 种取值，含短汉字取值（整属性不出值）
- n.x：13 次，12 种取值，含短汉字取值（整属性不出值）
- n.y：13 次，3 种取值，含短汉字取值（整属性不出值）
- p.d：1 次，1 种取值；×1
- p.i：1 次，1 种取值；×1
- p.n：1 次，1 种取值；×1
- p.s：1 次，1 种取值；false×1
- p.t：1 次，1 种取值，含短汉字取值（整属性不出值）
- p.ts：1 次，1 种取值；×1
- p.u：1 次，1 种取值；×1
- p.w：1 次，1 种取值；false×1
- s.aa：13 次，1 种取值，含短汉字取值（整属性不出值）
- s.cy：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.dfexn：5 次，1 种取值；<点击选择人员>×5
- s.ds：9 次，1 种取值，含短汉字取值（整属性不出值）
- s.f：10 次，1 种取值，含 8 位以上数字取值
- s.ft：13 次，1 种取值，含短汉字取值（整属性不出值）
- s.ftv：13 次，1 种取值，含短汉字取值（整属性不出值）
- s.fv：10 次，4 种取值，含 8 位以上数字取值
- s.g：13 次，1 种取值，含短汉字取值（整属性不出值）
- s.i：9 次，1 种取值；approve×9
- s.j：13 次，2 种取值；multiple×1；single×12
- s.k：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.l：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.m：13 次，1 种取值；false×13
- s.md：8 次，2 种取值，含短汉字取值（整属性不出值）
- s.mu：13 次，1 种取值，含短汉字取值（整属性不出值）
- s.n：9 次，1 种取值，含短汉字取值（整属性不出值）
- s.na：8 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_b：13 次，2 种取值，含短汉字取值（整属性不出值）
- s.na_i：13 次，2 种取值，含短汉字取值（整属性不出值）
- s.otmn：6 次，1 种取值，含短汉字取值（整属性不出值）
- s.q：8 次，1 种取值，含短汉字取值（整属性不出值）
- s.t：13 次，1 种取值，含短汉字取值（整属性不出值）
- s.v：9 次，1 种取值，含短汉字取值（整属性不出值）
- s.w：8 次，1 种取值，含短汉字取值（整属性不出值）

## Q6-B 节点权限定义（类别｜名称｜类型｜删除标志｜排序）

- col_flow_perm_policy｜newCol｜0｜0｜0
- col_flow_perm_policy｜newCol｜0｜0｜0
- col_flow_perm_policy｜collaboration｜0｜0｜1
- col_flow_perm_policy｜collaboration｜0｜0｜1
- col_flow_perm_policy｜approve｜0｜0｜2
- col_flow_perm_policy｜approve｜0｜0｜2
- col_flow_perm_policy｜inform｜0｜0｜3
- col_flow_perm_policy｜inform｜0｜0｜3
- col_flow_perm_policy｜formaudit｜0｜0｜4
- col_flow_perm_policy｜formaudit｜0｜0｜4
- col_flow_perm_policy｜vouch｜0｜0｜5
- col_flow_perm_policy｜vouch｜0｜0｜5
- col_flow_perm_policy｜newsaudit｜0｜0｜6
- col_flow_perm_policy｜newsaudit｜0｜0｜6
- col_flow_perm_policy｜bulletionaudit｜0｜0｜7
- col_flow_perm_policy｜bulletionaudit｜0｜0｜7
- col_flow_perm_policy｜read｜0｜0｜8
- col_flow_perm_policy｜read｜0｜0｜8
- col_flow_perm_policy｜sendoredit｜0｜0｜9
- col_flow_perm_policy｜招聘面试审批｜1｜0｜9
- col_flow_perm_policy｜sendoredit｜0｜0｜9
- col_flow_perm_policy｜居间费审批是否有备案｜1｜0｜10
- col_flow_perm_policy｜商机确认已经建群｜1｜0｜11
- col_flow_perm_policy｜预算审核意见｜1｜0｜12
- col_flow_perm_policy｜转正意见｜1｜0｜13
- col_flow_perm_policy｜票据审批｜1｜0｜15
- col_flow_perm_policy｜开票执行｜1｜0｜16
- col_flow_perm_policy｜凭证｜1｜0｜17
- col_flow_perm_policy｜红字开票执行｜1｜0｜18
- col_flow_perm_policy｜票据核票｜1｜0｜19
- edoc_new_change_permission_policy｜qianshou｜0｜0｜1
- edoc_new_change_permission_policy｜fenban｜0｜0｜2
- edoc_new_change_permission_policy｜yuedu｜0｜0｜3
- edoc_new_qianbao_permission_policy｜niwen｜0｜0｜0
- edoc_new_qianbao_permission_policy｜shenhe｜0｜0｜1
- edoc_new_qianbao_permission_policy｜shenpi｜0｜0｜2
- edoc_new_qianbao_permission_policy｜huiqian｜0｜0｜3
- edoc_new_qianbao_permission_policy｜banli｜0｜0｜4
- edoc_new_qianbao_permission_policy｜yuedu｜0｜0｜5
- edoc_new_qianbao_permission_policy｜zhihui｜0｜0｜6
- edoc_new_qianbao_permission_policy｜qianfa｜0｜0｜7
- edoc_new_qianbao_permission_policy｜pishi｜0｜0｜8
- edoc_new_qianbao_permission_policy｜wenshuguanli｜0｜0｜9
- edoc_new_qianbao_permission_policy｜chengban｜0｜0｜10
- edoc_new_qianbao_permission_policy｜sendoredit｜0｜0｜11
- edoc_new_qianbao_permission_policy｜sendoredit｜0｜0｜11
- edoc_new_rec_permission_policy｜dengji｜0｜0｜0
- edoc_new_rec_permission_policy｜banli｜0｜0｜1
- edoc_new_rec_permission_policy｜huiqian｜0｜0｜2
- edoc_new_rec_permission_policy｜niban｜0｜0｜3
- edoc_new_rec_permission_policy｜pishi｜0｜0｜4
- edoc_new_rec_permission_policy｜zhihui｜0｜0｜5
- edoc_new_rec_permission_policy｜chengban｜0｜0｜6
- edoc_new_rec_permission_policy｜shenpi｜0｜0｜7
- edoc_new_rec_permission_policy｜wenshuguanli｜0｜0｜8
- edoc_new_rec_permission_policy｜report｜0｜0｜9
- edoc_new_rec_permission_policy｜yuedu｜0｜0｜10
- edoc_new_rec_permission_policy｜sendoredit｜0｜0｜11
- edoc_new_rec_permission_policy｜sendoredit｜0｜0｜11
- edoc_new_rec_permission_policy｜qianshoudengji｜0｜0｜12
- edoc_new_rec_permission_policy｜qianshoudengji｜0｜0｜12
- edoc_new_send_permission_policy｜niwen｜0｜0｜0
- edoc_new_send_permission_policy｜shenpi｜0｜0｜1
- edoc_new_send_permission_policy｜shenhe｜0｜0｜2
- edoc_new_send_permission_policy｜fuhe｜0｜0｜3
- edoc_new_send_permission_policy｜huiqian｜0｜0｜4
- edoc_new_send_permission_policy｜qianfa｜0｜0｜5
- edoc_new_send_permission_policy｜zhihui｜0｜0｜6
- edoc_new_send_permission_policy｜yuedu｜0｜0｜7
- edoc_new_send_permission_policy｜wenshuguanli｜0｜0｜8
- edoc_new_send_permission_policy｜faxing｜0｜0｜9
- edoc_new_send_permission_policy｜chengban｜0｜0｜10
- edoc_new_send_permission_policy｜sendoredit｜0｜0｜12
- edoc_new_send_permission_policy｜sendoredit｜0｜0｜12
- edoc_qianbao_permission_policy｜niwen｜0｜0｜0
- edoc_qianbao_permission_policy｜shenpi｜0｜0｜1
- edoc_qianbao_permission_policy｜shenhe｜0｜0｜2
- edoc_qianbao_permission_policy｜pishi｜0｜0｜3
- edoc_qianbao_permission_policy｜zhihui｜0｜0｜4
- edoc_qianbao_permission_policy｜huiqian｜0｜0｜5
- edoc_qianbao_permission_policy｜qianfa｜0｜0｜6
- edoc_qianbao_permission_policy｜banli｜0｜0｜7
- edoc_qianbao_permission_policy｜wenshuguanli｜0｜0｜8
- edoc_qianbao_permission_policy｜yuedu｜0｜0｜9
- edoc_rec_permission_policy｜dengji｜0｜0｜0
- edoc_rec_permission_policy｜yuedu｜0｜0｜1
- edoc_rec_permission_policy｜niban｜0｜0｜2
- edoc_rec_permission_policy｜zhihui｜0｜0｜3
- edoc_rec_permission_policy｜chengban｜0｜0｜4
- edoc_rec_permission_policy｜pishi｜0｜0｜5
- edoc_rec_permission_policy｜shenpi｜0｜0｜6
- edoc_rec_permission_policy｜banli｜0｜0｜7
- edoc_rec_permission_policy｜huiqian｜0｜0｜8
- edoc_rec_permission_policy｜report｜0｜0｜9
- edoc_rec_permission_policy｜wenshuguanli｜0｜0｜10
- edoc_send_permission_policy｜niwen｜0｜0｜0
- edoc_send_permission_policy｜shenpi｜0｜0｜1
- edoc_send_permission_policy｜shenhe｜0｜0｜2
- edoc_send_permission_policy｜fuhe｜0｜0｜3
- edoc_send_permission_policy｜zhihui｜0｜0｜4
- edoc_send_permission_policy｜huiqian｜0｜0｜5
- edoc_send_permission_policy｜qianfa｜0｜0｜6
- edoc_send_permission_policy｜fengfa｜0｜0｜7
- edoc_send_permission_policy｜wenshuguanli｜0｜0｜8
- edoc_send_permission_policy｜yuedu｜0｜0｜9
- info_send_permission_policy｜shangbao｜0｜0｜1
- info_send_permission_policy｜jieshou｜0｜0｜2
- info_send_permission_policy｜shenhe｜0｜0｜3
- info_send_permission_policy｜qianfa｜0｜0｜4
- info_send_permission_policy｜caibian｜0｜0｜5
- office_flow_perm_policy｜autoaudit｜0｜0｜null
- office_flow_perm_policy｜autoaudit｜0｜0｜null
- office_flow_perm_policy｜stockaudit｜0｜0｜null
- office_flow_perm_policy｜stockaudit｜0｜0｜null
- office_flow_perm_policy｜assetaudit｜0｜0｜null
- office_flow_perm_policy｜assetaudit｜0｜0｜null
- office_flow_perm_policy｜zhihui｜0｜0｜null

## 取证结论旁注（B31 页）

新增报价流程的条件表达式中有 1 处把具体人员写死在分支条件里（形如「销售负责人 == 〔具体人员〕」）：老系统用具体人员而不是角色或规则来决定分支，迁入新系统时不能照搬，应改为角色或规则表达，并列入迁入核对清单。该表达式在 `l.c` 属性中（新增报价模板的条件分支）。
