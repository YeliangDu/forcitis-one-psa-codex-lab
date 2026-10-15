"""FORCITIS one · 运维机上的阿里云临时凭据（profile forcitis-production，OAuth 模式）与工具来源检查，供云助手通道脚本共用。

aliyun CLI 只在临时凭据过期之后才换新，过期前调用不会延长；所以剩余时间不够时先等它过期，再用一次无害调用换新。
凭据只在本进程内使用，不打印。工具必须从仓库检出中、与提交一致的文件运行：改过的副本（例如降低阈值）一律拒绝。
"""
import json
import os
import subprocess
import time
from pathlib import Path

PROFILE = 'forcitis-production'
CONFIG = Path(os.path.expanduser('~/.aliyun/config.json'))
MIN_SECONDS = 900
RENEW_STEPS = ('the temporary credentials could not be renewed; stop and report. To renew by hand: wait until they have expired, then run '
               f'`aliyun sts GetCallerIdentity --profile {PROFILE}` once and check again. Do not lower the threshold or edit this file')


def _profile():
    return next(p for p in json.loads(CONFIG.read_text())['profiles'] if p['name'] == PROFILE)


def credentials(min_seconds=MIN_SECONDS):
    """The profile, valid for at least min_seconds; waits for the expiry and renews when it is closer than that."""
    profile = _profile()
    left = profile['sts_expiration'] - time.time()
    if left >= min_seconds:
        return profile
    if left > 0:
        print(f'temporary credentials expire in {int(left)} s (< {min_seconds} s); waiting for the expiry to renew them', flush=True)
        time.sleep(left + 5)
    subprocess.run(['aliyun', 'sts', 'GetCallerIdentity', '--profile', PROFILE], capture_output=True, timeout=60)
    profile = _profile()
    if profile['sts_expiration'] - time.time() < min_seconds:
        raise SystemExit(RENEW_STEPS)
    return profile


def require_committed(*files):
    """Refuses to run unless every file is tracked in a git checkout and identical to the checked-out commit."""
    for file in files:
        path = Path(file).resolve()
        check = subprocess.run(['git', '-C', str(path.parent), 'ls-files', '--error-unmatch', path.name], capture_output=True)
        changed = subprocess.run(['git', '-C', str(path.parent), 'diff', '--quiet', 'HEAD', '--', path.name], capture_output=True)
        if check.returncode != 0 or changed.returncode != 0:
            raise SystemExit(f'{path} is not the committed file of a repository checkout; run the tools from the repository, unmodified')


def ecs_client(region, min_seconds=MIN_SECONDS):
    """An ECS client on credentials valid for at least min_seconds; build one per long call rather than one per process."""
    from alibabacloud_ecs20140526.client import Client as Ecs
    from alibabacloud_tea_openapi import models as om
    profile = credentials(min_seconds)
    return Ecs(om.Config(access_key_id=profile['access_key_id'], access_key_secret=profile['access_key_secret'],
                         security_token=profile['sts_token'], region_id=region, endpoint=f'ecs.{region}.aliyuncs.com'))
