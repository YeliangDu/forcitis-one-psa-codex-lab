# B31P 第三批结构探查结果（one-ops，2026-10-17，one_evidence_ro 只读）

来源：`b31p_r3_columns.sql`、`b31p_r3_xml_structure.sql`、`b31p_r3_json_structure.sql`。全部在 SQL Server 内解析（XQuery、OPENJSON），只输出元素名、属性名、键路径、值类型与出现次数；不含任何属性值、文本或 XML/JSON 本身，也不含实例与个人信息。

## ① 列名与类型（a8_db）

### CTP_FORMULA（10 列）

ID bigint；FORMULA_TYPE smallint；EXPRESSION ntext(1073741823)；EXPRESSION_XML ntext(1073741823)；OWNER_ID bigint；OWNER_TYPE smallint；CREATE_USER bigint；CREATE_DATE datetime；DESCRIPTION ntext(1073741823)；VERSION nvarchar(10)

### CTP_NODE_PERMISSION（17 列）

ID bigint；CATEGORY nvarchar(255)；NAME nvarchar(255)；VALUE nvarchar(1000)；TYPE smallint；NODE_POLICY ntext(1073741823)；REFERENCE_ID bigint；DESCRIPTION nvarchar(255)；ORG_ACCOUNT_ID bigint；SORT int；IS_DELETE smallint；CREATE_MEMBER_ID bigint；MODIFY_MEMBER_ID bigint；CREATE_DATE datetime；MODIFY_DATE datetime；IS_ADMIN smallint；SHOW_SUBJECT nvarchar(255)

### ORG_POST（14 列）

ID bigint；NAME nvarchar(500)；CODE nvarchar(500)；IS_ENABLE smallint；TYPE int；SORT_ID int；CREATE_TIME datetime；UPDATE_TIME datetime；DESCRIPTION nvarchar(4000)；ORG_ACCOUNT_ID bigint；IS_DELETED smallint；STATUS smallint；EXTERNAL_TYPE smallint；I18N_NAME_ID bigint

### ORG_ROLE（17 列）

ID bigint；NAME nvarchar(500)；CATEGORY nvarchar(500)；CODE nvarchar(500)；TYPE smallint；BOND smallint；IS_BENCHMARK smallint；ORG_ACCOUNT_ID bigint；SORT_ID int；IS_ENABLE smallint；IS_DELETED smallint；STATUS smallint；CREATE_TIME datetime；UPDATE_TIME datetime；DESCRIPTION nvarchar(4000)；EXTERNAL_TYPE smallint；SHOW_CHART smallint

### WF_PROCESS_TEMPLETE（13 列）

ID bigint；PROCESSNAME nvarchar(500)；WORKFLOW ntext(1073741823)；WORKFLOWRULE ntext(1073741823)；CREATEDATE datetime；CREATEUSER nvarchar(36)；MODIFYDATE datetime；MODIFYUSER nvarchar(36)；APPID bigint；STATE int；BATCHID bigint；OLD_TEMPLATE_ID bigint；SHADOW_XML_ID bigint

主键均为 ID；CTP_TEMPLATE 有 FORM_APPID（→CAP_FORM_DEFINITION.ID）与 WORKFLOW_ID（→WF_PROCESS_TEMPLETE.ID）列，FORM_APPID 有非聚集索引。

## ② 解析情况

| 模板 | 来源 | 字符数 | 结论 |
|---|---|---|---|
| 需求工单 | WF_PROCESS_TEMPLETE.WORKFLOW | 3552 | XML，可解析 |
| 用印申请 | WF_PROCESS_TEMPLETE.WORKFLOW | 18696 | XML，可解析 |
| 需求工单 | CAP_FORM_DEFINITION.FIELD_INFO | 9059 | **不是 XML，是 JSON**（以 `{` 开头，ISJSON=1） |
| 用印申请 | CAP_FORM_DEFINITION.FIELD_INFO | 11902 | **JSON** |

## ③ 流程定义 XML 的元素树（WORKFLOW；元素名为单字母缩写，父元素 → 元素：次数）

**需求工单**：(根) → ps：1；n → a：5；n → s：8；p → l：8；p → n：8；ps → p：1

**用印申请**：(根) → ps：1；n → a：23；n → s：36；p → l：47；p → n：36；ps → p：1

## ④ 流程定义 XML 的属性名（元素.属性：次数）

**需求工单**：a.b×4；a.c×5；a.d×4；a.f×4；a.g×4；a.h×5；a.i×5；a.j×5；a.k×4；l.a×1；l.b×1；l.c×1；l.d×1；l.e×2；l.h×8；l.i×8；l.j×8；l.k×8；l.m×1；l.t×8；n.b×1；n.i×8；n.l×4；n.n×8；n.ncn×2；n.ncn_i18n×2；n.p×2；n.t×8；n.x×8；n.y×8；p.d×1；p.i×1；p.n×1；p.s×1；p.t×1；p.ts×1；p.u×1；p.w×1；s.aa×8；s.cy×4；s.dfexn×1；s.ds×5；s.f×5；s.ft×8；s.ftv×8；s.fv×5；s.g×8；s.i×5；s.j×8；s.k×4；s.l×4；s.m×8；s.md×4；s.mu×8；s.n×5；s.na×4；s.na_b×8；s.na_i×8；s.oe×1；s.otmn×1；s.q×4；s.t×8；s.v×4；s.w×4

**用印申请**：a.b×22；a.c×23；a.d×22；a.f×22；a.g×22；a.h×23；a.i×23；a.j×23；a.k×22；l.a×12；l.b×12；l.c×12；l.d×3；l.e×18；l.h×47；l.i×47；l.j×47；l.k×47；l.m×12；l.t×47；n.b×1；n.i×36；n.l×22；n.n×36；n.ncn×5；n.ncn_i18n×5；n.p×12；n.t×36；n.x×36；n.y×36；p.d×1；p.i×1；p.n×1；p.s×1；p.t×1；p.ts×1；p.u×1；p.w×1；s.aa×36；s.cy×11；s.dfexn×3；s.ds×22；s.f×23；s.ft×36；s.ftv×36；s.fv×23；s.g×36；s.i×22；s.j×36；s.k×11；s.l×11；s.m×36；s.md×11；s.mu×36；s.n×22；s.na×11；s.na_b×36；s.na_i×36；s.otmn×3；s.q×11；s.t×36；s.v×15；s.w×11

## ⑤ 表单字段定义 JSON 的键路径（数组下标记 []；值类型码：0 空、1 字符串、2 数字、3 布尔、4 数组、5 对象；路径：类型×次数）

**需求工单**：$.formsons［4］×1；$.front_formmain［5］×1；$.front_formmain.buttonStyle［5］×1；$.front_formmain.conditionStyleSet［4］×1；$.front_formmain.createDate［1］×1；$.front_formmain.dbTableName［1］×1；$.front_formmain.display［1］×1；$.front_formmain.emptyLineFields［4］×1；$.front_formmain.emptyLineRule［2］×1；$.front_formmain.fieldInfo［4］×1；$.front_formmain.fieldInfo.[]［5］×31；$.front_formmain.fieldInfo.[].attachmentTemplate［5］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.createdate［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.downloadUrl［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.fileUrl［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.filename［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.mimeType［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.size［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.v［0］×1；$.front_formmain.fieldInfo.[].barcode［1］×12；$.front_formmain.fieldInfo.[].canManualModify［3］×19；$.front_formmain.fieldInfo.[].className［1］×12；$.front_formmain.fieldInfo.[].columnName［1］×19；$.front_formmain.fieldInfo.[].customParam［1］×1；$.front_formmain.fieldInfo.[].desc［1］×19；$.front_formmain.fieldInfo.[].display［1］×31；$.front_formmain.fieldInfo.[].enumInfo［1］×4；$.front_formmain.fieldInfo.[].extend［5］×19；$.front_formmain.fieldInfo.[].extend.selectOrgValue［1］×19；$.front_formmain.fieldInfo.[].extend.supportOrgCode［3］×19；$.front_formmain.fieldInfo.[].fieldLength［1］×31；$.front_formmain.fieldInfo.[].fieldType［1］×19；$.front_formmain.fieldInfo.[].formatType［1］×19；$.front_formmain.fieldInfo.[].id［1］×31；$.front_formmain.fieldInfo.[].isNull［1］×12；$.front_formmain.fieldInfo.[].isPrimary［1］×12；$.front_formmain.fieldInfo.[].isSecurity［1］×19；$.front_formmain.fieldInfo.[].maxAttachments［1］×1；$.front_formmain.fieldInfo.[].minAttachments［1］×1；$.front_formmain.fieldInfo.[].name［1］×31；$.front_formmain.fieldInfo.[].placeHolder［1］×19；$.front_formmain.fieldInfo.[].relationId［1］×1；$.front_formmain.fieldInfo.[].type［1］×31；$.front_formmain.frontTableName［1］×1；$.front_formmain.id［1］×1；$.front_formmain.ownerTable［1］×1；$.front_formmain.repeatMaxRow［2］×1；$.front_formmain.tableFilterSet［4］×1；$.front_formmain.tableName［1］×1；$.front_formmain.tableType［1］×1

**用印申请**：$.formsons［4］×1；$.front_formmain［5］×1；$.front_formmain.buttonStyle［5］×1；$.front_formmain.conditionStyleSet［4］×1；$.front_formmain.createDate［1］×1；$.front_formmain.dbTableName［1］×1；$.front_formmain.display［1］×1；$.front_formmain.emptyLineFields［4］×1；$.front_formmain.emptyLineRule［2］×1；$.front_formmain.fieldInfo［4］×1；$.front_formmain.fieldInfo.[]［5］×40；$.front_formmain.fieldInfo.[].attachmentTemplate［5］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.createdate［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.downloadUrl［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.fileUrl［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.filename［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.mimeType［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.size［0］×1；$.front_formmain.fieldInfo.[].attachmentTemplate.v［0］×1；$.front_formmain.fieldInfo.[].barcode［1］×12；$.front_formmain.fieldInfo.[].canManualModify［3］×28；$.front_formmain.fieldInfo.[].className［1］×12；$.front_formmain.fieldInfo.[].columnName［1］×28；$.front_formmain.fieldInfo.[].customParam［1］×1；$.front_formmain.fieldInfo.[].desc［1］×28；$.front_formmain.fieldInfo.[].display［1］×40；$.front_formmain.fieldInfo.[].enumInfo［1］×8；$.front_formmain.fieldInfo.[].extend［5］×28；$.front_formmain.fieldInfo.[].extend.selectOrgValue［1］×28；$.front_formmain.fieldInfo.[].extend.supportOrgCode［3］×28；$.front_formmain.fieldInfo.[].fieldLength［1］×40；$.front_formmain.fieldInfo.[].fieldType［1］×28；$.front_formmain.fieldInfo.[].formatType［1］×28；$.front_formmain.fieldInfo.[].id［1］×40；$.front_formmain.fieldInfo.[].isNull［1］×12；$.front_formmain.fieldInfo.[].isPrimary［1］×12；$.front_formmain.fieldInfo.[].isSecurity［1］×28；$.front_formmain.fieldInfo.[].maxAttachments［1］×1；$.front_formmain.fieldInfo.[].minAttachments［1］×1；$.front_formmain.fieldInfo.[].name［1］×40；$.front_formmain.fieldInfo.[].placeHolder［1］×28；$.front_formmain.fieldInfo.[].relationId［1］×3；$.front_formmain.fieldInfo.[].type［1］×40；$.front_formmain.frontTableName［1］×1；$.front_formmain.id［1］×1；$.front_formmain.ownerTable［1］×1；$.front_formmain.repeatMaxRow［2］×1；$.front_formmain.tableFilterSet［4］×1；$.front_formmain.tableName［1］×1；$.front_formmain.tableType［1］×1
