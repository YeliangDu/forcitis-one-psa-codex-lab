#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：命令连到生产目标并带写操作就拒绝，只读取证照常放行（GOV-1022-QUA-24，CLAUDE.md 底线 4）。

生产目标由 prod_targets.json（one-ops 维护，不含密钥）里的 patterns 认定：命令的词里（引号、变量赋值拼接后）出现任一 pattern 即算碰到生产。
只判断这些远程工具：psql／mysql／sqlcmd／mongosh／redis-cli（SQL 与命令是否只读）、aliyun（API 名 Describe／List／Get／Query／Check／Search 开头为读，其余为写）、
ssh（远程命令须全在只读白名单内，无远程命令即交互会话按写）、scp／rsync／sftp（目的端碰到生产为写，从生产拉取为读）、curl（-X POST／PUT／DELETE／PATCH、-d、-F、-T 为写）。
其他命令（git commit 信息、gh 评论、编辑文档里写着实例号）不管。`bash -c`、`eval`、`$(…)` 里的命令递归检查。
目标含读不出的变量（`$HOST`、命令替换）且命令是写操作时，无法确认不在生产，拒绝。
特征表缺失或不合格：写操作类命令（不论目标）拒绝并说明，只读命令放行并告警，不放开。命令读不懂（引号未闭合）且含远程工具名时拒绝。
退出码 2 + 标准错误 = 拒绝；0 = 放行。出错一律拒绝（崩溃的退出码 1 对 PreToolUse 钩子是「不拦截」）。
用法：prod_write_gate.py  |  prod_write_gate.py --self-test
"""
import json
import os
import re
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from command_words import name_of, plain, simple_commands  # noqa: E402

TABLE = os.environ.get("PROD_TARGETS_FILE") or str(Path(__file__).resolve().parent / "prod_targets.json")
SQL_TOOLS = {"psql", "mysql", "sqlcmd", "mongosh", "mongo", "redis-cli", "mariadb", "usql"}
REMOTE_TOOLS = SQL_TOOLS | {"aliyun", "ssh", "scp", "rsync", "sftp", "curl"}
SQL_STATEMENT_OPTIONS = {"-c", "--command", "-e", "--execute", "-q", "-Q", "--eval"}
SQL_READ = re.compile(r"^\s*(?:select|show|explain|describe|desc|with|values|table|\\d|\\l|pragma|get|keys|scan|info|ping|dbsize|ttl|type|exists)\b", re.I)
SQL_WRITE = re.compile(r"\b(?:insert|update|delete|drop|alter|create|truncate|grant|revoke|merge|copy|call|exec|execute|vacuum|reindex|replace|rename|set|flushall|flushdb|del|expire|config|shutdown|into)\b", re.I)
ALIYUN_READ = re.compile(r"^(?:describe|list|get|query|check|search|preview|verify|tag-?list)", re.I)
SSH_VALUE_OPTIONS = set("bcDEeFIiJLlmOopQRSWw")
SSH_READ = {"ls", "cat", "tail", "head", "grep", "egrep", "fgrep", "df", "du", "ps", "stat", "wc", "uptime", "hostname", "whoami", "id", "date", "free", "ss", "netstat", "journalctl", "echo", "pwd",
            "uname", "sort", "uniq", "cut", "awk", "top", "lsblk", "file", "readlink", "which", "test", "true", "find", "systemctl", "docker", "pg_dump"}
SSH_READ_SUBCOMMANDS = {"systemctl": {"status", "is-active", "is-enabled", "list-units", "list-unit-files", "show", "cat"}, "docker": {"ps", "logs", "inspect", "stats", "images", "top"}}
FIND_WRITE = {"-delete", "-exec", "-execdir", "-ok", "-fprint", "-fprintf", "-fls"}
CURL_WRITE = re.compile(r"^-(?:d|F|T)$|^--(?:data|data-\w+|form|form-string|upload-file|json)$|^-X$|^--request$")
REMOTE_HOST = re.compile(r"^(?:[\w.-]+@)?[\w.-]+:")


def load_table(path=None):
    """(lower-cased patterns, problem or None)."""
    path = path or TABLE
    try:
        with open(path, encoding="utf-8") as stream:
            data = json.load(stream)
        targets = data["targets"]
        if not isinstance(targets, list):
            raise ValueError("targets 必须是数组")
        patterns = []
        for target in targets:
            found = target["patterns"]
            if not (isinstance(found, list) and found and all(isinstance(p, str) and len(p.strip()) >= 6 for p in found)):
                raise ValueError(f"{target.get('id', '?')} 的 patterns 必须是非空字符串数组，每项至少 6 个字符（太短会误伤）")
            patterns += [p.strip().lower() for p in found]
        return patterns, None
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        return None, f"生产特征表 {os.path.basename(path)} 缺失或不合格（{error.__class__.__name__}: {error}）"


def touches(argv, env, patterns):
    text = " ".join(argv + list(env.values())).lower()
    return any(p in text for p in patterns)


def sql_statements(argv):
    """The statement texts given on the command line, or None when the content is not visible (a file, standard input, an interactive session)."""
    found = []
    for i, token in enumerate(argv):
        option, _, inline = token.partition("=")
        if option in SQL_STATEMENT_OPTIONS and (inline or i + 1 < len(argv)):
            found.append(inline or argv[i + 1])
        elif token.startswith("-c") and len(token) > 2 and not token.startswith("--"):
            found.append(token[2:])
    if name_of(argv[0]) in ("redis-cli",) and not found:
        rest = [t for t in argv[1:] if not t.startswith("-")]
        found = [" ".join(rest[1:])] if len(rest) > 1 else []
    if any(t in ("-f", "--file", "-i", "--input-file", "<", "<<", "<<<") or t.startswith("--file=") for t in argv) or not found:
        return None
    return found


def is_write(command, patterns_hit):
    """True when this remote-tool command writes (or its effect cannot be seen)."""
    argv = command.argv
    tool = name_of(argv[0])
    if tool in SQL_TOOLS:
        statements = sql_statements(argv)
        return statements is None or any(not SQL_READ.match(s) or SQL_WRITE.search(re.sub(r"'[^']*'", "''", s)) for s in statements)
    if tool == "aliyun":
        words = [t for t in argv[1:] if not t.startswith("-")]
        return not (len(words) >= 2 and ALIYUN_READ.match(words[1]))
    if tool == "curl":
        for i, token in enumerate(argv):
            if CURL_WRITE.match(token):
                if token in ("-X", "--request"):
                    if i + 1 < len(argv) and argv[i + 1].upper() in ("GET", "HEAD", "OPTIONS"):
                        continue
                return True
        return False
    if tool in ("scp", "rsync"):
        operands = [t for t in argv[1:] if not t.startswith("-")]
        return bool(operands) and operands[-1] != "" and (REMOTE_HOST.match(operands[-1]) is not None or patterns_hit(operands[-1:]))
    if tool == "sftp":
        return True
    if tool == "ssh":
        return ssh_writes(argv, patterns_hit)
    return False


def ssh_remote_command(argv):
    i, host_seen = 1, False
    while i < len(argv):
        token = argv[i]
        if not host_seen:
            if token.startswith("-") and len(token) > 1:
                i += 2 if len(token) == 2 and token[1] in SSH_VALUE_OPTIONS else 1
                continue
            host_seen = True
            i += 1
            continue
        return argv[i:]
    return None


def ssh_writes(argv, patterns_hit):
    remote = ssh_remote_command(argv)
    if not remote:
        return True  # an interactive session cannot be told from a write
    text = " ".join(remote)
    try:
        commands = simple_commands(text)
    except ValueError:
        return True
    for command in commands:
        if command.redirected or command.unresolved or any(t in (">", ">>") for t in command.argv):
            return True
        tool = name_of(command.argv[0])
        if tool in SQL_TOOLS:
            if is_write(command, patterns_hit):
                return True
            continue
        if tool not in SSH_READ:
            return True
        if tool in SSH_READ_SUBCOMMANDS:
            sub = next((t for t in command.argv[1:] if not t.startswith("-")), None)
            if sub not in SSH_READ_SUBCOMMANDS[tool]:
                return True
        if tool == "find" and any(t in FIND_WRITE for t in command.argv):
            return True
        if tool == "awk" and any("system(" in t or "print >" in t for t in command.argv):
            return True
    return False


def judge(command_text, patterns, problem=None):
    """Refusal text or None."""
    try:
        commands = simple_commands(command_text)
    except ValueError:
        if any(t in plain(command_text) for t in REMOTE_TOOLS):
            return "命令读不懂（引号未闭合等）且含 psql／ssh／aliyun 等远程工具，无法确认不是连生产的写操作，拒绝。"
        return None
    warning = None
    for command in commands:
        tool = name_of(command.argv[0])
        if tool not in REMOTE_TOOLS:
            continue
        hit = lambda argv: bool(patterns) and touches(argv, {}, patterns)  # noqa: E731
        writes = is_write(command, hit)
        if patterns is None:
            if writes:
                return f"{problem}：写操作类命令（{tool}）无法判断是否连生产，一律拒绝；修好 scripts/hooks/prod_targets.json 后再做。"
            continue
        touched = touches(command.argv, command.env, patterns)
        if writes and touched:
            return (f"拒绝：{tool} 命令连到生产目标并带写操作（或写效果看不出来）。CLAUDE.md 底线 4：不做生产写操作；生产取证只用专用只读账号且只读（ADR-0037）。"
                    "确需写入的事项属须用户决定的事（底线 5）：写收件箱等答复，不在命令里绕。")
        if writes and command.unresolved and patterns:
            return f"拒绝：{tool} 的写操作含读不出的变量或命令替换，无法确认目标不是生产；把目标写成字面值再执行。"
    return warning


def main():
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        print("prod_write_gate.py 读不懂钩子输入（不是 JSON），按失败处理", file=sys.stderr)
        return 2
    if not isinstance(payload, dict):
        print("prod_write_gate.py 读不懂钩子输入（不是对象），按失败处理", file=sys.stderr)
        return 2
    if payload.get("tool_name", "Bash") != "Bash":
        return 0
    tool_input = payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    if not isinstance(command, str):
        return 0
    patterns, problem = load_table()
    reason = judge(command, patterns, problem)
    if reason:
        print(reason, file=sys.stderr)
        return 2
    if problem and any(t in plain(command) for t in REMOTE_TOOLS):
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": f"告警：{problem}，写操作类命令会被拒绝。"}}, ensure_ascii=False))
    return 0


def guarded_main():
    try:
        return main()
    except Exception as error:
        print(f"prod_write_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 2


def self_test():
    patterns = ["pgm-prod0001", "rm-prodhost1", "db.prod.example.com"]
    refused = lambda command, table=patterns, problem=None: judge(command, table, problem) is not None  # noqa: E731
    allowed = lambda command, table=patterns, problem=None: judge(command, table, problem) is None  # noqa: E731
    P = "pgm-prod0001"

    # reads pass
    for command in (f'psql -h {P}.pg.rds.aliyuncs.com -U ro -c "select count(*) from t"', f'psql -h {P} -c "SELECT 1"', f'mysql -h {P} -e "show tables"', f'psql -h {P} -c "explain select 1"',
                    "aliyun rds DescribeDBInstances --InstanceId pgm-prod0001", "aliyun ecs DescribeInstances --InstanceIds rm-prodhost1", f'ssh ops@{P} "tail -n 50 /var/log/app.log"',
                    f"ssh {P} 'df -h && uptime'", f"ssh -i key -p 22 {P} 'systemctl status app'", f"ssh {P} docker ps", f"scp ops@{P}:/var/log/a.log ./a.log", f"rsync -a ops@{P}:/data/ ./data/",
                    f"curl https://{P}/health", f"curl -X GET https://db.prod.example.com/x",
                    "git commit -m 'pgm-prod0001 续费说明'", "gh pr comment 3 --body 'psql -h pgm-prod0001 update x'", "echo pgm-prod0001 >> notes.md", "grep pgm-prod0001 docs/x.md"):
        assert allowed(command), f"read must pass: {command}"
    # writes to production are refused
    for command in (f'psql -h {P} -c "insert into t values (1)"', f'psql -h {P} -c "UPDATE t SET a=1"', f'psql -h {P} -c "delete from t"', f'psql -h {P} -c "drop table t"', f'psql -h {P} -c "ALTER TABLE t ADD c int"',
                    f'psql -h {P} -c "select 1; delete from t"', f'psql -h {P} -c "select * from t for update"', f'psql -h {P} -c "truncate t"', f"psql -h {P} -f fix.sql", f"psql -h {P} < fix.sql",
                    f"psql -h {P}", f'mysql -h {P} -e "grant all on *.* to x"', f"mysql -h {P} < x.sql", f'redis-cli -h {P} flushall', f'redis-cli -h {P} set a 1',
                    "aliyun rds DeleteAccount --DBInstanceId pgm-prod0001", "aliyun rds ModifyDBInstanceSpec --DBInstanceId pgm-prod0001", "aliyun ecs StopInstance --InstanceId rm-prodhost1", "aliyun --profile pgm-prod0001 ecs RunCommand",
                    f'ssh {P} "rm -rf /data/x"', f"ssh {P} 'echo x > /etc/app.conf'", f"ssh {P} 'sed -i s/a/b/ /etc/app.conf'", f"ssh {P} 'systemctl restart app'", f"ssh {P}", f"ssh {P} 'tee /etc/x'",
                    f"ssh {P} 'find / -delete'", f"ssh {P} 'cat /etc/x | sh'", f"ssh {P} 'psql -c \"delete from t\"'", f"scp ./fix.sql ops@{P}:/tmp/", f"rsync -a ./dist/ ops@{P}:/srv/", f"sftp {P}",
                    f"curl -X POST https://{P}/api", f"curl -d x=1 https://db.prod.example.com/api", f"curl -X DELETE https://db.prod.example.com/x", f"curl -T f.bin https://db.prod.example.com/up"):
        assert refused(command), f"write must be refused: {command}"
    # writes elsewhere pass
    for command in ('psql -h localhost -c "insert into t values (1)"', "psql -h pgm-dev00001 -f x.sql", "aliyun rds DeleteAccount --DBInstanceId pgm-dev00001", "ssh dev 'rm -rf /tmp/x'", "scp a dev:/tmp/", "curl -X POST https://dev/x"):
        assert allowed(command), f"non-production write must pass: {command}"
    # bypasses: quotes, variable concatenation, wrappers, nesting
    for command in (f"p=pgm-prod; q=0001; psql -h $p$q -c 'delete from t'", f"HOST=pgm-prod0001 psql -h $HOST -c 'delete from t'", f"export H={P}; psql -h \"${{H}}\" -c 'drop table t'",
                    f"psql -h 'pgm-'prod0001 -c 'delete from t'", f"psql -h pgm-\"prod0001\" -c 'delete from t'", f"env PGHOST={P} psql -c 'delete from t'", f"nohup timeout 9 psql -h {P} -c 'delete from t'",
                    f"bash -c \"psql -h {P} -c 'delete from t'\"", f"echo $(psql -h {P} -c 'delete from t')", f"eval \"psql -h {P} -c 'delete from t'\"", f"PSQL=psql; $PSQL -h {P} -c 'delete from t'",
                    f"/usr/bin/PSQL -h {P} -c 'delete from t'", f"cd /tmp && true; ssh {P} 'rm x'", f"(ssh {P} 'rm x')"):
        assert refused(command), f"bypass must be refused: {command}"
    assert refused('psql -h "$PGHOST" -c "delete from t"'), "unresolved target + write is refused"
    assert refused("psql -h $(cat host.txt) -c 'delete from t'") and refused("aliyun rds DeleteAccount --DBInstanceId $ID")
    assert allowed('psql -h "$PGHOST" -c "select 1"') and allowed("aliyun rds DescribeDBInstances --InstanceId $ID"), "unresolved target + read passes"
    assert refused("psql -h 'pgm-prod0001 -c 'delete'"), "unreadable command with a remote tool is refused"
    assert allowed("echo 'unterminated"), "unreadable command with no remote tool is not this gate's business"
    # table missing or broken: writes are refused, reads pass, nothing is silently opened
    assert refused('psql -h localhost -c "insert into t values (1)"', None, "生产特征表缺失") and allowed('psql -h x -c "select 1"', None, "生产特征表缺失")
    assert refused("aliyun rds DeleteAccount --DBInstanceId x", None, "生产特征表缺失") and allowed("ls -la", None, "生产特征表缺失")
    assert allowed("psql -h pgm-prod0001 -c 'delete from t'", []), "an empty table (no production yet) is valid and inert"
    with tempfile.TemporaryDirectory() as tmp:
        good = os.path.join(tmp, "t.json")
        for text, ok in ((json.dumps({"targets": [{"id": "a", "patterns": ["abcdef123"]}]}), True), ('{"targets": []}', True), ("not json", False), ("{}", False), ('{"targets": [{"id": "a"}]}', False),
                         ('{"targets": [{"id": "a", "patterns": []}]}', False), ('{"targets": [{"id": "a", "patterns": ["ab"]}]}', False), ('{"targets": [{"id": "a", "patterns": [1]}]}', False), ('{"targets": "x"}', False)):
            open(good, "w").write(text)
            assert (load_table(good)[1] is None) == ok, text
        assert load_table(os.path.join(tmp, "missing.json"))[1] is not None
        table, problem = load_table()
        assert problem is None and table, "the shipped table loads and names the legacy production RDS"
        # hook process end to end, with the shipped and a missing table
        import subprocess
        script = os.path.abspath(__file__)

        def hook(command, table_path=None, tool="Bash"):
            env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", "")}
            if table_path:
                env["PROD_TARGETS_FILE"] = table_path
            return subprocess.run([sys.executable, "-I", script], input=json.dumps({"tool_name": tool, "tool_input": {"command": command}}), capture_output=True, text=True, env=env)

        result = hook("aliyun rds DeleteAccount --DBInstanceId rm-bp1690ep53t9778dp")
        assert result.returncode == 2 and "底线 4" in result.stderr, result
        assert hook("aliyun rds DescribeDBInstances --DBInstanceId rm-bp1690ep53t9778dp").returncode == 0
        assert hook("ls").returncode == 0 and hook("x", tool="Read").returncode == 0
        gone = hook("psql -c 'delete from t'", os.path.join(tmp, "missing.json"))
        assert gone.returncode == 2 and "缺失或不合格" in gone.stderr, gone
        warned = hook("psql -c 'select 1'", os.path.join(tmp, "missing.json"))
        assert warned.returncode == 0 and "告警" in warned.stdout, warned
        for raw in ("not json", "[1]"):
            bad = subprocess.run([sys.executable, "-I", script], input=raw, capture_output=True, text=True, env={"LC_ALL": "C"})
            assert bad.returncode == 2, raw
    print("prod_write_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else guarded_main())
