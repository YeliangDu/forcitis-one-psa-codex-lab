#!/usr/bin/env python3
"""FORCITIS one · C-14 · 只读：执行账号（老系统主数据源）在 master、msdb、rdscore 里的身份与有效权限，只出数字与是否映射，不改任何东西。

依据：C-14 预检门槛（#372）发现执行账号在 msdb、rdscore 缺 VIEW DEFINITION 与 ALTER ANY USER；用户 2026-10-16 选择授权方案，授权前先看它现有的身份与权限。
用法：c14_u8c_status.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ops_venv_guard  # noqa: E402,F401  须排在所有第三方与同目录导入之前
import c14_run  # noqa: E402

SERVER = ("SELECT IS_SRVROLEMEMBER(N'sysadmin') AS sysadmin, IS_SRVROLEMEMBER(N'securityadmin') AS securityadmin, IS_SRVROLEMEMBER(N'dbcreator') AS dbcreator,\n"
          "       ISNULL(HAS_PERMS_BY_NAME(NULL, NULL, N'CONTROL SERVER'), 0) AS control_server, ISNULL(HAS_PERMS_BY_NAME(NULL, NULL, N'ALTER ANY LOGIN'), 0) AS alter_any_login,\n"
          "       (SELECT COUNT(*) FROM fn_my_permissions(NULL, N'SERVER')) AS server_permissions;")
DATABASE = ("SELECT (SELECT COUNT(*) FROM sys.database_principals WHERE sid = SUSER_SID()) AS mapped_user, ISNULL(IS_MEMBER(N'db_owner'), 0) AS db_owner,\n"
            "       ISNULL(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'CONTROL'), 0) AS control, ISNULL(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'VIEW DEFINITION'), 0) AS view_definition,\n"
            "       ISNULL(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'ALTER ANY USER'), 0) AS alter_any_user, ISNULL(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'ALTER ANY ROLE'), 0) AS alter_any_role,\n"
            "       (SELECT COUNT(*) FROM fn_my_permissions(NULL, N'DATABASE')) AS database_permissions;")


def main():
    for label, db, sql in (('server', 'master', SERVER), ('msdb', 'msdb', DATABASE), ('rdscore', 'rdscore', DATABASE)):
        rc, _, out = c14_run.legacy_sql(f'status {label}', db, sql, mode='ro')
        print(label, 'rc', rc, 'numbers', c14_run.numbers(out)[-1:] or None)


if __name__ == '__main__':
    main()
