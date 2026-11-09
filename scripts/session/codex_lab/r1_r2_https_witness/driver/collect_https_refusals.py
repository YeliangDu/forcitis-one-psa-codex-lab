"""Same frozen vertical scenario: hosted HTTPS credential refusal boundary only.

No positive credential GET, gh, token, credential config, Git or network operation.
The actual pinned helper script is run unchanged; strace observes its syscalls.
"""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

CLOSURE_SHA256 = '6ce7a247f631ec9f10fa8f61819fe17fe0bf4bb9f43046900dbd6b0eeec8dc1e'
SOURCE_PINS = {'branch_effect_https.py': 'a3939093351c3f563d8908a46a5c7c816e03197ec3dca2b4ffac3b9e92b835ab', 'branch_effect_port.py': '5c31e8f61f84eddf816dd864faaf2444695bfeb9087be576c72c95f871d5e056', 'exact_gh_credential.py': 'ad29df3720433f3a6c48c107438dcd37e2e9dc7e68b7f46c95c9104751871e74'}
SCENARIO_SHA256 = '7c778e9bc57f56bb187b1355131816c7e30f553af9c5f6c997a6c1cd4e602b48'
NL = bytes([10])
REPO = b'YeliangDu/forcitis-one-psa-codex-lab.git'


class Hold(RuntimeError):
    pass


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def fields(rows):
    return NL.join(k + b'=' + v for k, v in rows) + NL + NL


def cases():
    base = [(b'protocol', b'https'), (b'host', b'github.com'), (b'path', REPO)]
    result = []
    def changed(key, value):
        return [(k, value if k == key else v) for k, v in base]
    for protocol in (b'http', b'ssh', b'git', b'file', b'ftp', b''):
        result.append(('non-https-' + protocol.decode(), ['get'], fields(changed(b'protocol', protocol)), 1))
    for index, host in enumerate((b'evil.invalid', b'github.com.evil.invalid', b'github.com:443', b'GitHub.com', b'user@github.com', b'')):
        result.append(('non-exact-host-' + str(index), ['get'], fields(changed(b'host', host)), 1))
    for index, path in enumerate((REPO[:-4], b'Other/repo.git', b'/' + REPO, REPO + b'/', REPO + b'.extra',
                                 b'yeliangdu/forcitis-one-psa-codex-lab.git', b'YeliangDu/../repo.git',
                                 b'YeliangDu%2Fforcitis-one-psa-codex-lab.git')):
        result.append(('non-exact-repo-path-' + str(index), ['get'], fields(changed(b'path', path)), 1))
    for index, username in enumerate((b'forbidden', b'', b'yeliangdu', b'YeliangDu ', b'YeliangDu@github.com')):
        result.append(('non-exact-username-' + str(index), ['get'], fields(base + [(b'username', username)]), 1))
    for key, value in base + [(b'username', b'YeliangDu')]:
        original = base if key != b'username' else base + [(key, value)]
        result.append(('duplicate-' + key.decode(), ['get'], fields(original + [(key, value)]), 1))
    for index, extra in enumerate(((b'url', b'https://github.com/invalid'), (b'password', b'forbidden'),
                                   (b'authtype', b'forbidden'), (b'unknown', b'forbidden'))):
        result.append(('extra-field-' + str(index), ['get'], fields(base + [extra]), 1))
    valid = fields(base)
    malformed = [('empty', b''), ('missing-terminal-blank', valid[:-1]),
                 ('no-terminal-newline', valid[:-2]), ('nul', valid[:-2] + bytes([0]) + NL + NL),
                 ('crlf', valid.replace(NL, bytes([13, 10]))),
                 ('missing-equals', b'protocol' + NL + NL), ('oversize', b'x' * 4097),
                 ('extra-after-blank', valid + b'x'), ('empty-row', valid[:-2] + NL + NL + NL)]
    for name, raw in malformed:
        result.append(('malformed-' + name, ['get'], raw, 1))
    for key, _ in base:
        result.append(('missing-' + key.decode(), ['get'], fields([(k, v) for k, v in base if k != key]), 1))
    for operation in ('store', 'erase'):
        result.append((operation + '-valid-context-no-delegate', [operation], valid, 0))
        result.append((operation + '-oversize-no-delegate', [operation], b'x' * 4097, 0))
    # Independent precondition: never launch a syntactically valid exact GET.
    for name, argv, raw, _ in result:
        if argv == ['get']:
            exact = False
            if len(raw) <= 4096 and raw.endswith(NL + NL) and bytes([0]) not in raw and bytes([13]) not in raw:
                parsed = {}; valid_rows = True
                for row in raw[:-2].split(NL):
                    key, sep, value = row.partition(b'=')
                    if not sep or key in parsed or key not in (b'protocol', b'host', b'path', b'username'):
                        valid_rows = False; break
                    parsed[key] = value
                exact = valid_rows and parsed.get(b'protocol') == b'https' and parsed.get(b'host') == b'github.com' and parsed.get(b'path') == REPO and parsed.get(b'username', b'YeliangDu') == b'YeliangDu'
            if exact:
                raise Hold('positive credential GET accidentally selected: ' + name)
    return result


def run(source, evidence, parent_netns):
    if sys.platform != 'linux' or os.geteuid() == 0 or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise Hold('non-root GitHub-hosted Linux required')
    if 'NoNewPrivs:' + chr(9) + '1' not in Path('/proc/self/status').read_text():
        raise Hold('no-new-privileges required')
    namespace = os.readlink('/proc/self/ns/net')
    if namespace == parent_netns:
        raise Hold('private network namespace required')
    ip = next((p for p in ('/usr/sbin/ip', '/usr/bin/ip') if Path(p).is_file()), None)
    tracer = next((p for p in ('/usr/bin/strace', '/bin/strace') if Path(p).is_file()), None)
    if ip is None or tracer is None:
        raise Hold('fixed-path ip and strace required; do not install or substitute a mock')
    links = subprocess.check_output([ip, '-o', 'link', 'show'], env={'PATH': '/usr/bin:/bin'}, text=True, timeout=5)
    if [line.split(':', 2)[1].strip().split('@', 1)[0] for line in links.splitlines() if line.strip()] != ['lo']:
        raise Hold('only lo permitted')
    if os.readlink('/proc/self/ns/net') != namespace:
        raise Hold('namespace moved during interface readback')
    if any(os.environ.get(k) for k in ('GH_TOKEN', 'GITHUB_TOKEN', 'GH_CONFIG_DIR', 'SSH_AUTH_SOCK', 'OPENAI_API_KEY', 'AWS_ACCESS_KEY_ID')):
        raise Hold('credential-bearing environment refused')
    raw = (source / 'SOURCE-CLOSURE.json').read_bytes()
    if sha(raw) != CLOSURE_SHA256:
        raise Hold('externally frozen source closure differs')
    for name, pin in SOURCE_PINS.items():
        p = source / name
        if p.resolve(strict=True) != p or sha(p.read_bytes()) != pin:
            raise Hold('source pin/path differs: ' + name)
    observations = []
    with tempfile.TemporaryDirectory(prefix='https-refusal-') as temporary:
        root = Path(temporary).resolve(); root.chmod(0o700)
        helper = root / 'exact_gh_credential.py'
        helper.write_bytes((source / 'exact_gh_credential.py').read_bytes()); helper.chmod(0o500)
        env = dict(PATH='/usr/bin:/bin', HOME=str(root), PYTHONDONTWRITEBYTECODE='1', LANG='C.UTF-8')
        # Deliberately no GH_CONFIG_DIR; no helper config/credential files exist.
        for index, (name, operation, input_bytes, expected_exit) in enumerate(cases()):
            trace = root / ('syscalls-' + str(index) + '.log')
            helper_argv = [sys.executable, '-I', '-S', '-B', str(helper), *operation]
            command = [tracer, '-f', '-qq', '-s', '512', '-e', 'trace=execve,execveat,connect', '-o', str(trace), *helper_argv]
            p = subprocess.run(command, input=input_bytes, env=env, cwd=root,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=8)
            if not trace.exists() or trace.stat().st_size > 1024 * 1024:
                raise Hold('syscall capture missing/oversize')
            traced = trace.read_bytes(); text = traced.decode('utf-8', errors='replace')
            execs = [line for line in text.splitlines() if 'execve(' in line or 'execveat(' in line]
            if not execs:
                raise Hold('strace did not observe script execution; ptrace/tool capability missing')
            if len(execs) != 1 or 'connect(' in text:
                raise AssertionError('DELEGATION_OR_NETWORK_IN_REFUSAL_PATH:' + name)
            if p.returncode != expected_exit or p.stdout != b'' or p.stderr != b'':
                # Never print unexpected output: a future helper must not leak secrets in evidence.
                raise AssertionError('NONEMPTY_OUTPUT_OR_WRONG_REFUSAL_EXIT:' + name)
            observations.append(dict(name=name, helper_argv=helper_argv, tracer_argv=command,
                                     operation=operation[0], input_bytes=len(input_bytes), input_sha256=sha(input_bytes),
                                     expected_exit=expected_exit, actual_exit=p.returncode,
                                     stdout_bytes=0, stderr_bytes=0, stdout_sha256=sha(p.stdout), stderr_sha256=sha(p.stderr),
                                     exec_syscalls=1, additional_exec_syscalls=0, connect_syscalls=0,
                                     raw_trace_sha256=sha(traced)))
            # Trace contains only argv/syscall metadata, no stdin or credential output.
            (evidence / ('trace-' + str(index) + '.log')).write_bytes(traced)
            (evidence / 'helper-observations.json').write_text(json.dumps(observations, indent=2) + chr(10))
    if os.readlink('/proc/self/ns/net') != namespace:
        raise Hold('namespace changed during selection')
    return dict(classification='HTTPS-refusal-boundary-observed-overall-HOLD', scenario_sha256=SCENARIO_SHA256,
                source_closure_sha256=CLOSURE_SHA256, source_pins=SOURCE_PINS,
                tools=dict(python=dict(path=sys.executable, sha256=sha(Path(sys.executable).read_bytes())),
                           strace=dict(path=tracer, sha256=sha(Path(tracer).read_bytes())),
                           ip=dict(path=ip, sha256=sha(Path(ip).read_bytes()))),
                selection_count=1, helper_invocations=len(observations), positive_get_invocations=0,
                store_erase_invocations=sum(v['operation'] in ('store', 'erase') for v in observations),
                network_namespace=namespace, real_gh_execs=0, credential_files_read=False,
                actual_send=False, external_acceptance=False, overall_vertical_pass=False)


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--source-root', required=True, type=Path)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--parent-netns', required=True)
    args = parser.parse_args()
    evidence = args.evidence_dir.resolve(strict=True)
    if not evidence.is_dir() or any(evidence.iterdir()):
        raise Hold('fresh empty evidence directory required')
    try:
        report = run(args.source_root.resolve(strict=True), evidence, args.parent_netns)
    except AssertionError as error:
        report = dict(classification='assertion-failed', reason=str(error), overall_vertical_pass=False)
        (evidence / 'verdict.json').write_text(json.dumps(report, indent=2) + chr(10))
        return 1
    except Exception as error:
        report = dict(classification='capability-HOLD', reason=type(error).__name__ + ': ' + str(error)[:1200], overall_vertical_pass=False)
        (evidence / 'verdict.json').write_text(json.dumps(report, indent=2) + chr(10))
        return 86
    (evidence / 'verdict.json').write_text(json.dumps(report, indent=2) + chr(10))
    print(json.dumps(report))
    return 0  # This negative boundary selection only; not overall oracle PASS.


if __name__ == '__main__':
    raise SystemExit(main())
