#!/usr/bin/env python3
"""PreToolUse 钩子（Bash）：命令碰到生产目标，除非是点名的只读用法，一律拒绝（GOV-1022-QUA-24，CLAUDE.md 底线 4；#653 opus 首轮后改为默认拒绝）。

生产目标由 prod_targets.json（one-ops 维护，不含密钥）里的 patterns 认定：命令的词里（引号、ANSI-C `$'\\x..'`、续行、变量赋值拼接、反引号与 `$(…)` 展开后）、命令的环境变量里出现任一 pattern 即「碰到生产」。
特征表读本工作区那份，并与 origin/main 上那份取并集（会话在自己分支里删表项不生效；本地缺项时告警）。
判断，对每条简单命令（`bash -c`／`bash -lc`／`eval`／heredoc／here-string／`echo … | bash`／`env -S` 里的命令递归展开）：
- 已知远程工具（psql／mysql／sqlcmd／osql／bcp／tsql／mongosh／redis-cli／usql／aliyun／ssh／scp／rsync／sftp／curl／wget／httpie）：碰到生产且带写操作（或写效果看不出来）拒绝，只读照常放行。
  SQL 只认白名单：语句须以 select／with／show／explain／describe／values／table 或会话设置开头，不含写关键词、有副作用的函数、psql 写类元命令；-i／-f 文件与 `< 文件` 读进来按同样规则判，看不见的（heredoc、管道、交互）按写。
  aliyun 的 API 名以 Describe／List／Get／Query／Check／Search 等开头为读，其余为写；DescribeDBInstanceSSL、GetSecretValue 等返回密钥的接口一律拒绝（底线 3）。
  ssh 须带远程命令且全在只读白名单内；scp／rsync 目的端是远程为写；curl／wget／httpie 有非 GET 方法、数据或上传选项为写。
- 不连接的命令（git、gh、echo、printf、grep、rg、cat、sed、head、tail、ls、jq、写文档的 new_intent.py 等）碰到生产特征放行：它们只是把实例号当文字。
- 其他命令（python、xargs、find -exec、bcp 以外的工具、script、caffeinate、alias、gradle 等）碰到生产特征一律拒绝；解释器与 shell 的脚本文件读进来，含特征即视为碰到；shell／解释器的标准输入来自 heredoc、管道且全文含特征，也拒绝。
特征表缺失或不合格：写操作类远程命令（不论目标）拒绝并说明，只读放行并告警，不放开。命令读不懂（引号未闭合）且含远程工具名或特征时拒绝。
目标位置读不出（`$HOST`、命令替换）的写操作拒绝；别处出现 `$`（单引号里的 `$` 不算）不影响。
间接引用（IP 与别名、ssh_config、ODBC DSN、脚本里内嵌实例号再被别的程序读）不全覆盖，靠只读账号兜底。
退出码 2 + 标准错误 = 拒绝；0 = 放行。出错一律拒绝（崩溃的退出码 1 对 PreToolUse 钩子是「不拦截」）。
用法：prod_write_gate.py  |  prod_write_gate.py --self-test
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from command_words import SHELLS, name_of, plain, simple_commands  # noqa: E402

TABLE = os.environ.get("PROD_TARGETS_FILE") or str(Path(HERE) / "prod_targets.json")
SQL_TOOLS = {"psql", "mysql", "mariadb", "sqlcmd", "osql", "mssql-cli", "mongosh", "mongo", "redis-cli", "usql", "tsql", "isql"}
WRITE_ONLY_TOOLS = {"sqlpackage", "sftp"}
HTTP_TOOLS = {"curl", "wget", "http", "https", "xh", "xhs"}
REMOTE_TOOLS = SQL_TOOLS | WRITE_ONLY_TOOLS | HTTP_TOOLS | {"bcp", "aliyun", "ssh", "scp", "rsync"}
REMOTE_WORD = re.compile(r"(?<![A-Za-z0-9_])(?:" + "|".join(sorted(re.escape(t) for t in REMOTE_TOOLS)) + r"|bash|sh|zsh|eval|source|python[0-9.]*|node|perl|ruby|php|xargs|find|script|parallel)(?![A-Za-z0-9_-])")
INTERPRETER = re.compile(r"^(?:python[0-9.]*|node|perl|ruby|php|lua|bash|sh|zsh|dash|ksh|source|\.)$")
SHORT_C = re.compile(r"-[A-Za-z]*c[A-Za-z]*")
NO_CONNECT = {"git", "gh", "echo", "printf", "grep", "egrep", "fgrep", "rg", "cat", "sed", "head", "tail", "ls", "jq", "wc", "sort", "uniq", "cut", "tr", "diff", "test", "[", "true", "false", "cd", "pwd", "export",
              "set", "unset", "read", "basename", "dirname", "mkdir", "touch", "cp", "mv", "stat", "file", "date", "tee", ":", "alias_never", "less", "more", "nl", "tac", "fold", "column", "comm", "join", "paste", "expr"}
DOC_SCRIPTS = {"new_intent.py", "new_term_issue.py", "new_decision.py"}
ENV_TARGET_VARS = ("PGHOST", "PGSERVICE", "PGDATABASE", "PGUSER", "PGHOSTADDR", "SQLCMDSERVER", "SQLCMDDBNAME", "SQLCMDUSER", "MYSQL_HOST", "DATABASE_URL", "MONGODB_URI", "REDIS_URL", "ALIBABA_CLOUD_PROFILE")
MAX_SQL_FILE = 65536
MAX_SCRIPT_FILE = 262144

STATEMENT_OPTIONS = {"psql": {"-c", "--command"}, "mysql": {"-e", "--execute"}, "mariadb": {"-e", "--execute"}, "sqlcmd": {"-Q", "-q"}, "osql": {"-Q", "-q"}, "mssql-cli": {"-Q", "--query"},
                     "mongosh": {"--eval"}, "mongo": {"--eval"}, "usql": {"-c", "--command"}}
FILE_OPTIONS = {"psql": {"-f", "--file"}, "sqlcmd": {"-i"}, "osql": {"-i"}, "usql": {"-f", "--file"}, "mysql": set(), "mariadb": set()}
DATA_OPTIONS = {"-c", "--command", "-Q", "-q", "-e", "--execute", "--eval", "--query", "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode", "--data-ascii", "-F", "--form", "--form-string", "-H", "--header",
                "-u", "--user", "-A", "--user-agent", "-b", "--cookie", "-o", "--output", "-w", "-T", "--upload-file", "--json", "-X", "--request", "-U", "-P", "-p", "--password", "--post-data", "--body-data"}
HOST_OPTIONS = {"-h", "--host", "-S", "--server", "--url", "-s_host"}

SQL_READ = re.compile(r"^(?:select|with|show|explain|describe|desc|values|table|go|"
                      r"set\s+(?:nocount|ansi_\w+|quoted_identifier|arithabort|textsize|lock_timeout|language|dateformat|transaction\s+isolation\s+level\s+read\s+(?:uncommitted|committed))\b|"
                      r"db\.\w+\.(?:find|count|countdocuments|estimateddocumentcount|aggregate|distinct)\b|db\.(?:stats|getcollectionnames)\b|"
                      r"ping|dbsize|info|keys|scan|ttl|type|exists|get|mget|strlen|hget|hgetall|hkeys|hlen|lrange|llen|smembers|scard|zrange|zcard)\b", re.I)
SQL_WRITE = re.compile(r"\b(?:insert|update|delete|drop|alter|create|truncate|grant|revoke|merge|copy|call|exec|execute|vacuum|reindex|replace|rename|set|flushall|flushdb|del|expire|config|shutdown|into|do|source|load|"
                       r"dbcc|kill|backup|restore|deny|reconfigure|checkpoint|disable|enable|bulk|openrowset|opendatasource|openquery|sp_\w+|xp_\w+|use|lock|unlock|analyze|cluster|refresh|reset|listen|notify|prepare|deallocate|"
                       r"begin|commit|rollback|savepoint|release|declare|waitfor|"
                       r"pg_(?:terminate_backend|cancel_backend|reload_conf|rotate_logfile|switch_wal|create_\w+|drop_\w+|advisory_\w+|promote|start_backup|stop_backup|replication_\w+|sleep\w*|read_file|ls_dir|file_write)|"
                       r"set_config|nextval|setval|lo_\w+|dblink\w*|for\s+(?:update|share|no\s+key\s+update|key\s+share))\b", re.I)
MONGO_WRITE = re.compile(r"\.(?:delete\w*|update\w*|insert\w*|replace\w*|drop\w*|remove\w*|save|bulkwrite|findandmodify|findoneand\w+|createindex\w*|createcollection|renamecollection|runcommand|eval|mapreduce)\b|\$(?:out|merge)\b", re.I)
META_READ = re.compile(r"^(?:d\w*|l\+?|z|conninfo|timing|x|pset|echo|encoding)$")
REDIS_READ = {"get", "mget", "keys", "scan", "info", "ping", "dbsize", "ttl", "pttl", "type", "exists", "hget", "hgetall", "hkeys", "hlen", "hmget", "lrange", "llen", "smembers", "scard", "sismember", "zrange", "zcard", "strlen", "memory", "client", "slowlog", "sscan", "hscan", "zscan"}
REDIS_VALUE_OPTIONS = {"-h", "-p", "-a", "-n", "-u", "-s", "--user", "--pass", "--askpass", "--sni", "--cacert", "--cert", "--key", "-r", "-i", "-x", "-d", "-D", "--bigkeys"}
ALIYUN_READ = re.compile(r"^(?:describe|list|get|query|check|search|preview|verify|tag-?list)", re.I)
ALIYUN_SECRET = re.compile(r"^(?:describedbinstancessl|getsecretvalue|describedbinstancetde|getparametersbypath|getcredential\w*|createaccesskey|describeaccountsecret\w*)$", re.I)
ALIYUN_VALUE_OPTIONS = {"--profile", "-p", "--region", "--mode", "--endpoint", "--access-key-id", "--access-key-secret", "--sts-token", "--ram-role-name", "--ram-role-arn", "--role-session-name", "--language", "--config-path",
                        "--retry-count", "--read-timeout", "--connect-timeout", "--version", "--header", "--body", "--body-file", "--output", "--waiter", "--pager", "--method", "--secure", "--insecure"}
SSH_VALUE_OPTIONS = set("bcDEeFIiJLlmOopQRSWw")
SSH_READ = {"ls", "cat", "tail", "head", "grep", "egrep", "fgrep", "df", "du", "ps", "stat", "wc", "uptime", "hostname", "whoami", "id", "date", "free", "ss", "netstat", "journalctl", "echo", "pwd",
            "uname", "sort", "uniq", "cut", "top", "lsblk", "file", "readlink", "which", "test", "true", "find", "systemctl", "docker", "pg_dump"}
SSH_READ_SUBCOMMANDS = {"systemctl": {"status", "is-active", "is-enabled", "list-units", "list-unit-files", "show", "cat"}, "docker": {"ps", "logs", "inspect", "stats", "images", "top"}}
FIND_WRITE = {"-delete", "-exec", "-execdir", "-ok", "-okdir", "-fprint", "-fprint0", "-fprintf", "-fls"}
CURL_LONG_WRITE = {"data", "data-ascii", "data-binary", "data-raw", "data-urlencode", "form", "form-string", "upload-file", "json", "config", "next", "post301", "post302", "post303", "variable"}
CURL_VALUE_SHORT = set("oumAbcCeEHhKkPQrtwxyYzZ")
READ_METHODS = {"GET", "HEAD", "OPTIONS"}
SCP_VALUE_SHORT = set("PiFcloJSDX")
RSYNC_VALUE_SHORT = set("eBfTM@")
RSYNC_VALUE_LONG = {"--rsh", "--exclude", "--include", "--filter", "--exclude-from", "--include-from", "--files-from", "--rsync-path", "--port", "--bwlimit", "--timeout", "--block-size", "--compare-dest", "--copy-dest",
                    "--link-dest", "--log-file", "--backup-dir", "--suffix", "--chmod", "--chown", "--usermap", "--groupmap", "--partial-dir", "--temp-dir", "--max-size", "--min-size", "--iconv", "--protocol",
                    "--address", "--sockopts", "--out-format", "--password-file", "--remote-option", "--max-delete", "--modify-window", "--contimeout", "--read-batch", "--write-batch", "--only-write-batch"}
REMOTE_HOST = re.compile(r"^(?:[\w.-]+@)?[\w.-]+:")


def read_table_file(path):
    """(lower-cased patterns, problem or None) of one table file."""
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


def main_table_patterns():
    """Patterns of the table on origin/main (None when it cannot be read); the checkout's own copy cannot take entries away."""
    if os.environ.get("PROD_TARGETS_FILE"):
        return None
    try:
        shown = subprocess.run(["git", "-C", HERE, "show", "origin/main:scripts/hooks/prod_targets.json"], capture_output=True, text=True, timeout=10)
    except (OSError, subprocess.SubprocessError):
        return None
    if shown.returncode:
        return None
    with tempfile.TemporaryDirectory() as tmp:
        copy = os.path.join(tmp, "prod_targets.json")
        Path(copy).write_text(shown.stdout, encoding="utf-8")
        patterns, problem = read_table_file(copy)
    return None if problem else patterns


def load_table(path=None):
    """(lower-cased patterns, problem or None, warning or None). The local table is unioned with origin/main's; a local table missing entries of main's, or an empty union, is a warning."""
    patterns, problem = read_table_file(path or TABLE)
    if problem:
        return None, problem, None
    warning = None
    if path is None:
        main = main_table_patterns()
        if main is not None:
            missing = [p for p in main if p not in patterns]
            if missing:
                warning = f"本工作区的 prod_targets.json 缺少 origin/main 上的 {len(missing)} 项特征，门按两者并集判断；请把分支更新到 main。"
            patterns = patterns + missing
    if not patterns:
        warning = "生产特征表为空，门不对任何命令生效；确认这是预期（尚无生产目标）。"
    return patterns, None, warning


def could_concern(text):
    """Pre-filter for the dispatcher (text already `plain`): False only when the gate would certainly allow."""
    if REMOTE_WORD.search(text) or "$" in text or "`" in text:
        return True
    patterns, problem = read_table_file(TABLE)
    return bool(problem) or any(p in text for p in patterns)


def hits(texts, patterns):
    text = " ".join(texts).lower()
    return any(p in text for p in patterns)


def touches(command, patterns, env_hit=False):
    if hits(command.argv + list(command.env.values()), patterns):
        return True
    return env_hit and name_of(command.argv[0]) in SQL_TOOLS | {"aliyun"}


# ---- SQL -----------------------------------------------------------------------------------------------------------------------------------------------------------
def mask_sql(text):
    """Statement text without comments, string literals, quoted and bracketed identifiers (their content is data, not syntax)."""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"--[^\n]*", " ", text)
    text = re.sub(r"\$(\w*)\$.*?\$\1\$", "''", text, flags=re.S)
    text = re.sub(r"'(?:[^']|'')*'", "''", text)
    text = re.sub(r'"(?:[^"]|"")*"', '""', text)
    text = re.sub(r"\[[^\]\n]*\]", "[]", text)
    return re.sub(r"`[^`]*`", "``", text)


def statement_is_read(text, tool):
    masked = mask_sql(text)
    for part in re.split(r";|\n\s*go\s*(?:\n|$)", masked, flags=re.I):
        stmt = part.strip()
        if not stmt:
            continue
        for meta in re.finditer(r"\\([A-Za-z!]\w*\+?)", stmt):
            if not META_READ.match(meta.group(1)):
                return False
        stmt = re.sub(r"^(?:\\\w+\+?\s*)+", "", stmt) if stmt.startswith("\\") else stmt
        if stmt.startswith("\\") or (stmt and not SQL_READ.match(stmt)):
            return False
        if SQL_WRITE.search(stmt) and not re.match(r"^set\s", stmt, re.I):
            return False
        if tool in ("mongosh", "mongo") and MONGO_WRITE.search(stmt):
            return False
    return True


def read_text(path, cwd, limit):
    full = path if os.path.isabs(path) else os.path.join(cwd or os.getcwd(), path)
    try:
        if os.path.isfile(full) and os.path.getsize(full) <= limit:
            return Path(full).read_text(encoding="utf-8", errors="replace")
    except OSError:
        pass
    return None


def sql_sources(argv, tool, cwd):
    """List of statement texts the command will run, or None when some of them cannot be seen (heredoc, here-string, pipe, interactive, unreadable file)."""
    statements, files, i = [], [], 1
    opts, fopts = STATEMENT_OPTIONS.get(tool, set()), FILE_OPTIONS.get(tool, set())
    while i < len(argv):
        token = argv[i]
        option, eq, inline = token.partition("=")
        if token in ("<<", "<<<") or (token.startswith("<<") and len(token) > 2):
            return None
        if token == "<":
            if i + 1 >= len(argv):
                return None
            files.append(argv[i + 1])
            i += 2
            continue
        if option in opts and (inline or i + 1 < len(argv)):
            statements.append(inline if eq else argv[i + 1])
            i += 1 if eq else 2
            continue
        if option in fopts and (inline or i + 1 < len(argv)):
            files.append(inline if eq else argv[i + 1])
            i += 1 if eq else 2
            continue
        if not token.startswith("--") and len(token) > 2 and token[:2] in opts | fopts:  # -cSELECT, -ifix.sql
            (statements if token[:2] in opts else files).append(token[2:])
        elif tool in ("mongosh", "mongo") and token.endswith((".js", ".mongodb")) and not token.startswith("-"):
            files.append(token)
        i += 1
    for path in files:
        content = read_text(path, cwd, MAX_SQL_FILE)
        if content is None:
            return None
        statements.append(content)
    return statements or None


def redis_writes(argv):
    positional, i = [], 1
    while i < len(argv):
        token = argv[i]
        if token.startswith("-") and len(token) > 1:
            i += 2 if token in REDIS_VALUE_OPTIONS else 1
            continue
        positional.append(token)
        i += 1
    return not positional or positional[0].lower() not in REDIS_READ


def sql_writes(command, cwd):
    argv, tool = command.argv, name_of(command.argv[0])
    if tool == "redis-cli":
        return redis_writes(argv)
    if tool == "bcp":
        words = [t.lower() for t in argv[1:]]
        return not (("out" in words or "queryout" in words) and "in" not in words)
    sources = sql_sources(argv, tool, cwd) if not command.piped else None
    return sources is None or not all(statement_is_read(s, tool) for s in sources)


# ---- aliyun, curl, scp/rsync, ssh -----------------------------------------------------------------------------------------------------------------------------------
def aliyun_words(argv):
    """Non-option words of an aliyun command (product, API name, ...) with the values of options skipped."""
    words, i = [], 1
    while i < len(argv):
        token = argv[i]
        if token.startswith("-"):
            i += 1 if "=" in token else 2
            continue
        words.append(token)
        i += 1
    return words


def aliyun_writes(argv):
    words = aliyun_words(argv)
    return not (len(words) == 2 and ALIYUN_READ.match(words[1]))


def aliyun_secret(argv):
    return any(ALIYUN_SECRET.match(w) for w in aliyun_words(argv))


def curl_writes(argv):
    """True for any method other than GET/HEAD/OPTIONS, data or upload options, config files, `--next` (short options may be bundled with their value attached)."""
    method, i = None, 1
    while i < len(argv):
        token = argv[i]
        if token.startswith("--"):
            name, eq, inline = token[2:].partition("=")
            if name in CURL_LONG_WRITE:
                return True
            if name == "request":
                method = (inline if eq else argv[i + 1] if i + 1 < len(argv) else "").upper()
                i += 1 if eq else 2
                continue
            i += 1
            continue
        if token.startswith("-") and len(token) > 1:
            j = 1
            while j < len(token):
                char = token[j]
                if char in "dFT:K":
                    return True
                if char == "X":
                    method = (token[j + 1:] or (argv[i + 1] if i + 1 < len(argv) else "")).upper()
                    i += 0 if token[j + 1:] else 1
                    break
                if char in CURL_VALUE_SHORT:
                    i += 0 if token[j + 1:] else 1
                    break
                j += 1
        i += 1
    return method is not None and method not in READ_METHODS


def wget_writes(argv):
    for i, token in enumerate(argv):
        name, _, inline = token.partition("=")
        if name in ("--post-data", "--post-file", "--body-data", "--body-file"):
            return True
        if name == "--method":
            value = inline or (argv[i + 1] if i + 1 < len(argv) else "")
            if value.upper() not in READ_METHODS:
                return True
    return False


def httpie_writes(argv):
    words = [t for t in argv[1:] if not t.startswith("-")]
    if words and words[0].upper() in ("POST", "PUT", "PATCH", "DELETE"):
        return True
    return any(re.search(r"[^/:]=|:=|@", w) and not w.startswith(("http://", "https://")) for w in words[1:]) or any(t in argv for t in ("--form", "--json", "-f", "-j"))


def operands_of(argv, short_values, long_values):
    operands, i = [], 1
    while i < len(argv):
        token = argv[i]
        if token == "--":
            operands += argv[i + 1:]
            break
        if token.startswith("--"):
            i += 2 if token in long_values else 1
        elif token.startswith("-") and len(token) > 1:
            letters = token[1:]
            consumed = False
            for j, char in enumerate(letters):
                if char in short_values:
                    consumed = j == len(letters) - 1
                    break
            i += 2 if consumed else 1
        else:
            operands.append(token)
            i += 1
    return operands


def copy_writes(argv, tool, patterns):
    if tool == "scp":
        operands = operands_of(argv, SCP_VALUE_SHORT, set())
    else:
        operands = operands_of(argv, RSYNC_VALUE_SHORT, RSYNC_VALUE_LONG)
        if any(t.startswith(("--remove-source-files", "--rsync-path")) for t in argv[1:]):
            return True
    if len(operands) < 2:
        return True
    dest = operands[-1]
    return REMOTE_HOST.match(dest) is not None or (bool(patterns) and hits([dest], patterns))


def ssh_remote_command(argv):
    """(remote command words or None, risky option seen)."""
    i, host_seen, risky = 1, False, False
    while i < len(argv):
        token = argv[i]
        if not host_seen:
            if token.startswith("-") and len(token) > 1:
                letters = token[1:]
                for c in letters:
                    if c in "LRDWw":
                        risky = True
                    if c in SSH_VALUE_OPTIONS:
                        break
                valued = next((j for j, c in enumerate(letters) if c in SSH_VALUE_OPTIONS), None)
                value = letters[valued + 1:] or (argv[i + 1] if i + 1 < len(argv) else "") if valued is not None else ""
                if valued is not None and letters[valued] == "o" and re.search(r"(?i)proxycommand|localcommand|permitlocalcommand|remotecommand", value):
                    risky = True
                i += 2 if valued is not None and not letters[valued + 1:] else 1
                continue
            host_seen = True
            i += 1
            continue
        return argv[i:], risky
    return None, risky


def read_only_remote(command):
    argv, tool = command.argv, name_of(command.argv[0])
    sub = [t for t in argv[1:] if not t.startswith("-")]
    if tool == "find" and any(t in FIND_WRITE for t in argv):
        return False
    if tool == "sort" and any(t == "--output" or t.startswith(("--output=", "-o")) or (t.startswith("-") and not t.startswith("--") and "o" in t[1:]) for t in argv[1:]):
        return False
    if tool == "uniq" and len(sub) > 1:
        return False
    if tool == "journalctl" and any(t.startswith(("--vacuum", "--rotate", "--flush", "--relinquish", "--sync", "--setup-keys", "--update-catalog", "--smart-relinquish")) for t in argv):
        return False
    if tool == "date" and any(t in ("-s", "--set") or t.startswith(("--set=", "-s")) for t in argv[1:]):
        return False
    if tool == "ss" and any(t in ("-K", "--kill") or (t.startswith("-") and not t.startswith("--") and "K" in t) for t in argv[1:]):
        return False
    if tool == "hostname" and sub:
        return False
    if tool == "file" and any(t == "-C" or t == "--compile" for t in argv[1:]):
        return False
    if tool == "pg_dump" and any(t in ("-f", "--file") or t.startswith(("--file=", "-f")) for t in argv[1:]):
        return False
    if tool in SSH_READ_SUBCOMMANDS and (sub[:1] or [None])[0] not in SSH_READ_SUBCOMMANDS[tool]:
        return False
    return True


def ssh_writes(argv, patterns, cwd):
    remote, risky = ssh_remote_command(argv)
    if risky or not remote:
        return True  # an interactive session, a tunnel or a local-command option cannot be told from a write
    try:
        commands = simple_commands(" ".join(remote))
    except ValueError:
        return True
    for command in commands:
        if command.redirected or command.unresolved or any(t in (">", ">>") for t in command.argv):
            return True
        tool = name_of(command.argv[0])
        if tool in SQL_TOOLS:
            if sql_writes(command, cwd):
                return True
            continue
        if tool not in SSH_READ or not read_only_remote(command):
            return True
    return False


def is_write(command, patterns, cwd=None):
    """True when this remote-tool command writes (or its effect cannot be seen)."""
    argv = command.argv
    tool = name_of(argv[0])
    if tool in SQL_TOOLS or tool == "bcp":
        return sql_writes(command, cwd)
    if tool in WRITE_ONLY_TOOLS:
        return True
    if tool == "aliyun":
        return aliyun_writes(argv)
    if tool == "curl":
        return curl_writes(argv)
    if tool == "wget":
        return wget_writes(argv)
    if tool in ("http", "https", "xh", "xhs"):
        return httpie_writes(argv)
    if tool in ("scp", "rsync"):
        return copy_writes(argv, tool, patterns)
    if tool == "ssh":
        return ssh_writes(argv, patterns, cwd)
    return False


def host_has_variable(token):
    """`$` in the host part of a URL (a variable port is fine), or anywhere in a word that is not a URL."""
    if "://" not in token:
        return "$" in token
    authority = token.split("://", 1)[1].split("/", 1)[0].split("?", 1)[0]
    hostport = authority.rpartition("@")[2]
    host = hostport.rsplit(":", 1)[0] if ":" in hostport and not hostport.startswith("[") else hostport
    return "$" in host or "$" in authority.rpartition("@")[0]


def target_unresolved(command):
    """True when a word that names the target (host option value, URL, positional operand, aliyun id) still holds a `$` expansion; data and statement values do not count."""
    argv, tool = command.argv, name_of(command.argv[0])
    skip = False
    for i, token in enumerate(argv[1:], 1):
        if skip:
            skip = False
            continue
        if token.startswith("-"):
            option, eq, inline = token.partition("=")
            if tool == "aliyun" and (option.lower().endswith(("id", "ids", "name")) or option in ("--profile", "-p", "--endpoint")):
                if "$" in (inline if eq else argv[i + 1] if i + 1 < len(argv) else ""):
                    return True
            elif option in HOST_OPTIONS and "$" in (inline if eq else argv[i + 1] if i + 1 < len(argv) else ""):
                return True
            if not eq and (option in DATA_OPTIONS or option in HOST_OPTIONS or tool == "aliyun"):
                skip = True
            continue
        if host_has_variable(token):
            return True
    return False


# ---- judging -------------------------------------------------------------------------------------------------------------------------------------------------------
def body_is_judged_separately(command):
    """A shell whose program is a `-c` string, an eval argument, a heredoc or a here-string: that body is a command line of its own in the list."""
    tool, argv = name_of(command.argv[0]), command.argv
    return tool in SHELLS and (tool == "eval" or any(SHORT_C.fullmatch(t) for t in argv[1:]) or any(t.startswith("<<") for t in argv))


def indirect_hit(command, raw_hit, patterns, cwd):
    """An interpreter or shell that reads its program from a file, a heredoc or a pipe, and that program mentions production."""
    tool = name_of(command.argv[0])
    if not INTERPRETER.match(tool):
        return False
    argv = command.argv
    for token in argv[1:]:
        if not token.startswith("-"):
            content = read_text(token, cwd, MAX_SCRIPT_FILE)
            if content is not None and any(p in plain(content) for p in patterns):
                return True
    return raw_hit and (command.piped or any(t.startswith("<") for t in argv))


def allowed_without_connecting(command):
    tool = name_of(command.argv[0])
    if tool in NO_CONNECT:
        return True
    return bool(INTERPRETER.match(tool)) and any(os.path.basename(t) in DOC_SCRIPTS for t in command.argv[1:3])


def judge(command_text, patterns, problem=None, cwd=None, environ=None):
    """Refusal text or None."""
    environ = os.environ if environ is None else environ
    env_hit = bool(patterns) and any(p in str(environ.get(v, "")).lower() for v in ENV_TARGET_VARS for p in patterns)
    raw_hit = bool(patterns) and any(p in plain(command_text) for p in patterns)
    try:
        commands = simple_commands(command_text)
    except ValueError:
        if raw_hit or any(t in plain(command_text) for t in REMOTE_TOOLS):
            return "命令读不懂（引号未闭合等）且含生产特征或 psql／ssh／aliyun 等远程工具，无法确认不是连生产的写操作，拒绝。"
        return None
    for command in commands:
        tool = name_of(command.argv[0])
        if tool == "aliyun" and aliyun_secret(command.argv):
            return "拒绝：该 aliyun 接口会返回私钥或口令（如 DescribeDBInstanceSSL、GetSecretValue），底线 3 不调用返回密钥的接口。"
        if tool not in REMOTE_TOOLS:
            if patterns is None or body_is_judged_separately(command):
                continue
            if (touches(command, patterns) or indirect_hit(command, raw_hit, patterns, cwd)) and not allowed_without_connecting(command):
                return (f"拒绝：{tool} 命令碰到生产目标，而它不是点名的只读工具（psql／sqlcmd／aliyun Describe／ssh 只读白名单等）。CLAUDE.md 底线 4：不做生产写操作；"
                        "生产只读取证走 ADR-0037 专用只读账号和已登记的只读入口（直接用 psql／sqlcmd／ssh 的只读用法）。")
            continue
        writes = is_write(command, patterns, cwd)
        if patterns is None:
            if writes:
                return f"{problem}：写操作类命令（{tool}）无法判断是否连生产，一律拒绝；修好 scripts/hooks/prod_targets.json 后再做。"
            continue
        if writes and touches(command, patterns, env_hit):
            return (f"拒绝：{tool} 命令连到生产目标并带写操作（或写效果看不出来）。CLAUDE.md 底线 4：不做生产写操作；生产取证只用专用只读账号且只读（ADR-0037）。"
                    "确需写入的事项属须用户决定的事（底线 5）：写收件箱等答复，不在命令里绕。")
        if writes and command.unresolved and target_unresolved(command):
            return f"拒绝：{tool} 的写操作，目标（主机、URL、实例号）含读不出的变量或命令替换，无法确认不是生产；把目标写成字面值再执行。"
    return None


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
    cwd = payload.get("cwd") if isinstance(payload.get("cwd"), str) and payload.get("cwd") else None
    patterns, problem, warning = load_table()
    reason = judge(command, patterns, problem, cwd)
    if reason:
        print(reason, file=sys.stderr)
        return 2
    notes = []
    if problem and any(t in plain(command) for t in REMOTE_TOOLS):
        notes.append(f"告警：{problem}，写操作类命令会被拒绝。")
    if warning and (REMOTE_WORD.search(plain(command)) or "$" in command):
        notes.append(f"告警：{warning}")
    if notes:
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": "\n".join(notes)}}, ensure_ascii=False))
    return 0


def guarded_main():
    try:
        return main()
    except Exception as error:
        print(f"prod_write_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 2


def self_test():
    patterns = ["pgm-prod0001", "rm-prodhost1", "db.prod.example.com"]
    cwd = tempfile.mkdtemp()
    refused = lambda command, table=patterns, problem=None, environ=None: judge(command, table, problem, cwd, environ or {}) is not None  # noqa: E731
    allowed = lambda command, table=patterns, problem=None, environ=None: judge(command, table, problem, cwd, environ or {}) is None  # noqa: E731
    P = "pgm-prod0001"
    Path(cwd, "ro.sql").write_text("select count(*) from t;\nselect 2\n", encoding="utf-8")
    Path(cwd, "fix.sql").write_text("select 1;\ndelete from t where id = 1;\n", encoding="utf-8")
    Path(cwd, "runner.py").write_text(f"INSTANCE = 'rm-prodhost1'\nconnect(INSTANCE)\n", encoding="utf-8")
    Path(cwd, "clean.py").write_text("print(1)\n", encoding="utf-8")
    Path(cwd, "deploy.sh").write_text(f"sqlcmd -S {P} -Q 'delete from t'\n", encoding="utf-8")

    # 1. reads and harmless mentions pass
    for command in (f'psql -h {P}.pg.rds.aliyuncs.com -U ro -c "select count(*) from t"', f'psql -h {P} -c "SELECT 1"', f'mysql -h {P} -e "show tables"', f'psql -h {P} -c "explain select 1"', f'psql -h {P} -q -c "select 1"',
                    f'sqlcmd -S {P} -U ro -Q "set nocount on; select * from t"', f'sqlcmd -S {P} -i ro.sql', f"sqlcmd -S {P} -iro.sql", f'sqlcmd -S {P} < ro.sql', f'sqlcmd -S {P} -Q "select [desc], [update] from [t]"',
                    f'psql -h {P} -c "select \'drop table t\'"', f'psql -h {P} -c "select * from t -- delete me"', f'psql -h {P} -c "\\dt"', f'redis-cli -h {P} -p 6379 get a', f'bcp "select 1" queryout o.csv -S {P}',
                    "aliyun rds DescribeDBInstances --InstanceId pgm-prod0001", "aliyun --profile x ecs DescribeInstances --InstanceIds rm-prodhost1", f'ssh ops@{P} "tail -n 50 /var/log/app.log"',
                    f"ssh {P} 'df -h && uptime'", f"ssh -i key -p 22 {P} 'systemctl status app'", f"ssh {P} docker ps", f"scp ops@{P}:/var/log/a.log ./a.log", f"rsync -a ops@{P}:/data/ ./data/",
                    f"curl https://{P}/health", f"curl -X GET https://db.prod.example.com/x", f"curl -XGET https://db.prod.example.com/x", f"curl -sS -o out.json https://db.prod.example.com/x", f"wget https://db.prod.example.com/x",
                    "git commit -m 'pgm-prod0001 续费说明'", "gh pr comment 3 --body 'psql -h pgm-prod0001 update x'", "echo pgm-prod0001 >> notes.md", "grep pgm-prod0001 docs/x.md", "cat <<'EOF' > n.md\npgm-prod0001\nEOF",
                    "python3 -I scripts/glossary/new_intent.py 'x pgm-prod0001'", "sed -i s/pgm-prod0001/X/ docs/a.md", "ls docs/pgm-prod0001", "python3 clean.py", "bash -c 'echo hi'",
                    f"bash -c \"sqlcmd -S {P} -Q 'select 1'\"", "curl -X POST http://localhost:$PORT/api", 'curl -s -d "$BODY" http://localhost:8080/api', "psql -h localhost -c 'DO $$ begin null; end $$'",
                    "psql -h localhost -c 'select 1' && curl http://localhost:$PORT", "ls $HOME"):
        assert allowed(command), f"must pass: {command!r}"
    # 2. writes to production are refused, every class
    for command in (f'psql -h {P} -c "insert into t values (1)"', f'psql -h {P} -c "UPDATE t SET a=1"', f'psql -h {P} -c "delete from t"', f'psql -h {P} -c "drop table t"', f'psql -h {P} -c "ALTER TABLE t ADD c int"',
                    f'psql -h {P} -c "select 1; delete from t"', f'psql -h {P} -c "select * from t for update"', f'psql -h {P} -c "truncate t"', f"psql -h {P} -f fix.sql", f"psql -h {P} < fix.sql", f"psql -h {P} -f missing.sql",
                    f"psql -h {P}", f'mysql -h {P} -e "grant all on *.* to x"', f"mysql -h {P} < x.sql", f'redis-cli -h {P} flushall', f'redis-cli -h {P} set a 1',
                    f'sqlcmd -S {P} -Q "select 1; dbcc shrinkdatabase(x)"', f'sqlcmd -S {P} -Q "select 1; kill 55"', f'sqlcmd -S {P} -Q "backup database x to disk=\'y\'"', f'sqlcmd -S {P} -Q "select 1; deny select on t to u"',
                    f'sqlcmd -S {P} -Q "select 1; checkpoint; reconfigure"', f'sqlcmd -S {P} -Q "exec sp_configure"', f'sqlcmd -S {P} -Q "select * into t2 from t"', f'sqlcmd -S {P} -Q "use master; select 1"',
                    f'psql -h {P} -c "select pg_terminate_backend(1)"', f"psql -h {P} -c \"select 'drop table t' \\gexec\"", f'psql -h {P} -c "select set_config(\'a\',\'b\',false)"', f'psql -h {P} -c "select nextval(\'s\')"',
                    f'psql -h {P} -c "\\copy t from f"', f'psql -h {P} -c "\\i fix.sql"', f'psql -h {P} -c "do $$ begin delete from t; end $$"', f'psql -h {P} <<EOF\nselect 1\nEOF', f'psql -h {P} <<< "select 1"', f'echo "select 1" | psql -h {P}',
                    f'sqlcmd -S {P} -ifix.sql', f'bcp db.dbo.t in x.dat -S {P}', f'bcp db.dbo.t out x.dat -S {P} -b in', f'tsql -H {P}', f'isql {P}', f'sqlpackage /Action:Publish /TargetServerName:{P}',
                    f'mongosh --host {P} --eval "db.t.deleteMany({{}})"', f'mongosh --host {P} --eval "db.t.find()"; mongosh --host {P} --eval "db.t.insertOne({{}})"',
                    "aliyun rds DeleteAccount --DBInstanceId pgm-prod0001", "aliyun rds ModifyDBInstanceSpec --DBInstanceId pgm-prod0001", "aliyun ecs StopInstance --InstanceId rm-prodhost1", "aliyun --profile pgm-prod0001 ecs RunCommand",
                    "aliyun rds --RegionId DescribeX DeleteAccount --DBInstanceId pgm-prod0001", "aliyun ecs InvokeCommand --InstanceId rm-prodhost1",
                    f'ssh {P} "rm -rf /data/x"', f"ssh {P} 'echo x > /etc/app.conf'", f"ssh {P} 'sed -i s/a/b/ /etc/app.conf'", f"ssh {P} 'systemctl restart app'", f"ssh {P}", f"ssh {P} 'tee /etc/x'",
                    f"ssh {P} 'find / -delete'", f"ssh {P} 'find / -fprint0 /tmp/x'", f"ssh {P} 'cat /etc/x | sh'", f"ssh {P} 'psql -c \"delete from t\"'",
                    f"ssh {P} 'awk \"BEGIN{{print 1 | \\\"sh\\\"}}\"'", f"ssh {P} 'sort -o /etc/passwd /tmp/x'", f"ssh {P} 'uniq in /etc/passwd'", f"ssh {P} 'journalctl --vacuum-size=1M'", f"ssh {P} 'date -s 2020-01-01'",
                    f"ssh {P} 'ss -K'", f"ssh {P} 'hostname evil'", f"ssh -L 5432:db:5432 {P}", f"ssh -L 5432:db:5432 {P} ls", f"ssh -D 1080 {P} uptime", f"ssh -R 8080:localhost:80 {P} df", f"ssh -o ProxyCommand='nc x 22' {P} ls", f"ssh -N -R 8080:localhost:80 {P}",
                    f"scp ./fix.sql ops@{P}:/tmp/", f"rsync -a ./dist/ ops@{P}:/srv/", f"rsync -a ./dist/ ops@{P}:/srv/ --exclude .git", f"rsync -a ./dist/ ops@{P}:/srv/ -e ssh", f"rsync --remove-source-files ops@{P}:/data/ ./x/",
                    f"rsync --rsync-path='sh -c x' ops@{P}:/data/ ./x/", f"sftp {P}", f"scp -P 22 ./a ops@{P}:/x", f"scp ops@{P}:/a ops@{P}:/b",
                    f"curl -X POST https://{P}/api", f"curl -XPOST https://{P}/api", f"curl -sd x=1 https://{P}/api", f"curl --data=x https://{P}/api", f"curl -K cfg https://{P}/api", f"curl -d x=1 https://db.prod.example.com/api",
                    f"curl -X DELETE https://db.prod.example.com/x", f"curl -T f.bin https://db.prod.example.com/up", f"curl --json '{{}}' https://{P}/x", f"curl https://{P}/a --next https://{P}/b -d x",
                    f"wget --method=DELETE https://{P}/x", f"wget --post-data=x https://{P}/x", f"http POST https://{P}/x", f"http https://{P}/x name=a", f"xh PUT https://{P}/x"):
        assert refused(command), f"write must be refused: {command!r}"
    # 3. writes elsewhere pass
    for command in ('psql -h localhost -c "insert into t values (1)"', "psql -h pgm-dev00001 -f x.sql", "aliyun rds DeleteAccount --DBInstanceId pgm-dev00001", "ssh dev 'rm -rf /tmp/x'", "scp a dev:/tmp/", "curl -X POST https://dev/x",
                    "python3 clean.py", "xargs -n1 echo", "find . -name '*.py'", "caffeinate -i ls"):
        assert allowed(command), f"non-production write must pass: {command!r}"
    # 4. the opus review's gaps: unknown tools that touch production, every one refused
    for command in (f"python3 -c \"import pymssql; pymssql.connect('{P}').execute('delete from t')\"", f"./gradlew flywayMigrate -Pflyway.url=jdbc:sqlserver://{P}", f"xargs -I{{}} sqlcmd -S {P} -Q {{}}",
                    f"find . -exec sqlcmd -S {P} -Q 'delete from t' \\;", f"script -q /dev/null sqlcmd -S {P} -Q 'delete from t'", f"arch -arm64 sqlcmd -S {P} -Q 'delete from t'", f"caffeinate -i sqlcmd -S {P} -Q 'delete from t'",
                    f"alias q='sqlcmd -S {P}'\nq -Q 'delete from t'", f"parallel sqlcmd -S {P} ::: x", f"python3 runner.py", f"python3 deploy_x.py --db {P}", f"node -e \"x('{P}')\"", f"mvn flyway:migrate -Dflyway.url={P}",
                    "bash deploy.sh", "source deploy.sh", ". deploy.sh", f"python3 - <<EOF\nconnect('{P}')\nEOF", f"echo \"x\" > /dev/null; python3 <<EOF\nconnect('{P}')\nEOF", f"cat x.sql | python3 -c 'import sys' # {P}",
                    f"bash <<'EOF'\nsqlcmd -S {P} -Q 'delete from t'\nEOF", f'bash <<< "sqlcmd -S {P} -Q \'delete from t\'"', f'echo "sqlcmd -S {P} -Q \'delete from t\'" | bash', f"cat run.sh | bash # {P}"):
        assert refused(command), f"unknown tool touching production must be refused: {command!r}"
    # 5. the opus review's parsing gaps
    for command in (f"sqlcmd -S `echo {P}` -Q \"delete from t\"", "sqlcmd -S `cat h.txt` -Q \"delete from t\"", f"sqlcmd -S $'\\x70gm-prod0001' -Q 'delete from t'", f"$'\\x73qlcmd' -S {P} -Q 'delete from t'", f"sqlcmd -S $'\\160gm-prod0001' -Q 'delete from t'",
                    f"sqlc\\\nmd -S {P} -Q \"delete from t\"", f"sqlcmd -S pgm-\\\nprod0001 -Q 'delete from t'", f"bash -lc \"sqlcmd -S {P} -Q 'delete from t'\"", f"sh -ec \"psql -h {P} -c 'delete from t'\"", f"bash -xc \"psql -h {P} -c 'drop table t'\"",
                    f"eval sqlcmd -S {P} -Q \"'delete from t'\"", f"env -S 'sqlcmd -S {P} -Q \"delete from t\"'", f"env -Ssqlcmd -S {P} -Q x", f"p=pgm-prod; q=0001; psql -h $p$q -c 'delete from t'", f"HOST={P} psql -h $HOST -c 'delete from t'",
                    f"export H={P}; psql -h \"${{H}}\" -c 'drop table t'", f"psql -h 'pgm-'prod0001 -c 'delete from t'", f"env PGHOST={P} psql -c 'delete from t'", f"nohup timeout 9 psql -h {P} -c 'delete from t'",
                    f"echo $(psql -h {P} -c 'delete from t')", f"PSQL=psql; $PSQL -h {P} -c 'delete from t'", f"/usr/bin/PSQL -h {P} -c 'delete from t'", f"cd /tmp && true; ssh {P} 'rm x'", f"(ssh {P} 'rm x')",
                    f"bash -c \"bash -c \\\"psql -h {P} -c 'delete from t'\\\"\""):
        assert refused(command), f"bypass must be refused: {command!r}"
    assert refused('psql -h "$PGHOST" -c "delete from t"'), "unresolved target + write is refused"
    assert refused("psql -h $(cat host.txt) -c 'delete from t'") and refused("aliyun rds DeleteAccount --DBInstanceId $ID") and refused('psql "$DATABASE_URL" -c "insert into t values (1)"')
    assert allowed('psql -h "$PGHOST" -c "select 1"') and allowed("aliyun rds DescribeDBInstances --InstanceId $ID"), "unresolved target + read passes"
    assert allowed("psql -h localhost -c \"insert into t values ($X)\"") and allowed("curl -X POST http://localhost:8080/api -d $BODY"), "`$` in a statement or data is not a target"
    assert refused("psql -h 'pgm-prod0001 -c 'delete'"), "unreadable command with a remote tool is refused"
    assert allowed("echo 'unterminated"), "unreadable command with no remote tool is not this gate's business"
    # 6. secrets (bottom line 3) and the environment
    for command in ("aliyun rds DescribeDBInstanceSSL --DBInstanceId pgm-dev00001", "aliyun kms GetSecretValue --SecretName x"):
        assert refused(command), command
    assert refused('psql -c "delete from t"', environ={"PGHOST": P}) and allowed('psql -c "select 1"', environ={"PGHOST": P}) and allowed('psql -c "delete from t"', environ={"PGHOST": "localhost"})
    assert refused('sqlcmd -Q "delete from t"', environ={"SQLCMDSERVER": "rm-prodhost1.sqlserver.rds.aliyuncs.com"})
    # 7. table missing or broken: writes are refused, reads pass, nothing is silently opened
    assert refused('psql -h localhost -c "insert into t values (1)"', None, "生产特征表缺失") and allowed('psql -h x -c "select 1"', None, "生产特征表缺失")
    assert refused("aliyun rds DeleteAccount --DBInstanceId x", None, "生产特征表缺失") and allowed("ls -la", None, "生产特征表缺失") and allowed("python3 clean.py", None, "生产特征表缺失")
    assert allowed("psql -h pgm-prod0001 -c 'delete from t'", []), "an empty table (no production yet) is valid and inert"
    with tempfile.TemporaryDirectory() as tmp:
        good = os.path.join(tmp, "t.json")
        for text, ok in ((json.dumps({"targets": [{"id": "a", "patterns": ["abcdef123"]}]}), True), ('{"targets": []}', True), ("not json", False), ("{}", False), ('{"targets": [{"id": "a"}]}', False),
                         ('{"targets": [{"id": "a", "patterns": []}]}', False), ('{"targets": [{"id": "a", "patterns": ["ab"]}]}', False), ('{"targets": [{"id": "a", "patterns": [1]}]}', False), ('{"targets": "x"}', False)):
            open(good, "w").write(text)
            assert (load_table(good)[1] is None) == ok, text
        assert load_table(os.path.join(tmp, "missing.json"))[1] is not None
        open(good, "w").write('{"targets": []}')
        assert load_table(good)[2] is not None, "an empty table warns"
        table, problem, _ = load_table()
        assert problem is None and table and "rm-bp1690ep53t9778dp" in table, "the shipped table loads and names the legacy production RDS"
        assert could_concern("sqlcmd -s x") and could_concern("rm-bp1690ep53t9778dp") and could_concern("echo $x") and not could_concern("echo hello") and not could_concern("git status")
        # hook process end to end, with the shipped and a missing table
        script = os.path.abspath(__file__)

        def hook(command, table_path=None, tool="Bash"):
            env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", "")}
            if table_path:
                env["PROD_TARGETS_FILE"] = table_path
            return subprocess.run([sys.executable, "-I", script], input=json.dumps({"tool_name": tool, "tool_input": {"command": command}, "cwd": cwd}), capture_output=True, text=True, env=env)

        result = hook("aliyun rds DeleteAccount --DBInstanceId rm-bp1690ep53t9778dp")
        assert result.returncode == 2 and "底线 4" in result.stderr, result
        assert hook("aliyun rds DescribeDBInstances --DBInstanceId rm-bp1690ep53t9778dp").returncode == 0
        assert hook("python3 -c \"connect('rm-bp1690ep53t9778dp')\"").returncode == 2
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
