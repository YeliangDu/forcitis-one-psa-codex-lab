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
BASE_ROWS = ([("login", "acct", "db1"), ("perm", "CONNECT", "-"), ("perm", "SELECT", "-"), ("srvperm", "CONNECT SQL", "-"), ("srvperm", "VIEW SERVER STATE", "-")]
             + [("dbperm", "D:0", d) for d in sorted(f.DATABASE_DENIES)] + [("dbperm", "G:0", "CONNECT"), ("dbperm", "G:0", "SELECT")]
             + [("owned", k, "0") for k in ("schemas", "objects", "roles", "database")])


def check_text(rows):
    return "  # k | a | b\n" + "".join(f"  {a} | {b} | {c}\n" for a, b, c in rows) + f"  ({len(rows)} rows)\n"


GOOD_CHECK = check_text(BASE_ROWS)
# one failing answer per ADR-0037 item 4 check (a)-(e), plus an empty and an error answer
BAD_CHECKS = {
    "login": check_text([("login", "other", "db1") if r[0] == "login" else r for r in BASE_ROWS]),
    "database": check_text([("login", "acct", "db2") if r[0] == "login" else r for r in BASE_ROWS]),
    "extra permission": check_text(BASE_ROWS + [("perm", "ALTER", "-")]),
    "no SELECT": check_text([r for r in BASE_ROWS if r[:2] != ("perm", "SELECT")]),
    "server permission": check_text(BASE_ROWS + [("srvperm", "CONTROL SERVER", "-")]),
    "deny missing": check_text([r for r in BASE_ROWS if r[:3] != ("dbperm", "D:0", "DELETE")]),
    "extra grant": check_text(BASE_ROWS + [("dbperm", "G:0", "INSERT")]),
    "grant with option": check_text([("dbperm", "W:0", "SELECT") if r[:3] == ("dbperm", "G:0", "SELECT") else r for r in BASE_ROWS]),
    "class 4 grant": check_text(BASE_ROWS + [("dbperm", "G:4", "CONNECT")]),
    "role": check_text(BASE_ROWS + [("role", "db_datareader", "-")]),
    "server role": check_text(BASE_ROWS + [("srvrole", "sysadmin", "-")]),
    "owns objects": check_text([("owned", "objects", "1") if r[:2] == ("owned", "objects") else r for r in BASE_ROWS]),
    "ownership unknown": check_text([r for r in BASE_ROWS if r[0] != "owned"]),
    "empty": "  # k | a | b\n  (0 rows)\n",
    "error": "SQLERROR 1 x\n",
    "error after rows": GOOD_CHECK.replace("  (", "SQLERROR 1205 x\n  (0 rows) #", 1).rsplit("  (0 rows) #", 1)[0] + "SQLERROR 1205 x\n",
    "error after trailer": GOOD_CHECK + "SQLERROR 1205 x\n",
    "no trailer": GOOD_CHECK.rsplit("  (", 1)[0],
    "wrong trailer": GOOD_CHECK.replace(f"({len(BASE_ROWS)} rows)", "(1 rows)"),
}
GOOD_DBS = "  # name\n  db1\n  master\n  tempdb\n  (3 rows)\n"
ROW_DATA = "  # a | b\n  PERSON-X | 12345\n  (1 rows)\n"
WRITES = ["DELETE FROM t", "update t set a = 1", "INSERT INTO t VALUES (1)", "DROP TABLE t", "EXEC sp_who", "SELECT * INTO x FROM t", "SELECT 1; SELECT 2", "SELECT 1; DELETE FROM t",
          "SELECT 1\nGO\nDELETE FROM t", "SELECT 1 /* x */ ; DROP TABLE t", "WITH c AS (SELECT 1 a) DELETE FROM c", "SELECT 'a'' --'; DELETE FROM t", "SELECT * FROM OPENROWSET('x','y','z')",
          "SELECT 1 FROM a.b.c.d", "SELECT 1 FROM t FOR UPDATE; COMMIT", "SELECT 1 /* /* */ */", "SELECT 'open", "DECLARE @a int SELECT 1", "", "-- only a comment", "SELECT 1\x00",
          "SELECT x FROM t WHERE y = (SELECT 1); EXECUTE('SELECT 1')",
          'SELECT 1 AS "a\n:"\'\n\'" DELETE FROM t COMMIT --\'"\n', "SELECT 1\n:r x\n", "SELECT 1\n  go  \nDELETE FROM t", "SELECT 'x\nGO\n' AS a", "SELECT $(v)", 'SELECT 1 AS "a"',
          "SELECT 1 SELECT 2", "SELECT 1 DISABLE TRIGGER tr ON dbo.t", "SELECT 1 RECEIVE TOP(1) * FROM dbo.q", "SELECT 1 CHECKPOINT", "SELECT 1 FROM srv . db . dbo . t", "SELECT 1 EXEC dbo.p", "SELECT 1DELETE FROM t", "SELECT 1COMMIT", "SELECT 1KILL 55", "SELECT 1DBCC FREEPROCCACHE", "SELECT 0x1DELETE FROM t", "SELECT 1e1DELETE FROM t", "SELECT 0xKILL 55", "SELECT 0xABKILL 55", "SELECT 1eCOMMIT", "SELECT 1eDELETE FROM t WHERE 1 = 1eCOMMIT", "SELECT 1e5", "SELECT 0x1F", "SELECT 1.eKILL 9999", "SELECT 1.EDBCC FREEPROCCACHE", "SELECT 1.eUSE master", "SELECT BEGIN TRAN; SELECT 1.ECOMMIT", "SELECT 12.eDELETE FROM t WHERE 1 = 1.ECOMMIT", "SELECT 1.",
          "SELECT 1DELETE FROM t WHERE 1 = 1COMMIT", "SELECT 1 COMMIT", "SELECT 1 KILL 55", "SELECT 1 WAITFOR DELAY '00:00:01'", "SELECT 1; FETCH NEXT FROM c",
          "SELECT 1\r:r x", "SELECT 1\x85:r x", "SELECT 1\u2028:r x", "SELECT 1\u2029:r x", "SELECT 1 EXECUTE dbo.p",
          "WITH c AS (SELECT 1 a) SELECT * FROM c", "(SELECT 1)", "VALUES (1)", "TRUNCATE TABLE t",
          "SELECT 1 FROM t WITH (UPDLOCK)", "SELECT NEXT VALUE FOR s"]
READS = ["SELECT 1", "select a, b from dbo.t where c = 'x; DELETE' -- DROP\n", "SELECT N'EXEC', [into], [set] FROM [dbo].[t] /* GO */ WHERE a IN ('it''s');", f.CHECK_SQL, "SELECT 1 UNION ALL SELECT 2", "SELECT a FROM t WHERE a IN (SELECT 1) UNION SELECT 2", "SELECT CASE WHEN a = 1 THEN 2 ELSE 3 END FROM t",
         "SELECT a FROM t ORDER BY a OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY", "SELECT t1.col2name, 1.5, 007 FROM t1 WHERE x = 10 AND [2024Q1] > 3"]


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


def entry(tmp, sql, results=(), conf=CONF, out=None, mode=0o600, checked=False, flags=(), existing=False, factory=None):
    """Runs main() against a fake cloud; returns (code, stderr, stdout, fake, log, out path)."""
    cfg = Path(tmp, "cfg", "config.json")
    cfg.parent.mkdir(exist_ok=True)
    cfg.write_text(json.dumps(conf))
    cfg.chmod(mode)
    if checked:  # the account and database checks pass first (they run before every read)
        results = [GOOD_CHECK, GOOD_DBS, *results]
    Path(tmp, "q.sql").write_text(sql, encoding="utf-8")
    out = out or str(Path(tmp, "out", "r.txt"))
    Path(out).parent.mkdir(exist_ok=True)
    Path(out).unlink(missing_ok=True)
    if existing:
        Path(out).write_text("old")
    log, fake = [], None
    fake = Fake(results, log)
    f.CONFIG = cfg
    f.register = lambda *a: log.append("register")
    so, se = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(so), contextlib.redirect_stderr(se):
        code = f.main(["--target", "t1", "--session", "s", "--purpose", "p", "--delete-at", "d", "--out", out, *flags, str(Path(tmp, "q.sql"))], cloud_factory=factory or (lambda conf: fake))
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
        real_load, config_reads = f.load_config, []
        f.load_config = lambda: config_reads.append(1) or real_load()
        for sql in WRITES:
            code, err, out, fake, log, _ = entry(tmp, sql)
            assert code == 2 and log == [] and config_reads == [] and "refused" in err and "config" not in err, (sql, code, log, err)
        f.load_config = real_load
        # a SELECT file runs: registered before the first cloud call, sanitized text sent, first run checks the account then runs, only counts are printed
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t -- why\n", [GOOD_CHECK, GOOD_DBS, ROW_DATA])
        assert code == 0 and log[0] == "register"
        assert fake.sent == [f.CHECK_SQL, f.CHECK_DBS_SQL, "SELECT a FROM t"], fake.sent
        assert "PERSON-X" not in out + err and Path(path).read_text() == ROW_DATA and oct(os.stat(path).st_mode & 0o777) == "0o600", "result goes to the file only"
        assert log.count("cleanup") == 3, "work dir removed after each server run"
        # every run checks the account first: a second run, and a config edited after a good run (another account) is refused
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [ROW_DATA], checked=True)
        assert code == 0 and fake.sent == [f.CHECK_SQL, f.CHECK_DBS_SQL, "SELECT a FROM t"], fake.sent
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [ROW_DATA], checked=True, conf={**CONF, "account": "admin"})
        assert code == 2 and fake.sent == [f.CHECK_SQL] and not Path(path).exists(), "another account is not accepted on the strength of an earlier run"
        # the account check stops the run: an extra permission, a wrong login, a wrong database, an error text
        for why, bad in BAD_CHECKS.items():
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [bad, GOOD_DBS, ROW_DATA])
            assert code == 2 and "account check failed" in err and fake.sent == [f.CHECK_SQL] and not Path(path).exists() and log[-1] == "cleanup", why
        # the login must reach exactly the configured databases (plus the platform guest ones): one more, one fewer, none
        for bad in (GOOD_DBS + "  extra\n", GOOD_DBS.replace("  db1\n", ""), "  # name\n  (0 rows)\n", "  # name\n  db1\n  master\nSQLERROR 1222 x\n", GOOD_DBS.rsplit("  (", 1)[0], GOOD_DBS + "SQLERROR 1222 x\n"):
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [GOOD_CHECK, bad, ROW_DATA])
            assert code == 2 and fake.sent == [f.CHECK_SQL, f.CHECK_DBS_SQL] and not Path(path).exists(), bad
        # a result with personal-data shapes or long cells is withheld: nothing written, only kinds and counts named (--definitions allows long definition text, not personal shapes)
        for rows, flags, why in (("  # a\n  11010519491231002X\n", [], "id number"), ("  # a\n  13812345678\n", [], "mobile"), ("  # a\n  x@y.com\n", [], "email"), ("  # a\n  138 1234 5678\n", [], "mobile"), ("  # a\n  +8613812345678\n", [], "mobile"), ("  # a\n  110105491231002\n", [], "id number"), ("  # a\n  " + "x" * 301 + "\n", [], "300"),
                                 ("  # a\n  " + "x" * 2001 + "\n", ["--definitions"], "2000"), ("  # a\n  11010519491231002X\n", ["--definitions"], "id number")):
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [rows], checked=True, flags=flags)
            assert code == 2 and "withheld" in err and why in err and not Path(path).exists() and "1101051949" not in err + out, (why, err)
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", ["  # a\n  " + "x" * 2000 + "\n"], checked=True, flags=["--definitions"])
        assert code == 0 and Path(path).exists(), "definition text up to 2000 characters passes with --definitions"
        # too large a reply, --out inside the repo or existing, a config readable by others, an unknown target: refused, nothing leaks
        code, err, *_ = entry(tmp, "SELECT a FROM t", ["TOOBIG"], checked=True)
        assert code == 2 and "too large" in err
        code, err, out, fake, log, _ = entry(tmp, "SELECT 1", out=str(f.REPO / "docs" / "r.txt"))
        assert code == 2 and log == [] and "outside" in err
        code, err, out, fake, log, path = entry(tmp, "SELECT 1", existing=True)
        assert code == 2 and log == [] and "already exists" in err and Path(path).read_text() == "old", (code, err)
        other = Path(tmp, "other")
        Path(other, ".git").mkdir(parents=True)
        code, err, out, fake, log, _ = entry(tmp, "SELECT 1", out=str(other / "r.txt"))
        assert code == 2 and log == [] and "outside" in err, "another repository's work tree is refused too"
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", ["connected as acct db=db1 mode=ro\n" + ROW_DATA], checked=True)
        assert code == 0 and Path(path).read_text() == ROW_DATA, "the connected-as line stays out of the file"
        code, err, out, fake, log, _ = entry(tmp, "SELECT 1", mode=0o644)
        assert code == 2 and log == [] and "group or others" in err
        code, err, out, fake, log, _ = entry(tmp, "SELECT 1", conf={**CONF, "targets": {"other": {"database": "d"}}})
        assert code == 2 and "unknown target" in err and "register" not in log, (code, err, log)
    # a cloud call that times out, fails or answers rubbish, or any unexpected error: only a fixed message, none of the configured values or the command line
    secrets_conf = {**CONF, "aliyun_profile": "SECRETPROFILE", "region": "SECRETREGION", "app_host": "SECRETHOST"}
    real_run = subprocess.run
    def boom(kind):
        def run(cmd, *a, **k):
            if kind == "timeout":
                raise subprocess.TimeoutExpired(cmd, 1)
            if kind == "missing":
                raise FileNotFoundError(cmd[0])
            return subprocess.CompletedProcess(cmd, 1 if kind == "fail" else 0, stdout="not json" if kind == "rubbish" else "", stderr="SECRETHOST " + " ".join(cmd))
        return run
    with tempfile.TemporaryDirectory() as tmp:
        for kind in ("timeout", "missing", "fail", "rubbish"):
            subprocess.run = boom(kind)
            try:
                code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", conf=secrets_conf, checked=True, factory=f.Cloud)
            finally:
                subprocess.run = real_run
            assert code == 2 and "refused" in err and not Path(path).exists() and not re.search("SECRET|aliyun|RunCommand", err + out), (kind, err)
        def broken(conf):
            raise RuntimeError("SECRETHOST")
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", conf=secrets_conf, checked=True, factory=broken)
        assert code == 2 and "RuntimeError" in err and "SECRET" not in err + out, err
    # the entry's source holds no production fingerprint (the gate would refuse the command otherwise, and identifiers belong in the local config)
    table = json.loads(Path(f.REPO, "scripts", "gates", "prod_targets.json").read_text())["targets"]
    folded = re.sub(r"[^0-9a-z]", "", (HERE / "forensic_read.py").read_text().lower() + (HERE / "forensic_read_selftest.py").read_text().lower())
    assert not [p for t in table for p in t["patterns"] if re.sub(r"[^0-9a-z]", "", p.lower()) in folded], "fingerprint in the entry source"
    print("forensic_read self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] != ["--self-test"]:
        sys.exit(__doc__)
    main()
