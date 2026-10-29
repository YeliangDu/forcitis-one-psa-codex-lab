#!/usr/bin/env python3
"""FORCITIS one · ADR-0037 · 以取证账号 one_evidence_ro 扫描两库凭据列候选（只出库、表、列名）。

取证口令在新 ECS 的 /etc/forcitis-one/evidence.env，用老系统服务器的临时公钥加密后转交；本进程只经手密文。
结果经 gzip + base64 带回（云助手单次输出约 24 KB 上限），写到给定路径。
用法：b5_evidence_scan.py <输出文件> [docs/data/evidence/dba-handoff/ 下的 SQL 文件名，默认 credential-columns-scan.sql]
"""
import base64
import gzip
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ops_venv_guard  # noqa: E402,F401  须排在所有第三方与同目录导入之前
from b5_remote import DRIVER, LEGACY_SERVER, LEGACY_WORK, ONE_SERVER, key_pair, put, stage_legacy, step  # noqa: E402

TOOLS = Path(__file__).resolve().parent
EVIDENCE = TOOLS.parents[2] / 'docs' / 'data' / 'evidence' / 'dba-handoff'  # #755: the tools live in tools/ops/dba/, the evidence queries in docs/
ONE_WORK = '/root/one-ops-b5-scan'
EVIDENCE_ENV = '/etc/forcitis-one/evidence.env'
ENCRYPT = r'''
import subprocess, sys, base64
values = dict(l.strip().split('=', 1) for l in open(sys.argv[2]) if '=' in l and not l.startswith('#'))
secret = values['EVIDENCE_DB_PASSWORD'].strip("'\"")
cipher = subprocess.run(['openssl', 'pkeyutl', '-encrypt', '-pubin', '-inkey', sys.argv[1], '-pkeyopt', 'rsa_padding_mode:oaep',
                         '-pkeyopt', 'rsa_oaep_md:sha256', '-pkeyopt', 'rsa_mgf1_md:sha256'], input=secret.encode(), capture_output=True, check=True).stdout
print('EVIDENCE:' + base64.b64encode(cipher).decode())
'''


def main(target):
    logintest_pub = key_pair(LEGACY_SERVER, LEGACY_WORK, 'logintest')
    _, status, out = step('encrypt evidence password', ONE_SERVER,
                          f'set -eu; umask 077; install -d -m 700 {ONE_WORK}\n' + put(f'{ONE_WORK}/pub', logintest_pub)
                          + put(f'{ONE_WORK}/enc.py', ENCRYPT) + f'python3 -I {ONE_WORK}/enc.py {ONE_WORK}/pub {EVIDENCE_ENV}; rm -rf {ONE_WORK}\n')
    cipher = re.findall(r'^EVIDENCE:([A-Za-z0-9+/=]+)$', out, re.M)
    if status != 'Success' or len(cipher) != 1:
        raise SystemExit('evidence password not available')
    stage = f'{LEGACY_WORK}/stage'
    files = {'OneSql.java': (TOOLS / 'OneSql.java').read_text(), 'evidence.enc': cipher[0],
             'scan.sql': (EVIDENCE / (sys.argv[2] if len(sys.argv) > 2 else 'credential-columns-scan.sql')).read_text()}
    env = f'LOGIN_USER=one_evidence_ro LOGIN_SECRET_ENC={stage}/evidence.enc LOGIN_KEY={LEGACY_WORK}/logintest.key'
    java = f'java -cp {LEGACY_WORK}/mssql-jdbc.jar {stage}/OneSql.java'
    script = stage_legacy(files) + DRIVER + (
        f'{{ {env} {java} ro a8_db {stage}/scan.sql < /dev/null; {env} {java} ro ruoyi-vue-pro {stage}/scan.sql < /dev/null; }} > {LEGACY_WORK}/out.txt 2>&1; rc=$?\n'
        f'gzip -9 -c {LEGACY_WORK}/out.txt | base64 -w0; echo; rm -rf {LEGACY_WORK}; exit $rc\n')
    _, status, out = step('scan as one_evidence_ro', LEGACY_SERVER, script)
    packed = re.findall(r'^[A-Za-z0-9+/=]{40,}$', out, re.M)
    if not packed:
        raise SystemExit(f'no output ({status})')
    Path(target).write_bytes(gzip.decompress(base64.b64decode(packed[-1])))
    _, _, out = step('cleanup', LEGACY_SERVER, f'rm -rf {LEGACY_WORK}; test -e {LEGACY_WORK} && echo LEFT || echo CLEAN')
    print(out.strip(), status)


if __name__ == '__main__':
    main(sys.argv[1])
