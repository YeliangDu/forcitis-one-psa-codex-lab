# 组织与角色抽取缺口

由 panorama/scan_org_roles.py 生成，勿手改。

## 声明有、枚举无（0）


## 枚举有、无系统角色持有（4）

- cost.timesheet:timesheet:approve
- cost.timesheet:timesheet:submit
- platform.numbering:legacy-number:read
- platform.numbering:voucher-numbering-rule:approve

按 SystemRoleDeclaration 与 AdministratorMode 代码定义统计，运行时由管理员配置的自定义角色不在此列。
系统角色的 kind 不在声明参数里：声明类即系统角色，启动对账一律写 RoleKind.SYSTEM，抽取据此填 SYSTEM；status 取 declared(...) 写入的 RoleStatus 常量。
三处角色原样并列，不合并、不裁定；命令引用按每条 entries 去重，A / R 分列，any 为两者并集。
组织类别含相关表的状态枚举；未具名 CHECK 的 constraint 为 null，不推断数据库自动命名。
