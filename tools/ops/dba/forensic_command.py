#!/usr/bin/env python3
"""Renders the ONE pre-created Cloud Assistant command of the forensic entry (card #1389) and prints the steps the user performs.

  forensic_command.py render --out FILE   writes the command content (no secrets: paths, account name, a public key, the client source) for CreateCommand / ModifyCommand
  forensic_command.py policy FILE         checks a filled copy of docs/ops/forensic-ram-policy.json
  forensic_command.py steps               prints the user's steps and the local config field names (never values)

The command takes one parameter, `req`. The template puts it after an `exit` line, so the shell never parses it; forensic_server.py reads it from the script file as data.
"""
import base64
import hashlib
import json
import lzma
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SERVER = HERE / "forensic_server.py"
ONE_SQL = HERE / "OneSql.java"
CONTENT_B64_MAX = 12000  # CreateCommand allows 24 KB of Base64 content and the invoke parameters share that budget: keep the command small, the client source is installed on the host instead
MAX_SQL = 4000  # bytes of query text one request may carry (it travels as a parameter inside the same budget)
COMMAND_TIMEOUT = 600
CONFIG_FIELDS = ("aliyun_profile", "region", "command_id", "app_host", "secret_host", "app_work_dir", "app_jar_process", "app_client_file", "app_key_file", "app_public_key_file", "secret_file",
                 "account", "targets")

HEADER = """#!/bin/bash
# FORCITIS forensic read-only command, rev {rev}. Rendered by tools/ops/dba/forensic_command.py from the repository; do not edit here.
python3 -I -c 'import lzma,base64,sys;exec(lzma.decompress(base64.b64decode(sys.argv[1])))' "{body}" "$0"
exit $?
#@@DATA@@
{{{{req}}}}
"""


def render(conf, gate_free_sqls, one_sql_text=None, pub_text=None):
    """(command content, rev). conf: the local config (paths and names only). The rev is a hash of everything baked in, so the entry can tell a stale command."""
    one_sql = (one_sql_text if one_sql_text is not None else ONE_SQL.read_text(encoding="utf-8")).encode()
    pub = pub_text if pub_text is not None else Path(conf["app_public_key_file"]).expanduser().read_text(encoding="utf-8")
    baked = {"work": conf["app_work_dir"], "secret_file": conf["secret_file"], "jar": conf["app_jar_process"], "client": conf["app_client_file"], "account": conf["account"], "key": conf["app_key_file"],
             "dbs": sorted({t["database"] for t in conf["targets"].values()}), "pub": pub, "onesql_sha": hashlib.sha256(one_sql).hexdigest(),
             "gate_free": sorted(hashlib.sha256(s.encode()).hexdigest() for s in gate_free_sqls), "max_sql": MAX_SQL, "timeout": COMMAND_TIMEOUT - 120}
    source = SERVER.read_text(encoding="utf-8")
    source = source.replace("@@CONF@@", base64.b64encode(json.dumps(baked, sort_keys=True).encode()).decode())
    rev = hashlib.sha256((HEADER + source.replace("@@REV@@", "")).encode()).hexdigest()[:16]  # the template text is part of the revision: it carries the request-after-exit property
    source = source.replace("@@REV@@", rev)
    body = base64.b64encode(lzma.compress(source.encode(), preset=9 | lzma.PRESET_EXTREME)).decode()
    content = HEADER.format(rev=rev, body=body)
    if content.count("{{") != 1 or content.count("}}") != 1:
        raise ValueError("the command content must hold exactly the one request placeholder")
    if len(base64.b64encode(content.encode())) > CONTENT_B64_MAX:
        raise ValueError("the command content is too large for the Cloud Assistant budget")
    return content, rev


ALLOWED_ACTIONS = {"ecs:InvokeCommand", "ecs:DescribeInvocations", "ecs:DescribeInvocationResults"}
REQUIRED_DENIES = {"ecs:RunCommand", "ecs:SendFile", "ecs:CreateCommand", "ecs:ModifyCommand", "ecs:DeleteCommand", "ecs:StartTerminalSession"}
RESOURCE_SHAPE = re.compile(r"acs:ecs:(?:\*|[0-9A-Za-z-]+):(?:\*|[0-9]+):(command|instance)/(<[A-Z_]+>|[ci]-[0-9A-Za-z]+)")


def load_policy(text):  # a repeated key in one object would let the last one win silently: refuse it
    def pairs(items):
        keys = [k for k, _ in items]
        if len(keys) != len(set(keys)):
            raise ValueError("duplicate key in the policy")
        return dict(items)
    return json.loads(text, object_pairs_hook=pairs)


def policy_problems(policy):
    """Reasons the forensic sub-user's policy is wider than card #1389 allows (empty list: fine). Works on the repository template and on a copy with the ids filled in."""
    bad = []
    statements = policy.get("Statement") if isinstance(policy, dict) else None
    if not isinstance(statements, list) or not statements:
        return ["no statements"]
    commands, instances, denied = [], [], set()
    for st in statements:
        if not isinstance(st, dict) or {"NotAction", "NotResource", "NotPrincipal"} & set(st):
            bad.append("a statement uses Not*")
            continue
        actions = st.get("Action") if isinstance(st.get("Action"), list) else [st.get("Action")]
        resources = st.get("Resource") if isinstance(st.get("Resource"), list) else [st.get("Resource")]
        if st.get("Effect") == "Deny":
            if resources != ["*"] or "Condition" in st:
                bad.append("a deny statement must apply to Resource * without a condition, or it is not a deny")
            denied |= set(actions)
        elif st.get("Effect") == "Allow":
            extra = set(actions) - ALLOWED_ACTIONS
            if extra or "ecs:InvokeCommand" not in actions:
                bad.append("allowed actions outside the three (or no InvokeCommand): " + ", ".join(sorted(map(str, extra))))
            for r in resources:
                m = RESOURCE_SHAPE.fullmatch(r) if isinstance(r, str) else None
                if not m:
                    bad.append("allowed resource that is not one command or one instance: " + str(r))
                else:
                    if not m.group(2).startswith(("<", m.group(1)[0] + "-")):
                        bad.append("resource id prefix does not match its kind: " + r)
                    (commands if m.group(1) == "command" else instances).append(r)
            if "Condition" in st:
                bad.append("a condition on an allow statement is not reviewed here")
        else:
            bad.append("an effect other than Allow and Deny")
    if len(commands) != 1:
        bad.append("exactly one command resource is required")
    if len(instances) != 2 or len(set(instances)) != 2:
        bad.append("exactly two distinct instance resources are required (the app host and the secret host)")
    if REQUIRED_DENIES - denied:
        bad.append("explicit deny missing for: " + ", ".join(sorted(REQUIRED_DENIES - denied)))
    return bad


STEPS = """Steps you perform yourself (nothing here is automatic; no value below is printed by this tool):
 1. On the app host, once: create the key pair of the entry (the private key stays there, mode 0600, readable by root only):
      openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out <app_key_file> && chmod 600 <app_key_file> && openssl pkey -in <app_key_file> -pubout -out <public key file>
    Copy the public key file to this computer (it is public) and name it in the local config as app_public_key_file.
 1b. Copy tools/ops/dba/OneSql.java from this repository to the app host at <app_client_file> (owner root, mode 0644, not writable by the application user). Do this again
    whenever that file changes in the repository; the command refuses to run a client whose hash differs from the revision it was rendered for.
 2. Fill the local config (~/.config/forcitis-forensic/config.json, mode 0600) with these fields: {fields}. Never commit it.
 3. Render the command content:  python3 tools/ops/dba/forensic_command.py render --out <file outside the repository>
 4. With your administrator profile (not the forensic sub-user), create the command from that file: type RunShellScript, Base64 content, EnableParameter true, timeout {timeout} s,
    KeepCommand yes. Put the returned command id into the local config as command_id. After a new repository revision (this tool's output changes), update the same command
    (ModifyCommand); the entry refuses a command whose revision differs from the repository's.
 5. Create the forensic RAM sub-user; attach the policy docs/ops/forensic-ram-policy.json with the command id and the two instance ids filled in (check your copy: python3 tools/ops/dba/forensic_command.py policy <file>); create its AccessKey and
    store it in an aliyun CLI profile named in the config as aliyun_profile. The sub-user holds no other permission.
 6. First run: use the entry with a trivial SELECT. Check by hand once, with that profile: (a) a call of RunCommand, SendFile or StartTerminalSession is refused;
    (b) DescribeInvocationResults for an invocation of any other command is refused. The first run also confirms what this repository could not: that the agent keeps
    the script file readable at $0 and that a request of the largest size fits the invoke budget.
"""


def main(argv):
    if argv[:1] == ["steps"]:
        sys.stdout.write(STEPS.format(fields=", ".join(CONFIG_FIELDS), timeout=COMMAND_TIMEOUT))
        return 0
    if len(argv) == 3 and argv[0] == "render" and argv[1] == "--out":
        sys.path.insert(0, str(HERE))
        import forensic_read as entry
        conf = entry.load_config()
        content, rev = render(conf, [entry.CHECK_SQL, entry.CHECK_DBS_SQL])
        Path(argv[2]).write_text(content, encoding="utf-8")
        sys.stdout.write(f"rev {rev}, {len(content)} characters written\n")
        return 0
    if len(argv) == 2 and argv[0] == "policy":
        problems = policy_problems(load_policy(Path(argv[1]).read_text(encoding="utf-8")))
        sys.stdout.write("\n".join(problems) + "\n" if problems else "policy ok\n")
        return 1 if problems else 0
    sys.stderr.write(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
