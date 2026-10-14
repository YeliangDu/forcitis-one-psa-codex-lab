#!/usr/bin/env python3
"""FORCITIS one · C-3 · 在 ECS 上为 one_<env>_app / _migrator / _readonly 生成口令并设置（经云助手以 root 执行）。

口令只在本进程内生成；数据库只收到 SCRAM-SHA-256 校验值；口令不进命令行参数、环境变量与输出。
写入：app → /etc/forcitis-one/one.env 的 SPRING_DATASOURCE_PASSWORD；migrator → /etc/forcitis-one/migrate/spring.flyway.{user,password}
（RELEASE.md §4）；readonly → /etc/forcitis-one/one-<env>-readonly.pgpass。已有口令时拒绝执行，轮换加 --rotate。
用法：c3_set_role_passwords.py <env> <RDS 内网地址> [--rotate]
"""
import base64, hashlib, hmac, os, re, secrets, subprocess, sys, tempfile

ADMIN_PGPASS = '/etc/forcitis-one/one-admin.pgpass'
ONE_ENV = '/etc/forcitis-one/one.env'
MIGRATE_DIR = '/etc/forcitis-one/migrate'


def scram_verifier(password: str) -> str:
    salt = secrets.token_bytes(16)
    iterations = 4096
    salted = hashlib.pbkdf2_hmac('sha256', password.encode(), salt, iterations)
    client_key = hmac.new(salted, b'Client Key', hashlib.sha256).digest()
    stored_key = hashlib.sha256(client_key).digest()
    server_key = hmac.new(salted, b'Server Key', hashlib.sha256).digest()
    b64 = lambda b: base64.b64encode(b).decode()
    return f'SCRAM-SHA-256${iterations}:{b64(salt)}${b64(stored_key)}:{b64(server_key)}'


def psql(host, user, database, sql, pgpass):
    result = subprocess.run(['psql', '-X', '-v', 'ON_ERROR_STOP=1', '-h', host, '-U', user, '-d', database, '-At'],
                            input=sql, text=True, capture_output=True, env={**os.environ, 'PGPASSFILE': pgpass})
    if result.returncode != 0:
        raise SystemExit(f'psql failed for {user}: {result.stderr.strip()}')
    return result.stdout.strip()


def write_private(path, content):
    fd = os.open(path + '.tmp', os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as f:
        f.write(content)
    os.replace(path + '.tmp', path)


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    env, host, rotate = sys.argv[1], sys.argv[2], '--rotate' in sys.argv[3:]
    if not re.fullmatch(r'dev|prod', env):
        raise SystemExit('env must be dev or prod')
    database = f'forcitis_one_{env}'
    app, migrator, readonly = (f'one_{env}_{r}' for r in ('app', 'migrator', 'readonly'))

    with open(ONE_ENV) as f:
        env_text = f.read()
    current = re.search(r'^SPRING_DATASOURCE_PASSWORD=(.*)$', env_text, re.M)
    if current is None:
        raise SystemExit('one.env has no SPRING_DATASOURCE_PASSWORD line')
    if current.group(1).strip("'\" ") and not rotate:
        raise SystemExit('passwords already set; pass --rotate to replace them')

    passwords = {role: secrets.token_urlsafe(32) for role in (app, migrator, readonly)}
    psql(host, 'one_admin', database,
         ''.join(f"ALTER ROLE {role} PASSWORD '{scram_verifier(pw)}';\n" for role, pw in passwords.items()), ADMIN_PGPASS)

    env_text = re.sub(r'^SPRING_DATASOURCE_PASSWORD=.*$', lambda _: f"SPRING_DATASOURCE_PASSWORD='{passwords[app]}'", env_text, flags=re.M)
    env_text = env_text.replace('<RDS 内网地址>', host)
    stat = os.stat(ONE_ENV)
    write_private(ONE_ENV, env_text)
    os.chown(ONE_ENV, stat.st_uid, stat.st_gid)
    os.chmod(ONE_ENV, stat.st_mode & 0o777)

    os.makedirs(MIGRATE_DIR, mode=0o700, exist_ok=True)
    write_private(f'{MIGRATE_DIR}/spring.flyway.user', migrator)
    write_private(f'{MIGRATE_DIR}/spring.flyway.password', passwords[migrator])
    write_private(f'/etc/forcitis-one/one-{env}-readonly.pgpass', f'{host}:5432:{database}:{readonly}:{passwords[readonly]}\n')

    with tempfile.TemporaryDirectory() as tmp:
        for role, pw in passwords.items():
            pgpass = f'{tmp}/{role}'
            write_private(pgpass, f'{host}:5432:{database}:{role}:{pw}\n')
            print(role, psql(host, role, database, "SELECT current_user || ' read_only=' || current_setting('default_transaction_read_only');", pgpass))
    print('passwords set:', ', '.join(passwords))


if __name__ == '__main__':
    main()
