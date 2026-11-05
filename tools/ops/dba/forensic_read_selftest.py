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

def CONF_WITH_PUB(tmp):
    pub = Path(tmp, "pub2.pem")
    pub.write_text(PUB)
    return {**CONF, "app_public_key_file": str(pub)}


CONF = {"aliyun_profile": "p", "region": "r", "command_id": "c-test", "app_host": "app", "secret_host": "sec", "app_work_dir": "/w/app", "app_jar_process": "x.jar",
        "app_client_file": "/w/OneSql.java", "app_key_file": "/w/k.key", "app_public_key_file": "/nonexistent-set-by-entry", "secret_file": "/w/s.env", "account": "acct",
        "targets": {"t1": {"database": "db1"}}}
PUB = "-----BEGIN PUBLIC KEY-----\n" + "A" * 64 + "\n-----END PUBLIC KEY-----\n"
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
    "object grant": check_text(BASE_ROWS + [("dbperm", "G:1", "SELECT")]),
    "object deny of another permission": check_text(BASE_ROWS + [("dbperm", "D:1", "UPDATE")]),
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
ROW_DATA = "  # a | b\n  PERSON-X | 12345\n  (1 rows)\nDONE batches=1\n"
WRITES = ["DELETE FROM t", "update t set a = 1", "INSERT INTO t VALUES (1)", "DROP TABLE t", "EXEC sp_who", "SELECT * INTO x FROM t", "SELECT 1; SELECT 2", "SELECT 1; DELETE FROM t",
          "SELECT 1\nGO\nDELETE FROM t", "SELECT 1 /* x */ ; DROP TABLE t", "WITH c AS (SELECT 1 a) DELETE FROM c", "SELECT 'a'' --'; DELETE FROM t", "SELECT * FROM OPENROWSET('x','y','z')",
          "SELECT 1 FROM a.b.c.d", "SELECT 1 FROM t FOR UPDATE; COMMIT", "SELECT 1 /* /* */ */", "SELECT 'open", "DECLARE @a int SELECT 1", "", "-- only a comment", "SELECT 1\x00",
          "SELECT x FROM t WHERE y = (SELECT 1); EXECUTE('SELECT 1')",
          'SELECT 1 AS "a\n:"\'\n\'" DELETE FROM t COMMIT --\'"\n', "SELECT 1\n:r x\n", "SELECT 1\n  go  \nDELETE FROM t", "SELECT 'x\nGO\n' AS a", "SELECT $(v)", 'SELECT 1 AS "a"',
          "SELECT 1 SELECT 2", "SELECT 1 DISABLE TRIGGER tr ON dbo.t", "SELECT 1 RECEIVE TOP(1) * FROM dbo.q", "SELECT 1 CHECKPOINT", "SELECT 1 FROM srv . db . dbo . t", "SELECT 1 EXEC dbo.p", "SELECT 1DELETE FROM t", "SELECT 1COMMIT", "SELECT 1KILL 55", "SELECT 1DBCC FREEPROCCACHE", "SELECT 0x1DELETE FROM t", "SELECT 1e1DELETE FROM t", "SELECT 0xKILL 55", "SELECT 0xABKILL 55", "SELECT 1eCOMMIT", "SELECT 1eDELETE FROM t WHERE 1 = 1eCOMMIT", "SELECT 1e5", "SELECT 0x1F", "SELECT a FROM dbo.t [{limit 1}DELETE FROM dbo.t COMMIT --]", "SELECT 1 KILX{limit 1}L 9999", "SELECT {fn NOW()}", "SELECT 1 AS [a']  'x{limit 1}DELETE FROM t COMMIT --'", "SELECT 1 AS [a{b]", "SELECT 1 AS [a']\n/**/:'\nDELETE FROM t COMMIT --'", "SELECT 1\n/**/GO\nDELETE FROM t", "SELECT 1 FROM LOOPSRV\u200b.master.sys.databases",
          "SELECT NEXT\u200bVALUE FOR seq", "SELECT 1\u200bDELETE FROM t", "SELECT 1 END CONVERSATION 'x'", "SELECT 1.eKILL 9999", "SELECT 1.EDBCC FREEPROCCACHE", "SELECT 1.eUSE master", "SELECT BEGIN TRAN; SELECT 1.ECOMMIT", "SELECT 12.eDELETE FROM t WHERE 1 = 1.ECOMMIT", "SELECT 1.",
          "SELECT 1DELETE FROM t WHERE 1 = 1COMMIT", "SELECT 1 COMMIT", "SELECT 1 KILL 55", "SELECT 1 WAITFOR DELAY '00:00:01'", "SELECT 1; FETCH NEXT FROM c",
          "SELECT 1\r:r x", "SELECT 1\x85:r x", "SELECT 1\u2028:r x", "SELECT 1\u2029:r x", "SELECT 1 EXECUTE dbo.p",
          "WITH c AS (SELECT 1 a) SELECT * FROM c", "(SELECT 1)", "VALUES (1)", "TRUNCATE TABLE t",
          "SELECT 1 FROM t WITH (UPDLOCK)", "SELECT NEXT VALUE FOR s"]
READS = ["SELECT 1", "select a, b from dbo.t where c = 'x; DELETE' -- DROP\n", "SELECT N'EXEC', [into], [set] FROM [dbo].[t] /* GO */ WHERE a IN ('it''s');", f.CHECK_SQL, "SELECT 1 UNION ALL SELECT 2", "SELECT N'{}', '{' AS a -- {\n/* { */", "SELECT a FROM t WHERE a IN (SELECT 1) UNION SELECT 2", "SELECT CASE WHEN a = 1 THEN 2 ELSE 3 END FROM t",
         "SELECT a FROM t ORDER BY a OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY", "SELECT N'名称', [名称], t1.col2name, 1.5, 007 FROM t1 WHERE x = 10 AND [2024Q1] > 3"]


class Fake:
    """Stands for the one pre-created cloud command: answers the two requests (encrypt on the secret host, query on the app host) the way forensic_server.py prints them."""

    def __init__(self, results, log, conf=None):
        self.results, self.log, self.sent, self.requests = list(results), log, [], []
        try:
            self.rev = f.command_rev(conf)
        except (KeyError, TypeError, OSError):  # a config the entry will refuse anyway
            self.rev = "REVX"

    def invoke(self, instance, request, timeout=600):
        self.log.append("cloud")
        self.requests.append((instance, request))
        if request["op"] == "encrypt":
            assert instance == "sec", "the credential is encrypted on the secret host"
            return "Success", f"REV:{self.rev}\nEVIDENCE:QUJD\n"
        assert request["op"] == "query" and instance == "app" and set(request) == {"op", "db", "cipher", "sql"} and request["cipher"] == "QUJD", request
        self.sent.append(base64.b64decode(request["sql"]).decode())
        res = self.results.pop(0)
        return "Success", f"REV:{self.rev}\n" + (res if res == "TOOBIG" else base64.b64encode(gzip.compress(res.encode())).decode() + "\n")


def entry(tmp, sql, results=(), conf=CONF, out=None, mode=0o600, checked=False, flags=(), existing=False, factory=None, raw=False):
    """Runs main() against a fake cloud; returns (code, stderr, stdout, fake, log, out path)."""
    cfg = Path(tmp, "cfg", "config.json")
    cfg.parent.mkdir(exist_ok=True)
    pub = Path(tmp, "pub.pem")
    pub.write_text(PUB)
    conf = {**conf, "app_public_key_file": str(pub)} if "app_public_key_file" in conf else conf
    cfg.write_text(json.dumps(conf))
    cfg.chmod(mode)
    if not raw:  # a real run ends its output with the DONE line; raw=True leaves results as given
        results = [r if r == "TOOBIG" or "DONE" in r or "SQLERROR" in r else r + ("" if "rows)" in r or not checked else "  (%d rows)\n" % (len([l for l in r.splitlines() if l.startswith("  ")]) - 1)) + "DONE batches=1\n" for r in results]
    if checked:  # the account and database checks pass first (they run before every read)
        results = [GOOD_CHECK, GOOD_DBS, *results]
    Path(tmp, "q.sql").write_text(sql, encoding="utf-8")
    out = out or str(Path(tmp, "out", "r.txt"))
    Path(out).parent.mkdir(exist_ok=True)
    Path(out).unlink(missing_ok=True)
    if existing:
        Path(out).write_text("old")
    log, fake = [], None
    fake = Fake(results, log, {**conf, "app_public_key_file": str(pub)})
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
        assert log.count("cloud") == 2 * 3, "per run: one encrypt and one query call"
        # every run checks the account first: a second run, and a config edited after a good run (another account) is refused
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [ROW_DATA], checked=True)
        assert code == 0 and fake.sent == [f.CHECK_SQL, f.CHECK_DBS_SQL, "SELECT a FROM t"], fake.sent
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [ROW_DATA], checked=True, conf={**CONF, "account": "admin"})
        assert code == 2 and fake.sent == [f.CHECK_SQL] and not Path(path).exists(), "another account is not accepted on the strength of an earlier run"
        # the account check stops the run: an extra permission, a wrong login, a wrong database, an error text
        for why, bad in BAD_CHECKS.items():
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [bad, GOOD_DBS, ROW_DATA])
            assert code == 2 and "account check failed" in err and fake.sent == [f.CHECK_SQL] and not Path(path).exists() , why
        # object-level DENY SELECT (D:1) only narrows the account: it passes the account check; the same rows with a grant instead are the "object grant" case above
        deny_rows = BASE_ROWS + [("dbperm", "D:1", "SELECT"), ("dbperm", "D:1", "SELECT")]
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [check_text(deny_rows), GOOD_DBS, ROW_DATA])
        assert code == 0 and Path(path).exists(), (code, err)
        # the login must reach exactly the configured databases (plus the platform guest ones): one more, one fewer, none
        for bad in (GOOD_DBS + "  extra\n", GOOD_DBS.replace("  db1\n", ""), "  # name\n  (0 rows)\n", "  # name\n  db1\n  master\nSQLERROR 1222 x\n", GOOD_DBS.rsplit("  (", 1)[0], GOOD_DBS + "SQLERROR 1222 x\n"):
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [GOOD_CHECK, bad, ROW_DATA])
            assert code == 2 and fake.sent == [f.CHECK_SQL, f.CHECK_DBS_SQL] and not Path(path).exists(), bad
        # a result with personal-data shapes or long cells is withheld: nothing written, only kinds and counts named (--definitions allows long definition text, not personal shapes)
        for rows, flags, why in (("  # a\n  11010519491231002X\n", [], "id number"), ("  # a\n  13812345678\n", [], "mobile"), ("  # a\n  x@y.com\n", [], "email"), ("  # a\n  tel:\\u{0009}13800138000\n", [], "mobile"), ("  # a\n  id\\u{0001}11010519491231002X\n", [], "id number"), ("  # a\n  138 1234 5678\n", [], "mobile"), ("  # a\n  +8613812345678\n", [], "mobile"), ("  # a\n  110105491231002\n", [], "id number"), ("  # a\n  " + "x" * 301 + "\n", [], "300"),
                                 ("  # a\n  " + "x" * 2001 + "\n", ["--definitions"], "2000"), ("  # a\n  11010519491231002X\n", ["--definitions"], "id number")):
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [rows], checked=True, flags=flags)
            assert code == 2 and "withheld" in err and why in err and not Path(path).exists() and "1101051949" not in err + out, (why, err)
        code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", ["  # a\n  " + "x" * 2000 + "\n"], checked=True, flags=["--definitions"])
        assert code == 0 and Path(path).exists(), "definition text up to 2000 characters passes with --definitions"
        # the server refused or failed the user's query (plan gate refusal, SQL error, Java exception) or the run did not finish: nonzero exit, nothing written, no "ok"
        for bad in ("SQLERROR 156 near x\nDONE batches=0\n", "connected as a\nSQLERROR 50000 plan gate: not a SELECT\n", "Exception in thread \"main\" java.lang.IllegalStateException: x\n", "  # a\n  1\n  (1 rows)\n", "", "  # a\n  x\nSQLERROR 1 y\nDONE batches=1\n", "  # a\n  x\nException in thread \"main\" java.lang.Error\nDONE batches=1\n"):
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [bad], checked=True, raw=True)
            assert code == 2 and "refused or failed" in err and "ok:" not in out and not Path(path).exists(), bad
        # a line break inside a cell would hide text from the cell-length check: refused by shape (continuation with or without leading spaces, a faked trailer or header)
        for bad in ("  # a\n  " + "\n".join(["x" * 250] * 20) + "\n  (1 rows)\nDONE batches=1\n", "  # a\n  " + "\n  ".join(["x" * 250] * 20) + "\n  (1 rows)\nDONE batches=1\n",
                    "  # a\n  x\n  (1 rows)\n  (1 rows)\nDONE batches=1\n", "  # a\n  x\n  # b\n  (1 rows)\nDONE batches=1\n"):
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", [bad], checked=True, raw=True)
            assert code == 2 and "expected shape" in err and "ok:" not in out and not Path(path).exists(), bad[:40]
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
    # card #1389: the entry reaches production only through the one pre-created command. Every aliyun call is InvokeCommand with that command id or a result read for it;
    # never RunCommand, command creation, file send or a terminal session. Missing command id or profile fails closed before anything is registered or sent.
    with tempfile.TemporaryDirectory() as tmp:
        for field in ("command_id", "aliyun_profile", "app_public_key_file", "app_client_file", "app_key_file"):
            code, err, out, fake, log, _ = entry(tmp, "SELECT 1", conf={k: v for k, v in CONF.items() if k != field})
            assert code == 2 and "lacks a required field" in err and not log, (field, err, log)
        calls, answered = [], {"id": "sec"}
        def recorder(cmd, *a, **k):
            calls.append(cmd)
            action = cmd[1:3]
            if action == ["ecs", "InvokeCommand"]:
                return subprocess.CompletedProcess(cmd, 0, stdout=json.dumps({"InvokeId": "t-1"}), stderr="")
            out = base64.b64encode(f"REV:{f.command_rev(CONF_WITH_PUB(tmp))}\nEVIDENCE:QUJD\n".encode()).decode()
            return subprocess.CompletedProcess(cmd, 0, stdout=json.dumps({"Invocation": {"InvocationResults": {"InvocationResult": [{"InstanceId": answered["id"], "InvocationStatus": "Success", "Output": out}]}}}), stderr="")
        subprocess.run, real_sleep = recorder, f.time.sleep
        f.time.sleep = lambda s: None
        try:
            cloud = f.Cloud(CONF)
            status, text = cloud.invoke("sec", {"op": "encrypt"})
        finally:
            subprocess.run, f.time.sleep = real_run, real_sleep
        assert status == "Success" and "EVIDENCE:QUJD" in text
        invoke = next(c for c in calls if c[2] == "InvokeCommand")
        assert invoke[invoke.index("--CommandId") + 1] == "c-test" and invoke[invoke.index("--InstanceId.1") + 1] == "sec", invoke
        parameters = json.loads(invoke[invoke.index("--Parameters") + 1])
        assert set(parameters) == {"req"} and re.fullmatch(r"[A-Za-z0-9_-]+", parameters["req"]), "the single request parameter is one base64url line"
        assert json.loads(base64.urlsafe_b64decode(parameters["req"] + "==")) == {"op": "encrypt"}
        read = next(c for c in calls if c[2] == "DescribeInvocationResults")
        assert read[read.index("--CommandId") + 1] == "c-test", "results are read for the command only"
        assert "--InstanceId" not in read, "the result query carries no InstanceId filter"
        # the returned target is checked: a result of another host is refused, a result without a target too
        subprocess.run, f.time.sleep = recorder, (lambda s: None)
        try:
            for returned in ("app", None):
                answered["id"] = returned
                try:
                    f.Cloud(CONF).invoke("sec", {"op": "encrypt"})
                    raise AssertionError("a result of another target was accepted")
                except f.Refused as error:
                    assert "not for the host" in str(error), str(error)
        finally:
            subprocess.run, f.time.sleep = real_run, real_sleep
            answered["id"] = "sec"
        assert all(c[:3] in (["aliyun", "ecs", "InvokeCommand"], ["aliyun", "ecs", "DescribeInvocationResults"]) for c in calls), calls
        assert not re.search(r"RunCommand|CreateCommand|ModifyCommand|SendFile|Session", (HERE / "forensic_read.py").read_text().replace("no RunCommand, no command creation", "")), "the entry names no other cloud action"
        # a request longer than one parameter can carry is refused before any call
        calls.clear()
        subprocess.run = recorder
        try:
            try:
                f.Cloud(CONF).invoke("app", {"op": "query", "db": "d", "cipher": "x", "sql": "A" * 9000})
                raise AssertionError("long request accepted")
            except f.Refused as error:
                assert "too long" in str(error) and not calls
        finally:
            subprocess.run = real_run
        # a refusal from the command, a command of another revision, a command that did not run: fixed messages, nothing written
        for text, why in (("REV:abc\nREFUSED:database not allowed\n", "refused the request (database not allowed)"), ("REV:stale\nEVIDENCE:QUJD\n", "not the repository revision"), ("", "not the repository revision")):
            class One(Fake):
                def invoke(self, instance, request, timeout=600):
                    return "Success", text
            code, err, out, fake, log, path = entry(tmp, "SELECT a FROM t", checked=True, factory=lambda conf: One([], [], None))
            assert code == 2 and why in err and not Path(path).exists(), (text, err)
        # the answer check itself, with the real computed revision: a good answer passes, a failed invocation of the right command is refused, a stale one is refused
        rev = f.command_rev(CONF_WITH_PUB(tmp))
        assert f.answer("Success", f"REV:{rev}\nEVIDENCE:QUJD\n", rev).startswith("REV:")
        for status, text, why in (("Failed", f"REV:{rev}\n", "did not succeed (Failed)"), ("Success", f"REV:{rev}x\n", "not the repository revision"), ("Success", f"REV:{rev}\nREFUSED:x\n", "refused the request (x)")):
            try:
                f.answer(status, text, rev)
                raise AssertionError("answer accepted: " + why)
            except f.Refused as error:
                assert why in str(error), str(error)
    # the entry's source holds no production fingerprint (the gate would refuse the command otherwise, and identifiers belong in the local config)
    table = json.loads(Path(f.REPO, "scripts", "gates", "prod_targets.json").read_text())["targets"]
    folded = re.sub(r"[^0-9a-z]", "", "".join((HERE / n).read_text().lower() for n in ("forensic_read.py", "forensic_read_selftest.py", "forensic_command.py", "forensic_command_selftest.py", "forensic_server.py")))
    assert not [p for t in table for p in t["patterns"] if re.sub(r"[^0-9a-z]", "", p.lower()) in folded], "fingerprint in the entry source"
    print("forensic_read self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] != ["--self-test"]:
        sys.exit(__doc__)
    main()
