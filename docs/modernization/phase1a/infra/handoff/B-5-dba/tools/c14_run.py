#!/usr/bin/env python3
"""FORCITIS one · C-14 · 只读启动校验拒绝路径的真实登录核实，探针部分（运维机上运行，阿里云 Python SDK；剧本 runbooks/C-14-readonly-probe.md）。

偏离与回退以老系统主数据源经老系统服务器上的 OneSql 执行（只用于授权变更，ADR-0037 第 5 项第 4 条）；校验以探针登录在新 ECS 上运行 rc.3 起的
只读核实命令（--connection=probe），有效权限计数也以探针登录在新 ECS 上取。探针口令在 SQL Server 上生成，只以新 ECS 临时公钥加密的密文经手，
写入 /etc/forcitis-one/readonly-probe.env（root 0600）。前提：新 ECS 的 /root/one-ops-c14/app.jar 是已验签发布包里的 jar，其 SHA-256 作为参数传入。
每项：对照计数 → 偏离 → 跑校验 → 回退 → 计数复原，并以执行账号回读探针的授权指纹。任何意外即回退当前项后收尾；收尾逐条执行、逐条记录，
回读不为 0/0/0 或服务器上有残留即告警并以非零退出。记录只写标记、错误号与计数，不写任何输出原文。
用法：c14_run.py <结果文件> <app.jar 的 SHA-256>
退出码：0 全部完成且收尾干净；1 中途停止（已收尾）；2 收尾不干净（须人工处理）。
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from b5_legacy_run import DRIVER, LEGACY_SERVER, LEGACY_WORK, ONE_SERVER, put, run, stage_legacy  # noqa: E402
from b5_evidence_run import key_pair  # noqa: E402
from ops_credentials import require_committed  # noqa: E402

TOOLS = Path(__file__).resolve().parent
require_committed(TOOLS / 'OneSql.java', TOOLS / 'b5_evidence_run.py', TOOLS / 'b5_read_all_run.py', TOOLS / 'b5_legacy_run.py', __file__)

W = '/root/one-ops-c14'
STAGE = f'{LEGACY_WORK}/c14-stage'
PROBE_ENV = '/etc/forcitis-one/readonly-probe.env'
LOGIN = 'one_readonly_probe'
DB = 'one_readonly_probe_db'
DB2 = 'one_readonly_probe_db2'
P = f'[{LOGIN}]'
PLATFORM_REFUSAL = 4613
PLATFORM_REFUSAL_ITEMS = (10, 15)

PREPARE_MASTER = f"""DECLARE @s nvarchar(64) = N'Pr-' + CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(24), 2);
DECLARE @ddl nvarchar(max) = N'CREATE LOGIN {P} WITH PASSWORD = ' + QUOTENAME(@s, N'''') + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF, DEFAULT_DATABASE = [master]';
EXEC (@ddl);
SELECT N'{LOGIN}' AS login_name, @s AS one_time_secret;
GO
CREATE DATABASE [{DB}];
GO
"""
PREPARE_DB = f"""CREATE USER {P} FOR LOGIN {P} WITH DEFAULT_SCHEMA = dbo;
GO
CREATE TABLE dbo.probe_t (id int);
GO
GRANT CONNECT, SELECT ON DATABASE::[{DB}] TO {P};
GO
DENY INSERT, UPDATE, DELETE, ALTER, EXECUTE, REFERENCES, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, CREATE SCHEMA ON DATABASE::[{DB}] TO {P};
GO
"""

ITEMS = [
    (1, [], [], 'PASSED'),
    (2, [(DB, f'REVOKE INSERT ON DATABASE::[{DB}] FROM {P};\nGO\nGRANT INSERT ON SCHEMA::dbo TO {P};')],
        [(DB, f'REVOKE INSERT ON SCHEMA::dbo FROM {P};\nGO\nDENY INSERT ON DATABASE::[{DB}] TO {P};')], '(b) (g), maybe (a)'),
    (3, [(DB, f'GRANT VIEW DEFINITION ON DATABASE::[{DB}] TO {P};')], [(DB, f'REVOKE VIEW DEFINITION ON DATABASE::[{DB}] FROM {P};')], '(a) (b)'),
    (4, [(DB, f'ALTER ROLE db_datareader ADD MEMBER {P};')], [(DB, f'ALTER ROLE db_datareader DROP MEMBER {P};')], '(c), maybe (a)'),
    (5, [(DB, f'CREATE ROLE [probe_select_role];\nGO\nGRANT SELECT ON DATABASE::[{DB}] TO [probe_select_role];\nGO\nALTER ROLE [probe_select_role] ADD MEMBER {P};')],
        [(DB, f'ALTER ROLE [probe_select_role] DROP MEMBER {P};\nGO\nDROP ROLE [probe_select_role];')], '(c)'),
    (6, [(DB, f'CREATE SCHEMA [probe_owned] AUTHORIZATION {P};')], [(DB, 'DROP SCHEMA [probe_owned];')], '(e)'),
    (7, [(DB, f'CREATE ROLE [probe_owned_role] AUTHORIZATION {P};')], [(DB, 'DROP ROLE [probe_owned_role];')], '(e)'),
    (8, [(DB, f'CREATE USER [probe_target] WITHOUT LOGIN;\nGO\nGRANT IMPERSONATE ON USER::[probe_target] TO {P};')],
        [(DB, f'REVOKE IMPERSONATE ON USER::[probe_target] FROM {P};\nGO\nDROP USER [probe_target];')], '(e) (g)'),
    (9, [('master', f'CREATE DATABASE [{DB2}];'), (DB2, f'CREATE USER {P} FOR LOGIN {P};')],
        [(DB2, f'DROP USER {P};'), ('master', f'DROP DATABASE [{DB2}];')], '(f)'),
    (10, [('master', f'DENY VIEW SERVER STATE TO {P};')], [('master', f'REVOKE VIEW SERVER STATE FROM {P};')], 'platform 4613, else (d)'),
    (11, [(DB, f'DENY SELECT ON sys.database_permissions TO {P};')], [(DB, f'REVOKE SELECT ON sys.database_permissions FROM {P};')],
         '(b) (e) (g) undetermined'),
    (12, [(DB, f'GRANT TAKE OWNERSHIP ON OBJECT::dbo.probe_t TO {P};')], [(DB, f'REVOKE TAKE OWNERSHIP ON OBJECT::dbo.probe_t FROM {P};')], '(g)'),
    (13, [(DB, f'GRANT CONTROL ON SCHEMA::dbo TO {P};')], [(DB, f'REVOKE CONTROL ON SCHEMA::dbo FROM {P};')], '(g), maybe (a)'),
    (14, [(DB, 'GRANT UPDATE ON OBJECT::dbo.probe_t TO public;')], [(DB, 'REVOKE UPDATE ON OBJECT::dbo.probe_t FROM public;')], '(g) public'),
    (15, [('master', f'GRANT IMPERSONATE ON LOGIN::sa TO {P};')], [('master', f'REVOKE IMPERSONATE ON LOGIN::sa FROM {P};')], 'platform 4613, else (d)'),
    (17, [('master', f'CREATE USER {P} FOR LOGIN {P};')], [('master', f'DROP USER {P};')], '(f)'),
]

CLEANUP = [
    (DB2, f"IF USER_ID(N'{LOGIN}') IS NOT NULL DROP USER {P};"),
    ('master', f"IF DB_ID(N'{DB2}') IS NOT NULL DROP DATABASE [{DB2}];"),
    ('master', f"IF USER_ID(N'{LOGIN}') IS NOT NULL DROP USER {P};"),
    ('master', f"IF DB_ID(N'{DB}') IS NOT NULL DROP DATABASE [{DB}];"),
    ('master', f"IF SUSER_ID(N'{LOGIN}') IS NOT NULL DROP LOGIN {P};"),
]
READBACK = (f"SELECT (SELECT COUNT(*) FROM sys.server_principals WHERE name = N'{LOGIN}') AS logins,\n"
            f"       (SELECT COUNT(*) FROM sys.databases WHERE name LIKE N'one[_]readonly[_]probe[_]db%') AS probe_databases,\n"
            f"       (SELECT COUNT(*) FROM master.sys.database_principals WHERE name = N'{LOGIN}') AS master_users;")
# The execution account's view of everything an item may change, as one row of numbers; compared with the control after every revert
FINGERPRINT = (f"SELECT (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id = USER_ID(N'{LOGIN}')) AS user_permissions,\n"
               f"       (SELECT ISNULL(CHECKSUM_AGG(CHECKSUM(class, major_id, minor_id, type, state)), 0) FROM sys.database_permissions\n"
               f"         WHERE grantee_principal_id = USER_ID(N'{LOGIN}')) AS user_permissions_sum,\n"
               f"       (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id = 0 AND class <> 0) AS public_object_permissions,\n"
               f"       (SELECT COUNT(*) FROM sys.database_role_members WHERE member_principal_id = USER_ID(N'{LOGIN}')) AS role_memberships,\n"
               f"       (SELECT COUNT(*) FROM sys.database_principals WHERE principal_id > 4 AND is_fixed_role = 0) AS principals,\n"
               f"       (SELECT COUNT(*) FROM sys.schemas) AS schemas,\n"
               f"       (SELECT COUNT(*) FROM sys.server_permissions WHERE grantee_principal_id = SUSER_ID(N'{LOGIN}')) AS server_permissions,\n"
               f"       (SELECT COUNT(*) FROM master.sys.database_principals WHERE name = N'{LOGIN}') AS master_users,\n"
               f"       (SELECT COUNT(*) FROM sys.databases WHERE name = N'{DB2}') AS second_database;")
COUNTS = ("SELECT (SELECT COUNT(*) FROM fn_my_permissions(NULL, 'DATABASE')) AS database_permissions,\n"
          "       (SELECT COUNT(*) FROM fn_my_permissions(NULL, 'SERVER')) AS server_permissions;")
VISIBILITY = 'SELECT COUNT(*) AS public_object_grants FROM sys.database_permissions WHERE grantee_principal_id = 0 AND class <> 0;'

log = []


def record(**entry):
    log.append(entry)
    print(json.dumps(entry, ensure_ascii=False), flush=True)


def save(target):
    Path(target).write_text('\n'.join(json.dumps(e, ensure_ascii=False) for e in log) + '\n')


class Stop(Exception):
    pass


def numbers(out):
    """Rows of a OneSql result made of numbers only, e.g. '  3 | 9'."""
    return [tuple(int(v) for v in row.split(' | ')) for row in re.findall(r'^  (-?\d+(?: \| -?\d+)*)$', out, re.M)]


def legacy_sql(label, db, sql, mode='rw', enc_pub=None):
    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text(), 'step.sql': sql if sql.rstrip().endswith('GO') else sql + '\nGO\n'}
    if enc_pub:
        files['enc.pub'] = enc_pub
    script = stage_legacy({}) + f'install -d -m 700 {STAGE}\n' + ''.join(put(f'{STAGE}/{name}', text) for name, text in files.items())
    script = script + DRIVER.replace(f'{LEGACY_WORK}/stage/OneSql.java', f'{STAGE}/OneSql.java')
    script += (f'export ENC_PUBKEY={STAGE}/enc.pub\n' if enc_pub else '') + f'set +e\nonesql {mode} "{db}" {STAGE}/step.sql\nrc=$?\nrm -rf {STAGE}\necho "RC=$rc"\n'
    invoke, status, _, out = run(LEGACY_SERVER, script)
    codes = re.findall(r'^RC=(\d+)$', out, re.M)
    rc = int(codes[-1]) if codes else -1
    errors = [int(n) for n in re.findall(r'^SQLERROR (\d+)', out, re.M)]
    record(step=label, where='legacy', db=db, invoke=invoke, status=status, rc=rc, sql_errors=errors)
    return rc, errors, out


def ecs(script, timeout=600):
    invoke, status, code, out = run(ONE_SERVER, script, timeout)
    return invoke, status, code, out


def verify(label):
    script = (f'systemd-run --quiet --wait --pipe --collect -p EnvironmentFile={PROBE_ENV} -p WorkingDirectory={W} '
              f'/usr/bin/java -Duser.timezone=UTC -jar {W}/app.jar --spring.profiles.active=read-only-verification --connection=probe; echo "EXIT=$?"')
    invoke, status, _, out = ecs(script)
    marks = re.findall(r'^\(([a-g])\) (PASSED|VIOLATED|UNDETERMINED)((?: reason=\w+ count=\d+(?: error=\d+ sqlstate=\w+)?)*)\s*$', out, re.M)
    result = re.findall(r'^RESULT (PASSED|VIOLATED|UNDETERMINED)$', out, re.M)
    code = re.findall(r'^EXIT=(\d+)$', out, re.M)
    entry = dict(step=label, where='ecs', invoke=invoke, status=status, result=result[-1] if result else None,
                 exit=int(code[-1]) if code else None, marks_parsed=len(marks), flagged={m: f'{s}{d}' for m, s, d in marks if s != 'PASSED'})
    record(**entry)
    if len(marks) != 7 or not result:
        raise Stop(f'{label}: verification output not understood')
    return entry


def probe_query(label, sql):
    script = (f'set -u\ncd {W}\n' + put(f'{W}/q.sql', sql + '\nGO\n')
              + f'LOGIN_USER={LOGIN} LOGIN_SECRET_ENC={W}/probe.enc LOGIN_KEY={W}/probe.key java -cp {W}/mssql-jdbc.jar {W}/OneSql.java ro {DB} {W}/q.sql; '
                f'echo "RC=$?"; rm -f {W}/q.sql\n')
    invoke, status, _, out = ecs(script)
    codes = re.findall(r'^RC=(\d+)$', out, re.M)
    return invoke, (int(codes[-1]) if codes else -1), numbers(out)


def counts(label):
    invoke, rc, rows = probe_query(label, COUNTS)
    value = rows[-1] if rc == 0 and rows and len(rows[-1]) == 2 else None
    record(step=label, where='ecs', invoke=invoke, rc=rc, counts=value)
    if value is None:
        raise Stop(f'{label}: counts unreadable')
    return value


def fingerprint(label):
    rc, errors, out = legacy_sql(label, DB, FINGERPRINT, mode='ro')
    rows = numbers(out)
    value = rows[-1] if rc == 0 and rows and len(rows[-1]) == 9 else None
    record(step=label, fingerprint=value)
    if value is None:
        raise Stop(f'{label}: fingerprint unreadable')
    return value


def apply(label, steps):
    for db, sql in steps:
        rc, errors, _ = legacy_sql(label, db, sql)
        if rc != 0:
            return errors or [-1]
    return []


def setup_ecs(cipher, jar_sha256):
    py = f'''import base64, os, re, subprocess
W = {W!r}
secret = subprocess.run(['openssl', 'pkeyutl', '-decrypt', '-inkey', W + '/transfer.key', '-pkeyopt', 'rsa_padding_mode:oaep',
                         '-pkeyopt', 'rsa_oaep_md:sha256', '-pkeyopt', 'rsa_mgf1_md:sha256'], input=base64.b64decode(open(W + '/secret.enc').read().strip()),
                        capture_output=True, check=True).stdout.decode()
values = {{}}
for line in open('/etc/forcitis-one/one.env', encoding='utf-8'):
    line = line.strip()
    if line and not line.startswith('#') and '=' in line:
        key, value = line.split('=', 1)
        values[key.strip()] = value.strip().strip("'\\"")
url, n = re.subn(r'(?i)databaseName=[^;]*', 'databaseName={DB}', values['LEGACY_DB_RO_URL'])
if n != 1 or "'" in url:
    raise SystemExit('cannot derive the probe URL from LEGACY_DB_RO_URL')
os.umask(0o077)
with open({PROBE_ENV!r}, 'w') as f:
    f.write("READONLY_PROBE_DB_URL='" + url + "'\\nREADONLY_PROBE_DB_USERNAME={LOGIN}\\nREADONLY_PROBE_DB_PASSWORD='" + secret + "'\\n")
os.chmod({PROBE_ENV!r}, 0o600)
subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:3072', '-out', W + '/probe.key'], capture_output=True, check=True)
pub = subprocess.run(['openssl', 'pkey', '-in', W + '/probe.key', '-pubout'], capture_output=True, check=True).stdout
open(W + '/probe.pub', 'wb').write(pub)
enc = subprocess.run(['openssl', 'pkeyutl', '-encrypt', '-pubin', '-inkey', W + '/probe.pub', '-pkeyopt', 'rsa_padding_mode:oaep', '-pkeyopt',
                      'rsa_oaep_md:sha256', '-pkeyopt', 'rsa_mgf1_md:sha256'], input=secret.encode(), capture_output=True, check=True).stdout
open(W + '/probe.enc', 'w').write(base64.b64encode(enc).decode())
os.remove(W + '/secret.enc'); os.remove(W + '/transfer.key')
print('PROBE_ENV_MODE', oct(os.stat({PROBE_ENV!r}).st_mode & 0o777))
'''
    script = (f'set -eu; umask 077; cd {W}\n'
              f'[ "$(sha256sum {W}/app.jar | cut -d" " -f1)" = "{jar_sha256}" ] || {{ echo JAR_MISMATCH; exit 3; }}\n'
              + put(f'{W}/secret.enc', cipher) + put(f'{W}/setup.py', py) + put(f'{W}/OneSql.java', (TOOLS / 'OneSql.java').read_text())
              + f'python3 -I {W}/setup.py; rm -f {W}/setup.py\n'
              + f'entry=$(unzip -l {W}/app.jar | grep -oE "BOOT-INF/lib/mssql-jdbc-[^ ]+\\.jar" | head -1); unzip -p {W}/app.jar "$entry" > {W}/mssql-jdbc.jar; echo DRIVER_OK\n')
    invoke, status, code, out = ecs(script)
    ok = status == 'Success' and 'PROBE_ENV_MODE 0o600' in out and 'DRIVER_OK' in out
    record(step='ecs setup', where='ecs', invoke=invoke, status=status, exit=code, ok=ok, jar_mismatch='JAR_MISMATCH' in out)
    if not ok:
        raise Stop('ecs setup failed')


def preflight(jar_sha256):
    rc, _, out = legacy_sql('preflight readback', 'master', READBACK, mode='ro')
    rows = numbers(out)
    record(step='preflight', readback=rows[-1] if rows else None)
    if rc != 0 or not rows or rows[-1] != (0, 0, 0):
        raise SystemExit('preflight: probe objects already exist or the readback failed; clean up by hand first, nothing was changed')
    invoke, status, _, out = ecs(f'test -e {PROBE_ENV} && echo ENV_LEFT; [ "$(sha256sum {W}/app.jar 2>/dev/null | cut -d" " -f1)" = "{jar_sha256}" ] && echo JAR_OK')
    record(step='preflight ecs', invoke=invoke, env_left='ENV_LEFT' in out, jar_ok='JAR_OK' in out)
    if 'ENV_LEFT' in out or 'JAR_OK' not in out:
        raise SystemExit('preflight: probe env file left over or app.jar does not match the given SHA-256; nothing was changed')


def cleanup():
    clean = True
    for db, sql in CLEANUP:
        try:
            rc, errors, _ = legacy_sql('cleanup', db, sql)
            if rc != 0 and db != DB2:
                clean = False
        except BaseException as failure:  # noqa: BLE001 every step runs whatever the previous one did
            record(step='cleanup', db=db, exception=type(failure).__name__)
            clean = False
    try:
        rc, _, out = legacy_sql('readback', 'master', READBACK, mode='ro')
        rows = numbers(out)
        readback = rows[-1] if rc == 0 and rows else None
        record(step='readback', logins_databases_masterusers=readback)
        clean = clean and readback == (0, 0, 0)
    except BaseException as failure:  # noqa: BLE001
        record(step='readback', exception=type(failure).__name__)
        clean = False
    try:
        invoke, status, _, out = ecs(f'rm -f {PROBE_ENV} {W}/probe.key {W}/probe.pub {W}/probe.enc {W}/transfer.key {W}/secret.enc {W}/OneSql.java {W}/mssql-jdbc.jar; '
                                     f'test -e {PROBE_ENV} && echo LEFT || echo CLEAN')
        record(step='ecs cleanup', invoke=invoke, result='CLEAN' if 'CLEAN' in out else 'LEFT')
        clean = clean and 'CLEAN' in out
    except BaseException as failure:  # noqa: BLE001
        record(step='ecs cleanup', exception=type(failure).__name__)
        clean = False
    try:
        invoke, _, _, out = run(LEGACY_SERVER, f'rm -rf {STAGE}; test -e {STAGE} && echo LEFT || echo CLEAN')
        record(step='legacy cleanup', invoke=invoke, result='CLEAN' if 'CLEAN' in out else 'LEFT')
        clean = clean and 'CLEAN' in out
    except BaseException as failure:  # noqa: BLE001
        record(step='legacy cleanup', exception=type(failure).__name__)
        clean = False
    if not clean:
        record(alert='CLEANUP NOT CLEAN: probe objects or files may be left on production; read the entries above and remove them by hand')
    return clean


def main(target, jar_sha256):
    if not re.fullmatch(r'[0-9a-f]{64}', jar_sha256):
        raise SystemExit('the second argument is the SHA-256 of app.jar (64 lowercase hex digits)')
    preflight(jar_sha256)
    stopped = False
    try:
        transfer_pub = key_pair(ONE_SERVER, W, 'transfer')
        rc, errors, out = legacy_sql('prepare login and database', 'master', PREPARE_MASTER, enc_pub=transfer_pub)
        cipher = re.findall(rf'{LOGIN} \| ENC:([A-Za-z0-9+/=]+)', out)
        if rc != 0 or len(cipher) != 1:
            raise Stop('prepare failed (login or database)')
        rc, errors, _ = legacy_sql('prepare user and grants', DB, PREPARE_DB)
        if rc != 0:
            raise Stop('prepare failed (user and grants)')
        setup_ecs(cipher[0], jar_sha256)
        control = counts('control counts')
        control_fingerprint = fingerprint('control fingerprint')

        for number, deviation, revert, expected in ITEMS:
            if number == 14:
                rc, _, _ = legacy_sql('visibility grant', DB, 'GRANT SELECT ON OBJECT::dbo.probe_t TO public;')
                if rc != 0:
                    raise Stop('visibility grant failed')
                invoke, qrc, rows = probe_query('visibility', VISIBILITY)
                rc, _, _ = legacy_sql('visibility revoke', DB, 'REVOKE SELECT ON OBJECT::dbo.probe_t FROM public;')
                if rc != 0:
                    raise Stop('visibility revoke failed')
                seen = rows[-1][0] if qrc == 0 and rows else None
                record(step='visibility', invoke=invoke, public_object_grants=seen)
                if fingerprint('visibility fingerprint') != control_fingerprint:
                    raise Stop('visibility check did not revert cleanly')
                if not seen:
                    record(item=14, skipped='the probe does not see public grants on user objects; (g) public is not effective here; report to one-coordinator')
                    continue
            errors = apply(f'item {number} deviation', deviation)
            if errors:
                if number in PLATFORM_REFUSAL_ITEMS and errors == [PLATFORM_REFUSAL]:
                    record(item=number, expected=expected, outcome='platform cannot construct', sql_errors=errors)
                    if counts(f'item {number} counts') != control or fingerprint(f'item {number} fingerprint') != control_fingerprint:
                        raise Stop(f'item {number}: state changed after a refused deviation')
                    continue
                record(item=number, deviation_errors=errors)
                apply(f'item {number} revert after failure', revert)
                raise Stop(f'item {number}: deviation failed {errors}')
            outcome = verify(f'item {number} verify')
            record(item=number, expected=expected, result=outcome['result'], flagged=outcome['flagged'])
            if number == 1 and outcome['result'] != 'PASSED':
                raise Stop('item 1 (baseline) did not pass')
            errors = apply(f'item {number} revert', revert)
            if errors:
                raise Stop(f'item {number}: revert failed {errors}')
            if counts(f'item {number} counts') != control:
                raise Stop(f'item {number}: effective permission counts not restored')
            if fingerprint(f'item {number} fingerprint') != control_fingerprint:
                raise Stop(f'item {number}: grants, role memberships or principals not restored')
    except Stop as stop:
        record(stop=str(stop))
        stopped = True
    except BaseException as failure:  # noqa: BLE001 whatever went wrong, the probe objects are removed
        record(stop=f'unexpected {type(failure).__name__}')
        stopped = True
    finally:
        save(target)
        clean = cleanup()
        save(target)
    if not clean:
        sys.exit(2)
    sys.exit(1 if stopped else 0)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__.strip().splitlines()[-3])
    main(sys.argv[1], sys.argv[2])
