#!/usr/bin/env python3
"""FORCITIS one · ADR-0037 · 新建取证账号 one_evidence_ro、前后授权比对与真实登录验证（运维机上运行，阿里云 Python SDK）。

变更以老系统主数据源执行（只用于授权变更本身，ADR-0037 第 5 项第 4 条）。取证账号口令在 SQL Server 上生成，只以新 ECS 临时公钥加密的密文输出；
新 ECS 解密后写入 /etc/forcitis-one/evidence.env（root 0600），再与三个运行时口令一起用老系统服务器的临时公钥加密转交登录测试。本进程只经手密文。
用法：b5_evidence_run.py
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from b5_legacy_run import DRIVER, LEGACY_SERVER, LEGACY_WORK, ONE_SERVER, put, run, stage_legacy  # noqa: E402
from b5_read_all_run import ENCRYPT_FROM_ENV, step  # noqa: E402

TOOLS = Path(__file__).resolve().parent
B5 = TOOLS.parent
ONE_WORK = '/root/one-ops-b5-evidence'
EVIDENCE_ENV = '/etc/forcitis-one/evidence.env'
RUNTIME = (('A8', 'one_a8_views_ro', 'a8_db', 'A8_DB_PASSWORD'),
           ('INBOX', 'psa_one_legacy_ro', 'ruoyi-vue-pro', 'FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD'),
           ('NAMES', 'one_legacy_names_ro', 'ruoyi-vue-pro', 'LEGACY_DB_RO_PASSWORD'))
DECRYPT = ('dec() { base64 -d "$1" | openssl pkeyutl -decrypt -inkey ' + ONE_WORK + '/transfer.key '
           '-pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256; }\n')


def key_pair(server, work, name):
    _, _, out = step(f'key pair on {server}', server,
                     f'set -eu; umask 077; install -d -m 700 {work}; '
                     f'openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out {work}/{name}.key 2>/dev/null; '
                     f'openssl pkey -in {work}/{name}.key -pubout')
    return out[out.index('-----BEGIN'):]


def main():
    transfer_pub = key_pair(ONE_SERVER, ONE_WORK, 'transfer')
    logintest_pub = key_pair(LEGACY_SERVER, LEGACY_WORK, 'logintest')

    stage = f'{LEGACY_WORK}/stage'
    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text(), 'transfer.pub': transfer_pub,
             'permissions_dump.sql': (B5 / 'read-all' / 'permissions_dump.sql').read_text()}
    files.update({p.name: p.read_text() for p in (B5 / 'evidence').glob('*.sql')})
    steps = (('before', 'ro', 'ruoyi-vue-pro', 'server_permissions_dump.sql'),
             ('before', 'ro', 'a8_db', 'permissions_dump.sql'), ('before', 'ro', 'ruoyi-vue-pro', 'permissions_dump.sql'),
             ('evidence login', 'rw', 'master', 'evidence_login.sql'),
             ('evidence user', 'rw', 'a8_db', 'evidence_user_a8.sql'), ('evidence user', 'rw', 'ruoyi-vue-pro', 'evidence_user_legacy.sql'),
             ('after', 'ro', 'ruoyi-vue-pro', 'server_permissions_dump.sql'),
             ('after', 'ro', 'a8_db', 'permissions_dump.sql'), ('after', 'ro', 'ruoyi-vue-pro', 'permissions_dump.sql'))
    script = stage_legacy(files) + DRIVER + f'export ENC_PUBKEY={stage}/transfer.pub\nset +e\n' + ''.join(
        f'echo "######## {label} {db}"; onesql {mode} {db} {stage}/{sql} || {{ echo "STEP FAILED: {label} {db}"; rm -rf {stage}; exit {20 + n}; }}\n'
        for n, (label, mode, db, sql) in enumerate(steps)) + f'rm -rf {stage}\n'
    _, status, out = step('dump, change, dump', LEGACY_SERVER, script)
    print(re.sub(r'ENC:[A-Za-z0-9+/=]+', 'ENC:<ciphertext>', out))
    secret = re.findall(r'one_evidence_ro \| ENC:([A-Za-z0-9+/=]+)', out)
    if status != 'Success' or len(secret) != 1:
        raise SystemExit('stopped: change step failed or no encrypted secret; see output above')

    pairs = ' '.join(f'{label}:{key}' for label, _, _, key in RUNTIME)
    script = ('set -eu; umask 077\n' + put(f'{ONE_WORK}/set_env.py', (TOOLS / 'set_env.py').read_text())
              + put(f'{ONE_WORK}/logintest.pub', logintest_pub) + put(f'{ONE_WORK}/evidence.enc', secret[0])
              + put(f'{ONE_WORK}/enc.py', ENCRYPT_FROM_ENV) + DECRYPT
              + f'[ -e {EVIDENCE_ENV} ] || install -m 600 -o root -g root /dev/null {EVIDENCE_ENV}\n'
              f'dec {ONE_WORK}/evidence.enc | python3 -I {ONE_WORK}/set_env.py {EVIDENCE_ENV} EVIDENCE_DB_PASSWORD EVIDENCE_DB_USERNAME=one_evidence_ro\n'
              f"stat -c '%U:%G %a %n' {EVIDENCE_ENV}\n"
              f'python3 -I {ONE_WORK}/enc.py {ONE_WORK}/logintest.pub {pairs}\n'
              'echo "EVIDENCE:$(dec ' + f'{ONE_WORK}/evidence.enc | openssl pkeyutl -encrypt -pubin -inkey {ONE_WORK}/logintest.pub '
              '-pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256 | base64 -w0)"\n'
              f'rm -rf {ONE_WORK}\n')
    _, status, out = step('evidence.env and login-test ciphertexts', ONE_SERVER, script)
    print(re.sub(r'^(A8|INBOX|NAMES|EVIDENCE):[A-Za-z0-9+/=]+$', r'\1:<ciphertext>', out, flags=re.M))
    ciphers = dict(re.findall(r'^(A8|INBOX|NAMES|EVIDENCE):([A-Za-z0-9+/=]+)$', out, re.M))
    if status != 'Success' or len(ciphers) != 4:
        raise SystemExit(f'expected four ciphertexts, got {sorted(ciphers)}')

    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text()}
    files.update({p.name: p.read_text() for p in (B5 / 'evidence').glob('evidence_login_read_*.sql')})
    files['effective_permissions.sql'] = (B5 / 'evidence' / 'effective_permissions.sql').read_text()
    files.update({p.name: p.read_text() for p in (B5 / 'read-all').glob('*_login_deny.sql')})
    files.update({f'{label}.enc': cipher for label, cipher in ciphers.items()})
    java = f'java -cp {LEGACY_WORK}/mssql-jdbc.jar {stage}/OneSql.java'
    checks = [(label, login, db, 'ro', 'effective_permissions.sql') for label, login, db, _ in RUNTIME]
    for db, kind in (('a8_db', 'a8'), ('ruoyi-vue-pro', 'legacy')):
        checks += [('EVIDENCE', 'one_evidence_ro', db, 'ro', 'effective_permissions.sql'),
                   ('EVIDENCE', 'one_evidence_ro', db, 'ro', f'evidence_login_read_{kind}.sql'),
                   ('EVIDENCE', 'one_evidence_ro', db, 'deny', f'{kind}_login_deny.sql')]
    test = 'rc=0\n' + ''.join(
        f'echo "######## {mode} {sql} as {login} on {db}"; LOGIN_USER={login} LOGIN_SECRET_ENC={stage}/{label}.enc '
        f'LOGIN_KEY={LEGACY_WORK}/logintest.key {java} {mode} {db} {stage}/{sql} < /dev/null || rc=1\n'
        for label, login, db, mode, sql in checks)
    _, status, out = step('login tests', LEGACY_SERVER, stage_legacy(files) + DRIVER + test + f'rm -rf {LEGACY_WORK}\nexit $rc\n')
    print(out)

    for server, work in ((LEGACY_SERVER, LEGACY_WORK), (ONE_SERVER, ONE_WORK)):
        _, _, out = step(f'cleanup {server}', server, f'rm -rf {work}; test -e {work} && echo LEFT || echo CLEAN')
        print(out.strip())


if __name__ == '__main__':
    main()
