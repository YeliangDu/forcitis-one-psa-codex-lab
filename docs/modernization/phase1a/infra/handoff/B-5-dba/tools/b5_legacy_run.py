#!/usr/bin/env python3
"""FORCITIS one · B-5 · 老系统两个只读视图与账号：执行、校验、写入 one.env、真实登录与写拒绝测试（运维机上运行，阿里云 Python SDK）。

执行账号取自老系统应用服务器配置中心的主数据源（经本机回环读取、管道交给 OneSql，不落盘不打印）。
口令在 SQL Server 上生成（legacy_10_apply.sql STEP 3），OneSql 只输出用新 ECS 临时 RSA 公钥加密的密文；新 ECS 解密后写入 one.env。
登录测试的口令再用老系统服务器上的第二把临时公钥加密转交。两台服务器的临时目录与私钥在结束时删除。本进程不接触任何口令原文。
用法：b5_legacy_run.py
"""
import base64, gzip, json, os, re, sys, time
from pathlib import Path

from alibabacloud_ecs20140526 import models as em

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'runbooks' / 'scripts'))
from ops_credentials import MIN_SECONDS, ecs_client, require_committed  # noqa: E402

REGION = 'cn-hangzhou'
LEGACY_SERVER = 'i-bp1b34hcl0g1cnkdtrb9'
ONE_SERVER = 'i-bp122xlpar8w2uylmal2'
TOOLS = Path(__file__).resolve().parent
B5 = TOOLS.parent
SQLSERVER = 'rm-bp1690ep53t9778dp.sqlserver.rds.aliyuncs.com'
LEGACY_URL = f'jdbc:sqlserver://{SQLSERVER}:1433;databaseName=ruoyi-vue-pro;encrypt=true;trustServerCertificate=true'
A8_URL = f'jdbc:sqlserver://{SQLSERVER}:1433;databaseName=a8_db;encrypt=true;trustServerCertificate=true'
LEGACY_WORK = '/root/one-ops-b5'
ONE_WORK = '/root/one-ops-b5'

require_committed(__file__, sys.argv[0])


def run(instance, script, timeout=600):
    ecs = ecs_client(REGION, max(MIN_SECONDS, timeout + 120))
    invoke = ecs.run_command(em.RunCommandRequest(region_id=REGION, instance_id=[instance], type='RunShellScript',
                                                  command_content=script, timeout=timeout, keep_command=False,
                                                  name='one-ops-b5')).body.invoke_id
    for _ in range(timeout // 2 + 10):
        time.sleep(2)
        result = ecs.describe_invocation_results(em.DescribeInvocationResultsRequest(region_id=REGION, invoke_id=invoke)).body \
            .invocation.invocation_results.invocation_result[0]
        if result.invocation_status in ('Success', 'Failed', 'Timeout', 'Error', 'Aborted'):
            output = base64.b64decode(result.output or '').decode()
            return invoke, result.invocation_status, result.exit_code, output
    raise SystemExit(f'{invoke} did not finish')


def put(path_on_server, text):
    packed = base64.b64encode(gzip.compress(text.encode(), 9)).decode()
    return f"echo '{packed}' | base64 -d | gunzip > {path_on_server}\n"


def stage_legacy(files):
    script = f'set -eu; umask 077; install -d -m 700 {LEGACY_WORK} {LEGACY_WORK}/stage\n'
    for name, text in files.items():
        script += put(f'{LEGACY_WORK}/stage/{name}', text)
    return script


DRIVER = f'''
if [ ! -s {LEGACY_WORK}/mssql-jdbc.jar ]; then
  pid=$(pgrep -f 'yudao-module-fst-biz.jar' | head -1)
  jar="$(readlink -f /proc/$pid/cwd)/yudao-module-fst-biz.jar"
  entry=$(unzip -l "$jar" | grep -oE 'BOOT-INF/lib/mssql-jdbc-[^ ]+\\.jar' | head -1)
  unzip -p "$jar" "$entry" > {LEGACY_WORK}/mssql-jdbc.jar
fi
nacos_ds() {{ curl -fsS -m 10 "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=psa-datasource.yaml&group=DEFAULT_GROUP&tenant=prod"; }}
onesql() {{ nacos_ds | java -cp {LEGACY_WORK}/mssql-jdbc.jar {LEGACY_WORK}/stage/OneSql.java "$@"; }}
'''


def main():
    log = {}
    # 1. New server: temporary key pair for receiving the generated passwords.
    _, st, _, out = run(ONE_SERVER, f'set -eu; umask 077; install -d -m 700 {ONE_WORK}; '
                                    f'openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out {ONE_WORK}/transfer.key 2>/dev/null; '
                                    f'openssl pkey -in {ONE_WORK}/transfer.key -pubout')
    transfer_pub = out[out.index('-----BEGIN'):]
    # 2. Legacy server: second key pair for the login tests.
    _, st, _, out = run(LEGACY_SERVER, f'set -eu; umask 077; install -d -m 700 {LEGACY_WORK}; '
                                       f'openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out {LEGACY_WORK}/logintest.key 2>/dev/null; '
                                       f'openssl pkey -in {LEGACY_WORK}/logintest.key -pubout')
    logintest_pub = out[out.index('-----BEGIN'):]

    # 3. Apply and verify on the legacy database.
    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text(), 'apply.sql': (B5 / 'legacy_10_apply.sql').read_text(),
             'verify.sql': (B5 / 'legacy_20_verify.sql').read_text(), 'transfer.pub': transfer_pub}
    script = stage_legacy(files) + DRIVER + (
        f'export ENC_PUBKEY={LEGACY_WORK}/stage/transfer.pub\n'
        'echo "######## legacy apply"; onesql rw ruoyi-vue-pro ' + f'{LEGACY_WORK}/stage/apply.sql || {{ rm -rf {LEGACY_WORK}/stage; exit 21; }}\n'
        'echo "######## legacy verify"; onesql ro ruoyi-vue-pro ' + f'{LEGACY_WORK}/stage/verify.sql || {{ rm -rf {LEGACY_WORK}/stage; exit 22; }}\n'
        f'rm -rf {LEGACY_WORK}/stage\n')
    invoke, st, code, out = run(LEGACY_SERVER, script)
    log['apply_verify'] = invoke
    print(f'apply+verify {invoke} {st} exit={code}')
    print(re.sub(r'ENC:[A-Za-z0-9+/=]+', 'ENC:<ciphertext>', out))
    if st != 'Success':
        raise SystemExit('apply or verify failed; see output above (nothing written to one.env)')
    secrets = dict(re.findall(r'^\s*\S+ \| (FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD|LEGACY_DB_RO_PASSWORD) \| ENC:([A-Za-z0-9+/=]+)', out, re.M))
    if len(secrets) != 2:
        raise SystemExit('expected two encrypted secrets in the apply output')

    # 4. New server: decrypt into one.env, set URLs and user names, and re-encrypt for the login test.
    dec = (f'dec() {{ base64 -d "$1" | openssl pkeyutl -decrypt -inkey {ONE_WORK}/transfer.key '
           '-pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256; }\n'
           f'enc() {{ openssl pkeyutl -encrypt -pubin -inkey {ONE_WORK}/logintest.pub '
           '-pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -pkeyopt rsa_mgf1_md:sha256 | base64 -w0; }\n')
    script = ('set -eu; umask 077\n' + put(f'{ONE_WORK}/set_env.py', (TOOLS / 'set_env.py').read_text())
              + put(f'{ONE_WORK}/logintest.pub', logintest_pub)
              + ''.join(put(f'{ONE_WORK}/{k}.enc', v) for k, v in secrets.items()) + dec
              + f'E=/etc/forcitis-one/one.env\n'
              f'dec {ONE_WORK}/FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD.enc | python3 {ONE_WORK}/set_env.py $E FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD '
              f'"FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL={LEGACY_URL}" FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_USERNAME=psa_one_legacy_ro\n'
              f'dec {ONE_WORK}/LEGACY_DB_RO_PASSWORD.enc | python3 {ONE_WORK}/set_env.py $E LEGACY_DB_RO_PASSWORD '
              f'"LEGACY_DB_RO_URL={LEGACY_URL}" LEGACY_DB_RO_USERNAME=one_legacy_names_ro "A8_DB_URL={A8_URL}"\n'
              "stat -c '%U:%G %a %n' $E\n"
              'echo "INBOX:$(dec ' + f'{ONE_WORK}/FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD.enc | enc)"\n'
              'echo "NAMES:$(dec ' + f'{ONE_WORK}/LEGACY_DB_RO_PASSWORD.enc | enc)"\n'
              f'rm -rf {ONE_WORK}\n')
    invoke, st, code, out = run(ONE_SERVER, script)
    log['env'] = invoke
    print(f'one.env {invoke} {st} exit={code}')
    print(re.sub(r'(INBOX|NAMES):[A-Za-z0-9+/=]+', r'\1:<ciphertext>', out))
    if st != 'Success':
        raise SystemExit('writing one.env failed')
    login = dict(re.findall(r'^(INBOX|NAMES):([A-Za-z0-9+/=]+)$', out, re.M))

    # 5. Legacy server: log in as each account; read own view, every write or foreign read must be denied.
    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text(), 'inbox.enc': login['INBOX'], 'names.enc': login['NAMES']}
    for name in ('legacy_inbox_login_read.sql', 'legacy_inbox_deny.sql', 'legacy_names_login_read.sql', 'legacy_names_deny.sql'):
        files[name] = (TOOLS / name).read_text()
    test = ''
    for who, login_name in (('inbox', 'psa_one_legacy_ro'), ('names', 'one_legacy_names_ro')):
        env = f'LOGIN_USER={login_name} LOGIN_SECRET_ENC={LEGACY_WORK}/stage/{who}.enc LOGIN_KEY={LEGACY_WORK}/logintest.key'
        test += (f'echo "######## read as {login_name}"; {env} java -cp {LEGACY_WORK}/mssql-jdbc.jar {LEGACY_WORK}/stage/OneSql.java ro ruoyi-vue-pro {LEGACY_WORK}/stage/legacy_{who}_login_read.sql < /dev/null || rc=1\n'
                 f'echo "######## denied as {login_name}"; {env} java -cp {LEGACY_WORK}/mssql-jdbc.jar {LEGACY_WORK}/stage/OneSql.java deny ruoyi-vue-pro {LEGACY_WORK}/stage/legacy_{who}_deny.sql < /dev/null || rc=1\n')
    script = stage_legacy(files) + DRIVER + 'rc=0\n' + test + f'rm -rf {LEGACY_WORK}\nexit $rc\n'
    invoke, st, code, out = run(LEGACY_SERVER, script)
    log['login_tests'] = invoke
    print(f'login tests {invoke} {st} exit={code}')
    print(out)

    # 6. Cleanup check on both servers.
    for server in (LEGACY_SERVER, ONE_SERVER):
        _, _, _, out = run(server, f'rm -rf {LEGACY_WORK}; test -e {LEGACY_WORK} && echo LEFT || echo CLEAN')
        print(server, out.strip())
    print(json.dumps(log))


if __name__ == '__main__':
    main()
