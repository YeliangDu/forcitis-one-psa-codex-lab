#!/usr/bin/env python3
"""Self-test of the pre-created forensic cloud command (card #1389): the rendered script is run for real with hostile request values, the server half is exercised with a
stub java, and the sub-user policy is checked. No network, no production. `forensic_command_selftest.py --self-test`."""
import base64
import copy
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import forensic_command as c  # noqa: E402
import forensic_read as f  # noqa: E402


def blob(request):
    return base64.urlsafe_b64encode(json.dumps(request).encode()).decode().rstrip("=")


def keypair(tmp):
    key, pub = Path(tmp, "k.key"), Path(tmp, "pub.pem")
    subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(key)], check=True, capture_output=True)
    subprocess.run(["openssl", "pkey", "-in", str(key), "-pubout", "-out", str(pub)], check=True, capture_output=True)
    return key, pub


def main():
    with tempfile.TemporaryDirectory() as tmp:
        key, pub = keypair(tmp)
        client = Path(tmp, "OneSql.java")
        client.write_text(f.ONE_SQL.read_text(encoding="utf-8"), encoding="utf-8")
        secret = Path(tmp, "s.env")
        secret.write_text("# c\nOTHER=1\nEVIDENCE_DB_PASSWORD='p@ss w0rd'\n")
        work = Path(tmp, "fw", "run")
        conf = {"app_work_dir": str(work), "secret_file": str(secret), "app_jar_process": "app.jar", "app_client_file": str(client), "app_key_file": str(key),
                "account": "acct", "targets": {"a": {"database": "db1"}, "b": {"database": "db2"}}}
        content, rev = c.render(conf, [f.CHECK_SQL, f.CHECK_DBS_SQL], pub_text=pub.read_text())
        # budget and placeholder shape
        assert content.count("{{") == 1 and content.count("}}") == 1 and "{{req}}" in content and content.index("exit $?") < content.index("#@@DATA@@") < content.index("{{req}}")
        assert len(base64.b64encode(content.encode())) <= c.CONTENT_B64_MAX and len(base64.b64encode(content.encode())) + len(base64.b64encode(b"A" * 9000)) < 24 * 1024
        assert c.render(conf, [f.CHECK_SQL, f.CHECK_DBS_SQL], pub_text=pub.read_text())[1] == rev, "the same inputs render the same revision"
        assert c.render({**conf, "account": "other"}, [f.CHECK_SQL], pub_text=pub.read_text())[1] != rev, "a changed baked value changes the revision"
        assert c.render(conf, [f.CHECK_SQL, f.CHECK_DBS_SQL], pub_text=pub.read_text(), one_sql_text="changed")[1] != rev, "a changed client source changes the revision"
        canary = Path(tmp, "CANARY")

        def run(tail, script=content):
            path = Path(tmp, "script.sh")
            path.write_text(script.replace("{{req}}", tail), encoding="utf-8")
            done = subprocess.run(["bash", str(path)], capture_output=True, text=True, cwd=tmp, timeout=60)
            return done.returncode, done.stdout + done.stderr

        # (a) no parameter value turns the command into anything else: hostile values behind the template's exit line; nothing runs, the answer is a refusal
        good = blob({"op": "ping"})
        hostile = ["\ntouch CANARY\n", "$(touch CANARY)", "`touch CANARY`", "';touch CANARY;'", "x\nexit\ntouch CANARY", "PYEOF\ntouch CANARY", "#@@DATA@@\n" + good + "\ntouch CANARY",
                   good + "\ntouch CANARY", good + "\r\ntouch CANARY", "touch CANARY", "a" * 20000, "", "{{req}}", "名称\ntouch CANARY", good + "; touch CANARY", "\n\n\n", "= = =",
                   base64.b64encode(b'{"op":"ping"}').decode() + "$(touch CANARY)", "A" * 8999 + "\ntouch CANARY"]
        for tail in hostile:
            code, out = run(tail)
            assert not canary.exists(), ("executed", tail[:30])
            assert code == 3 and "REFUSED:" in out, (tail[:30], code, out[:100])
        # the test sees a broken design: with the placeholder before the exit line the shell would parse the value, and the same hostile text creates the canary
        broken = content.replace("exit $?\n#@@DATA@@\n{{req}}\n", "{{req}}\nexit $?\n#@@DATA@@\n")
        assert broken != content
        run("\ntouch CANARY\n", broken)
        assert canary.exists(), "the hostile-value test does not detect an injectable template"
        canary.unlink()
        code, out = run(good + "\n")
        assert code == 0 and f"REV:{rev}" in out.splitlines(), out
        # request shapes the server half refuses: unknown op, extra key, non-string value, database outside the baked list, bad cipher, empty/NUL/too long query
        q = {"op": "query", "db": "db1", "cipher": "QUJD", "sql": base64.b64encode(b"SELECT 1").decode()}
        for bad, why in (({"op": "run"}, "unknown shape"), ({"op": "ping", "x": "y"}, "unknown shape"), ({"op": "encrypt", "x": "y"}, "unknown shape"), ({"op": "ping"} | {"op": 5}, "unknown shape"),
                         ({**q, "db": "master"}, "database not allowed"), ({**q, "cipher": "AB CD"}, "credential shape"), ({**q, "sql": base64.b64encode(b"").decode()}, "empty"),
                         ({**q, "sql": base64.b64encode(b"SELECT 1\x00").decode()}, "NUL"), ({**q, "sql": base64.b64encode(b"S" * (c.MAX_SQL + 1)).decode()}, "too long"),
                         ({**q, "sql": "!!!"}, "does not decode"), ([1], "unknown shape")):
            code, out = run(blob(bad) + "\n")
            assert code == 3 and "REFUSED:" in out and why in out and not work.exists(), (why, out[:120])
        # client source: missing, or not the revision the command was rendered for
        client.write_text("tampered")
        code, out = run(blob(q) + "\n")
        assert code == 3 and "does not match" in out, out
        client.write_text(f.ONE_SQL.read_text(encoding="utf-8"), encoding="utf-8")
        # encrypt: the credential comes back encrypted to the baked public key and opens with the private key only
        code, out = run(blob({"op": "encrypt"}) + "\n")
        cipher = re.search(r"^EVIDENCE:([A-Za-z0-9+/=]+)$", out, re.M)
        assert code == 0 and cipher and "p@ss" not in out, out
        plain = subprocess.run(["openssl", "pkeyutl", "-decrypt", "-inkey", str(key), "-pkeyopt", "rsa_padding_mode:oaep", "-pkeyopt", "rsa_oaep_md:sha256", "-pkeyopt", "rsa_mgf1_md:sha256"],
                               input=base64.b64decode(cipher.group(1)), capture_output=True, check=True).stdout
        assert plain == b"p@ss w0rd"
        # query, in process with a stub java and driver: gate on for the caller's query, off for the two fixed check queries; env and arguments; work dir removed
        namespace = {"__name__": "forensic_server_test"}
        import lzma
        body = re.search(r"b64decode\(sys\.argv\[1\]\)\)\)' \"([A-Za-z0-9+/=]+)\"", content).group(1)
        exec(lzma.decompress(base64.b64decode(body)).decode(), namespace)
        bindir = Path(tmp, "bin")
        bindir.mkdir()
        log = Path(tmp, "java.log")
        stub = bindir / "java"
        stub.write_text('#!/bin/bash\n{ echo "ARGS $*"; echo "GATE=${ONE_SQL_PLAN_GATE-unset} USER=$LOGIN_USER KEY=$LOGIN_KEY"; echo "ENC=$(cat "$LOGIN_SECRET_ENC")"; echo "SQL=$(cat "$6")"; } >> %s\n'
                        'echo "connected as acct db=$5 mode=ro"; echo "  # a"; echo "  1"; echo "  (1 rows)"; echo "DONE batches=1"\n' % log)
        stub.chmod(0o755)
        os.environ["PATH"] = f"{bindir}:{os.environ['PATH']}"
        namespace["jdbc_jar"] = lambda w: str(Path(w, "jdbc.jar"))
        import io, contextlib, gzip
        for sql, gated in (("SELECT a FROM t", True), (f.CHECK_SQL, False), (f.CHECK_DBS_SQL, False), (f.CHECK_SQL + " ", True)):
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                namespace["op_query"]({"op": "query", "db": "db2", "cipher": "QUJD", "sql": base64.b64encode(sql.encode()).decode()})
            packed = [l for l in out.getvalue().splitlines() if len(l) > 20][-1]
            assert "DONE batches=1" in gzip.decompress(base64.b64decode(packed)).decode()
            entry = log.read_text().strip().splitlines()[-4:]
            assert entry[0].endswith(" ro db2 " + str(work / "stage" / "q.sql")), entry[0]
            assert entry[1] == f"GATE={'1' if gated else 'unset'} USER=acct KEY={key}" and entry[2] == "ENC=QUJD", entry
            assert entry[3] == "SQL=" + sql.rstrip(), entry[3]
            assert not work.exists(), "the work directory is removed after a run"
        # the work dir guard: a baked work directory that is the root or one level deep is refused
        namespace["CONF"] = {**namespace["CONF"], "work": "/tmp"}
        try:
            namespace["op_query"]({"op": "query", "db": "db1", "cipher": "QUJD", "sql": base64.b64encode(b"SELECT 1").decode()})
            raise AssertionError("shallow work directory accepted")
        except SystemExit:
            pass
    # the sub-user policy: the repository template is fine; each widening is flagged
    policy = json.loads(Path(f.REPO, "docs", "ops", "forensic-ram-policy.json").read_text(encoding="utf-8"))
    assert c.policy_problems(policy) == [], c.policy_problems(policy)
    def widened(edit):
        copy_ = copy.deepcopy(policy)
        edit(copy_)
        return c.policy_problems(copy_)
    for name, edit in {
        "RunCommand allowed": lambda p: p["Statement"][0]["Action"].append("ecs:RunCommand"),
        "any ecs action": lambda p: p["Statement"][0]["Action"].append("ecs:DescribeInstances"),
        "wildcard action": lambda p: p["Statement"][0].update(Action=["ecs:*"]),
        "wildcard resource": lambda p: p["Statement"][0]["Resource"].append("*"),
        "any command": lambda p: p["Statement"][0]["Resource"].append("acs:ecs:*:*:command/*"),
        "any instance": lambda p: p["Statement"][0]["Resource"].append("acs:ecs:*:*:instance/*"),
        "second command": lambda p: p["Statement"][0]["Resource"].append("acs:ecs:*:*:command/<OTHER>"),
        "no instance": lambda p: p["Statement"][0].update(Resource=["acs:ecs:*:*:command/<COMMAND_ID>"]),
        "deny of RunCommand dropped": lambda p: p["Statement"][1]["Action"].remove("ecs:RunCommand"),
        "deny of terminal dropped": lambda p: p["Statement"][1]["Action"].remove("ecs:StartTerminalSession"),
        "deny of SendFile dropped": lambda p: p["Statement"][1]["Action"].remove("ecs:SendFile"),
        "NotAction": lambda p: p["Statement"][0].update(NotAction=["ecs:RunCommand"]),
        "extra allow": lambda p: p["Statement"].append({"Effect": "Allow", "Action": ["ecs:SendFile"], "Resource": ["acs:ecs:*:*:instance/<X>"]}),
        "condition": lambda p: p["Statement"][0].update(Condition={"IpAddress": {}}),
        "empty": lambda p: p.update(Statement=[]),
    }.items():
        assert widened(edit), f"policy check missed: {name}"
    print("forensic_command self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] != ["--self-test"]:
        sys.exit(__doc__)
    main()
