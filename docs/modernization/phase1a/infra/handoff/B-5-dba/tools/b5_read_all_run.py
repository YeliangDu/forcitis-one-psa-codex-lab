#!/usr/bin/env python3
"""FORCITIS one · B-5 · 三个新系统只读账号改为全库只读：执行、前后授权比对、真实登录验证（运维机上运行，阿里云 Python SDK）。

依据：用户「新系统可以读任何旧系统数据」「两者都是」「修改权限」「全部可读，不排除」（2026-10-15，经 one-coordinator）。
变更以老系统主数据源执行（经本机回环读取配置中心、管道交给 OneSql，不落盘不打印），每个库一个事务。
登录验证用新 ECS one.env 中现有的三个口令：在新 ECS 上用老系统服务器的临时公钥加密后转交，本进程只经手密文。
用法：b5_read_all_run.py
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from b5_legacy_run import DRIVER, LEGACY_SERVER, LEGACY_WORK, ONE_SERVER, put, run, stage_legacy  # noqa: E402

TOOLS = Path(__file__).resolve().parent
READ_ALL = TOOLS.parent / 'read-all'
ONE_WORK = '/root/one-ops-b5-read-all'
ACCOUNTS = (('A8', 'one_a8_views_ro', 'a8_db', 'A8_DB_PASSWORD', 'a8'),
            ('INBOX', 'psa_one_legacy_ro', 'ruoyi-vue-pro', 'FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD', 'legacy'),
            ('NAMES', 'one_legacy_names_ro', 'ruoyi-vue-pro', 'LEGACY_DB_RO_PASSWORD', 'legacy'))

ENCRYPT_FROM_ENV = r'''
import base64, subprocess, sys
pub, pairs = sys.argv[1], sys.argv[2:]
values = {}
for line in open('/etc/forcitis-one/one.env', encoding='utf-8'):
    line = line.strip()
    if line and not line.startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip().strip("'\"")
for pair in pairs:
    label, key = pair.split(':')
    if not values.get(key):
        sys.exit(f'{key} missing in one.env')
    cipher = subprocess.run(['openssl', 'pkeyutl', '-encrypt', '-pubin', '-inkey', pub, '-pkeyopt', 'rsa_padding_mode:oaep',
                             '-pkeyopt', 'rsa_oaep_md:sha256', '-pkeyopt', 'rsa_mgf1_md:sha256'],
                            input=values[key].encode(), capture_output=True, check=True).stdout
    print(f'{label}:{base64.b64encode(cipher).decode()}')
'''


def step(title, instance, script):
    invoke, status, code, out = run(instance, script)
    print(f'==== {title} {invoke} {status} exit={code}')
    return invoke, status, out


def main():
    _, status, out = step('legacy key pair', LEGACY_SERVER,
                          f'set -eu; umask 077; install -d -m 700 {LEGACY_WORK}; '
                          f'openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out {LEGACY_WORK}/logintest.key 2>/dev/null; '
                          f'openssl pkey -in {LEGACY_WORK}/logintest.key -pubout')
    logintest_pub = out[out.index('-----BEGIN'):]

    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text()}
    files.update({p.name: p.read_text() for p in READ_ALL.glob('*.sql')})
    stage = f'{LEGACY_WORK}/stage'
    script = stage_legacy(files) + DRIVER + 'set +e\n' + ''.join(
        f'echo "######## {label} {db}"; onesql {mode} {db} {stage}/{sql} || {{ echo "STEP FAILED: {label} {db}"; rm -rf {stage}; exit 2{n}; }}\n'
        for n, (label, mode, db, sql) in enumerate((
            ('before', 'ro', 'a8_db', 'permissions_dump.sql'), ('before', 'ro', 'ruoyi-vue-pro', 'permissions_dump.sql'),
            ('apply', 'rw', 'a8_db', 'a8_apply.sql'), ('apply', 'rw', 'ruoyi-vue-pro', 'legacy_apply.sql'),
            ('after', 'ro', 'a8_db', 'permissions_dump.sql'), ('after', 'ro', 'ruoyi-vue-pro', 'permissions_dump.sql'))))
    script += f'rm -rf {stage}\n'
    invoke, status, out = step('dump, apply, dump', LEGACY_SERVER, script)
    print(out)
    if status != 'Success':
        raise SystemExit('stopped before the login tests; see output above')

    pairs = ' '.join(f'{label}:{key}' for label, _, _, key, _ in ACCOUNTS)
    _, status, out = step('encrypt passwords on the new server', ONE_SERVER,
                          f'set -eu; umask 077; install -d -m 700 {ONE_WORK}\n' + put(f'{ONE_WORK}/logintest.pub', logintest_pub)
                          + put(f'{ONE_WORK}/enc.py', ENCRYPT_FROM_ENV)
                          + f'python3 -I {ONE_WORK}/enc.py {ONE_WORK}/logintest.pub {pairs}; rm -rf {ONE_WORK}\n')
    ciphers = dict(re.findall(r'^(A8|INBOX|NAMES):([A-Za-z0-9+/=]+)$', out, re.M))
    if status != 'Success' or len(ciphers) != 3:
        raise SystemExit(f'expected three encrypted passwords, got {sorted(ciphers)}')

    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text()}
    files.update({p.name: p.read_text() for p in READ_ALL.glob('*_login_*.sql')})
    files.update({f'{label}.enc': cipher for label, cipher in ciphers.items()})
    test = 'rc=0\n'
    for label, login, db, _, kind in ACCOUNTS:
        env = f'LOGIN_USER={login} LOGIN_SECRET_ENC={stage}/{label}.enc LOGIN_KEY={LEGACY_WORK}/logintest.key'
        java = f'java -cp {LEGACY_WORK}/mssql-jdbc.jar {stage}/OneSql.java'
        test += (f'echo "######## read as {login}"; {env} {java} ro {db} {stage}/{kind}_login_read.sql < /dev/null || rc=1\n'
                 f'echo "######## denied as {login}"; {env} {java} deny {db} {stage}/{kind}_login_deny.sql < /dev/null || rc=1\n')
    invoke, status, out = step('login tests', LEGACY_SERVER, stage_legacy(files) + DRIVER + test + f'rm -rf {LEGACY_WORK}\nexit $rc\n')
    print(out)

    for server, work in ((LEGACY_SERVER, LEGACY_WORK), (ONE_SERVER, ONE_WORK)):
        _, _, out = step(f'cleanup {server}', server, f'rm -rf {work}; test -e {work} && echo LEFT || echo CLEAN')
        print(out.strip())


if __name__ == '__main__':
    main()
