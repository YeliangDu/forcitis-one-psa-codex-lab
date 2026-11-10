"""Test-owned real child call observer, not an implementation/launcher stub."""
import hashlib
import json
from pathlib import Path
import runpy
import sys

program, request, report, encoded = sys.argv[1:]
expected = json.loads(encoded)
calls = []

def observe(frame, event, arg):
    if event == 'call':
        file = str(Path(frame.f_code.co_filename).resolve())
        if file in expected:
            digest = hashlib.sha256(Path(file).read_bytes()).hexdigest()
            if digest != expected[file]:
                raise AssertionError('CHILD_LOADED_PINNED_SOURCE_CHANGED')
            row = dict(file=file, function=frame.f_code.co_name, sha256=digest)
            if row not in calls:
                calls.append(row)

sys.path.insert(0, str(Path(program).parent))
sys.argv = [program, request]
sys.setprofile(observe)
try:
    runpy.run_path(program, run_name='__main__')
finally:
    sys.setprofile(None)
    Path(report).write_text(json.dumps(dict(pid=__import__('os').getpid(), calls=calls), sort_keys=True))
