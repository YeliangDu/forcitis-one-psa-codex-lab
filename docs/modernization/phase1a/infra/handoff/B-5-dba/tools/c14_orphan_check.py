#!/usr/bin/env python3
"""FORCITIS one · C-14 · 只读：探针登录已不存在时，msdb、rdscore 里是否还留着同名用户（孤儿）。

依据：孤儿实测（#385）第 1 轮收尾时执行账号在两库的 DROP USER 都被拒（15151），而 DROP USER 前的 USER_ID 判断为非空，说明 USER_ID 不受
sys.database_principals 元数据可见性限制。这里用 USER_ID 与 SUSER_ID 只读核对，只出数字，不改任何东西。
用法：c14_orphan_check.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ops_venv_guard  # noqa: E402,F401  须排在所有第三方与同目录导入之前
import c14_run  # noqa: E402

QUERY = f"SELECT ISNULL(USER_ID(N'{c14_run.LOGIN}'), 0) AS user_id, ISNULL(SUSER_ID(N'{c14_run.LOGIN}'), 0) AS login_id;"


def main():
    for database in ('msdb', 'rdscore', 'master'):
        rc, _, out = c14_run.legacy_sql(f'orphan check {database}', database, QUERY, mode='ro')
        print(database, 'rc', rc, 'user_id_login_id', c14_run.numbers(out)[-1:] or None)


if __name__ == '__main__':
    main()
