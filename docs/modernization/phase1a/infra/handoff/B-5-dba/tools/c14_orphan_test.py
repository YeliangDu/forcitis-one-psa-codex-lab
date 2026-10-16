#!/usr/bin/env python3
"""FORCITIS one · C-14 · 实测：删除探针登录后，RDS 在 msdb、rdscore 自动建的探针用户会不会随登录一起清理。

背景：执行账号看不见也删不了这两库里的用户（#372、#379 实测），所以让探针登录自己数自己的用户。
做法（用户 2026-10-16 选方案四）：用 c14_run 的准备与收尾。第 1 轮：建探针登录与库、库用户 → 探针登录以自己的身份，在 msdb、rdscore 各查
「名字等于自己」的用户的个数、其中 SID 等于自己的个数、principal_id、创建时刻（整数秒）→ 收尾。等 60 秒后第 2 轮：同名重建新登录（新 SID）再查一次。
判定（每个库）：第 1 轮不是「名字 1、SID 1」→ 基线不干净（先前已有残留或查询失败），无法判定；
第 2 轮建登录被拒或查询出错 → 记错误号，视为残留的证据；名字 1 而 SID 0 → 残留（孤儿未清理）；
SID 1 且 principal_id、创建时刻与第 1 轮相同 → RDS 重新映射了旧用户（也算残留）；SID 1 且二者有变 → 已清理。
第 2 轮只等 60 秒，RDS 若按作业周期异步清理，可能把「还没清理」判成残留，判残留时隔几分钟复测。第 2 轮若结果为残留，最多在 msdb、rdscore 各留一个同名孤儿用户，须用户在控制台处理。
退出码：0 两轮都量到；1 中途停止；2 收尾不干净；3 开工前拒绝（什么都没改）。
用法：c14_orphan_test.py <新结果文件> <app.jar 的 SHA-256>
"""
import re
import signal
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import c14_run  # noqa: E402
from c14_run import (CLEAN_READBACK, DB, LOGIN, ONE_SERVER, P, PREPARE_MASTER, READBACK, W, Stop, ecs, key_pair, legacy_sql, numbers, put,  # noqa: E402
                     record, refuse, setup_ecs)
from ops_credentials import require_committed  # noqa: E402

require_committed(Path(__file__))

OWN_USER = ("SELECT COUNT(*) AS by_name, ISNULL(SUM(CASE WHEN sid = SUSER_SID() THEN 1 ELSE 0 END), 0) AS by_sid, ISNULL(MIN(principal_id), 0) AS principal_id,\n"
            "       ISNULL(MIN(DATEDIFF(second, '2026-01-01', create_date)), 0) AS created_seconds FROM {db}.sys.database_principals WHERE name = SUSER_SNAME();")
SECOND_ROUND_WAIT_SECONDS = 60


def own_user(label, database):
    script = (f'set -u\ncd {W}\n' + put(f'{W}/q.sql', OWN_USER.format(db=database) + '\nGO\n')
              + f'LOGIN_USER={LOGIN} LOGIN_SECRET_ENC={W}/probe.enc LOGIN_KEY={W}/probe.key java -cp {W}/mssql-jdbc.jar {W}/OneSql.java ro {DB} {W}/q.sql; '
                f'echo "RC=$?"; rm -f {W}/q.sql\n')
    invoke, _, _, out = ecs(script)
    codes = re.findall(r'^RC=(\d+)$', out, re.M)
    rows = numbers(out)
    errors = [int(n) for n in re.findall(r'^SQLERROR (\d+)', out, re.M)]
    value = rows[-1] if codes and codes[-1] == '0' and rows and len(rows[-1]) == 4 else None
    record(step=label, invoke=invoke, database=database, by_name_by_sid_principal_id_created_seconds=value, sql_errors=errors)
    return value, errors, 'query'


def cycle(number, jar_sha256):
    rc, _, out = legacy_sql(f'cycle {number} readback before', 'master', READBACK, mode='ro')
    rows = numbers(out)
    if rc != 0 or not rows or rows[-1] != CLEAN_READBACK:
        raise Stop(f'cycle {number}: probe login or database exists already, or the readback failed')
    transfer_pub = key_pair(ONE_SERVER, W, 'transfer')
    rc, errors, out = legacy_sql(f'cycle {number} prepare login and database', 'master', PREPARE_MASTER, enc_pub=transfer_pub)
    cipher = re.findall(rf'{LOGIN} \| ENC:([A-Za-z0-9+/=]+)', out)
    if rc != 0 or len(cipher) != 1:
        record(step=f'cycle {number} prepare refused', sql_errors=errors)
        return {'msdb': (None, errors, 'prepare'), 'rdscore': (None, errors, 'prepare')}
    rc, _, _ = legacy_sql(f'cycle {number} prepare user', DB, f'CREATE USER {P} FOR LOGIN {P};')
    if rc != 0:
        raise Stop(f'cycle {number}: creating the database user failed')
    setup_ecs(cipher[0], jar_sha256)
    return {database: own_user(f'cycle {number} own user in {database}', database) for database in ('msdb', 'rdscore')}


def verdict(first, second):
    result = {}
    for database in ('msdb', 'rdscore'):
        one, _, _ = first[database]
        two, errors, source = second[database]
        if one is None or one[:2] != (1, 1):
            result[database] = 'baseline not clean or unreadable: cannot decide'
        elif two is None and errors and (source == 'prepare' or 916 in errors):
            result[database] = f'RESIDUE (inferred): the new login was refused or has no access (errors {errors})'
        elif two is None:
            result[database] = f'cannot decide: no SQL error to explain the missing reading (errors {errors})'
        elif two[1] == 0 and two[0] >= 1:
            result[database] = 'RESIDUE: a user with the probe\'s name exists with another SID'
        elif two[:2] == (0, 0):
            result[database] = 'RESIDUE (inferred): no user was made for the new login, most likely blocked by the old one (the old one is invisible to it)'
        elif two[:2] == (1, 1) and two[2:] == one[2:]:
            result[database] = 'RESIDUE: the old user was re-mapped to the new login'
        elif two[:2] == (1, 1):
            result[database] = 'CLEANED: RDS removed the old user and made a new one'
        else:
            result[database] = f'no user for the new login (by_name {two[0]}, by_sid {two[1]}): cannot decide'
    return result


def resume_signals():
    signals = {signal.SIGINT, signal.SIGTERM, signal.SIGHUP}
    signal.pthread_sigmask(signal.SIG_UNBLOCK, signals)
    signal.signal(signal.SIGINT, signal.default_int_handler)
    for number in (signal.SIGTERM, signal.SIGHUP):
        signal.signal(number, c14_run.interrupted)


def main(target, jar_sha256):
    if not re.fullmatch(r'[0-9a-f]{64}', jar_sha256):
        refuse('the second argument is the SHA-256 of app.jar (64 lowercase hex digits)')
    if Path(target).exists():
        refuse('the result file exists; give a new name; nothing was changed')
    try:
        c14_run.save(target)
    except OSError as failure:
        refuse(f'the result file cannot be written ({type(failure).__name__}); nothing was changed')
    c14_run.result_file.append(target)
    rc, _, out = legacy_sql('preflight readback', 'master', READBACK, mode='ro')
    rows = numbers(out)
    record(step='preflight', readback=rows[-1] if rows else None)
    if rc != 0 or not rows or rows[-1] != CLEAN_READBACK:
        refuse('preflight: probe objects already exist or the readback failed; nothing was changed')
    for number in (signal.SIGTERM, signal.SIGHUP):
        signal.signal(number, c14_run.interrupted)
    clean, measured, results = True, False, []
    try:
        for number in (1, 2):
            if number == 2:
                time.sleep(SECOND_ROUND_WAIT_SECONDS)
            results.append(cycle(number, jar_sha256))
            clean = c14_run.cleanup() and clean
            resume_signals()
            if not clean:
                break
            if number == 1 and any(v[0] is None or v[0][:2] != (1, 1) for v in results[0].values()):
                record(stop='cycle 1 baseline not clean: no second round')
                break
        if len(results) == 2:
            outcome = verdict(*results)
            record(verdict=outcome)
            measured = all(not text.startswith(('cannot decide', 'baseline not clean')) for text in outcome.values())
    except BaseException as failure:  # noqa: BLE001 whatever went wrong, the probe objects are removed
        record(stop=f'{type(failure).__name__}: {failure}' if isinstance(failure, Stop) else f'unexpected {type(failure).__name__}')
        clean = c14_run.cleanup() and clean
    try:
        c14_run.save(target)
    except Exception:  # noqa: BLE001 the records are in the result file already
        pass
    try:
        sys.stdout.flush()
        sys.stderr.flush()
    except OSError:
        sys.stdout = sys.stderr = open('/dev/null', 'w')
    sys.exit(2 if not clean else (0 if measured else 1))


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('usage: c14_orphan_test.py <new result file> <app.jar SHA-256>')
    main(sys.argv[1], sys.argv[2])
