#!/usr/bin/env python3
"""FORCITIS one · B-5 · 取证入口共用的云助手通道（运维机上运行，阿里云 Python SDK）：在两台服务器上跑脚本、传文件、生成临时密钥。

自 B-5 的执行脚本（b5_legacy_run、b5_read_all_run、b5_evidence_run，均为写操作且已执行完毕，#742 删除）原样抽出，供只读的 b5_evidence_scan 用。
导入即核对本模块与入口文件与 origin/main 逐字节一致（ops_credentials.require_committed）。
"""
import base64
import gzip
import sys
import time
from pathlib import Path

from alibabacloud_ecs20140526 import models as em

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'ecs' / 'scripts'))  # tools/ops/ecs/scripts
from ops_credentials import MIN_SECONDS, ecs_client, require_committed  # noqa: E402

REGION = 'cn-hangzhou'
LEGACY_SERVER = 'i-bp1b34hcl0g1cnkdtrb9'
ONE_SERVER = 'i-bp122xlpar8w2uylmal2'
LEGACY_WORK = '/root/one-ops-b5'

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
'''


def step(title, instance, script):
    invoke, status, code, out = run(instance, script)
    print(f'==== {title} {invoke} {status} exit={code}')
    return invoke, status, out


def key_pair(server, work, name):
    _, _, out = step(f'key pair on {server}', server,
                     f'set -eu; umask 077; install -d -m 700 {work}; '
                     f'openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out {work}/{name}.key 2>/dev/null; '
                     f'openssl pkey -in {work}/{name}.key -pubout')
    return out[out.index('-----BEGIN'):]
