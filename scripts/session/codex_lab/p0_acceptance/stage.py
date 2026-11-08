"""Hosted-only hash-checked fixture staging; no behavior implementation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--manifest-sha256', required=True)
    p.add_argument('--destination', type=Path, required=True)
    args = p.parse_args()
    root = Path(__file__).resolve().parent
    if os.environ.get('GITHUB_ACTIONS') != 'true':
        raise ValueError('CAPABILITY_HOLD: hosted staging only')
    raw = (root/'manifest.json').read_bytes()
    if hashlib.sha256(raw).hexdigest() != args.manifest_sha256:
        raise ValueError('CAPABILITY_HOLD: externally frozen A manifest differs')
    manifest = json.loads(raw)
    destination = args.destination.absolute()
    runner = Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
    if destination.exists() or runner not in destination.parents:
        raise ValueError('CAPABILITY_HOLD: new RUNNER_TEMP destination required')
    for relative, expected in manifest['files_sha256'].items():
        path = root/relative
        if path.resolve(strict=True) != path or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError('CAPABILITY_HOLD: missing/changed frozen input '+relative)
    destination.mkdir(mode=0o700)
    for relative in manifest['files_sha256']:
        path = destination/relative
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root/relative, path)
    shutil.copyfile(root/'manifest.json',destination/'manifest.json')
    print(json.dumps(dict(kind='p0-fixture-staging/1', manifest_sha256=args.manifest_sha256,
                         destination=str(destination), runtime_activated=False)))


if __name__ == '__main__':
    try: main()
    except (OSError,ValueError,KeyError) as e:
        print('CAPABILITY_HOLD: '+str(e))
        raise SystemExit(86)
