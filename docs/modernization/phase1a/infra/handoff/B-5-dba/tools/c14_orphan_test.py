#!/usr/bin/env python3
"""FORCITIS one · C-14 · 实测：删除探针登录后，RDS 在 msdb、rdscore 自动建的探针用户会不会随登录一起清理。

背景：执行账号看不见也删不了这两库里的用户（#372、#379 实测），所以只能让探针登录自己数自己的用户。
做法（用户 2026-10-16 选方案四）：用 c14_run 的准备与收尾，建探针登录与库 → 探针登录以自己的身份数 msdb、rdscore 里
「SID 等于自己」与「名字等于自己」的用户 → 收尾（删登录与库）→ 同名重建一个新登录（新 SID）再数一次：
名字有而 SID 对不上，说明上一轮的用户残留（孤儿）；SID 对得上，说明 RDS 已清理。第二轮收尾后探针对象全删。
本脚本不碰任何其他对象；两轮都始终走 c14_run.cleanup()。
用法：c14_orphan_test.py <新结果文件> <app.jar 的 SHA-256>
"""
import re
import signal
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import c14_run  # noqa: E402
from c14_run import (CLEAN_READBACK, DB, LOGIN, ONE_SERVER, PREPARE_MASTER, READBACK, W, key_pair, legacy_sql, numbers, probe_query, record,  # noqa: E402
                     setup_ecs)

OWN_USERS = ("SELECT (SELECT COUNT(*) FROM msdb.sys.database_principals WHERE sid = SUSER_SID()) AS msdb_by_sid,\n"
             "       (SELECT COUNT(*) FROM msdb.sys.database_principals WHERE name = SUSER_SNAME()) AS msdb_by_name,\n"
             "       (SELECT COUNT(*) FROM rdscore.sys.database_principals WHERE sid = SUSER_SID()) AS rdscore_by_sid,\n"
             "       (SELECT COUNT(*) FROM rdscore.sys.database_principals WHERE name = SUSER_SNAME()) AS rdscore_by_name;")


def cycle(number, jar_sha256):
    rc, _, out = legacy_sql(f'cycle {number} readback before', 'master', READBACK, mode='ro')
    rows = numbers(out)
    if rc != 0 or not rows or rows[-1] != CLEAN_READBACK:
        raise SystemExit(f'cycle {number}: probe objects already exist or the readback failed; nothing was changed')
    transfer_pub = key_pair(ONE_SERVER, W, 'transfer')
    rc, _, out = legacy_sql(f'cycle {number} prepare login and database', 'master', PREPARE_MASTER, enc_pub=transfer_pub)
    cipher = re.findall(rf'{LOGIN} \| ENC:([A-Za-z0-9+/=]+)', out)
    if rc != 0 or len(cipher) != 1:
        raise SystemExit(f'cycle {number}: prepare failed')
    setup_ecs(cipher[0], jar_sha256)
    invoke, qrc, rows = probe_query(f'cycle {number} own users', OWN_USERS)
    record(step=f'cycle {number} own users', invoke=invoke, rc=qrc, msdb_by_sid_by_name_rdscore_by_sid_by_name=rows[-1] if rows else None)
    return rows[-1] if qrc == 0 and rows else None


def main(target, jar_sha256):
    if not re.fullmatch(r'[0-9a-f]{64}', jar_sha256) or Path(target).exists():
        raise SystemExit('usage: c14_orphan_test.py <new result file> <app.jar SHA-256>')
    c14_run.save(target)
    c14_run.result_file.append(target)
    for number in (signal.SIGTERM, signal.SIGHUP):
        signal.signal(number, c14_run.interrupted)
    clean = True
    try:
        for number in (1, 2):
            seen = cycle(number, jar_sha256)
            clean = c14_run.cleanup() and clean
            if seen is None:
                break
    except BaseException as failure:  # noqa: BLE001 whatever went wrong, the probe objects are removed
        record(stop=f'unexpected {type(failure).__name__}')
        clean = c14_run.cleanup() and clean
    sys.exit(0 if clean else 2)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: c14_orphan_test.py <new result file> <app.jar SHA-256>')
    main(sys.argv[1], sys.argv[2])
