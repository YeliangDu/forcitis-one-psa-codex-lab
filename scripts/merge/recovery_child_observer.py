"""Test-owned child wrapper: observe actual SQLite/Git reads, forbid Git writes."""
import json
import os
from pathlib import Path
import runpy
import sqlite3
import subprocess
import sys

program, request, dbpath, scratch, report = sys.argv[1:]
expected_db = Path(dbpath).resolve()
observed = dict(pid=os.getpid(), opened_exact_v2=False, sql=[], git_reads=[], mutating_git=[])
connect, Popen = sqlite3.connect, subprocess.Popen

class WatchedConnection(sqlite3.Connection):
    pass

def watched_connect(database, *args, **kwargs):
    # Exact reviewed V2 _expected() derives SQLite's own DDL serialization.
    # This literal in-memory connection is NOT evidence of disk V2/reservation.
    if type(database) is str and database == ':memory:':
        return connect(database, *args, **kwargs)
    raw = str(database).removeprefix('file:').split('?', 1)[0]
    if Path(raw).resolve() != expected_db:
        raise AssertionError('RECOVERY_OPENED_OTHER_STORAGE')
    kwargs['factory'] = WatchedConnection
    db = connect(database, *args, **kwargs)
    if db.execute('pragma user_version').fetchone() != (2,):
        db.close()
        raise AssertionError('RECOVERY_NOT_ACTUAL_V2')
    observed['opened_exact_v2'] = True
    db.set_trace_callback(lambda sql: observed['sql'].append(sql))
    return db

class WatchedPopen(Popen):
    def __init__(self, args, *positional, **kwargs):
        if kwargs.get('shell') or not isinstance(args, (list, tuple)):
            raise AssertionError('RECOVERY_UNOBSERVABLE_PROCESS')
        argv = list(args)
        # Exact read-only scope; no fetch/push/update-ref/commit-tree/merge.
        allowed = ['git', '--no-replace-objects', '-C', scratch,
                   'rev-parse', '--verify', 'refs/heads/main']
        if argv != allowed:
            observed['mutating_git'].append(argv)
            raise AssertionError('RECOVERY_NON_READBACK_SUBPROCESS')
        self.read_argv = argv
        super().__init__(args, *positional, **kwargs)

    def communicate(self, *args, **kwargs):
        output = super().communicate(*args, **kwargs)
        value = output[0] or b''
        if isinstance(value, bytes):
            value = value.decode()
        observed['git_reads'].append(dict(argv=self.read_argv, stdout=value, returncode=self.returncode))
        return output

sqlite3.connect = watched_connect
subprocess.Popen = WatchedPopen
sys.path.insert(0, str(Path(program).parent))
sys.argv = [program, request]
try:
    runpy.run_path(program, run_name='__main__')
finally:
    Path(report).write_text(json.dumps(observed, sort_keys=True))
