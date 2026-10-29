#!/usr/bin/env python3
"""FORCITIS one · C-3 · 在 ECS 上轮换 one_admin 口令（经云助手以 root 执行）。

PostgreSQL 允许角色改自己的口令：用现有口令连接，执行 ALTER ROLE one_admin PASSWORD '<SCRAM 校验值>'。
新口令只在本进程内生成，数据库只收到校验值；口令不进命令行参数、环境变量与输出。
顺序：先写新口令文件（.new，0600）→ 改口令 → 用新口令登录验证 → 通过才替换正式文件；验证失败时保留 .new 供排查。
用法：c3_rotate_admin_password.py
"""
import os
import secrets
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from c3_set_role_passwords import scram_verifier, write_private  # noqa: E402

PGPASS = '/etc/forcitis-one/one-admin.pgpass'
ACCOUNT = 'one_admin'


def psql(host, pgpass, sql):
    return subprocess.run(['psql', '-X', '-v', 'ON_ERROR_STOP=1', '-h', host, '-U', ACCOUNT, '-d', 'postgres', '-At'],
                          input=sql, text=True, capture_output=True, env={**os.environ, 'PGPASSFILE': pgpass})


def main():
    with open(PGPASS) as f:
        host, port, database, user, _ = f.read().strip().split(':', 4)
    if user != ACCOUNT:
        raise SystemExit(f'{PGPASS} is not for {ACCOUNT}')
    password = secrets.token_urlsafe(32)
    pending = PGPASS + '.new'
    write_private(pending, f'{host}:{port}:{database}:{ACCOUNT}:{password}\n')

    changed = psql(host, PGPASS, f"ALTER ROLE {ACCOUNT} PASSWORD '{scram_verifier(password)}';\n")
    del password
    if changed.returncode != 0:
        os.remove(pending)
        raise SystemExit(f'ALTER ROLE refused, password unchanged: {changed.stderr.strip()}')

    check = psql(host, pending, 'SELECT current_user;\n')
    if check.returncode != 0 or check.stdout.strip() != ACCOUNT:
        raise SystemExit(f'password changed but login with the new one failed; {pending} kept for investigation: {check.stderr.strip()}')
    os.replace(pending, PGPASS)
    os.chmod(PGPASS, 0o600)
    print(f'{ACCOUNT} password rotated; login with the new password verified')


if __name__ == '__main__':
    main()
