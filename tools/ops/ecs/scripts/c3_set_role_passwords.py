#!/usr/bin/env python3
"""FORCITIS one · C-3 · 在 ECS 上为 one_<env>_app / _migrator / _readonly 生成口令并设置（经云助手以 root 执行）。

口令只在本进程内生成；数据库只收到 SCRAM-SHA-256 校验值；口令不进命令行参数、环境变量与输出。
写入：app → /etc/forcitis-one/one.env 的 SPRING_DATASOURCE_PASSWORD；migrator → /etc/forcitis-one/migrate/spring.flyway.{user,password}
（docs/ops/runbooks/release.md §4）；readonly → /etc/forcitis-one/one-<env>-readonly.pgpass。已有口令时拒绝执行，轮换加 --rotate。
顺序（同 c3_rotate_admin_password.py，#856）：先把新口令写进各文件旁的 .new（0600）→ 一个事务里 ALTER ROLE 三个角色 → 用新口令逐个登录验证
→ 全部通过才替换正式文件。写 .new 失败或 ALTER 失败：删掉 .new，数据库与正式文件都不变；验证失败：数据库已改，保留 .new 供排查，正式文件不变。
用法：c3_set_role_passwords.py <env> <RDS 内网地址> [--rotate]
      c3_set_role_passwords.py --self-test   用替身跑顺序与失败路径，不连数据库
"""
import base64, hashlib, hmac, os, re, secrets, subprocess, sys, tempfile

ADMIN_PGPASS = '/etc/forcitis-one/one-admin.pgpass'
ONE_ENV = '/etc/forcitis-one/one.env'
MIGRATE_DIR = '/etc/forcitis-one/migrate'
ETC = '/etc/forcitis-one'


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
    """(ok, stdout, stderr) of one psql call."""
    result = subprocess.run(['psql', '-X', '-v', 'ON_ERROR_STOP=1', '-h', host, '-U', user, '-d', database, '-At'],
                            input=sql, text=True, capture_output=True, env={**os.environ, 'PGPASSFILE': pgpass})
    return result.returncode == 0, result.stdout.strip(), result.stderr.strip()


def write_private(path, content):
    fd = os.open(path + '.tmp', os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as f:
        f.write(content)
    os.replace(path + '.tmp', path)


def run(env, host, rotate, paths, psql=psql, write=write_private):
    """Set the three role passwords in the order of the docstring. paths: one_env, migrate_dir, etc, admin_pgpass."""
    if not re.fullmatch(r'dev|prod', env):
        raise SystemExit('env must be dev or prod')
    database = f'forcitis_one_{env}'
    app, migrator, readonly = (f'one_{env}_{r}' for r in ('app', 'migrator', 'readonly'))

    with open(paths['one_env']) as f:
        env_text = f.read()
    current = re.search(r'^SPRING_DATASOURCE_PASSWORD=(.*)$', env_text, re.M)
    if current is None:
        raise SystemExit('one.env has no SPRING_DATASOURCE_PASSWORD line')
    if current.group(1).strip("'\" ") and not rotate:
        raise SystemExit('passwords already set; pass --rotate to replace them')

    passwords = {role: secrets.token_urlsafe(32) for role in (app, migrator, readonly)}
    env_text = re.sub(r'^SPRING_DATASOURCE_PASSWORD=.*$', lambda _: f"SPRING_DATASOURCE_PASSWORD='{passwords[app]}'", env_text, flags=re.M)
    env_text = env_text.replace('<RDS 内网地址>', host)
    os.makedirs(paths['migrate_dir'], mode=0o700, exist_ok=True)
    targets = {  # final file -> content
        paths['one_env']: env_text,
        os.path.join(paths['migrate_dir'], 'spring.flyway.user'): migrator,
        os.path.join(paths['migrate_dir'], 'spring.flyway.password'): passwords[migrator],
        os.path.join(paths['etc'], f'one-{env}-readonly.pgpass'): f'{host}:5432:{database}:{readonly}:{passwords[readonly]}\n',
    }

    written = []
    try:
        for path, content in targets.items():
            write(path + '.new', content)
            written.append(path + '.new')
    except OSError as e:
        for pending in written:
            os.remove(pending)
        raise SystemExit(f'writing the new files failed, nothing changed in the database: {type(e).__name__}')

    alter = 'BEGIN;\n' + ''.join(f"ALTER ROLE {role} PASSWORD '{scram_verifier(pw)}';\n" for role, pw in passwords.items()) + 'COMMIT;\n'
    ok, _, err = psql(host, 'one_admin', database, alter, paths['admin_pgpass'])
    if not ok:
        for pending in written:
            os.remove(pending)
        raise SystemExit(f'ALTER ROLE refused, passwords unchanged: {err}')

    with tempfile.TemporaryDirectory() as tmp:
        for role, pw in passwords.items():
            pgpass = os.path.join(tmp, role)
            write_private(pgpass, f'{host}:5432:{database}:{role}:{pw}\n')
            ok, out, err = psql(host, role, database, "SELECT current_user || ' read_only=' || current_setting('default_transaction_read_only');", pgpass)
            if not ok:
                raise SystemExit(f'passwords changed but login as {role} failed; the .new files are kept for investigation: {err}')
            print(role, out)

    stat = os.stat(paths['one_env'])
    for path in targets:
        os.replace(path + '.new', path)
    os.chown(paths['one_env'], stat.st_uid, stat.st_gid)
    os.chmod(paths['one_env'], stat.st_mode & 0o777)
    print('passwords set:', ', '.join(passwords))


def self_test():
    """Fakes for psql and the files; no database. Checks the order and that each failure leaves what the docstring promises."""
    import contextlib, io

    def setup(tmp):
        paths = {'one_env': os.path.join(tmp, 'one.env'), 'migrate_dir': os.path.join(tmp, 'migrate'), 'etc': tmp,
                 'admin_pgpass': os.path.join(tmp, 'admin.pgpass')}
        open(paths['one_env'], 'w').write("SPRING_DATASOURCE_PASSWORD=''\nX=<RDS 内网地址>\n")
        return paths

    def finals(paths):
        return [paths['one_env'], os.path.join(paths['migrate_dir'], 'spring.flyway.user'),
                os.path.join(paths['migrate_dir'], 'spring.flyway.password'), os.path.join(paths['etc'], 'one-dev-readonly.pgpass')]

    def fake_psql(fail_alter=False, fail_login=False):
        calls = []

        def call(host, user, database, sql, pgpass):
            calls.append((user, sql))
            if 'ALTER ROLE' in sql:
                return (not fail_alter, '', 'denied' if fail_alter else '')
            return (not fail_login, f'{user} read_only=off', 'auth failed' if fail_login else '')
        return call, calls

    def attempt(paths, **kw):
        with contextlib.redirect_stdout(io.StringIO()):
            try:
                run('dev', 'rds.internal', False, paths, **kw)
                return None
            except SystemExit as e:
                return str(e)

    with tempfile.TemporaryDirectory() as tmp:  # a failed write: no ALTER, no .new left, one.env untouched
        paths = setup(tmp)
        before = open(paths['one_env']).read()
        call, calls = fake_psql()
        count = []

        def flaky(path, content):
            count.append(path)
            if len(count) == 3:
                raise PermissionError('read-only file system')
            write_private(path, content)
        message = attempt(paths, psql=call, write=flaky)
        assert message and 'nothing changed in the database' in message, message
        assert calls == [], 'the database was not touched'
        assert not [p for p in finals(paths) if os.path.exists(p + '.new')], 'no .new file is left'
        assert open(paths['one_env']).read() == before, 'one.env is untouched'

    with tempfile.TemporaryDirectory() as tmp:  # ALTER refused: .new removed, files untouched
        paths = setup(tmp)
        before = open(paths['one_env']).read()
        call, calls = fake_psql(fail_alter=True)
        assert 'passwords unchanged' in attempt(paths, psql=call)
        assert not [p for p in finals(paths) if os.path.exists(p + '.new')] and open(paths['one_env']).read() == before
        assert len(calls) == 1 and calls[0][1].startswith('BEGIN;') and calls[0][1].endswith('COMMIT;\n'), 'one transaction'

    with tempfile.TemporaryDirectory() as tmp:  # login fails: .new kept, final files untouched
        paths = setup(tmp)
        before = open(paths['one_env']).read()
        call, _ = fake_psql(fail_login=True)
        assert 'kept for investigation' in attempt(paths, psql=call)
        assert all(os.path.exists(p + '.new') for p in finals(paths)) and open(paths['one_env']).read() == before

    with tempfile.TemporaryDirectory() as tmp:  # success: ALTER before any final file changes, then all replaced
        paths = setup(tmp)
        call, calls = fake_psql()
        assert attempt(paths, psql=call) is None
        assert [u for u, _ in calls] == ['one_admin', 'one_dev_app', 'one_dev_migrator', 'one_dev_readonly'], calls
        assert all(os.path.exists(p) and not os.path.exists(p + '.new') for p in finals(paths))
        text = open(paths['one_env']).read()
        assert re.search(r"^SPRING_DATASOURCE_PASSWORD='[^']{20,}'$", text, re.M) and 'rds.internal' in text
    print('c3_set_role_passwords self-test: ok')


def main():
    if sys.argv[1:] == ['--self-test']:
        self_test()
        return
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    run(sys.argv[1], sys.argv[2], '--rotate' in sys.argv[3:],
        {'one_env': ONE_ENV, 'migrate_dir': MIGRATE_DIR, 'etc': ETC, 'admin_pgpass': ADMIN_PGPASS})


if __name__ == '__main__':
    main()
