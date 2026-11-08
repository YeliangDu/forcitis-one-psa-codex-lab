"""Test-owned pre-exec pipe gate. PID survives exec into original profile child."""
import json
import os
import sys
fd, encoded = int(sys.argv[1]), sys.argv[2]
if os.read(fd, 1) != b'G':
    raise AssertionError('E2E_CHILD_BARRIER_NOT_RELEASED')
os.close(fd)
argv = json.loads(encoded)
os.execv(argv[0], argv)
