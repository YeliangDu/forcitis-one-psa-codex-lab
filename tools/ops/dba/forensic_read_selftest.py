#!/usr/bin/env python3
"""Self-test of forensic_read.py (card #1377): no network, no production, a fake cloud. `forensic_read_selftest.py --self-test`."""
import base64
import contextlib
import gzip
import io
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import forensic_read as f  # noqa: E402

CONF = {"aliyun_profile": "p", "region": "r", "app_host": "app", "secret_host": "sec", "app_work_dir": "/w/app", "app_jar_process": "x.jar",
        "secret_file": "/w/s.env", "secret_work_dir": "/w/sec", "account": "acct", "targets": {"t1": {"database": "db1"}}}
GOOD_CHECK = "  # login_name | current_db | permission_name\n  acct | db1 | CONNECT\n  acct | db1 | SELECT\n  (2 rows)\n"
ROW_DATA = "  # a | b\n  PERSON-X | 12345\n  (1 rows)\n"
WRITES = ["DELETE FROM t", "update t set a = 1", "INSERT INTO t VALUES (1)", "DROP TABLE t", "EXEC sp_who", "SELECT * INTO x FROM t", "SELECT 1; SELECT 2", "SELECT 1; DELETE FROM t",
          "SELECT 1\nGO\nDELETE FROM t", "SELECT 1 /* x */ ; DROP TABLE t", "WITH c AS (SELECT 1 a) DELETE FROM c", "SELECT 'a'' --'; DELETE FROM t", "SELECT * FROM OPENROWSET('x','y','z')",
          "SELECT 1 FROM a.b.c.d", "SELECT 1 FROM t FOR UPDATE; COMMIT", "SELECT 1 /* /* */ */", "SELECT 'open", "DECLARE @a int SELECT 1", "", "-- only a comment", "SELECT 1\x00",
          "SELECT x FROM t WHERE y = (SELECT 1); EXECUTE('SELECT 1')", "SELECT 1 EXEC dbo.p", "SELECT 1 EXECUTE dbo.p",
          "WITH c AS (SELECT 1 a) SELECT * FROM c", "(SELECT 1)", "VALUES (1)", "TRUNCATE TABLE t",
          "SELECT 1 FROM t WITH (UPDLOCK)", "SELECT NEXT VALUE FOR s"]
READS = ["SELECT 1", "select a, b from dbo.t where c = 'x; DELETE' -- DROP\n", "SELECT N'EXEC', [into], \"set\" FROM [dbo].[t] /* GO */ WHERE a IN ('it''s');", f.CHECK_SQL]


class Fake:
    def __init__(self, results, log):
        self.results, self.log, self.sent = list(results), log, []

    def run(self, instance, script, timeout=600):
        self.log.append("cloud")
        if "genpkey" in script:
            return "Success", 0, "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----\n"
        if "enc.py" in script and "EVIDENCE_DB_PASSWORD" not in script.split("enc.py")[-1]:
            return "Success", 0, "EVIDENCE:QUJD\n"
        if "OneSql.java ro" in script:
            subprocess.run(["bash", "-n"], input=script.encode(), check=True)
            q = re.search(r"echo '([A-Za-z0-9+/=]+)' \| base64 -d \| gunzip > \S+/q\.sql", script)
            self.sent.append(gzip.decompress(base64.b64decode(q.group(1))).decode())
            res = self.results.pop(0)
            return "Success", 0, res if res == "TOOBIG" else base64.b64encode(gzip.compress(res.encode())).decode() + "\n"
        if "test -e" in script:
            self.log.append("cleanup")
            return "Success", 0, "CLEAN\n"
        raise AssertionError("unexpected script")


def entry(tmp, sql, results=(), conf=CONF, out=None, mode=0o600, marker=False):
    """Runs main() against a fake cloud; returns (code, stderr, stdout, fake, log, out path)."""
    cfg = Path(tmp, "cfg", "config.json")
    cfg.parent.mkdir(exist_ok=True)
    cfg.write_text(json.dumps(conf))
    cfg.chmod(mode)
    marker_file = Path(tmp, "cfg", "verified-t1")
    marker_file.unlink(missing_ok=True)
    if marker:
        marker_file.write_text("x")
    Path(tmp, "q.sql").write_text(sql, encoding="utf-8")
    out = out or str(Path(tmp, "out", "r.txt"))
    Path(out).parent.mkdir(exist_ok=True)
    Path(out).unlink(missing_ok=True)
    log, fake = [], None
    fake = Fake(results, log)
    f.CONFIG = cfg
    f.register = lambda *a: log.append("register")
    so, se = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(so), contextlib.redirect_stderr(se):
        code = f.main(["--target", "t1", "--session", "s", "--purpose", "p", "--delete-at", "d", "--out", out, str(Path(tmp, "q.sql"))], cloud_factory=lambda conf: fake)
    return code, se.getvalue(), so.getvalue(), fake, log, out


def main():
    for sql in READS:
        f.validate_select(sql)
    for sql in WRITES:
        try:
            f.validate_select(sql)
        except f.Refused:
            continue
        raise AssertionError(f"validator accepted: {sql!r}")
    assert "DROP" not in f.validate_select("SELECT 1 -- DROP\n") and f.validate_select("SELECT 1 /* c */ FROM t").count("/*") == 0, "comments are removed from what is sent"
    with tempfile.TemporaryDirectory() as tmp:
        # a non-SELECT file is refused by the entry before the config is read, anything is registered or sent
        f.CONFIG = Path(tmp, "missing.json")
        for sql in WRITES:
            code, err, out, fake, log, _ = entry(tmp, sql)
            assert code == 2 and log == [] and "refused" in err and "config" not in err, (sql, code, log, err)
        # a SELECT file runs: registered before the first cloud call, sanitized text sent, first run checks the account then runs, only counts are printed
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t -- why\n", [GOOD_CHECK, ROW_DATA])
        assert code == 0 and log[0] == "register"
        assert fake.sent == [f.CHECK_SQL, "SELECT a FROM t"], fake.sent
        assert "PERSON-X" not in out + err and Path(path).read_text() == ROW_DATA and oct(os.stat(path).st_mode & 0o777) == "0o600", "result goes to the file only"
        assert log.count("cleanup") == 2 and Path(tmp, "cfg", "verified-t1").exists(), "work dir removed after each server run; marker written"
        # verified recently: no second check
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [ROW_DATA], marker=True)
        assert code == 0 and fake.sent == ["SELECT a FROM t"], fake.sent
        # the account check stops the run: an extra permission, a wrong login, a wrong database, an error text
        for bad in (GOOD_CHECK + "  acct | db1 | ALTER\n", GOOD_CHECK.replace("acct", "other"), GOOD_CHECK.replace("db1", "db2"), "SQLERROR 1 x\n", GOOD_CHECK.replace("  acct | db1 | SELECT\n", "")):
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [bad, ROW_DATA])
            assert code == 2 and fake.sent == [f.CHECK_SQL] and not Path(path).exists() and not Path(tmp, "cfg", "verified-t1").exists() and log[-1] == "cleanup", bad
        # too large a reply, --out inside the repo or existing, a config readable by others, an unknown target: refused, nothing leaks
        code, err, *_ = entry(tmp, "SELECT a FROM t", ["TOOBIG"], marker=True)
        assert code == 2 and "too large" in err
        code, err, out, fake, log, _ = entry(tmp, "SELECT 1", out=str(f.REPO / "docs" / "r.txt"))
        assert code == 2 and log == [] and "outside" in err
        code, err, out, fake, log, _ = entry(tmp, "SELECT 1", mode=0o644)
        assert code == 2 and log == [] and "group or others" in err
        code, err, out, fake, log, _ = entry(tmp, "SELECT 1", conf={**CONF, "targets": {"other": {"database": "d"}}})
        assert code == 2 and "unknown target" in err and "register" not in log, (code, err, log)
    # the entry's source holds no production fingerprint (the gate would refuse the command otherwise, and identifiers belong in the local config)
    table = json.loads(Path(f.REPO, "scripts", "gates", "prod_targets.json").read_text())["targets"]
    folded = re.sub(r"[^0-9a-z]", "", (HERE / "forensic_read.py").read_text().lower() + (HERE / "forensic_read_selftest.py").read_text().lower())
    assert not [p for t in table for p in t["patterns"] if re.sub(r"[^0-9a-z]", "", p.lower()) in folded], "fingerprint in the entry source"
    print("forensic_read self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] != ["--self-test"]:
        sys.exit(__doc__)
    main()
