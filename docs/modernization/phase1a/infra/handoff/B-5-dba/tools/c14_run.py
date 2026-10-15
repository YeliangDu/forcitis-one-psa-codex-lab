#!/usr/bin/env python3
"""FORCITIS one · C-14 · 只读启动校验拒绝路径的真实登录核实（运维机上运行，阿里云 Python SDK；剧本 runbooks/C-14-readonly-probe.md）。

偏离与回退以老系统主数据源经老系统服务器上的 OneSql 执行（只用于授权变更，ADR-0037 第 5 项第 4 条）；校验以探针登录在新 ECS 上运行 rc.2 的
只读核实命令（--connection=probe），有效权限计数也以探针登录在新 ECS 上取。探针口令在 SQL Server 上生成，只以新 ECS 临时公钥加密的密文经手，
写入 /etc/forcitis-one/readonly-probe.env（root 0600）。前提：新 ECS 的 /root/one-ops-c14/app.jar 是已验签的 rc.2 jar。
任何偏离意外失败、对照项不通过或回退后计数不复原即停止：回退当前项，再删除全部探针对象。
用法：c14_run.py <结果文件>
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from b5_legacy_run import DRIVER, LEGACY_SERVER, LEGACY_WORK, ONE_SERVER, put, run, stage_legacy  # noqa: E402
from b5_evidence_run import key_pair  # noqa: E402

TOOLS = Path(__file__).resolve().parent
W = '/root/one-ops-c14'
PROBE_ENV = '/etc/forcitis-one/readonly-probe.env'
LOGIN = 'one_readonly_probe'
DB = 'one_readonly_probe_db'
DB2 = 'one_readonly_probe_db2'
PLATFORM_REFUSALS = (10, 15)

PREPARE = [
    ('master', f"""DECLARE @s nvarchar(64) = N'Pr-' + CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(24), 2);
DECLARE @ddl nvarchar(max) = N'CREATE LOGIN [{LOGIN}] WITH PASSWORD = ' + QUOTENAME(@s, N'''') + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF, DEFAULT_DATABASE = [master]';
EXEC (@ddl);
SELECT N'{LOGIN}' AS login_name, @s AS one_time_secret;
GO
CREATE DATABASE [{DB}];
GO
"""),
    (DB, f"""CREATE USER [{LOGIN}] FOR LOGIN [{LOGIN}] WITH DEFAULT_SCHEMA = dbo;
GO
CREATE TABLE dbo.probe_t (id int);
GO
GRANT CONNECT, SELECT ON DATABASE::[{DB}] TO [{LOGIN}];
GO
DENY INSERT, UPDATE, DELETE, ALTER, EXECUTE, REFERENCES, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, CREATE SCHEMA ON DATABASE::[{DB}] TO [{LOGIN}];
GO
"""),
]

P = f'[{LOGIN}]'
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
    (DB2, f"IF USER_ID(N'{LOGIN}') IS NOT NULL DROP USER {P};", True),
    ('master', f"IF DB_ID(N'{DB2}') IS NOT NULL DROP DATABASE [{DB2}];\nGO\nIF USER_ID(N'{LOGIN}') IS NOT NULL DROP USER {P};\nGO\n"
               f"IF DB_ID(N'{DB}') IS NOT NULL DROP DATABASE [{DB}];\nGO\nIF SUSER_ID(N'{LOGIN}') IS NOT NULL DROP LOGIN {P};", False),
]
READBACK = (f"SELECT (SELECT COUNT(*) FROM sys.server_principals WHERE name = N'{LOGIN}') AS logins,\n"
            f"       (SELECT COUNT(*) FROM sys.databases WHERE name LIKE N'one_readonly_probe_db%') AS probe_databases,\n"
            f"       (SELECT COUNT(*) FROM master.sys.database_principals WHERE name = N'{LOGIN}') AS master_users;")

COUNTS = ("SELECT (SELECT COUNT(*) FROM fn_my_permissions(NULL, 'DATABASE')) AS database_permissions,\n"
          "       (SELECT COUNT(*) FROM fn_my_permissions(NULL, 'SERVER')) AS server_permissions;")
VISIBILITY = 'SELECT COUNT(*) AS public_object_grants FROM sys.database_permissions WHERE grantee_principal_id = 0 AND class <> 0;'

log = []


def record(**entry):
    log.append(entry)
    print(json.dumps(entry, ensure_ascii=False))


class Stop(Exception):
    pass


def legacy_sql(label, db, sql, enc_pub=None):
    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text(), 'step.sql': sql if sql.rstrip().endswith('GO') else sql + '\nGO\n'}
    if enc_pub:
        files['enc.pub'] = enc_pub
    env = f'export ENC_PUBKEY={LEGACY_WORK}/stage/enc.pub\n' if enc_pub else ''
    script = stage_legacy(files) + DRIVER + env + f'set +e\nonesql rw "{db}" {LEGACY_WORK}/stage/step.sql\nrc=$?\nrm -f {LEGACY_WORK}/stage/*\necho "RC=$rc"\n'
    invoke, status, _, out = run(LEGACY_SERVER, script)
    rc = int(re.findall(r'^RC=(\d+)$', out, re.M)[-1]) if re.search(r'^RC=\d+$', out, re.M) else -1
    errors = [int(n) for n in re.findall(r'^SQLERROR (\d+)', out, re.M)]
    shown = re.sub(r'ENC:[A-Za-z0-9+/=]+', 'ENC:<ciphertext>', out)
    record(step=label, where='legacy', db=db, invoke=invoke, rc=rc, sql_errors=errors)
    return rc, errors, out, shown


def ecs(label, script, timeout=600):
    invoke, status, code, out = run(ONE_SERVER, script, timeout)
    return invoke, status, code, out


def verify(label):
    script = (f'systemd-run --quiet --wait --pipe --collect -p EnvironmentFile={PROBE_ENV} -p WorkingDirectory={W} '
              f'/usr/bin/java -Duser.timezone=UTC -jar {W}/app.jar --spring.profiles.active=read-only-verification --connection=probe; echo "EXIT=$?"')
    invoke, _, _, out = ecs(label, script)
    marks = re.findall(r'^\(([a-g])\) (PASSED|VIOLATED|UNDETERMINED)(.*)$', out, re.M)
    result = re.findall(r'^RESULT (\w+)$', out, re.M)
    code = re.findall(r'^EXIT=(\d+)$', out, re.M)
    flagged = {m: f'{s}{d}' for m, s, d in marks if s != 'PASSED'}
    entry = dict(step=label, where='ecs', invoke=invoke, result=result[-1] if result else None, exit=int(code[-1]) if code else None, flagged=flagged)
    if len(marks) != 7:
        entry['unparsed_tail'] = out[-600:]
    record(**entry)
    return entry


def probe_query(label, sql):
    script = (f'set -u\ncd {W}\n' + put(f'{W}/q.sql', sql + '\nGO\n')
              + f'LOGIN_USER={LOGIN} LOGIN_SECRET_ENC={W}/probe.enc LOGIN_KEY={W}/probe.key java -cp {W}/mssql-jdbc.jar {W}/OneSql.java ro {DB} {W}/q.sql; '
                f'echo "RC=$?"; rm -f {W}/q.sql\n')
    invoke, _, _, out = ecs(label, script)
    rows = re.findall(r'^  (\d+)(?: \| (\d+))?$', out, re.M)
    return invoke, rows, out


def counts(label):
    invoke, rows, out = probe_query(label, COUNTS)
    if not rows or not rows[-1][1]:
        record(step=label, where='ecs', invoke=invoke, counts=None, tail=out[-400:])
        raise Stop(f'{label}: counts unreadable')
    value = (int(rows[-1][0]), int(rows[-1][1]))
    record(step=label, where='ecs', invoke=invoke, counts=value)
    return value


def apply(label, steps):
    for db, sql in steps:
        rc, errors, _, shown = legacy_sql(label, db, sql)
        if rc != 0:
            return errors or [-1], shown
    return [], ''


def setup_ecs(cipher):
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
print('probe env written', oct(os.stat({PROBE_ENV!r}).st_mode & 0o777))
'''
    script = (f'set -eu; umask 077; cd {W}\n' + put(f'{W}/secret.enc', cipher) + put(f'{W}/setup.py', py)
              + put(f'{W}/OneSql.java', (TOOLS / 'OneSql.java').read_text())
              + f'python3 -I {W}/setup.py; rm -f {W}/setup.py\n'
              + f'entry=$(unzip -l {W}/app.jar | grep -oE "BOOT-INF/lib/mssql-jdbc-[^ ]+\\.jar" | head -1); unzip -p {W}/app.jar "$entry" > {W}/mssql-jdbc.jar; echo "driver $entry"\n')
    invoke, status, code, out = ecs('ecs setup', script)
    record(step='ecs setup', where='ecs', invoke=invoke, status=status, exit=code, out=out.strip()[-300:])
    if status != 'Success':
        raise Stop('ecs setup failed')


def cleanup():
    for db, sql, tolerate in CLEANUP:
        rc, errors, _, shown = legacy_sql('cleanup', db, sql)
        if rc != 0 and not tolerate:
            record(step='cleanup', problem=shown[-500:])
    rc, errors, out, _ = legacy_sql('readback', 'master', READBACK)
    rows = re.findall(r'^  (\d+) \| (\d+) \| (\d+)$', out, re.M)
    record(step='readback', logins_dbs_masterusers=rows[-1] if rows else None)
    invoke, status, code, out = ecs('ecs cleanup', f'rm -f {PROBE_ENV}; rm -rf {W}; test -e {PROBE_ENV} -o -e {W} && echo LEFT || echo CLEAN')
    record(step='ecs cleanup', invoke=invoke, out=out.strip())
    invoke, _, _, out = run(LEGACY_SERVER, f'rm -rf {LEGACY_WORK}; test -e {LEGACY_WORK} && echo LEFT || echo CLEAN')
    record(step='legacy cleanup', invoke=invoke, out=out.strip())


def main(target):
    try:
        transfer_pub = key_pair(ONE_SERVER, W, 'transfer')
        db_master, sql_master = PREPARE[0]
        rc, errors, out, shown = legacy_sql('prepare login and database', db_master, sql_master, enc_pub=transfer_pub)
        cipher = re.findall(rf'{LOGIN} \| ENC:([A-Za-z0-9+/=]+)', out)
        if rc != 0 or len(cipher) != 1:
            record(step='prepare', problem=shown[-800:])
            raise Stop('prepare failed (login or database)')
        rc, errors, _, shown = legacy_sql('prepare user and grants', *PREPARE[1])
        if rc != 0:
            record(step='prepare', problem=shown[-800:])
            raise Stop('prepare failed (user and grants)')
        setup_ecs(cipher[0])
        control = counts('control counts')

        for number, deviation, revert, expected in ITEMS:
            if number == 14:
                rc, _, _, _ = legacy_sql('visibility grant', DB, 'GRANT SELECT ON OBJECT::dbo.probe_t TO public;')
                invoke, rows, _ = probe_query('visibility', VISIBILITY)
                legacy_sql('visibility revoke', DB, 'REVOKE SELECT ON OBJECT::dbo.probe_t FROM public;')
                seen = int(rows[-1][0]) if rows else None
                record(step='visibility', invoke=invoke, public_object_grants=seen)
                if not seen:
                    record(item=14, skipped=f'probe sees {seen} public object grants; (g) public not effective here; report to one-coordinator and one-1a-next-2')
                    if counts('item 14 counts') != control:
                        raise Stop('counts changed after the visibility check')
                    continue
            errors, shown = apply(f'item {number} deviation', deviation)
            if errors:
                if number in PLATFORM_REFUSALS:
                    record(item=number, expected=expected, outcome='platform cannot construct', sql_errors=errors)
                    if counts(f'item {number} counts') != control:
                        raise Stop(f'item {number}: counts changed after a refused deviation')
                    continue
                record(item=number, problem=shown[-800:])
                apply(f'item {number} revert after failure', revert)
                raise Stop(f'item {number}: deviation failed {errors}')
            outcome = verify(f'item {number} verify')
            record(item=number, expected=expected, result=outcome['result'], flagged=outcome['flagged'])
            if number == 1 and outcome['result'] != 'PASSED':
                raise Stop('item 1 (baseline) did not pass')
            errors, shown = apply(f'item {number} revert', revert)
            if errors:
                record(item=number, revert_problem=shown[-800:])
                raise Stop(f'item {number}: revert failed {errors}')
            if counts(f'item {number} counts') != control:
                raise Stop(f'item {number}: counts not restored')
    except Stop as stop:
        record(stop=str(stop))
    finally:
        cleanup()
        Path(target).write_text('\n'.join(json.dumps(e, ensure_ascii=False) for e in log) + '\n')


if __name__ == '__main__':
    main(sys.argv[1])
