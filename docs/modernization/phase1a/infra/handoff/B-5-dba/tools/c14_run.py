#!/usr/bin/env python3
"""FORCITIS one · C-14 · 只读启动校验拒绝路径的真实登录核实，探针部分（运维机上运行，阿里云 Python SDK 与 aliyun CLI；剧本 runbooks/C-14-readonly-probe.md）。

偏离与回退以老系统主数据源经老系统服务器上的 OneSql 执行（只用于授权变更，ADR-0037 第 5 项第 4 条）；校验以探针登录在新 ECS 上运行 rc.3 起的
只读核实命令（--connection=probe），有效权限计数也以探针登录在新 ECS 上取。探针账号经 RDS 云接口（aliyun rds CreateAccount / DeleteAccount）创建与删除：
RDS 托管的删除会清掉它在 msdb 里自动建的用户，T-SQL 的 CREATE / DROP LOGIN 则不会，且执行账号在 msdb、rdscore 无权删用户（2026-10-16 实测）。
口令在本进程内存里运行时生成，只经云接口命令行（本机，创建命令执行期间 ps 可见）和以新 ECS 临时公钥加密的密文，写入 /etc/forcitis-one/readonly-probe.env（root 0600），不落盘、不打印。
rdscore 里同名用户是平台对任何账号的固有残留（删账号也不清，同名重建沿用同一用户号，不累积）：登记为永久已知残留，回读不要求它为 0，但须为 0 或与预检、对照时记下的用户号相同（还没记下时只记录）。
回读用 USER_ID（不受元数据可见性限制）：探针登录、探针库、master / msdb 用户为 0，rdscore 用户须为 0 或与基线同号。
前提：新 ECS 的 /root/one-ops-c14/app.jar 是已验签发布包里的 jar，其 SHA-256 作为参数传入。
每项：对照计数 → 偏离 → 跑校验 → 回退 → 计数复原，并以执行账号回读探针的授权指纹。任何意外即回退当前项后收尾；收尾逐条执行、逐条记录，
回读不干净或服务器上有残留即告警并以非零退出。记录只写标记、错误号与计数，不写任何输出原文。
H-2（msdb 里 SQLAgentOperatorRole 移出）不做：执行账号在 msdb 无 ALTER ANY ROLE（c14_u8c_status 实测），平台不可构造，不换方式。
用法：c14_run.py <结果文件> <app.jar 的 SHA-256>
运行方式：在 tmux 或 nohup 下，输出重定向到文件，不要经管道（| tee）运行；结果文件必须是新名字（已存在则拒绝运行）。
退出码：0 全部完成且收尾干净；1 中途停止（已收尾）；2 收尾不干净或回读对照失败（须人工处理）；3 用法错误或预检失败（什么都没改）。
"""
import base64
import json
import os
import re
import secrets
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from b5_legacy_run import DRIVER, LEGACY_SERVER, LEGACY_WORK, ONE_SERVER, put, run, stage_legacy  # noqa: E402
from b5_evidence_run import key_pair  # noqa: E402
from ops_credentials import require_committed  # noqa: E402

TOOLS = Path(__file__).resolve().parent
require_committed(TOOLS / 'OneSql.java', TOOLS / 'b5_evidence_run.py', TOOLS / 'b5_read_all_run.py', TOOLS / 'b5_legacy_run.py', __file__)

W = '/root/one-ops-c14'
STAGE_PREFIX = f'{LEGACY_WORK}/c14-stage'
calls = [0]
PROBE_ENV = '/etc/forcitis-one/readonly-probe.env'
LOGIN = 'one_readonly_probe'
DB = 'one_readonly_probe_db'
DB2 = 'one_readonly_probe_db2'
P = f'[{LOGIN}]'
PLATFORM_REFUSALS = {10: (4613,), 15: (4613, 15151)}  # 4613: no server-level DENY / GRANT here; 15151 on item 15 with the probe login visible: sa is invisible or the execution account may not grant IMPERSONATE on it, both platform limits
PLATFORM_REFUSAL_ITEMS = tuple(PLATFORM_REFUSALS)

PREPARE_MASTER = f"""CREATE DATABASE [{DB}];
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
    (15, [('master', f'GRANT IMPERSONATE ON LOGIN::sa TO {P};')], [('master', f'REVOKE IMPERSONATE ON LOGIN::sa FROM {P};')], 'platform 4613 (item 15 also 15151), else (d)'),
    (17, [('master', f'CREATE USER {P} FOR LOGIN {P};')], [('master', f'DROP USER {P};')], '(f)'),
]

DROP_USER = f"IF USER_ID(N'{LOGIN}') IS NOT NULL DROP USER {P};"
# The probe account itself goes through the RDS API (DeleteAccount), last; see delete_account()
KILL_SESSIONS = (f"DECLARE @k nvarchar(max) = N''; SELECT @k = @k + N'KILL ' + CAST(session_id AS nvarchar(10)) + N';' FROM sys.dm_exec_sessions "
                 f"WHERE login_name = N'{LOGIN}' AND session_id <> @@SPID; IF LEN(@k) > 0 EXEC (@k);")
CLEANUP = [
    ('master', KILL_SESSIONS),
    (DB2, DROP_USER),
    ('master', f"IF DB_ID(N'{DB2}') IS NOT NULL DROP DATABASE [{DB2}];"),
    ('master', DROP_USER),
    ('master', f"IF DB_ID(N'{DB}') IS NOT NULL DROP DATABASE [{DB}];"),
]
READBACK = (f"SELECT (SELECT COUNT(*) FROM sys.server_principals WHERE name = N'{LOGIN}') AS logins,\n"
            f"       (SELECT COUNT(*) FROM sys.databases WHERE name LIKE N'one[_]readonly[_]probe[_]db%') AS probe_databases,\n"
            f"       ISNULL(USER_ID(N'{LOGIN}'), 0) AS master_user;")
USER_ID_QUERY = f"SELECT ISNULL(USER_ID(N'{LOGIN}'), 0) AS user_id;"
CLEAN_READBACK = (0, 0, 0, 0)  # logins, probe databases, master user, msdb user; the rdscore user is the platform's known residue: it must be 0 or the baseline number
INSTANCE = 'rm-bp1690ep53t9778dp'
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


result_file = []


def record(**entry):
    """A broken terminal (SIGHUP, closed pipe) must never stop the cleanup: the line also goes to the result file, and a failing print is ignored."""
    log.append(entry)
    line = json.dumps(entry, ensure_ascii=False)
    try:
        print(line, flush=True)
    except OSError:
        pass
    if result_file:
        try:
            with open(result_file[0], 'a', encoding='utf-8') as handle:
                handle.write(line + '\n')
        except OSError:
            pass


def save(target):
    Path(target).write_text('\n'.join(json.dumps(e, ensure_ascii=False, default=str) for e in log) + '\n')


class Stop(Exception):
    pass


def refuse(message):
    """Exit 3: nothing was changed (wrong usage, failed preflight). The records so far go to the result file too, when one was given."""
    print(message)
    if len(sys.argv) > 1:
        try:
            save(sys.argv[1])
        except OSError:
            pass
    sys.exit(3)


def interrupted(signum, frame):
    raise KeyboardInterrupt(f'signal {signum}')


def numbers(out):
    """Rows of a OneSql result made of numbers only, e.g. '  3 | 9'."""
    return [tuple(int(v) for v in row.split(' | ')) for row in re.findall(r'^  (-?\d+(?: \| -?\d+)*)$', out, re.M)]


def legacy_sql(label, db, sql, mode='rw', enc_pub=None):
    calls[0] += 1
    STAGE = f'{STAGE_PREFIX}-{calls[0]}'
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
    calls[0] += 1
    # The command's own output is filtered here to the mark lines, RESULT and the exit code: nothing else (detail lines, URLs) leaves the ECS
    script = (f'out=$(systemd-run --quiet --wait --pipe --collect --unit=one-c14-probe-{calls[0]} -p RuntimeMaxSec=300 -p EnvironmentFile={PROBE_ENV} -p WorkingDirectory={W} '
              f'/usr/bin/java -Duser.timezone=UTC -jar {W}/app.jar --spring.profiles.active=read-only-verification --connection=probe 2>&1); code=$?; '
              f'printf "%s\\n" "$out" | grep -E "^\\([a-g]\\) |^RESULT "; echo "EXIT=$code"')
    invoke, status, _, out = ecs(script)
    marks = [(m, s, ''.join(re.findall(r' reason=\w+ count=\d+(?: error=\d+ sqlstate=\w+)?', d)))
             for m, s, d in re.findall(r'^\(([a-g])\) (PASSED|VIOLATED|UNDETERMINED)\b(.*)$', out, re.M)]
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


def readback(label):
    """Probe account, probe databases, master / msdb users (zero when clean) and the rdscore user (the platform's known residue), as USER_ID values."""
    rc, _, out = legacy_sql(label, 'master', READBACK, mode='ro')
    rows = numbers(out)
    base = rows[-1] if rc == 0 and rows and len(rows[-1]) == 3 else None
    ids = []
    for database in ('msdb', 'rdscore'):
        rc2, _, out2 = legacy_sql(f'{label} {database}', database, USER_ID_QUERY, mode='ro')
        rows2 = numbers(out2)
        ids.append(rows2[-1][0] if rc2 == 0 and rows2 and len(rows2[-1]) == 1 else None)
    if base is None or None in ids:
        return None
    return (*base, ids[0], ids[1])


def rds(*args, secret=None):
    """aliyun rds <action> on the instance; the output is returned with the secret removed and is never printed."""
    result = subprocess.run(['aliyun', 'rds', *args, '--profile', 'forcitis-production', '--region', 'cn-hangzhou', '--DBInstanceId', INSTANCE],
                            capture_output=True, text=True, timeout=300)
    out, err = result.stdout or '', result.stderr or ''
    if secret:
        out, err = out.replace(secret, '***'), err.replace(secret, '***')
    return result.returncode, out, err


def account_status():
    """Status of the probe account on the instance: '' when there is none, None when it cannot be read. Only stdout is parsed."""
    rc, out, _ = rds('DescribeAccounts', '--AccountName', LOGIN)
    if rc != 0:
        return None
    try:
        accounts = [a for a in json.loads(out)['Accounts']['DBInstanceAccount'] if a.get('AccountName') == LOGIN]
    except (ValueError, KeyError, TypeError):
        return None
    return accounts[0]['AccountStatus'] if accounts else ''


def account_status_retried():
    for _ in range(4):
        status = account_status()
        if status is not None:
            return status
        time.sleep(5)
    return None


rdscore_user = []  # the rdscore user number seen before the run or right after the account exists; the run must not end with another one
requested = []  # the creation call was made, so an empty status right after may only mean it is still on its way


def create_account(secret):
    requested.append(True)
    rc, out, err = rds('CreateAccount', '--AccountName', LOGIN, '--AccountPassword', secret, '--AccountType', 'Normal', secret=secret)
    request = re.findall(r'"RequestId":\s*"([0-9A-F-]+)"', out)
    code = re.findall(r'ErrorCode: (\S+)', out + err)
    record(step='rds create account', rc=rc, request_id=request[0] if request else None, error_code=code[0] if code else None)
    if rc != 0:
        raise Stop('creating the probe account through RDS failed')
    for _ in range(30):
        if account_status() == 'Available':
            return
        time.sleep(10)
    raise Stop('the probe account did not become Available in 5 minutes')


def database_listed(name):
    """True / False, or None when the listing cannot be read. One database by name, exact match on the parsed answer; a NotFound answer is False."""
    rc, out, err = rds('DescribeDatabases', '--DBName', name)
    if rc != 0:
        return False if 'NotFound' in out + err else None
    try:
        return any(x.get('DBName') == name for x in json.loads(out)['Databases']['Database'])
    except (ValueError, KeyError, TypeError):
        return None


def delete_databases():
    """A database RDS has put under mirroring (a few minutes after it was made) cannot be dropped with T-SQL (3743); the RDS API does it. Only the probe's own.
    Each is tried whether or not the listing could be read; one failing does not skip the other. Returns the names still listed at the end."""
    left = []
    for name in (DB2, DB):
        try:
            listed = database_listed(name)
            if listed is False:
                record(step='rds delete database', database=name, outcome='not listed')
                continue
            rc, request, code = None, None, None
            for _ in range(3):
                rc, out, err = rds('DeleteDatabase', '--DBName', name)
                request = re.findall(r'"RequestId":\s*"([0-9A-F-]+)"', out)
                code = re.findall(r'ErrorCode: (\S+)', out + err)
                if rc == 0:
                    break
                time.sleep(10)
            last, polls = None, 0
            for polls in range(1, 31):
                last = database_listed(name)
                if last is False:
                    break
                time.sleep(10)
            record(step='rds delete database', database=name, rc=rc, request_id=request[0] if request else None, error_code=code[0] if code else None,
                   polls=polls, last_listed=last, gone=last is False)
            if last is not False:
                left.append(name)
        except BaseException as failure:  # noqa: BLE001 the other database is still tried
            record(step='rds delete database', database=name, exception=type(failure).__name__)
            left.append(name)
    return left


def delete_account():
    status = account_status_retried()
    if status == '' and requested:
        for _ in range(6):  # a creation that was interrupted may still be running on the server
            time.sleep(10)
            status = account_status_retried()
            if status != '':
                break
    if status == '':
        record(step='rds delete account', outcome='no such account')
        return True
    rc, request, code = None, None, None
    for _ in range(3):  # also when the status could not be read: the delete is tried all the same
        rc, out, err = rds('DeleteAccount', '--AccountName', LOGIN)
        request = re.findall(r'"RequestId":\s*"([0-9A-F-]+)"', out)
        code = re.findall(r'ErrorCode: (\S+)', out + err)
        if rc == 0:
            break
        time.sleep(10)
    left = None
    for _ in range(30):
        left = account_status()
        if left == '':
            break
        time.sleep(10)
    record(step='rds delete account', rc=rc, request_id=request[0] if request else None, error_code=code[0] if code else None, gone=left == '')
    return left == ''


def encrypt_for_ecs(public_key, secret):
    with tempfile.NamedTemporaryFile('w', suffix='.pub') as handle:
        handle.write(public_key)
        handle.flush()
        cipher = subprocess.run(['openssl', 'pkeyutl', '-encrypt', '-pubin', '-inkey', handle.name, '-pkeyopt', 'rsa_padding_mode:oaep',
                                 '-pkeyopt', 'rsa_oaep_md:sha256', '-pkeyopt', 'rsa_mgf1_md:sha256'], input=secret.encode(), capture_output=True, check=True).stdout
    return base64.b64encode(cipher).decode()


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
              # The ECS image has no unzip: the driver comes out of the jar with Python's zipfile
              + f'python3 -I -c "import sys, zipfile; z = zipfile.ZipFile(sys.argv[1]); n = [e for e in z.namelist() if e.startswith(\'BOOT-INF/lib/mssql-jdbc-\')]; '
                f'open(sys.argv[2], \'wb\').write(z.read(n[0])) if len(n) == 1 else sys.exit(1)" {W}/app.jar {W}/mssql-jdbc.jar && echo DRIVER_OK\n')
    invoke, status, code, out = ecs(script)
    ok = status == 'Success' and 'PROBE_ENV_MODE 0o600' in out and 'DRIVER_OK' in out
    record(step='ecs setup', where='ecs', invoke=invoke, status=status, exit=code, ok=ok, jar_mismatch='JAR_MISMATCH' in out)
    if not ok:
        raise Stop('ecs setup failed')


def preflight(jar_sha256):
    rows = readback('preflight readback')
    record(step='preflight', readback=rows)
    if rows is not None and rows[4]:
        rdscore_user.append(rows[4])
    if rows is None or rows[:4] != CLEAN_READBACK:
        refuse('preflight: probe objects already exist or the readback failed; clean up by hand first, nothing was changed')
    if account_status() != '':
        refuse('preflight: an RDS account with the probe\'s name exists or its status is unreadable; nothing was changed')
    for system_database in ('msdb', 'rdscore'):
        rc, _, out = legacy_sql(f'preflight connect {system_database}', system_database, 'SELECT 1 AS reachable;', mode='ro')
        if rc != 0 or (1,) not in numbers(out):
            refuse(f'preflight: the execution account cannot open {system_database}, the readback could not look there; nothing was changed')
    invoke, status, _, out = ecs(f'test -e {PROBE_ENV} && echo ENV_LEFT; [ "$(sha256sum {W}/app.jar 2>/dev/null | cut -d" " -f1)" = "{jar_sha256}" ] && echo JAR_OK')
    record(step='preflight ecs', invoke=invoke, env_left='ENV_LEFT' in out, jar_ok='JAR_OK' in out)
    if 'ENV_LEFT' in out or 'JAR_OK' not in out:
        refuse('preflight: probe env file left over or app.jar does not match the given SHA-256; nothing was changed')


def cleanup():
    clean = True
    signals = {signal.SIGINT, signal.SIGTERM, signal.SIGHUP}
    signal.pthread_sigmask(signal.SIG_BLOCK, signals)  # blocked first, so that no signal slips in between the three settings below
    for number in signals:
        signal.signal(number, signal.SIG_IGN)  # a second Ctrl-C must not skip a DROP
    try:
        ecs('systemctl stop "one-c14-probe-*.service" 2>/dev/null; true')
    except BaseException as failure:  # noqa: BLE001
        record(step='ecs stop units', exception=type(failure).__name__)
    for db, sql in CLEANUP:
        try:
            rc, errors, _ = legacy_sql('cleanup', db, sql)
            if rc != 0 and db != DB2 and sql != KILL_SESSIONS and 'DROP DATABASE' not in sql:  # a refused DROP DATABASE (3743: mirrored) goes through the API below
                clean = False
        except BaseException as failure:  # noqa: BLE001 every step runs whatever the previous one did
            record(step='cleanup', db=db, exception=type(failure).__name__)
            clean = False
    try:
        if delete_databases():
            clean = False
    except BaseException as failure:  # noqa: BLE001
        record(step='rds delete databases', exception=type(failure).__name__)
        clean = False
    try:
        clean = delete_account() and clean
    except BaseException as failure:  # noqa: BLE001
        record(step='rds delete account', exception=type(failure).__name__)
        clean = False
    try:
        rows = readback('readback')
        for _ in range(6):  # RDS may remove the msdb user a little after the account
            if rows is not None and rows[:4] == CLEAN_READBACK:
                break
            time.sleep(10)
            rows = readback('readback again')
        record(step='readback', logins_databases_master_msdb_rdscore_users=rows)
        clean = clean and rows is not None and rows[:4] == CLEAN_READBACK and (not rdscore_user or rows[4] in (0, *rdscore_user))
    except BaseException as failure:  # noqa: BLE001
        record(step='readback', exception=type(failure).__name__)
        clean = False
    try:
        invoke, status, _, out = ecs(f'systemctl stop "one-c14-probe-*.service" 2>/dev/null; rm -f {PROBE_ENV} {W}/probe.key {W}/probe.pub {W}/probe.enc {W}/transfer.key {W}/secret.enc {W}/setup.py {W}/q.sql {W}/OneSql.java {W}/mssql-jdbc.jar; '
                                     f'test -e {PROBE_ENV} && echo LEFT || echo CLEAN')
        record(step='ecs cleanup', invoke=invoke, result='CLEAN' if 'CLEAN' in out else 'LEFT')
        clean = clean and 'CLEAN' in out
    except BaseException as failure:  # noqa: BLE001
        record(step='ecs cleanup', exception=type(failure).__name__)
        clean = False
    try:
        invoke, _, _, out = run(LEGACY_SERVER, f'rm -rf {STAGE_PREFIX}-*; ls -d {STAGE_PREFIX}-* >/dev/null 2>&1 && echo LEFT || echo CLEAN')
        record(step='legacy cleanup', invoke=invoke, result='CLEAN' if 'CLEAN' in out else 'LEFT')
        clean = clean and 'CLEAN' in out
    except BaseException as failure:  # noqa: BLE001
        record(step='legacy cleanup', exception=type(failure).__name__)
        clean = False
    if not clean:
        record(alert='CLEANUP NOT CLEAN: probe objects or files may be left on production; read the entries above and remove them by hand')
    return clean


def main(target, jar_sha256):
    control_failed = []
    if not re.fullmatch(r'[0-9a-f]{64}', jar_sha256):
        refuse('the second argument is the SHA-256 of app.jar (64 lowercase hex digits)')
    if Path(target).exists():
        refuse('the result file exists; give a new name so that an earlier result is never overwritten; nothing was changed')
    try:
        save(target)
    except OSError as failure:
        refuse(f'the result file cannot be written ({type(failure).__name__}); nothing was changed')
    result_file.append(target)
    try:
        preflight(jar_sha256)
    except SystemExit as stop:
        if stop.code == 3:
            raise
        refuse(f'preflight stopped ({stop.code}); nothing was changed')
    except BaseException as failure:  # noqa: BLE001 nothing has been changed yet
        refuse(f'preflight failed ({type(failure).__name__}); nothing was changed')
    for number in (signal.SIGTERM, signal.SIGHUP):
        signal.signal(number, interrupted)
    stopped = False
    try:
        transfer_pub = key_pair(ONE_SERVER, W, 'transfer')
        secret = 'Zq9_' + secrets.token_hex(4).upper() + 'x' + secrets.token_hex(6)
        cipher = encrypt_for_ecs(transfer_pub, secret)
        create_account(secret)
        del secret
        rc, errors, _ = legacy_sql('prepare database', 'master', PREPARE_MASTER)
        if rc != 0:
            raise Stop('prepare failed (database)')
        rc, errors, _ = legacy_sql('prepare user and grants', DB, PREPARE_DB)
        if rc != 0:
            raise Stop('prepare failed (user and grants)')
        rows = readback('readback control')
        record(step='readback control', readback=rows)
        if rows is None or rows[0] != 1 or rows[1] != 1 or rows[2] != 0 or rows[3] <= 0 or rows[4] <= 0 or (rdscore_user and rows[4] != rdscore_user[0]):
            control_failed.append(True)
            raise Stop('readback control: right after the account exists the readback must show the login, the probe database and the RDS-made msdb and rdscore users (the rdscore one as before), else a zero would prove nothing')
        if not rdscore_user:
            rdscore_user.append(rows[4])
        setup_ecs(cipher, jar_sha256)
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
                if qrc != 0 or seen is None:
                    raise Stop('item 14: the visibility check could not be read')
                if not seen:
                    raise Stop('item 14: the probe does not see public grants on user objects, (g) public is not effective here; reported to one-coordinator and one-1a-next-2, as the runbook says')
            errors = apply(f'item {number} deviation', deviation)
            if errors:
                if number in PLATFORM_REFUSAL_ITEMS and len(errors) == 1 and errors[0] in PLATFORM_REFUSALS[number]:
                    if errors[0] == 15151:  # "not found, or no permission": the probe login must be visible to the execution account, else the error says nothing about sa
                        rc, _, out = legacy_sql(f'item {number} login visibility', 'master',
                                                f"SELECT (SELECT COUNT(*) FROM sys.server_principals WHERE name = N'sa') AS sa, (SELECT COUNT(*) FROM sys.server_principals WHERE name = N'{LOGIN}') AS probe, "
                                                "ISNULL(HAS_PERMS_BY_NAME(NULL, NULL, N'CONTROL SERVER'), 0) AS control_server, ISNULL(HAS_PERMS_BY_NAME(N'sa', N'LOGIN', N'CONTROL'), 0) AS control_sa;", mode='ro')
                        seen = numbers(out)
                        record(step=f'item {number} login visibility', sa_probe_control_server_control_sa=seen[-1] if seen else None)
                        if rc != 0 or not seen or len(seen[-1]) != 4 or seen[-1][1] != 1:
                            raise Stop(f'item {number}: 15151 cannot be told apart from the probe login being invisible to the execution account')
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
        clean = cleanup()
        try:
            save(target)
        except Exception:  # noqa: BLE001 the records are in the result file already (record() appends); this is only the tidy rewrite
            try:
                print('\n'.join(json.dumps(e, ensure_ascii=False, default=str) for e in log))
            except Exception:  # noqa: BLE001
                pass
    if control_failed:
        record(alert='VISIBILITY CONTROL FAILED: the probe\'s users in msdb and rdscore may be left behind and cannot be verified from here; remove them by hand')
        clean = False
    code = 2 if not clean else (1 if stopped else 0)
    try:
        sys.stdout.flush()
        sys.stderr.flush()
    except OSError:
        # A closed terminal: without this the interpreter's own flush at exit would turn the exit code into 120
        sys.stdout = sys.stderr = open(os.devnull, 'w')
    sys.exit(code)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        refuse(next(line for line in __doc__.splitlines() if line.startswith('用法：')))
    main(sys.argv[1], sys.argv[2])
