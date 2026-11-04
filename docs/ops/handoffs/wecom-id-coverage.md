# B-6 交接包：在 A8 补齐「在职但无企业微信 ID」至 0（给组织数据管理员）

收件人：组织数据管理员（人选见 `DECISIONS_A.md` A-4）。时点：1a 出口前（D-9 出口条件）。依据：D-1a-34（三个登录入口都按企业微信 ID 精确映射，未映射即拒绝登录）、D-1a-35、D-1a-10。

## 为什么要做

FORCITIS one 只按 A8 人员档案里的企业微信 ID 认人。在职员工在 A8 里没有企业微信 ID，就无法用企业微信登录 FORCITIS one，也收不到审批卡片。1a 出口要求这份清单为 0；确实没有企业微信的人逐个登记原因。

## 步骤

1. **取清单**
   - （已失效，#934）原先取自管理端「同步记录」页的问题清单与指标 `forcitis.organization.workforce.employees.without.wecom`；旧同步及其页面、指标已删除，代码里没有等价的清单来源，需另定取清单的办法，现以下一条的预估查询为准。
   - 上线前预估（可选）：请 A8 DBA 用 B-5 建好的只读账号执行下面的查询，结果只给组织数据管理员，不外传：
     ```sql
     SELECT M.CODE, M.NAME, M.ORG_UNIT_PATH
       FROM forcitis_one.v_org_member M
      WHERE M.LOGIN_NAME IS NOT NULL
        AND M.ORG_UNIT_PATH LIKE '00000001%'
        AND (M.WE_COM_ID IS NULL OR LTRIM(RTRIM(M.WE_COM_ID)) = '')
      ORDER BY M.ORG_UNIT_PATH, M.CODE;
     ```
     说明：在职以「有登录名、在同步根单位下」近似；正式口径以同步后的问题清单为准。
2. **逐人处理**（都在 A8 中操作，FORCITIS one 不改组织数据，D-1a-11）
   - 有企业微信：在 A8 人员档案填入其企业微信 userid（企业微信管理后台「通讯录 → 成员详情」中的「账号」）。
   - 同一企业微信 ID 被多名在职者使用：修正为各自的 ID（否则 FORCITIS one 对这几人都不存，D-1a-13）。
   - 确实没有企业微信：在登记表中逐人写明原因（例如外包、长期病假），交业务管理员确认。登记表放在公司内部共享位置，不进仓库（含个人信息）。
3. **等下一轮同步**（每 5 分钟一次），回到问题清单确认该人已消失。

## 验收标准

- 问题清单中「在职但无企业微信 ID」为 0，或剩余每一人都在登记表中有经业务管理员确认的原因。
- 指标 `forcitis.organization.workforce.employees.without.wecom` 等于登记表人数。
- 每日对账（D-1a-10）中「企业微信 ID」一项零差异。

## 回滚

不涉及；A8 中填错的 ID 直接在 A8 改正，下一轮同步生效。
