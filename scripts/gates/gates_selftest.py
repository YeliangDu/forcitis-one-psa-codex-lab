#!/usr/bin/env python3
"""门的自测，合一（O8，D25；定稿 1.10「自测 1 个文件」）。`gates_selftest.py [段…]` 跑指定的段，不带参数跑全部；各门的 `--self-test` 只跑自己那一段。
- prod_write_gate：命令解析、生产写门、可信脚本（cwd 与 CLAUDE_PROJECT_DIR 同一仓库）、「生产写门 17 例」（Codex 复核 #653 的 17 条 P1 缺口，测试表与真实特征表下都须拒绝，按计数断言）、版式。
- pre_tool_dispatch：预筛不藏拒绝、失败关闭、时限。main_checkout_gate（含 git_ceiling_env.sh）与 commit_msg_gate：在临时仓库与 worktree 里端到端提交。
- install：install.sh 与 .claude/settings.json 挂载，全部在临时目录里做，HOME 指向临时目录，不碰真实安装目录（安装、切换、并发、自检不过、只读、篡改核对与修复、挂载失败关闭、可信锚）。
不写任何真实生产样本的字面值，拆段拼接也不行（O2 复审 M3）：真实特征运行时按 target id 从 prod_targets.json 取（install 段只经 install.sh 自检与探针按位置取用）；其余用虚构样本（192.0.2.x 是文档专用地址）。"""
import ast
import contextlib
import inspect
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import pre_tool_dispatch as d  # noqa: E402
import prod_write_gate as g  # noqa: E402
from prod_write_gate import (COMMAND_TOOLS, DOCS_ROOT, LAUNCH_TOOL, REPO_ROOT, brace_variants, could_concern, decode_ansi_c, judge, load_table, mask_sql, name_of, plain, simple_commands, tokens_of)  # noqa: E402

PYTHON = "/usr/bin/python3" if os.path.exists("/usr/bin/python3") else sys.executable

def env(**extra):  # a git environment without the caller's config and GIT_* variables
    base = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    return {**base, "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", **extra}

def git(cwd, *args, ok=True, **extra):
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8", env=env(**extra))
    assert not ok or r.returncode == 0, f"git {args}: {r.stderr}"
    return r

def put(path, text, mode=0o755):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    os.chmod(path, mode)

def size(key, path):  # (lines of `path`, its limit policy.json gates.max_lines.<key>)
    return Path(path).read_text(encoding="utf-8").count("\n"), policy()["max_lines"][key]

HOOKS = os.path.join(REPO_ROOT, "scripts", "hooks")  # ── prod_write_gate ──
TEST_IP = "192.0.2.15"  # RFC 5737 documentation address, not a production host
SAMPLE_IDS = ("legacy-rds-sqlserver", "legacy-ecs", "new-prod-names")  # the 17 cases' P, H, D: the first pattern of each of these targets

def shipped_firsts():
    """{target id: its first pattern} of the shipped prod_targets.json, read at run time (the guard sources hold no production sample)."""
    return {t["id"]: t["patterns"][0].strip() for t in json.loads(Path(HERE, "prod_targets.json").read_text(encoding="utf-8"))["targets"]}

def shipped_forms():
    """{"<target id> <form>": pattern}: the first IP-shaped and the first name-shaped pattern of every target of the shipped table (O2 round 1, note ①: each form is run)."""
    forms = {}
    for target in json.loads(Path(HERE, "prod_targets.json").read_text(encoding="utf-8"))["targets"]:
        for pattern in target["patterns"]:
            form = "ip" if re.fullmatch(r"\d{1,3}(?:\.\d{1,3}){3}", pattern.strip()) else "name"
            forms.setdefault(f"{target['id']} {form}", pattern.strip())
    return forms

def real_samples():
    """(P, H, D) for the 17 cases under the real table."""
    firsts = shipped_firsts()
    return tuple(firsts[i] for i in SAMPLE_IDS)

def braced(word):
    """`word` with one character written as a brace expansion `{c,c}`: bash (and curl) turn it back into `word`, the word itself is not on the line."""
    k = len(word) // 2
    return word[:k] + "{" + word[k] + "," + word[k] + "}" + word[k + 1:]

def seventeen(P, H, D):
    """生产写门 17 例：编号 = Codex 复核 #653（2026-10-22）的 P1 缺口序号，命令取自其复现样本（P 为 RDS 实例、H 为应用服务器、D 为生产库名；第 12 例的花括号按 braced 生成）。"""
    return {
        1: [f"sqlcmd -S {P} -Q \"SELECT '--'; DELETE FROM dbo.t\"", f"sqlcmd -S {P} -Q \"SELECT '/*'; DELETE FROM dbo.t; SELECT '*/'\""],  # SQL 注释藏写语句
        2: [f"sqlcmd -S {P} -Q \"SET NOCOUNT ON\nDELETE FROM dbo.t\""],  # SET 开头夹带写
        3: [f"sqlcmd -S {P} -z \"$NEW_PASSWORD\" -Q \"SELECT 1\"", f"sqlcmd -S {P} -v op=\"; DELETE FROM dbo.t\" -Q 'SELECT 1 $(op)'",
            f"mysql -h {H} --init-command=\"DELETE FROM t\" -e \"SELECT 1\"", f"psql -h localhost -c 'SELECT 1' -o '|ssh {H} touch /tmp/review'"],  # SQL 工具的其他执行入口
        4: [f"psql -d {D} -c 'SELECT pg_catalog.\"nextval\"($$seq$$)'", f"psql -d {D} -c \"SELECT pg_logical_emit_message(false, 'x', 'x')\""],  # 带引号函数名、有副作用函数
        5: [f"echo ok # <<EOF\nsqlcmd -S {P} -Q \"DELETE FROM dbo.t\"", f'echo "<<EOF"; sqlcmd -S {P} -Q "DELETE FROM dbo.t"', f"cat <<EOF\n$(sqlcmd -S {P} -Q \"DELETE FROM dbo.t\")\nEOF"],  # heredoc 误识别
        6: [f"printf \"%s\" <(sqlcmd -S {P} -Q \"DELETE FROM t\")"],  # 进程替换
        7: [f"eval eval eval eval ssh {H} touch /tmp/review"],  # 嵌套超深仍豁免外层
        8: [f"export SQLCMDSERVER={P}; sqlcmd -Q \"DELETE FROM dbo.t\"", f"export H={P}; sqlcmd -S\"$H\" -Q \"DELETE FROM dbo.t\""],  # export 不传播、粘连主机选项
        9: [f"python3 -X new_intent.py -c \"import os; os.system('ssh {H} touch /tmp/review')\"", f"node --title new_intent.py -e \"require('child_process').execSync('ssh {H} touch /tmp/review')\""],  # 冒充文档脚本
        10: [f"cat > /tmp/prod_gate_review_f13574.sh <<'EOF'\nssh {H} touch /tmp/review\nEOF\nbash /tmp/prod_gate_review_f13574.sh"],  # 先生成脚本再执行
        11: [f"rg --pre \"ssh {H} touch /tmp/review\" x README.md", f"git push ssh://{H}/srv/repo HEAD:main"],  # 不连接工具的执行入口
        12: [f"curl -XDELETE http://{braced(H)}/api/item/1", f"curl -XDELETE 'http://{braced(H)}/api/item/1'"],  # 花括号展开藏目标
        13: [f"curl --quote \"rm /tmp/x\" sftp://{H}/", f"curl 'gopher://{H}:6379/_SET%20x%201%0D%0A'"],  # curl 非 HTTP 协议
        14: [f"printf 'url = \"http://{H}/api\"\\ndata = \"x=1\"\\n' | curl -K -"],  # curl 从管道读配置
        15: [f"scp -o 'ProxyCommand=ssh {H} touch /tmp/review' {H}:/var/log/app.log ./app.log", f"rsync -e 'ssh -oProxyCommand=\"ssh {H} touch /tmp/review\"' {H}:/var/log/ ./logs/",
             f"scp -O '{H}:$(touch /tmp/review)/app.log' ./app.log"],  # scp/rsync 传输程序与远端 shell
        16: [f"bcp \"DELETE FROM dbo.t; SELECT 1\" queryout ./out.txt -S {P}", f"redis-cli -h {H} slowlog reset", f"redis-cli -h {H} client kill type normal"],  # 只看方向或一级子命令
        17: [f"ssh {H} 'date -us 2020-01-01'"],  # ssh 只读工具的组合短选项
    }

def words_self_test():
    names = lambda text: [name_of(c.argv[0]) for c in simple_commands(text)]  # noqa: E731
    argvs = lambda text: [c.argv for c in simple_commands(text)]  # noqa: E731
    # ANSI-C words are decoded the way bash does
    assert decode_ansi_c(r"\x67it \147it git \U00000067it\n") == "git git git git\n" and decode_ansi_c(r"a\x00b") == "a"
    assert plain("$'\\x67it' push") == "git push" and plain("$'\\147it' push") == "git push" and plain("$'git' push") == "git push" and plain('$"git" push') == "git push"
    assert argvs("$'\\x73qlcmd' -S $'\\x68ost' -Q x") == [["sqlcmd", "-S", "host", "-Q", "x"]]
    # a backslash-newline is deleted, also in the middle of a word
    assert plain("gi\\\nt push") == "git push" and argvs("sqlc\\\nmd -S h") == [["sqlcmd", "-S", "h"]] and argvs("a \\\n  b") == [["a", "b"]]
    # backticks are substitutions: the inner command is a command of its own and the outer word is unresolved; in single quotes they are text
    found = simple_commands("sqlcmd -S `echo rm-x` -Q q")
    assert "echo" in names("sqlcmd -S `echo rm-x` -Q q") and found[0].unresolved
    assert names("git commit -m 'use `foo` and $x'") == ["git"] and not simple_commands("git commit -m 'use `foo` and $x'")[0].unresolved
    assert simple_commands('echo "$X"')[0].unresolved and not simple_commands("echo '$X'")[0].unresolved
    assert "git" in names("echo `git push`") and any(t == ";" for t in tokens_of("echo `git push`")), "the other gates keep reading a backtick as a new command"
    # shells: bundled -c options, multi-word eval, heredoc / here-string / echo-pipe bodies
    for text in ("bash -lc 'psql -h h'", "sh -ec 'psql -h h'", "bash -xc \"psql -h h\"", "eval psql -h h", "eval 'psql' '-h' h", "bash <<'EOF'\npsql -h h\nEOF", "bash <<< 'psql -h h'", "echo 'psql -h h' | bash", "zsh -c \"bash -c 'psql -h h'\"",
                 "env -S 'psql -h h'", "env -S'psql -h h'", "env --split-string='psql -h h'", "env A=1 -S 'psql -h h'"):
        assert "psql" in names(text), text
    assert ["psql", "-h", "h"] in argvs("eval psql -h h") and ["psql", "-h", "h"] in argvs("env -S 'psql -h h'")
    pick = lambda text: next(c for c in simple_commands(text) if c.argv[0] == "bash")  # noqa: E731
    assert pick("echo hi | bash").piped and not pick("echo hi; bash").piped
    # variables and wrappers still work
    assert argvs("h=a; psql -h ${h}b") == [["psql", "-h", "ab"]] and argvs("FOO=1 nohup timeout 9 psql x") == [["psql", "x"]]
    # Codex review of #653: byte-wise ANSI-C, quote-aware here-documents, process substitution, nesting limit, export
    assert decode_ansi_c(r"\xe8\xb0\x83\xe5\xba\xa6") == "\u8c03\u5ea6" and decode_ansi_c(r"\u8c03") == "\u8c03"
    # comments and here-documents are not mimicked (intent 2026-10-26-140639): their text is read as words, so what they mention is seen; judge refuses such lines
    assert "sqlcmd" in names("echo ok # x\nsqlcmd -S p -Q x") and "sqlcmd" in names("cat <<'EOF'\n$(sqlcmd -S p -Q x)\nEOF") and "psql" in names("cat <<EOF\npsql -h x\nEOF")
    assert names('printf "%s" <(sqlcmd -S p -Q x)') == ["printf", "sqlcmd"] and names("diff <(ls a) >(cat)") == ["diff", "ls", "cat"]
    try:
        simple_commands("eval eval eval eval ssh h touch x")
        raise AssertionError("nesting deeper than 3 must raise")
    except ValueError:
        pass
    assert simple_commands("export SQLCMDSERVER=prod; sqlcmd -Q x")[-1].env == {"SQLCMDSERVER": "prod"} and simple_commands("export A=1; unset A; ls")[-1].env == {}
    assert simple_commands("A=1; export A; ls")[-1].env == {"A": "1"} and simple_commands("A=1; declare -x A; ls")[-1].env == {"A": "1"} and simple_commands("A=1; ls")[-1].env == {}
    assert simple_commands("set -a; A=1; ls")[-1].env == {"A": "1"} and simple_commands("set -o allexport; A=1; ls")[-1].env == {"A": "1"} and simple_commands("set -a; set +a; A=1; ls")[-1].env == {}
    assert simple_commands("PGHOST=x; ls")[-1].env == {"PGHOST": "x"} and simple_commands("SQLCMDSERVER=x; sqlcmd")[-1].env == {"SQLCMDSERVER": "x"}, "a target variable may be exported already"
    assert simple_commands("A=1; declare -a A; ls")[-1].env == {} and simple_commands("export B; ls")[-1].env == {}, "an array declaration or an unknown name exports nothing"
    assert argvs("export H=prod; sqlcmd -S\"$H\" -Q x")[-1][:2] == ["sqlcmd", "-Sprod"]
    # unreadable input
    try:
        simple_commands("echo 'unterminated")
        raise AssertionError("must raise")
    except ValueError:
        pass
    print("command_words self-test: ok")

def gate_self_test():
    patterns = ["pgm-prod0001", "rm-prodhost1", "db.prod.example.com", TEST_IP]
    cwd = tempfile.mkdtemp()
    refused = lambda command, table=patterns, problem=None, environ=None: judge(command, table, problem, cwd, environ or {}) is not None  # noqa: E731
    allowed = lambda command, table=patterns, problem=None, environ=None: judge(command, table, problem, cwd, environ or {}) is None  # noqa: E731
    P, H = "pgm-prod0001", TEST_IP
    Path(cwd, "ro.sql").write_text("select count(*) from t;\nselect 2\n", encoding="utf-8")
    Path(cwd, "fix.sql").write_text("select 1;\ndelete from t where id = 1;\n", encoding="utf-8")
    Path(cwd, "runner.py").write_text("INSTANCE = 'rm-prodhost1'\nconnect(INSTANCE)\n", encoding="utf-8")
    Path(cwd, "clean.py").write_text("print(1)\n", encoding="utf-8")
    Path(cwd, "deploy.sh").write_text(f"sqlcmd -S {P} -Q 'delete from t'\n", encoding="utf-8")
    intent = os.path.join(REPO_ROOT, DOCS_ROOT, "new_intent.py")
    def as_main(rels):  # this repository's files read as origin/main's: a branch may edit new_intent's neighbours (trust against origin/main itself: trust_self_test)
        return {rel: subprocess.run(["git", "-C", REPO_ROOT, "hash-object", os.path.join(REPO_ROOT, rel)], capture_output=True, text=True).stdout.strip() for rel in rels}
    real_blobs, g.main_blobs = g.main_blobs, as_main
    # 1. reads and harmless mentions pass
    for command in (f'psql -h {P}.pg.rds.aliyuncs.com -U ro -c "select count(*) from t"', f'psql -h {P} -c "SELECT 1"', f'mysql -h {P} -e "show tables"', f'mysql -h {P} -e "SHOW CREATE TABLE t"', f'psql -h {P} -c "explain select 1"', f'psql -h {P} -q -c "select 1"',
                    f'sqlcmd -S {P} -U ro -Q "set nocount on; select * from t"', f'sqlcmd -S {P} -Q "set nocount on\nselect 1"', f'sqlcmd -S {P} --query "select 1"', f'sqlcmd -S {P} -i ro.sql', f"sqlcmd -S {P} -iro.sql", f'sqlcmd -S {P} < ro.sql',
                    f'sqlcmd -S {P} -Q "select [desc], [update] from [t]"', f'psql -h {P} -c "select \'drop table t\'"', f'psql -h {P} -c "select * from t -- delete me"', f'psql -h {P} -c "\\dt"', f'psql -h {P} -c "\\dt+"',
                    f"psql -h {P} <<< 'select 1'", f"echo 'select 1' | psql -h {P}", f"sqlcmd -S {P} -?", f"psql -h {P} --version", f'redis-cli -h {P} -p 6379 get a', f'redis-cli -h {P} slowlog get 5',
                    f'bcp "select 1" queryout o.csv -S {P}', "aliyun rds DescribeDBInstances --InstanceId pgm-prod0001", "aliyun --profile x ecs DescribeInstances --InstanceIds rm-prodhost1", f"aliyun cs GET /clusters/{P}",
                    f'ssh ops@{P} "tail -n 50 /var/log/app.log"', f"ssh {P} 'df -h && uptime'", f"ssh -i key -p 22 {P} 'systemctl status app'", f"ssh {P} docker ps", f"ssh {P} 'date -u +%s'", f"ssh {P} 'date -Iseconds'",
                    f"scp ops@{P}:/var/log/a.log ./a.log", f"rsync -a ops@{P}:/data/ ./data/", f"rsync -a -e 'ssh -p 22' ops@{P}:/data/ ./data/",
                    f"curl https://{P}/health", f"curl -X GET https://db.prod.example.com/x", f"curl -XGET https://db.prod.example.com/x", f"curl -sS -o out.json https://db.prod.example.com/x", f"wget https://db.prod.example.com/x",
                    "git commit -m 'pgm-prod0001 续费说明'", "git log --grep pgm-prod0001", "gh pr comment 3 --body 'psql -h pgm-prod0001 update x'", "echo pgm-prod0001 >> notes.md", "grep pgm-prod0001 docs/x.md",
                    "sed -i s/pgm-prod0001/X/ docs/a.md", "ls docs/pgm-prod0001", "rg pgm-prod0001 docs", "python3 clean.py", "bash -c 'echo hi'", f"bash -c \"sqlcmd -S {P} -Q 'select 1'\"",
                    "curl -X POST http://localhost:$PORT/api", 'curl -s -d "$BODY" http://localhost:8080/api', "psql -h localhost -c 'DO $$ begin null; end $$'", "psql -h localhost -c 'select 1' && curl http://localhost:$PORT", "ls $HOME",
                    "echo 'cost $5 for pgm-prod0001'", "ls -la # list files", "git commit -m 'fix #12'", "cat <<EOF > n.md\nplain text\nEOF", "ls \\\n-la"):
        assert allowed(command), f"must pass: {command!r}"
    # 1b. a comment or a here-document is not stripped as the shell would (intent 2026-10-26-140639): with a production sample, a remote tool or a target variable on the line, refused
    for command in (f"psql -h {P} <<'EOF'\nselect 1;\nEOF", "cat <<'EOF' > n.md\npgm-prod0001\nEOF", "cat <<EOF\nplain text pgm-prod0001\nEOF", f"ls # {P}", "cat <<EOF\npsql -h localhost\nEOF", "echo a # '\ntime \\./deploy\n: \\'",
                    f"ssh {P} uptime # can't", f"cat <<'EOF'\n$(sqlcmd -S {P} -Q 'delete from t')\nEOF", "echo a # x \\\nenv \\./deploy", "ls # x \\\nif scripts/deploy; then :; fi", f">\"$(psql -h {P} -c 'delete from t')\" ls"):
        assert refused(command), ("refused rather than read wrongly", command)
    assert refused("ls # x", environ={"PGHOST": P}) and allowed("ls # x", environ={"PGHOST": "localhost"}), "a target variable pointing at production counts too"
    for command in ("python3 runner.py # it's", 'python3 runner.py # "', "python3 runner.py <<EOF\nit's\nEOF", "bash runner.py # it's", "echo a # '\npython3 runner.py\n: \\'", "H=pgm-pr; <<<\"psql -h ${H}od0001 -c 'delete from t'\" bash",
                    "cat <<EOF\n'\nEOF\npython3 runner.py\n: \\'", "./deploy.sh # x", "echo a # it's", "echo a # x \\\n./deploy", ">o python3 runner.py", "echo a\\\\\n./deploy # x",  # the script the program reads (2b59000cb, c211b73bd final)
                    "T=ps; H=pgm-pr; ls # it's\n${T}ql -h ${H}od0001 -c 'delete from t'", "T=ps; H=pgm-pr; ${T}ql -h ${H}od0001 -c 'delete from t' # it's", "T=ps; H=pgm-pr; cat <\\\n<X\nit's\nX\n${T}ql -h ${H}od0001 -c 'delete from t'",
                    "T=ps; H=pgm-pr; ls # '\n${T}ql -h ${H}od0001 -c 'delete from t'\n: \\'", ">x python3 a.py # c", "ls # x \\\n./run", "echo a # '\nenv scripts/deploy\n: \\'",  # anywhere; variables spell tool and target
                    "ls # '\n`echo ps`ql -h `echo pgm-pr`od0001 -c \"delete from t\"\n: \\'", f"echo a\\\\\npsql -h {P} -c 'delete from t'", "echo a # '\n./deploy\n: \\'"):  # backticks, a path command; `\\` then a newline ends the command (3eac19088 final)
        assert refused(command), ("a comment or here-document with a program, or unreadable: refused", command)
    # 2. writes to production are refused, every class
    for command in (f'psql -h {P} -c "insert into t values (1)"', f'psql -h {P} -c "UPDATE t SET a=1"', f'psql -h {P} -c "delete from t"', f'psql -h {P} -c "drop table t"', f'psql -h {P} -c "ALTER TABLE t ADD c int"',
                    f'psql -h {P} -c "select 1; delete from t"', f'psql -h {P} -c "select * from t for update"', f'psql -h {P} -c "truncate t"', f"psql -h {P} -f fix.sql", f"psql -h {P} < fix.sql", f"psql -h {P} -f missing.sql",
                    f"psql -h {P}", f'mysql -h {P} -e "grant all on *.* to x"', f"mysql -h {P} < x.sql", f'redis-cli -h {P} flushall', f'redis-cli -h {P} set a 1', f'redis-cli -h {H} slowlog reset', f'redis-cli -h {H} client kill type normal',
                    f'sqlcmd -S {P} -Q "select 1; dbcc shrinkdatabase(x)"', f'sqlcmd -S {P} -Q "select 1; kill 55"', f'sqlcmd -S {P} -Q "backup database x to disk=\'y\'"', f'sqlcmd -S {P} -Q "select 1; deny select on t to u"',
                    f'sqlcmd -S {P} -Q "select 1; checkpoint; reconfigure"', f'sqlcmd -S {P} -Q "exec sp_configure"', f'sqlcmd -S {P} -Q "select * into t2 from t"', f'sqlcmd -S {P} -Q "use master; select 1"',
                    f'psql -h {P} -c "select pg_terminate_backend(1)"', f"psql -h {P} -c \"select 'drop table t' \\gexec\"", f'psql -h {P} -c "select set_config(\'a\',\'b\',false)"', f'psql -h {P} -c "select nextval(\'s\')"',
                    f'psql -h {P} -c \'SELECT pg_catalog."nextval"($$seq$$)\'', f'psql -h {P} -c "SELECT pg_logical_emit_message(false, \'x\', \'x\')"', f'psql -h {P} -c "select 1; comment on table t is \'x\'"',
                    f'psql -h {P} -c "\\copy t from f"', f'psql -h {P} -c "\\i fix.sql"', f'psql -h {P} -c "do $$ begin delete from t; end $$"', f">o scp f {P}:/x", "T=ps H=pgm-pr >o; ${T}ql -h ${H}od0001 -c 'delete from t'",
                    f'sqlcmd -S {P} -Q "SELECT \'--\'; DELETE FROM dbo.t"', f'sqlcmd -S {P} -Q "SELECT \'/*\'; DELETE FROM dbo.t; SELECT \'*/\'"', f'sqlcmd -S {P} -Q "SET NOCOUNT ON\nDELETE FROM dbo.t"', f'sqlcmd -S {P} -Q "select \'unterminated"',
                    f'sqlcmd -S {P} -z "$NEW" -Q "select 1"', f'sqlcmd -S {P} -v op="; DELETE FROM dbo.t" -Q \'SELECT 1 $(op)\'', f'sqlcmd -S {P} -Q "select 1 $(op)"', f'mysql -h {H} --init-command="DELETE FROM t" -e "select 1"',
                    f"psql -h localhost -c 'select 1' -o '|ssh {H} touch /tmp/x'", f'psql -h {P} <<EOF\nselect 1;\ndelete from t;\nEOF', f'psql -h {P} <<< "delete from t"', f'echo "delete from t" | psql -h {P}', f'printf "delete from t" | psql -h {P}',
                    f'cat fix.sql | psql -h {P}', f'sqlcmd -S {P} -ifix.sql', f'bcp db.dbo.t in x.dat -S {P}', f'bcp db.dbo.t out x.dat -S {P} -b in', f'bcp "DELETE FROM dbo.t; SELECT 1" queryout ./out.txt -S {P}', f'tsql -H {P}', f'isql {P}',
                    f'sqlpackage /Action:Publish /TargetServerName:{P}', f'mongosh --host {P} --eval "db.t.deleteMany({{}})"', "aliyun rds DeleteAccount --DBInstanceId pgm-prod0001", "aliyun rds ModifyDBInstanceSpec --DBInstanceId pgm-prod0001",
                    "aliyun ecs StopInstance --InstanceId rm-prodhost1", "aliyun --profile pgm-prod0001 ecs RunCommand", "aliyun rds --RegionId DescribeX DeleteAccount --DBInstanceId pgm-prod0001",
                    "aliyun ecs InvokeCommand --InstanceId rm-prodhost1", f"aliyun cs DELETE /clusters/{P}",
                    f'ssh {P} "rm -rf /data/x"', f"ssh {P} 'echo x > /etc/app.conf'", f"ssh {P} 'sed -i s/a/b/ /etc/app.conf'", f"ssh {P} 'systemctl restart app'", f"ssh {P}", f"ssh {P} 'tee /etc/x'",
                    f"ssh {P} 'find / -delete'", f"ssh {P} 'find / -fprint0 /tmp/x'", f"ssh {P} 'cat /etc/x | sh'", f"ssh {P} 'psql -c \"delete from t\"'",
                    f"ssh {P} 'awk \"BEGIN{{print 1 | \\\"sh\\\"}}\"'", f"ssh {P} 'sort -o /etc/passwd /tmp/x'", f"ssh {P} 'uniq in /etc/passwd'", f"ssh {P} 'journalctl --vacuum-size=1M'", f"ssh {P} 'date -s 2020-01-01'", f"ssh {H} 'date -us 2020-01-01'",
                    f"ssh {P} 'ss -K'", f"ssh {P} 'hostname evil'", f"ssh -L 5432:db:5432 {P}", f"ssh -L 5432:db:5432 {P} ls", f"ssh -D 1080 {P} uptime", f"ssh -R 8080:localhost:80 {P} df", f"ssh -o ProxyCommand='nc x 22' {P} ls", f"ssh -N -R 8080:localhost:80 {P}",
                    f"scp ./fix.sql ops@{P}:/tmp/", f"rsync -a ./dist/ ops@{P}:/srv/", f"rsync -a ./dist/ ops@{P}:/srv/ --exclude .git", f"rsync -a ./dist/ ops@{P}:/srv/ -e ssh", f"rsync --remove-source-files ops@{P}:/data/ ./x/",
                    f"rsync --rsync-path='sh -c x' ops@{P}:/data/ ./x/", f"sftp {P}", f"scp -P 22 ./a ops@{P}:/x", f"scp ops@{P}:/a ops@{P}:/b", f"scp -o 'ProxyCommand=ssh {H} touch /tmp/x' {H}:/var/log/a ./a",
                    f"rsync -e 'ssh -oProxyCommand=\"ssh {H} touch /tmp/x\"' {H}:/var/log/ ./logs/", f"scp -O '{H}:$(touch /tmp/x)/a' ./a", f"scp -S ./evil {H}:/a ./a",
                    f"curl -X POST https://{P}/api", f"curl -XPOST https://{P}/api", f"curl -sd x=1 https://{P}/api", f"curl --data=x https://{P}/api", f"curl -K cfg https://{P}/api", f"curl -d x=1 https://db.prod.example.com/api",
                    f"curl -X DELETE https://db.prod.example.com/x", f"curl -T f.bin https://db.prod.example.com/up", f"curl --json '{{}}' https://{P}/x", f"curl https://{P}/a --next https://{P}/b -d x",
                    f"curl --quote 'rm /tmp/x' sftp://{H}/", f"curl 'gopher://{H}:6379/_SET%20x%201'", f"printf 'url = \"http://{H}/api\"' | curl -K -", "curl -XDELETE http://192.0.{2,2}.15/api/item/1", "curl -XDELETE 'http://192.0.{2,2}.15/api/item/1'",
                    f"wget --method=DELETE https://{P}/x", f"wget --post-data=x https://{P}/x", f"http POST https://{P}/x", f"http https://{P}/x name=a", f"xh PUT https://{P}/x"):
        assert refused(command), f"write must be refused: {command!r}"
    # 3. writes elsewhere pass
    for command in ('psql -h localhost -c "insert into t values (1)"', "psql -h pgm-dev00001 -f x.sql", "aliyun rds DeleteAccount --DBInstanceId pgm-dev00001", "ssh dev 'rm -rf /tmp/x'", "scp a dev:/tmp/", "curl -X POST https://dev/x", "python3 clean.py",
                    "xargs -n1 echo", "find . -name '*.py'", "caffeinate -i ls", "psql -h localhost -c 'select 1' -o out.txt"):
        assert allowed(command), f"non-production write must pass: {command!r}"
    # 4. unknown tools that touch production, every one refused
    for command in (f"python3 -c \"import pymssql; pymssql.connect('{P}').execute('delete from t')\"", f"./gradlew flywayMigrate -Pflyway.url=jdbc:sqlserver://{P}", f"xargs -I{{}} sqlcmd -S {P} -Q {{}}",
                    f"find . -exec sqlcmd -S {P} -Q 'delete from t' \\;", f"script -q /dev/null sqlcmd -S {P} -Q 'delete from t'", f"arch -arm64 sqlcmd -S {P} -Q 'delete from t'", f"caffeinate -i sqlcmd -S {P} -Q 'delete from t'",
                    f"alias q='sqlcmd -S {P}'\nq -Q 'delete from t'", f"parallel sqlcmd -S {P} ::: x", "python3 runner.py", f"python3 deploy_x.py --db {P}", f"node -e \"x('{P}')\"", f"mvn flyway:migrate -Dflyway.url={P}", "bash deploy.sh", "source deploy.sh",
                    ". deploy.sh", f"python3 - <<EOF\nconnect('{P}')\nEOF", f"echo \"x\" > /dev/null; python3 <<EOF\nconnect('{P}')\nEOF", f"cat x.sql | python3 -c 'import sys' # {P}",
                    f"bash <<'EOF'\nsqlcmd -S {P} -Q 'delete from t'\nEOF", f'bash <<< "sqlcmd -S {P} -Q \'delete from t\'"', f'echo "sqlcmd -S {P} -Q \'delete from t\'" | bash', f"cat run.sh | bash # {P}",
                    f"python3 -X new_intent.py -c \"import os; os.system('ssh {H} touch /tmp/x')\"", f"node --title new_intent.py -e \"require('child_process').execSync('ssh {H} touch x')\"", f"python3 {cwd}/new_intent.py '{P}'",
                    f"cat > /tmp/gate_probe_x.sh <<'EOF'\nssh {H} touch /tmp/x\nEOF\nbash /tmp/gate_probe_x.sh", f"rg --pre 'ssh {H} touch /tmp/x' x README.md", f"git push ssh://{H}/srv/repo HEAD:main", f"git -c core.sshCommand='ssh {H}' fetch",
                    f"sed -n 'e ssh {H} touch /tmp/x' README.md", f"sed 's/x/ssh {H} touch x/e' README.md", f"sort --compress-program='ssh {H}' f", "bash -c 'bash -c \"bash -c \\\"bash -c \\\\\\\"ssh 192.0.2.15 touch x\\\\\\\"\\\"\\\"\"'",
                    f"eval eval eval eval ssh {H} touch /tmp/x", f"echo ok # <<EOF\nsqlcmd -S {P} -Q 'delete from t'", f'echo "<<EOF"; sqlcmd -S {P} -Q "delete from t"', f"cat <<EOF\n$(sqlcmd -S {P} -Q 'delete from t')\nEOF",
                    f'printf "%s" <(sqlcmd -S {P} -Q "delete from t")', f"export SQLCMDSERVER={P}; sqlcmd -Q 'delete from t'", f"export H={P}; sqlcmd -S\"$H\" -Q 'delete from t'", "curl -XDELETE http://192.0.{2,3}.15/x"):
        assert refused(command), f"unknown tool touching production must be refused: {command!r}"
    assert refused(f'sqlcmd -S {P} -Q "select next value for dbo.s"') and refused(f"psql -h {P} -c \"select query_to_xml('delete from t', true, false, '')\""), "sequence advance and SQL run from a string"
    # 5. the opus review's parsing gaps
    for command in (f"sqlcmd -S `echo {P}` -Q \"delete from t\"", "sqlcmd -S `cat h.txt` -Q \"delete from t\"", f"sqlcmd -S $'\\x70gm-prod0001' -Q 'delete from t'", f"$'\\x73qlcmd' -S {P} -Q 'delete from t'", f"sqlcmd -S $'\\160gm-prod0001' -Q 'delete from t'",
                    f"sqlc\\\nmd -S {P} -Q \"delete from t\"", f"sqlcmd -S pgm-\\\nprod0001 -Q 'delete from t'", f"bash -lc \"sqlcmd -S {P} -Q 'delete from t'\"", f"sh -ec \"psql -h {P} -c 'delete from t'\"", f"bash -xc \"psql -h {P} -c 'drop table t'\"",
                    f"eval sqlcmd -S {P} -Q \"'delete from t'\"", f"env -S 'sqlcmd -S {P} -Q \"delete from t\"'", f"env -Ssqlcmd -S {P} -Q x", f"p=pgm-prod; q=0001; psql -h $p$q -c 'delete from t'", f"HOST={P} psql -h $HOST -c 'delete from t'",
                    f"export H={P}; psql -h \"${{H}}\" -c 'drop table t'", f"psql -h 'pgm-'prod0001 -c 'delete from t'", f"env PGHOST={P} psql -c 'delete from t'", f"nohup timeout 9 psql -h {P} -c 'delete from t'",
                    f"echo $(psql -h {P} -c 'delete from t')", f"PSQL=psql; $PSQL -h {P} -c 'delete from t'", f"/usr/bin/PSQL -h {P} -c 'delete from t'", f"cd /tmp && true; ssh {P} 'rm x'", f"(ssh {P} 'rm x')",
                    f"bash -c \"bash -c \\\"psql -h {P} -c 'delete from t'\\\"\""):
        assert refused(command), f"bypass must be refused: {command!r}"
    assert refused('psql -h "$PGHOST" -c "delete from t"'), "unresolved target + write is refused"
    assert refused("psql -h $(cat host.txt) -c 'delete from t'") and refused("aliyun rds DeleteAccount --DBInstanceId $ID") and refused('psql "$DATABASE_URL" -c "insert into t values (1)"') and refused('psql -h"$H" -c "delete from t"')
    assert allowed('psql -h "$PGHOST" -c "select 1"') and allowed("aliyun rds DescribeDBInstances --InstanceId $ID"), "unresolved target + read passes"
    assert allowed("psql -h localhost -c \"insert into t values ($X)\"") and allowed("curl -X POST http://localhost:8080/api -d $BODY"), "`$` in a statement or data is not a target"
    assert refused("psql -h 'pgm-prod0001 -c 'delete'"), "unreadable command with a remote tool is refused"
    assert allowed("echo 'unterminated"), "unreadable command with no remote tool is not this gate's business"
    # 5b. a bare assignment exported afterwards: `export NAME`, `declare -x`, `typeset -x`, or under `set -a` / `set -o allexport` (O2 review, M4)
    for command in (f"PGHOST={P}; export PGHOST; psql -c 'delete from t'", f"set -a; PGHOST={P}; psql -c 'delete from t'", f"PGHOST={P}; declare -x PGHOST; psql -c 'delete from t'",
                    f"PGHOST={P}; typeset -x PGHOST; psql -c 'delete from t'", f"set -o allexport; SQLCMDSERVER={P}; sqlcmd -Q 'delete from t'", f"set -ea; PGHOST={P}; psql -c 'delete from t'"):
        assert refused(command), f"exported later must be refused: {command!r}"
    assert allowed("PGHOST=localhost; export PGHOST; psql -c 'delete from t'") and allowed("set -a; PGHOST=localhost; psql -c 'delete from t'"), "an exported non-production host passes"
    # 5c. a bare assignment to a target variable counts as exported: the variable may be exported already by the process environment or an rc file (O2 round 1, M1)
    assert refused(f"PGHOST={P}; psql -c 'delete from t'", environ={"PGHOST": "localhost"}), "exported in the environment, reassigned bare"
    assert refused(f"PGHOST={P}; psql -c 'delete from t'") and refused(f"SQLCMDSERVER={H}; sqlcmd -Q 'delete from t'"), "not in the environment the gate sees (an rc file may export it)"
    assert allowed("PGHOST=localhost; psql -c 'delete from t'", environ={"PGHOST": "localhost"}) and allowed("PGHOST=localhost; psql -c 'delete from t'"), "a bare assignment to a non-production host passes"
    # 5d. two-letter remote tool names (xh) count as whole words only, not as substrings (O2 round 1, suggestion)
    assert allowed("cat exhibit 'oops") and allowed("grep xhr 'oops"), "an unreadable line whose words only contain xh is not this gate's business"
    assert refused("xh 'oops") and refused("cd x && xh 'oops") and refused("/usr/bin/xh 'oops"), "an unreadable line running xh is refused"
    real_load = g.load_table
    try:
        g.load_table = lambda path=None: (None, "生产特征表（测试）缺失", None)
        for command, noted in (("xh GET localhost:8080/x", True), ("cat exhibit", False)):
            code, out, _ = g.decide({"tool_name": "Bash", "tool_input": {"command": command}})
            assert code == 0 and ("写操作类命令会被拒绝" in out) == noted, ("a broken table is noted for a remote tool only, xh as a word", command, code, out)
    finally:
        g.load_table = real_load
    assert refused(f"curl https://{P}/a#b -X DELETE") and refused(f"curl https://{P}/a#b --data x=1"), "a `#` inside a word is no comment (bash): the rest of the command still counts (796bf2c85 final M1)"
    # 6. secrets (bottom line 3) and the environment
    for command in ("aliyun rds DescribeDBInstanceSSL --DBInstanceId pgm-dev00001", "aliyun kms GetSecretValue --SecretName x"):
        assert refused(command), command
    assert refused('psql -c "delete from t"', environ={"PGHOST": P}) and allowed('psql -c "select 1"', environ={"PGHOST": P}) and allowed('psql -c "delete from t"', environ={"PGHOST": "localhost"})
    assert allowed('psql -h localhost -c "delete from t"', environ={"PGHOST": P}), "an explicit host overrides the environment"
    assert refused('sqlcmd -Q "delete from t"', environ={"SQLCMDSERVER": "rm-prodhost1.sqlserver.rds.aliyuncs.com"})
    # 7. table missing or broken: writes are refused, reads pass, interpreters are refused, nothing is silently opened
    assert refused('psql -h localhost -c "insert into t values (1)"', None, "生产特征表缺失") and allowed('psql -h x -c "select 1"', None, "生产特征表缺失")
    assert refused("aliyun rds DeleteAccount --DBInstanceId x", None, "生产特征表缺失") and allowed("ls -la", None, "生产特征表缺失") and refused("python3 clean.py", None, "生产特征表缺失")
    assert refused("python3 -c 'print(1)'", None, "生产特征表缺失") and allowed("mvn -q test", None, "生产特征表缺失") and refused(f"python3 -I {intent} x", None, "生产特征表缺失")
    assert allowed("psql -h pgm-prod0001 -c 'delete from t'", []), "judge itself: an empty list refuses nothing (load_table turns an empty union into a problem, below)"
    # 7b. the local table empty and origin/main unreadable (git fails) or without a table: a problem, so writes and interpreters are refused, reads pass (O2 review, M2)
    with tempfile.TemporaryDirectory() as tmp:
        empty = os.path.join(tmp, "empty.json")
        Path(empty).write_text('{"targets": []}', encoding="utf-8")
        real_table, real_main, real_root = g.TABLE, g.main_table_text, g.REPO_ROOT
        try:
            g.TABLE = empty
            for main_result in ((None, "fatal: not a git repository"), (None, None), ('{"targets": []}', None)):
                g.main_table_text = lambda result=main_result: result
                table, problem, warning = load_table()
                assert table is None and problem and "为空" in problem, main_result
                assert (warning is not None) == (main_result[1] is not None), ("a git failure warns, a missing table does not", main_result)
                assert refused('psql -h localhost -c "delete from t"', table, problem) and refused("python3 clean.py", table, problem) and allowed('psql -h x -c "select 1"', table, problem), main_result
            g.TABLE = os.path.join(HERE, "prod_targets.json")
            g.main_table_text = lambda: (None, "fatal: x")
            table, problem, warning = load_table()
            assert table and problem is None and warning and "git 出错" in warning, "a git failure with a usable local table warns and keeps the local table"
            g.TABLE = empty
            g.main_table_text = lambda: (json.dumps({"targets": [{"id": "a", "patterns": ["pgm-prod0001"]}]}), None)
            table, problem, warning = load_table()
            assert table == ["pgm-prod0001"] and problem is None and warning, "an empty local table still takes origin/main's patterns"
            g.main_table_text, g.REPO_ROOT = real_main, tmp
            text, failure = g.main_table_text()
            assert text is None and failure and "输出不完整" not in failure, ("not a repository: git's own error is the failure", failure)
            subprocess.run(["git", "init", "-q", tmp], check=True)
            assert g.main_table_text() == (None, None), "a repository without origin/main: missing, no failure"
        finally:
            g.TABLE, g.main_table_text, g.REPO_ROOT = real_table, real_main, real_root
        assert load_table.__code__.co_names.count("environ") == 0, "PROD_TARGETS_FILE does not switch the origin/main union off (O2 review, suggestion 1)"
    # 8. the guard's own self-tests: the guard sources hold no real production sample, so they pass under the shipped table; trust (content equal to origin/main) is a second way
    real = load_table()[0]
    for path in (os.path.join(HERE, "prod_write_gate.py"), os.path.join(HERE, "gates_selftest.py"), os.path.join(HOOKS, "command_words.py"), os.path.join(HERE, "pre_tool_dispatch.py"), os.path.join(HERE, "main_checkout_gate.py")):
        assert allowed(f"python3 -I {path} --self-test", real), path
    assert refused(f"python3 -I {os.path.join(HERE, 'prod_write_gate.py')} -c 'x' {P}")
    assert refused(f"python3 -I {intent} 'x pgm-prod0001'"), "new_intent.py is no registered entry (intent 2026-10-26-132048): a production sample in its text is refused"
    # 9. trusted only as a registered entry (origin/main's trusted_entries), written exactly in one of its forms, while the script is the file on origin/main; new_intent mentions "import"
    real_main_blob, real_entries, mine = g.main_blobs, g.trusted_entries, ["import"]
    try:
        entry = {"script": "new_intent.py", "interpreter": ["python3", "-I"], "forms": [["{name}"]]}
        g.trusted_entries = lambda: ([entry], {"name": re.compile(r"[a-z]{1,9}")})
        g.main_blobs = lambda rels: {}
        assert refused(f"python3 -I {intent} x", mine), "not on origin/main: not trusted, and its text mentions the pattern"
        g.main_blobs = as_main
        assert allowed(f"python3 -I {intent} x", mine), "registered, written in its form, unchanged"
        assert refused(f"python3 -I {intent} x y", mine) and refused(f"python3 -I {intent} X", mine) and refused(f"python3 {intent} x", mine), "only a registered form: argument count, slot pattern, interpreter options"
        g.main_blobs = lambda rels: {rel: "0" * 40 for rel in rels}
        assert refused(f"python3 -I {intent} x", mine), "an edited file (content differs from origin/main) is not trusted"
        g.main_blobs = as_main
        Path(cwd, "new_intent.py").write_text("import os\n", encoding="utf-8")
        assert refused(f"python3 -I {cwd}/new_intent.py x", mine) and refused("python3 -I new_intent.py x", mine), "the registered path only, not the same name elsewhere"
        g.trusted_entries = lambda: ([], {})
        assert refused(f"python3 -I {intent} x", mine), "not registered"
        g.trusted_entries = lambda: ([dict(entry, forms=[["{any}"]])], {"any": re.compile(".+")})
        assert refused(f"python3 -I {intent} 'a;b'", mine) and allowed(f"python3 -I {intent} ab", mine), "a shell metacharacter anywhere on the line: no entry, whatever the slot allows"
        assert g.trusted_entry(f"python3 -I '~/{os.path.relpath(intent, os.path.expanduser('~'))}' ab", cwd) is None, "a quoted `~` is not the home: no expansion on the line (50a6af8c3 review M1)"
        g.trusted_entries, real_text = real_entries, g.main_table_text
        g.main_table_text = lambda root=None: (None, None)
        assert g.trusted_entries() == ([], {}), "origin/main's table only: the local one, which a branch may edit, registers nothing"
        g.main_table_text = real_text
    finally:
        g.main_blobs, g.trusted_entries = real_main_blob, real_entries
    for command in (f"git tag -f origin/main HEAD && echo {P}", f"git update-ref refs/remotes/origin/main HEAD; echo {P}", f"git grep -nO'sqlcmd -S {P}' foo", f"git grep -inOx {P} foo"):
        assert refused(command), f"origin/main must not be rewritable, -O in a bundle: {command!r}"
    assert "refs/remotes/origin/main" in inspect.getsource(real_blobs) and "refs/remotes/origin/main" in inspect.getsource(g.main_table_text), "the trust anchor is read by its full ref name, a tag called origin/main cannot shadow it"
    # 10. direct execution of a local file, git programs, ssh options, GNU long-option prefixes (opus round 2, items 3 to 6)
    Path(cwd, "direct.sh").write_text(f"sqlcmd -S {P} -Q 'delete from t'\n", encoding="utf-8")
    Path(cwd, "tame.sh").write_text("echo hi\n", encoding="utf-8")
    for command in (f"echo \"sqlcmd -S {P} -Q 'delete from t'\" > x && chmod +x x && ./x", "./direct.sh", f"{cwd}/direct.sh", "ls && ./direct.sh", f"chmod +x x; ./x {P}", f"cd /tmp && ./run.sh {P}",
                    f"git rebase -x \"sqlcmd -S {P} -Q 'delete from t'\" HEAD~1", f"git rebase --exec \"sqlcmd -S {P}\" HEAD~1", f"git rebase --ex \"sqlcmd -S {P}\" HEAD~1", f"git bisect run sqlcmd -S {P} -Q x",
                    f"git filter-branch --tree-filter \"sqlcmd -S {P} -Q 'delete from t'\"", f"GIT_EXTERNAL_DIFF=\"sqlcmd -S {P} -Q 'delete from t'\" git diff", f"GIT_PAGER=\"sqlcmd -S {P}\" git log", f"EDITOR=\"sqlcmd -S {P}\" git commit",
                    f"git diff --ext-diff && echo {P}", f"git grep -O'sqlcmd -S {P}' x", f"git submodule foreach 'sqlcmd -S {P}'", f"git config alias.q '!sqlcmd -S {P}' && git q", f"git q {P}",
                    f"ssh -o KnownHostsCommand=\"/bin/sh -c 'touch /tmp/x'\" ops@{H} ls", f"ssh -oKnownHostsCommand=x ops@{H} ls", f"ssh -o PKCS11Provider=/tmp/evil.so ops@{H} ls", f"ssh -F evil.cfg ops@{H} ls", f"ssh -I /tmp/evil.so ops@{H} ls",
                    f"ssh -o 'Include evil.cfg' ops@{H} ls", f"ssh -o 'Match exec x' ops@{H} ls", f"ssh -o ProxyJump=x ops@{H} ls",
                    f"ssh ops@{H} sort --outp=/etc/passwd x", f"ssh ops@{H} date --se=@0", f"ssh ops@{H} journalctl --rot", f"ssh ops@{H} journalctl --vac=1G", f"ssh ops@{H} ss --ki", f"ssh ops@{H} file --comp x", f"ssh ops@{H} pg_dump --fi=/tmp/x db",
                    f"wget --post-d=x https://{P}/x", f"wget --meth=DELETE https://{P}/x", f"wget --body-d=x https://{P}/x", f"sort --compress-prog='ssh {H}' f", f"sort --compress='ssh {H}' f"):
        assert refused(command), f"must be refused: {command!r}"
    for command in ("./tame.sh", f"git log --oneline && echo {P}", f"git diff --stat HEAD~1 -- {P}", f"git rebase main && echo {P}", f"git commit -m 'rebase -x {P}'",
                    f"ssh -o ConnectTimeout=5 -o BatchMode=yes -o StrictHostKeyChecking=no -i key -p 22 ops@{H} uptime", f"ssh -oPort=22 ops@{H} 'tail -n 5 x'", f"ssh -o 'User ops' ops@{H} uptime",
                    f"ssh ops@{H} 'sort -r x'", f"ssh ops@{H} 'journalctl --since today --no-pager'", f"ssh ops@{H} 'date --iso-8601=seconds'", f"ssh ops@{H} 'journalctl --reverse -n 5'", f"for f in a b; do sqlcmd -S {P} -Q 'select 1'; done"):
        assert allowed(command), f"must pass: {command!r}"
    # the SQL lexer and brace expansion
    assert mask_sql("select 'a--b'; select 1 -- c").count("select") == 2 and mask_sql("select 1 /* x */ + 2") is not None and mask_sql("select 'abc") is None and mask_sql("select /* x") is None
    assert mask_sql('select pg_catalog."nextval"(1)') == 'select pg_catalog. nextval (1)' and mask_sql("select $$a;b$$") == "select ''" and mask_sql("select $x$a$x$ , $1") == "select '' , $1"
    assert brace_variants("a{1,2}b") == ["a1b", "a2b"] and brace_variants("192.0.{2..4}.15") == [TEST_IP, "192.0.3.15", "192.0.4.15"] and brace_variants("plain") == ["plain"]
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
        firsts = shipped_firsts()
        assert problem is None and table and firsts and all(v.lower() in table for v in firsts.values()), "the shipped table loads and every target's first pattern is in it"
        assert could_concern("sqlcmd -s x") and all(could_concern(v.lower()) for v in firsts.values()) and could_concern("echo $x") and could_concern("curl a{1,2}") and not could_concern("echo hello") and not could_concern("git status")
        # the pre-filter names every interpreter, shell and remote tool the gate acts on, and `. file` (O3 review, M1)
        for name in sorted(g.INTERPRETERS | g.SHELLS | g.REMOTE_TOOLS | g.RUNS_OTHERS):
            word = "python3" if name == "python" else name
            for command in (f"{word} deploy", f"nohup {word} deploy", f"cd x && {word} deploy", f"({word} deploy)"):
                assert could_concern(plain(command)), command
            assert g.INTERPRETER.match(word) or name not in g.INTERPRETERS, word
        assert could_concern(plain("cat sshd_conf 'oops")) and not could_concern(plain("ls ./x")) and not could_concern(plain("echo a. b")), "a substring of a remote tool counts; a dot elsewhere does not"
        assert could_concern(plain("xh POST x")) and could_concern(plain("a&&xh x")) and not could_concern(plain("cat exhibit xhr")), "xh only as a word (O2 round 1, suggestion)"
        # hook process end to end, with the shipped and a missing table
        script = os.path.join(HERE, "prod_write_gate.py")
        def hook(command, table_path=None, tool="Bash"):
            env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", "")}
            if table_path:
                env["PROD_TARGETS_FILE"] = table_path
            return subprocess.run([sys.executable, "-I", script], input=json.dumps({"tool_name": tool, "tool_input": {"command": command}, "cwd": cwd}), capture_output=True, text=True, env=env)
        result = hook("aliyun rds DeleteAccount --DBInstanceId " + firsts["legacy-rds-sqlserver"])
        assert result.returncode == 2 and "底线 4" in result.stderr, result
        assert hook("aliyun rds DescribeDBInstances --DBInstanceId " + firsts["legacy-rds-sqlserver"]).returncode == 0
        assert hook("python3 -c \"connect('" + firsts["legacy-rds-sqlserver"] + "')\"").returncode == 2
        assert hook("ls").returncode == 0 and hook("x", tool="Read").returncode == 0
        gone = hook("psql -c 'delete from t'", os.path.join(tmp, "missing.json"))
        assert gone.returncode == 2 and "缺失或不合格" in gone.stderr, gone
        warned = hook("psql -c 'select 1'", os.path.join(tmp, "missing.json"))
        assert warned.returncode == 0 and "告警" in warned.stdout, warned
        Path(tmp, "empty.json").write_text('{"targets": []}', encoding="utf-8")
        no_git = {"LC_ALL": "C", "PATH": tmp, "PROD_TARGETS_FILE": os.path.join(tmp, "empty.json")}
        for command, code in (("psql -h db.example.org -c 'delete from t'", 2), ("python3 clean.py", 2), ("psql -h db.example.org -c 'select 1'", 0)):
            run = subprocess.run([sys.executable, "-I", script], input=json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": cwd}), capture_output=True, text=True, env=no_git)
            assert run.returncode == code and ("为空" in run.stderr if code else "为空" in run.stdout and "git 出错" in run.stdout), (command, run)
        for raw in ("not json", "[1]"):
            bad = subprocess.run([sys.executable, "-I", script], input=raw, capture_output=True, text=True, env={"LC_ALL": "C"})
            assert bad.returncode == 2, raw
    g.main_blobs = real_blobs
    print("prod_write_gate self-test: ok")

def seventeen_self_test():
    """17 例全拦：真实特征表（本仓库 prod_targets.json 加 origin/main 并集）与测试表下各跑；读不懂、未拦的例子逐条列出（只列轮次和编号，不打印命令，免得带出生产值）。
    真实表下：SAMPLE_IDS 三个 target 的第一项作 P、H、D 跑一轮，再用每个 target 的第一项填满 P、H、D 各跑一轮（O2 复审 M3），
    每个 target 的每种形态（IP、主机名）的第一项若不是该 target 的第一项，也各填满跑一轮（O2 首轮留意 ①）。"""
    cwd = tempfile.mkdtemp()
    real = load_table()[0]
    firsts = shipped_firsts()
    assert real and firsts and all(v.lower() in real for v in firsts.values()), "真实特征表每个 target 至少一项，且都在门用的特征里"
    assert set(SAMPLE_IDS) <= set(firsts), f"真实特征表须有这几个 target：{SAMPLE_IDS}"
    cases = seventeen(*real_samples())
    assert sorted(cases) == list(range(1, 18)) and sum(len(v) for v in cases.values()) >= 17
    test_table = ["pgm-prod0001", "rm-prodhost1", "db.prod.example.com", TEST_IP, "prod_db_0001"]
    runs = [("真实表 P/H/D", cases, real)] + [(f"真实表 {i}", seventeen(v, v, v), real) for i, v in firsts.items()]
    forms = shipped_forms()
    assert {k.rsplit(" ", 1)[1] for k in forms} == {"ip", "name"}, "the shipped table has IP-shaped and name-shaped patterns"
    runs += [(f"真实表 {k}", seventeen(v, v, v), real) for k, v in forms.items() if v not in firsts.values()]
    runs.append(("测试表", seventeen("pgm-prod0001", TEST_IP, "prod_db_0001"), test_table))
    missed = [(label, n, k) for label, run, table in runs for n, cs in run.items() for k, c in enumerate(cs) if judge(c, table, None, cwd, {}) is None]
    assert not missed, f"生产写门 17 例有未拦的（轮次、例号、第几条）：{missed}"
    total = sum(len(cs) for _, run, _ in runs for cs in run.values())  # V02 is checked by these counts, not by the exit code alone (review of 2f6742ea8)
    assert len(cases) >= 17 and total >= 17 * len(runs) and total - len(missed) == total, f"生产写门 17 例：{len(cases)} 例，拦下 {total - len(missed)}/{total}"
    print(f"prod_write_gate 17 例: ok（{len(cases)} 例 {sum(len(v) for v in cases.values())} 条复现命令 × {len(runs)} 轮，拦下 {total - len(missed)}/{total}：真实表 {len(runs) - 1} 轮、测试表 1 轮）")

def layout_self_test():
    """门本体的版式与出处：行数不超 policy.json 的上限；一行一条语句、块体另起一行（O2 复审 M1）；守护源码里没有生产样本，拆段拼接也算（O2 复审 M3）。"""
    gate_path = os.path.join(HERE, "prod_write_gate.py")
    limit = policy()["max_lines"]["prod_write_gate"]
    source = Path(gate_path).read_text(encoding="utf-8")
    assert source.count("\n") <= limit, f"prod_write_gate.py 超过 {limit} 行"
    lines = source.split("\n")
    crowded = []
    for node in ast.walk(ast.parse(source)):
        if isinstance(node, ast.stmt):
            line = lines[node.lineno - 1].encode("utf-8")
            if node.col_offset != len(line) - len(line.lstrip(b" ")):
                crowded.append(node.lineno)
    assert not crowded, f"prod_write_gate.py 有一行多条语句或块体与条件同行：{sorted(set(crowded))[:20]}"
    folded = [re.sub(r"[^0-9a-z]", "", v.lower()) for v in load_table()[0]]
    sources = [os.path.join(HERE, name) for name in sorted(os.listdir(HERE)) if name.endswith(".py")] + [os.path.join(HOOKS, "command_words.py")]
    for path in sources:
        text = re.sub(r"[^0-9a-z]", "", Path(path).read_text(encoding="utf-8").lower())
        found = sum(1 for v in folded if v in text)
        assert not found, f"{os.path.basename(path)} 含 {found} 项生产特征（拆段拼接也算），改为运行时从 prod_targets.json 按 id 取"
    # what a trusted script loads or is compared with decides whether the gate lets it pass: every such file needs the opus final review (K18 follow-up, a3be80fcd final suggestion 1)
    final, entries = json.loads(Path(ROOT, "policy.json").read_text(encoding="utf-8"))["review"]["final_paths"], json.loads(Path(HERE, "prod_targets.json").read_text(encoding="utf-8"))["trusted_entries"]["entries"]
    scripts = [os.path.join(ROOT, DOCS_ROOT, e["script"]) for e in entries]
    assert all(g.closure(s) is not None for s in scripts), "every registered entry's closure can be read"
    loaded = {os.path.relpath(f, ROOT) for s in scripts for f in g.closure(s) | {s} if os.path.exists(f)} | {f"{DOCS_ROOT}/{d}" for e in entries for d in e["trust_dirs"]}
    assert all(rel.startswith(tuple(final)) for rel in loaded), ("files a trusted entry loads or reads, missing from policy.json review.final_paths", sorted(r for r in loaded if not r.startswith(tuple(final))))
    slots = {k: re.compile(v) for k, v in json.loads(Path(HERE, "prod_targets.json").read_text(encoding="utf-8"))["trusted_entries"]["slots"].items()}
    assert slots["sql"].fullmatch("credential_columns_scan.sql") and not any(slots["sql"].fullmatch(v) for v in ("/tmp/x.sql", "../x.sql", "a/b.sql")), "the evidence tool's SQL is a bare name under evidence/ (50a6af8c3 final M4)"
    assert all("-I" in e["interpreter"] and os.path.isabs(os.path.expanduser(e["interpreter"][0])) for e in entries), "an entry pins its interpreter by path and runs it isolated (-I: no PYTHON* variables, no user site)"
    print("prod_write_gate layout: ok")

def trust_self_test():
    """可信脚本只认 cwd 与 CLAUDE_PROJECT_DIR 同一仓库（同一个真实 --git-common-dir），特征表并上两者的 origin/main（调度裁定，D25）。全部在临时仓库里做，生产样本只用虚构值；主检出 M 与用 git worktree add 建的工作区 W 共用 .git、路径不同，算同一仓库；伪造仓库 F 内容相同但有独立的 .git，不算。"""
    fake, only = "fake-prod-db.example.invalid", "only-on-t.example.invalid"  # fictitious hosts
    script = os.path.join(HERE, "prod_write_gate.py")
    doc = DOCS_ROOT + "/new_intent.py"  # one of the gate's DOC_SCRIPTS, at its fixed path
    legacy = DOCS_ROOT + "/phase1a/infra/new_legacy_read.py"  # another DOC_SCRIPT: its arguments name production instances by design
    register = DOCS_ROOT + "/phase1a/infra/build_legacy_read_register.py"  # a script it runs
    tool, helper = DOCS_ROOT + "/t/tools/scan.py", DOCS_ROOT + "/lib/helper.py"  # a read-only evidence tool, a module it imports from a directory above
    sql, added = DOCS_ROOT + "/t/sql/q.sql", DOCS_ROOT + "/t/sql/new.sql"  # in the directory the tool reads (table trust_dirs)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        main, work, forged, other, loose = (os.path.join(tmp, n) for n in ("M", "W", "F", "T", "N"))
        # the registered entries of the temporary repositories' origin/main table: the document script, the register script, a read-only tool reading t/sql/
        entries = [{"script": "new_intent.py", "interpreter": ["python3"], "forms": [[]]},
                   {"script": "phase1a/infra/new_legacy_read.py", "interpreter": ["python3"], "forms": [["--session={name}"] + ["{text}"] * 6, ["{text}"]]},
                   {"script": "t/tools/scan.py", "interpreter": ["python3"], "forms": [["{text}"]], "trust_dirs": ["t/sql/"]}]
        tools = {"slots": {"name": "[a-z]{1,9}", "text": "[^$;|&#'\"]{1,60}"}, "entries": entries}
        built = DOCS_ROOT + "/adr/b.py"  # a script the document script spells by path (as build_records runs adr/build_adrs.py)
        body = {doc: f"import os, json\nB = os.path.join('x', 'adr', 'b.py')\nos.system(\"psql -h {fake} -c 'delete from t'\")\n", legacy: "print('register')\n",
                register: "print('built')\n", built: "x = 1\n", tool: "from lib import helper\n", helper: "x = 1\n", sql: "select 1\n",
                "scripts/gates/prod_targets.json": json.dumps({"targets": [{"id": "fake", "patterns": [fake]}], "trusted_entries": tools})}
        mkrepo(main, body)
        mkrepo(forged, body)
        put(os.path.join(loose, doc), body[doc], 0o644)  # the same file in a directory that is no repository
        mkrepo(other, {"scripts/gates/prod_targets.json": json.dumps({"targets": [{"id": "t", "patterns": [only]}]})})
        subprocess.run(["git", "-C", main, "worktree", "add", "-q", "--detach", work], check=True, capture_output=True)
        table = os.path.join(tmp, "fake_targets.json")
        Path(table).write_text(json.dumps({"targets": [{"id": "fake", "patterns": [fake]}]}), encoding="utf-8")
        def hook(command, cwd, project, process_cwd=None, tool="Bash", where=None, **extra):  # python3 resolves to /usr/bin/python3, the only interpreter a trusted script runs on
            env = {"LC_ALL": "C", "PATH": "/usr/bin:/bin:" + os.environ.get("PATH", ""), "PROD_TARGETS_FILE": table, **({"CLAUDE_PROJECT_DIR": project} if project is not None else {}), **extra}
            payload = {"tool_name": tool, "tool_input": {"command": command, **({"cwd": where} if where else {})}, **({"cwd": cwd} if cwd else {})}
            return subprocess.run([sys.executable, "-I", script], input=json.dumps(payload), capture_output=True, text=True, env=env, cwd=process_cwd or tmp).returncode
        run_doc = "python3 " + doc
        for cwd, project, code, why in ((work, main, 0, "a worktree of the project's repository: one .git, other path, trusted"), (main, work, 0, "the other way round"),
                                        (main, main, 0, "the project itself"), (forged, main, 2, "a forged repository with its own .git is not trusted"),
                                        (work, forged, 2, "the project forged, cwd real"), (forged, forged + "/", 0, "the forged repository as its own project (control)"),
                                        (work, None, 2, "no CLAUDE_PROJECT_DIR"), (work, "", 2, "an empty CLAUDE_PROJECT_DIR"), (loose, main, 2, "cwd in no repository")):
            assert hook(run_doc, cwd, project) == code, (why, cwd, project)
        assert hook(run_doc, None, main, process_cwd=work) == 2, "no cwd in the hook input: not trusted, even when the process runs in the worktree"
        assert hook(f"python3 {legacy} --session=t ro '{fake} 库' a b c d", work, main) == 0, "the legacy-read register script may name a production instance"
        scan, register_run = f"python3 {tool} {fake}", f"python3 {legacy} '{fake}'"
        cases = ((None, "", scan, "tool, imports and read directory as on main"), (helper, "x = 2\n", scan, "an imported module edited"),
                 (sql, "delete from t\n", scan, "a file it reads edited"), (DOCS_ROOT + "/json.py", "", run_doc, "a module planted beside a document script"),
                 (register, "import os\n", register_run, "a script it runs, edited (323f466a2 review, suggestion)"), (added, "", scan, "a file added where it reads"))
        for rel, text, run, why in cases:
            if rel:
                put(os.path.join(work, rel), text, 0o644)
            assert hook(run, work, main) == (2 if rel else 0), why
            gitc(work, "checkout", "-q", "--", ".")
            gitc(work, "clean", "-fdq")
        # what Python also loads, or how a line swaps it (cfcbcf473 first round M1-M2, final M1-M3): each case on a clean worktree, refused; the controls pass
        def pyc(rel, flags):  # a byte-code cache header: magic, flags (1 = hash-based without the source check, 3 = with it), hash
            put(os.path.join(work, rel), "", 0o644)
            Path(work, rel).write_bytes(b"\0" * 4 + flags.to_bytes(4, "little") + b"\0" * 8)
        link = lambda rel: (os.remove(os.path.join(work, rel)) if os.path.exists(os.path.join(work, rel)) else None, os.symlink(os.path.join(main, helper), os.path.join(work, rel)))  # noqa: E731
        loads = ((lambda: put(os.path.join(work, built), "x = 2\n", 0o644), run_doc, 2, "a script it spells by path, edited"),
                 (lambda: pyc(DOCS_ROOT + "/json/__init__.pyc", 0), run_doc, 2, "a package __init__ with a loader suffix other than .py"),
                 (lambda: put(os.path.join(work, DOCS_ROOT, "lib/helper.cpython-39-darwin.so"), "", 0o644), scan, 2, "an extension module beside the imported one"),
                 (lambda: pyc(DOCS_ROOT + "/lib/__pycache__/helper.cpython-39.pyc", 1), scan, 2, "an unchecked hash-based .pyc"),
                 (lambda: pyc(DOCS_ROOT + "/lib/__pycache__/helper.cpython-39.pyc", 3), scan, 0, "a checked hash-based .pyc (control)"),
                 (lambda: link(helper), scan, 2, "an imported module that is a symlink (to main's own file)"), (lambda: link(DOCS_ROOT + "/t/sql/l.sql"), scan, 2, "a symlink where it reads"),
                 (lambda: None, f"PYTHONPATH=/tmp {run_doc}", 2, "PYTHONPATH on the line"), (lambda: None, f"export PYTHONPATH=/tmp; {run_doc}", 2, "PYTHONPATH exported earlier"),
                 (lambda: None, f"python3 -X pycache_prefix=/tmp {doc}", 2, "-X pycache_prefix"), (lambda: None, f"cd {main} && {run_doc}", 2, "a relative script after cd"),
                 (lambda: None, f"{{ cd {main}; {run_doc}; }}", 2, "cd in a group (905ad6209 review)"), (lambda: None, f"command {run_doc}", 2, "a wrapper before the interpreter"),
                 (lambda: None, f"env -C {main} {run_doc}", 2, "env -C"), (lambda: None, f"PATH={tmp}/bin:/usr/bin {run_doc}", 2, "PATH on the line"),
                 (lambda: put(os.path.join(tmp, "bin/python3"), "#!/bin/sh\necho FAKE\n"), f"{tmp}/bin/python3 {doc}", 2, "another interpreter than the registered one"),
                 (lambda: put(os.path.join(work, doc + "#x"), body[doc]), f"python3 {doc}#x", 2, "a `#` inside the word: bash runs new_intent.py#x (796bf2c85 final M1)"),
                 (lambda: put(os.path.join(work, doc + "\r"), body[doc]), f"python3 {doc}\r", 2, "a carriage return: bash runs new_intent.py\\r"))
        for setup, run, code, why in loads:
            setup()
            assert hook(run, work, main) == code, why
            gitc(work, "checkout", "-q", "--", ".")
            gitc(work, "clean", "-fdqx")
        # PYTHON* variables, the user site, PATH: no longer judged by the gate; an entry runs its pinned interpreter with -I (-S when it needs no site), checked on the shipped table (layout)
        assert g.trusted_entry("", tmp) is None and g.trusted_entry(f"python3 {doc} > out", tmp) is None, "an empty line, a redirection: no entry (an empty line does not crash)"
        os.symlink(os.path.join(work, legacy), os.path.join(work, DOCS_ROOT, "lr.py"))  # runs beside the link, with its neighbours
        assert hook(f"python3 {DOCS_ROOT}/lr.py '{fake}'", work, main) == 2, "a symlink to a registered script is no entry (50a6af8c3 final M2)"
        os.remove(os.path.join(work, DOCS_ROOT, "lr.py"))
        put(os.path.join(work, DOCS_ROOT, "sub/inner/keep"), "", 0o644)
        put(os.path.join(work, DOCS_ROOT, "sub/new_intent.py"), body[doc], 0o644)
        os.symlink(os.path.join(work, DOCS_ROOT, "sub/inner"), os.path.join(work, DOCS_ROOT, "ln"))  # the kernel resolves ln before its `..`: sub/new_intent.py runs
        assert hook(f"python3 {DOCS_ROOT}/ln/../new_intent.py", work, main) == 2, "a `..` after a symlinked directory is resolved as the kernel does (285242ecb final M2)"
        os.remove(os.path.join(work, DOCS_ROOT, "ln"))
        shutil.rmtree(os.path.join(work, DOCS_ROOT, "sub"))
        assert hook(run_doc, loose, main, tool=COMMAND_TOOLS[2], where=work) == 0 and hook(run_doc, loose, main, tool=COMMAND_TOOLS[2]) == 2, "the terminal panel: its own cwd"
        # a worktree of M whose path is F's path, a newline and more: rev-parse prints the top level on two lines, so a reader taking the second line would anchor on F (88edef207 final M1)
        shifted = forged + "\nx"
        subprocess.run(["git", "-C", main, "worktree", "add", "-q", "--detach", shifted], check=True, capture_output=True)
        assert hook("python3 " + os.path.join(forged, doc), shifted, main) == 2 and hook("python3 " + os.path.join(forged, doc), forged, forged) == 0, "a newline in a path: no trust (control: F itself)"
        # a clean filter must not make an edited script hash like main's: hash-object --no-filters (88edef207 final S2)
        gitc(main, "config", "filter.strip.clean", "grep -v EVIL")
        put(os.path.join(work, ".gitattributes"), "*.py filter=strip\n", 0o644)
        Path(work, doc).write_text(body[doc] + "print('EVIL')\n", encoding="utf-8")
        assert hook(run_doc, work, main) == 2, "an edited script is not trusted, whatever a clean filter says"
        # the table: cwd's and CLAUDE_PROJECT_DIR's origin/main are unioned in, so only ever more is refused
        write = f"psql -h {only} -c 'delete from t'"
        assert hook(write, other, main) == 2 and hook(write, main, other) == 2 and hook(write, main, main) == 0, "a pattern only on the origin/main of cwd's or the project's repository"
        assert hook(write, None, main, process_cwd=other) == 2, "no cwd in the hook input: the process cwd's repository is unioned in"
    print("prod_write_gate trust: ok")

def prod_write_gate_test():
    words_self_test()
    gate_self_test()
    trust_self_test()
    seventeen_self_test()
    layout_self_test()

DISPATCH = os.path.join(HERE, "pre_tool_dispatch.py")  # ── pre_tool_dispatch ──
PROD_P, PROD_H, PROD_D = real_samples()

def payload(command, **extra):
    return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": extra.pop("cwd", HERE), **extra})

def pre_tool_dispatch_test():
    # 1. the pre-filter never hides a refusal: for every command the dispatcher decides exactly what the gate decides on its own
    corpus = [c for cs in seventeen(PROD_P, PROD_H, PROD_D).values() for c in cs]
    corpus += ["ls", "git status", "echo hi", "cat api.py", "npm test", "", "   ", "python3 x.py", f"git commit -m '{PROD_P} 说明'", f"psql -h {PROD_P} -c 'select 1'", f"aliyun rds DeleteAccount --DBInstanceId {PROD_P}",
               f"aliyun rds DescribeDBInstances --DBInstanceId {PROD_P}", f"python3 -c \"connect('{PROD_P}')\"", f"xargs -I{{}} sqlcmd -S {PROD_P} -Q {{}}", f"sqlcmd -S `echo {PROD_P}` -Q 'delete from t'",
               f"bash -lc \"psql -h {PROD_P} -c 'drop table t'\"", f"$'\\x73qlcmd' -S {PROD_P} -Q 'delete from t'", f"bash <<'EOF'\nsqlcmd -S {PROD_P} -Q 'delete from t'\nEOF", f"env -S 'sqlcmd -S {PROD_P} -Q x'",
               f"curl -XPOST https://{PROD_P}/x", f"al\"\"iyun rds DeleteAccount --DBInstanceId {PROD_P}", "PSQL -c x", "codex exec p", "git push --no-verify", "gh pr create --title x"]
    with tempfile.TemporaryDirectory() as elsewhere:
        for command in corpus:
            raw = payload(command, cwd=elsewhere)
            assert d.dispatch(raw) == g.decide(json.loads(raw)), command
        # 1b. what the old pre-filter hid (O3 review, M1): interpreters outside its own list, `. file`, a command shlex cannot read; each script names production
        for name in ("deploy", "deploy.lua"):
            put(os.path.join(elsewhere, name), f"ssh {PROD_H} rm -rf /srv/x\n", 0o644)
        hidden = ["lua deploy.lua", "dash deploy", "ksh deploy", ". deploy", "nohup dash deploy", "lua deploy.lua && echo ok", "cat sshd_conf 'oops", "sudo -u x ksh deploy", "(. deploy)"]
        for command in hidden:
            raw = payload(command, cwd=elsewhere)
            assert g.decide(json.loads(raw))[0] == 2 and d.dispatch(raw) == g.decide(json.loads(raw)), command
    # 2. every one of the 17 cases is refused through the dispatcher, with the gate's own reason
    for number, commands in seventeen(PROD_P, PROD_H, PROD_D).items():
        for command, tool in ((c, t) for c in commands for t in COMMAND_TOOLS):  # Monitor and the terminal panel run shell commands as Bash does (intent 160437)
            code, out, err = d.dispatch(payload(command, tool_name=tool))
            assert code == 2 and err.strip(), (number, command, tool)
    # 3. ordinary commands and other tools start nothing; checks that used to hang off the dispatcher are no longer here
    for command in ("ls", "echo hi", "git status", "npm test", "codex exec p", "git push --no-verify", "gh pr create --title x"):
        assert d.dispatch(payload(command)) == (0, "", ""), command
    # preview_start runs the named .claude/launch.json configuration: judged as that command; a url starts nothing; an unreadable configuration is refused
    with tempfile.TemporaryDirectory() as launch, tempfile.TemporaryDirectory() as other:
        configs = [{"name": "w", "runtimeExecutable": "psql", "runtimeArgs": ["-h", PROD_H, "-c", "delete from t"]}, {"name": "ok", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"]},
                   {"name": "args", "runtimeExecutable": "psql", "args": ["-h", PROD_H, "-c", "delete from t"]}, {"name": "prog", "runtimeExecutable": "sh", "runtimeArgs": ["-c"], "program": f"psql -h {PROD_H} -c 'delete from t'"},
                   {"name": "envw", "env": {"PGHOST": PROD_H}, "runtimeExecutable": "psql", "runtimeArgs": ["-c", "delete from t"]},
                   {"name": "twice", "runtimeExecutable": "npm"}, {"name": "TWICE", "runtimeExecutable": "npm"},
                   {"name": "cwdpy", "runtimeExecutable": "/usr/bin/python3", "runtimeArgs": [os.path.join(ROOT, DOCS_ROOT, "new_intent.py"), PROD_H], "cwd": "sub"}, {"name": "var", "runtimeExecutable": "npm", "runtimeArgs": ["${env:X}"]}]
        put(os.path.join(launch, ".claude", "launch.json"), json.dumps({"configurations": configs}), 0o644)
        put(os.path.join(other, ".claude", "launch.json"), json.dumps({"configurations": [{"name": "w", "runtimeExecutable": "npm"}]}), 0o644)
        put(os.path.join(other, "unnamed", ".claude", "launch.json"), json.dumps({"configurations": [{"runtimeExecutable": "psql", "runtimeArgs": ["-h", PROD_H]}, {"name": "ok", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"]}]}), 0o644)
        saved, os.environ["CLAUDE_PROJECT_DIR"] = os.environ.get("CLAUDE_PROJECT_DIR"), launch  # the app reads the session's working directory
        for tool_input, where, code in (({"name": "w"}, launch, 2), ({"name": "ok"}, launch, 0), ({"url": "http://localhost:3000"}, launch, 0), ({"name": "missing"}, launch, 2),
                                        ({"name": "args"}, launch, 2), ({"name": "prog"}, launch, 2), ({"name": "w"}, other, 2), ({"name": "ok"}, other, 2), ({"name": "W"}, launch, 2),
                                        ({"name": "envw"}, launch, 2), ({"name": "twice"}, launch, 2), ({"name": "cwdpy"}, launch, 2), ({"name": "var"}, launch, 2)):  # other: a second launch.json disagrees or lacks the name; W: case ignored as the app does
            assert d.dispatch(json.dumps({"tool_name": LAUNCH_TOOL, "tool_input": tool_input, "cwd": where}))[0] == code, (tool_input, where)
        assert g.launch_command({"name": "ok"}, os.path.join(other, "unnamed"))[0] is None, "an unnamed entry: the app derives its name, so which one runs cannot be seen (69af6c8da review M2)"
        assert g.launch_command({"name": "cwdpy"}, launch)[0].startswith("cd sub && "), "the configuration's cwd runs first: a relative script there is another file"
        os.environ.pop("CLAUDE_PROJECT_DIR") if saved is None else os.environ.update(CLAUDE_PROJECT_DIR=saved)
    monitor_ws = json.dumps({"tool_name": "Monitor", "tool_input": {"ws": {"url": "wss://x.example.invalid"}, "description": "d", "timeout_ms": 1000}})  # runs no command
    for raw in (monitor_ws, json.dumps({"tool_name": "Agent", "tool_input": {"description": "复审", "prompt": "p"}}), json.dumps({"tool_name": "Read", "tool_input": {"file_path": "x"}}),
                json.dumps({"tool_name": "Bash", "tool_input": {"command": "ls", "description": "跑测试", "run_in_background": True}})):
        assert d.dispatch(raw) == (0, "", ""), raw
    # a Bash call without a string command fails closed (O3 review, suggestion 2; it used to pass)
    for raw in (json.dumps({"tool_name": "Bash", "tool_input": {}}), json.dumps({"tool_name": "Bash", "tool_input": {"command": 1}}), json.dumps({"tool_name": "Bash"}), json.dumps({"tool_input": {"command": None}})):
        code, out, err = d.dispatch(raw)
        assert code == 2 and "按拒绝处理" in err and g.decide(json.loads(raw))[0] == 2, raw
    # 4. unreadable input is refused; a gate that cannot be imported, crashes or hangs exits 3 (settings.json's entry then runs its own pre-filter, C3)
    for raw in ("not json", "[1]", "{"):
        code, out, err = d.dispatch(raw)
        assert code == 2 and "拒绝" in err, raw
    def broken_import():
        raise ImportError("no prod_write_gate")
    class Crashing:
        plain = staticmethod(g.plain)
        @staticmethod
        def could_concern(text):
            return True
        @staticmethod
        def decide(payload):
            raise RuntimeError("boom")
    class Hanging(Crashing):
        @staticmethod
        def decide(payload):
            time.sleep(5)
            return 0, "", ""
    real_stdin, real_deadline, d.DEADLINE = sys.stdin, d.DEADLINE, 1  # a gate with no answer in time is a failure (O7 final, suggestion 1)
    try:
        for loader in (broken_import, lambda: Crashing, lambda: Hanging):
            sys.stdin = io.StringIO(payload(f"psql -h {PROD_P} -c 'select 1'"))
            with contextlib.redirect_stderr(io.StringIO()) as err:
                code = d.main(loader)
            assert code == 3 and "生产写门出错" in err.getvalue() and "保守预筛" in err.getvalue(), (loader, code, err.getvalue())
    finally:
        sys.stdin, d.DEADLINE = real_stdin, real_deadline
    # 4b. the deadline holds while the gate waits on git (O7 fix final M1): a git that sleeps is cut at DEADLINE, not swallowed by the gate's `except OSError`
    with tempfile.TemporaryDirectory() as fake:
        put(os.path.join(fake, "git"), "#!/bin/bash\nexec sleep 30\n")
        run = "import sys;sys.path.insert(0,sys.argv[1]);import pre_tool_dispatch as d;d.DEADLINE=2;sys.exit(d.main())"
        start = time.monotonic()
        slow = subprocess.run([sys.executable, "-c", run, HERE], input=payload("psql -h localhost -c 'select 1'", cwd=g.REPO_ROOT), capture_output=True, text=True, env={"LC_ALL": "C", "PATH": fake + os.pathsep + os.environ.get("PATH", "")}, timeout=60)
        assert slow.returncode == 3 and "生产写门出错" in slow.stderr and "Overdue" in slow.stderr and time.monotonic() - start < 8, (slow.returncode, slow.stderr, time.monotonic() - start)
    # 5. end to end as a hook process under the system python, also with -I
    env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", "")}
    for runner in (["/usr/bin/python3"], ["/usr/bin/python3", "-I"]) if os.path.exists("/usr/bin/python3") else ([sys.executable], [sys.executable, "-I"]):
        refused = subprocess.run([*runner, DISPATCH], input=payload(f"aliyun rds DeleteAccount --DBInstanceId {PROD_P}"), capture_output=True, text=True, env=env)
        assert refused.returncode == 2 and "底线 4" in refused.stderr, refused
        passed = subprocess.run([*runner, DISPATCH], input=payload("ls"), capture_output=True, text=True, env=env)
        assert passed.returncode == 0 and not passed.stdout and not passed.stderr, passed
        bad = subprocess.run([*runner, DISPATCH], input="not json", capture_output=True, text=True, env=env)
        assert bad.returncode == 2 and "拒绝" in bad.stderr, bad
    # 6. size limit from policy.json (gates.max_lines.pre_tool_dispatch)
    lines, limit = size("pre_tool_dispatch", DISPATCH)
    assert lines <= limit, f"pre_tool_dispatch.py 超过 {limit} 行"
    print("pre_tool_dispatch self-test: ok")

MAIN_GATE = os.path.join(HERE, "main_checkout_gate.py")  # ── main_checkout_gate ──
REFUSED = "拒绝：这是在主检出"
FAILED = "主检出提交保护出错"

def run(*args, cwd, **extra):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, env=env(**extra))

def checkout_gate(cwd, **extra):
    r = run("/usr/bin/python3" if os.path.exists("/usr/bin/python3") else sys.executable, "-I", MAIN_GATE, "--pre-commit", cwd=cwd, **extra)
    return r.returncode, r.stderr

def main_checkout_gate_test():
    with tempfile.TemporaryDirectory() as raw:
        d = os.path.realpath(raw)
        main = os.path.join(d, "repo")
        git(d, "init", "-q", "-b", "main", main)
        git(main, "commit", "-q", "--allow-empty", "-m", "one")
        between = os.path.join(main, ".claude", "worktrees")
        work = os.path.join(between, "w1")
        git(main, "worktree", "add", "-q", work, "-b", "side")
        # 1. the verdicts, asked directly
        code, message = checkout_gate(main)
        assert code == 1 and REFUSED in message and main in message, f"main checkout on main must be refused: {code} {message}"
        code, message = checkout_gate(between)
        assert code == 1 and REFUSED in message, "from .claude/worktrees/ itself git finds the main checkout (the 2026-10-21 incident)"
        assert checkout_gate(os.path.join(main, ".claude"))[0] == 1, "a subdirectory of the main checkout is the main checkout"
        assert checkout_gate(work) == (0, ""), "a commit in a linked worktree goes ahead"
        git(main, "checkout", "-q", "-b", "elsewhere")
        code, message = checkout_gate(main)
        assert code == 1 and REFUSED in message and "refs/heads/elsewhere" in message, "the main checkout on another branch is refused too (intent 225957)"
        w2 = os.path.join(between, "w2")
        git(main, "worktree", "add", "-q", w2, "main")
        assert checkout_gate(w2)[0] == 0, "a linked worktree that has main checked out is not the main checkout"
        git(main, "worktree", "remove", "--force", w2)
        git(main, "checkout", "-q", "--detach", "main")
        assert checkout_gate(main)[0] == 1, "detached HEAD in the main checkout is refused: commit, then update-ref main, moves main in two steps"
        git(main, "checkout", "-q", "main")
        # 2. fails closed: no repository, no git, git that says nonsense or fails
        lonely, empty, fake = (os.path.join(d, name) for name in ("lonely", "empty-bin", "fake-bin"))
        for folder in (lonely, empty, fake):
            os.makedirs(folder)
        code, message = checkout_gate(lonely, GIT_CEILING_DIRECTORIES=d)
        assert code == 1 and FAILED in message, f"no repository: {code} {message}"
        code, message = checkout_gate(main, PATH=empty)
        assert code == 1 and FAILED in message and "git 跑不起来" in message, f"git not found: {code} {message}"
        for name, body in (("garbage", "echo only-one-line"), ("broken", "echo boom >&2; exit 128")):
            put(os.path.join(fake, "git"), f"#!/bin/sh\n{body}\n")
            code, message = checkout_gate(main, PATH=fake + os.pathsep + "/bin:/usr/bin")
            assert code == 1 and FAILED in message, f"fake git ({name}) must be refused: {code} {message}"
        # 3. end to end: a real `git commit` through a pre-commit hook (git sets GIT_DIR / GIT_INDEX_FILE for hooks)
        hooks = os.path.join(d, "hooks")
        put(os.path.join(hooks, "pre-commit"), f"#!/bin/sh\nexec python3 -I '{MAIN_GATE}' --pre-commit\n")
        git(main, "config", "core.hooksPath", hooks)
        before = git(main, "rev-parse", "main").stdout
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=main)
        assert r.returncode != 0 and REFUSED in r.stderr and git(main, "rev-parse", "main").stdout == before, f"real commit on the main checkout's main must fail: {r.stderr}"
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=between)
        assert r.returncode != 0 and REFUSED in r.stderr, "real commit from .claude/worktrees/ must fail"
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=work)
        assert r.returncode == 0, f"real commit in the worktree must go ahead: {r.stderr}"
        os.makedirs(os.path.join(work, "sub"))
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=os.path.join(work, "sub"))
        assert r.returncode == 0, f"a commit from a worktree subdirectory (git sets GIT_PREFIX) goes ahead: {r.stderr}"
        # the repository is named from the worktree: git hands the hook the main checkout's git-dir (O4 review item 1)
        main_git = os.path.join(main, ".git")
        for argv, extra in ((("git", "commit", "-q", "--allow-empty", "-m", "d"), {"GIT_DIR": main_git, "GIT_WORK_TREE": work}), (("git", "--git-dir=" + main_git, "--work-tree=" + work, "commit", "-q", "--allow-empty", "-m", "e"), {})):
            r = run(*argv, cwd=work, **extra)
            assert r.returncode != 0 and REFUSED in r.stderr and git(main, "rev-parse", "main").stdout == before, (argv, extra, r.returncode, r.stderr)
        # 4. the session-start script: ceiling at <main checkout>/.claude, once however often it runs
        env_file = os.path.join(d, "claude.env")
        script = os.path.join(ROOT, "scripts", "hooks", "git_ceiling_env.sh")
        for _ in range(2):
            r = subprocess.run(["bash", script], cwd=work, capture_output=True, text=True, env=env(CLAUDE_ENV_FILE=env_file, CLAUDE_PROJECT_DIR=work))
            assert r.returncode == 0, r.stderr
        assert open(env_file, encoding="utf-8").read().splitlines() == [f"export GIT_CEILING_DIRECTORIES={main}/.claude"]
        ceiling = env(GIT_CEILING_DIRECTORIES=os.path.join(main, ".claude"))
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=between, capture_output=True, env=ceiling).returncode != 0, "with the ceiling, git finds nothing from .claude/worktrees/"
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=work, capture_output=True, env=ceiling).returncode == 0, "the worktree itself is found"
        assert subprocess.run(["bash", script], cwd=work, capture_output=True, env=env()).returncode == 0, "without CLAUDE_ENV_FILE it does nothing"
    # 5. size limit and wiring
    lines, limit = size("main_checkout_gate", MAIN_GATE)
    assert lines <= limit, f"main_checkout_gate.py has {lines} lines, policy.json allows {limit}"
    config = Path(ROOT, ".pre-commit-config.yaml").read_text(encoding="utf-8")
    block = re.search(r"- id: main-checkout-gate\n(?:\s{8,}.*\n)+", config)
    assert block and "entry: python3 -I scripts/gates/main_checkout_gate.py --pre-commit" in block.group(0) and "stages: [pre-commit, pre-merge-commit]" in block.group(0), "pre-commit wiring"
    assert not os.path.exists(os.path.join(ROOT, "scripts", "hooks", "main_checkout_gate.py")), "the old gate is gone"
    print("main_checkout_gate self-test: ok")

MSG_GATE = os.path.join(HERE, "commit_msg_gate.py")  # ── commit_msg_gate ──

def commit(cwd, message, name="a.py", **extra):
    """Commit one changed file with the hook installed; returns (exit code, stderr, final message)."""
    with open(os.path.join(cwd, name), "a", encoding="utf-8") as f:
        f.write("x\n")
    git(cwd, "add", name)
    r = git(cwd, "commit", "-q", "-m", message, ok=False, **extra)
    return r.returncode, r.stderr, git(cwd, "log", "-1", "--format=%B").stdout

def commit_msg_gate_test():
    with tempfile.TemporaryDirectory() as raw:
        tmp = os.path.realpath(raw)
        repo = os.path.join(tmp, "repo")
        hooks = os.path.join(tmp, "hooks")
        put(os.path.join(hooks, "commit-msg"), f'#!/bin/sh\nexec {PYTHON} -I {MSG_GATE} "$1"\n')
        git(tmp, "init", "-q", "-b", "main", repo)
        git(repo, "config", "core.hooksPath", hooks)
        for name in ("one-ops", "one-1a", "one-1a-next-3", "_retired"):
            put(os.path.join(repo, "docs/modernization/sessions", name + ".md"), "x\n")
        put(os.path.join(repo, "docs/modernization/roles/one-platform.md"), "x\n")
        put(os.path.join(repo, ".claude/session-name"), "one-ops\n")
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", "init", "--no-verify")
        code, err, msg = commit(repo, "feat: 改脚本，正文没有任务号也没有 PR 号")
        assert code == 0 and msg.rstrip().endswith("Session: one-ops"), "no task id or PR is accepted and gets the trailer: " + err + msg
        code, err, msg = commit(repo, "feat: x\n\n为什么：y\n\nCo-Authored-By: A <a@b>")
        assert code == 0 and "Co-Authored-By: A <a@b>\nSession: one-ops" in msg and msg.count("Session:") == 1, msg
        code, err, msg = commit(repo, "feat: x\n\nSession: one-1a")
        assert code == 0 and msg.count("Session:") == 1 and "Session: one-1a" in msg, "an existing Session is kept: " + msg
        code, err, msg = commit(repo, "fixup! feat: x")
        assert code == 0 and "Session:" not in msg, "fixup titles are left alone: " + msg
        code, err, msg = commit(repo, "#593 修复钩子")
        assert code == 0 and "Session: one-ops" in msg, "a title starting with #<number> is not a comment: " + msg
        put(os.path.join(repo, ".claude/session-name"), "one-nope\n")
        code, err, msg = commit(repo, "feat: 标记不是已知会话")
        assert code == 0 and "Session:" not in msg and "未识别" in err, "an unknown marker warns, never guesses: " + err + msg
        os.remove(os.path.join(repo, ".claude/session-name"))
        git(repo, "add", "-A")
        code, err, msg = commit(repo, "feat: 目录名 repo 认不出")
        assert code == 0 and "Session:" not in msg and "未识别" in err, err + msg
        wt = os.path.join(tmp, "one-1a-next-3-abc")
        git(repo, "worktree", "add", "-q", "-b", "claude/zzz", wt)
        code, err, msg = commit(wt, "feat: 按工作区目录名认", name="b.py")
        assert code == 0 and msg.rstrip().endswith("Session: one-1a-next-3"), "longest known prefix of the worktree directory: " + msg
        wt2 = os.path.join(tmp, "plain")
        git(repo, "worktree", "add", "-q", "-b", "claude/one-platform-r2", wt2)
        code, err, msg = commit(wt2, "feat: 按分支名认（roles 目录）", name="c.py")
        assert code == 0 and msg.rstrip().endswith("Session: one-platform"), "branch without claude/ prefix, names from roles/: " + msg
        wt3 = os.path.join(tmp, "one-1a-r9")
        git(repo, "worktree", "add", "-q", "-b", "claude/one-ops-r9", wt3)
        code, err, msg = commit(wt3, "feat: 目录名与分支名认出不同会话", name="f.py")
        assert code == 0 and msg.rstrip().endswith("Session: one-1a"), "the directory wins over the branch, whatever the name lengths: " + msg
        git(repo, "checkout", "-q", "-b", "side")
        commit(repo, "side", name="d.py")
        git(repo, "checkout", "-q", "main")
        commit(repo, "main", name="e.py")
        put(os.path.join(repo, ".claude/session-name"), "one-ops\n")
        git(repo, "merge", "-q", "--no-ff", "side", "-m", "合并 side（标题不以 Merge 开头）")
        assert "Session:" not in git(repo, "log", "-1", "--format=%B").stdout, "a merge in progress is left alone"
        broken = os.path.join(tmp, "notgit")
        put(os.path.join(broken, "MSG"), "feat: x\n")
        r = subprocess.run([PYTHON, "-I", MSG_GATE, os.path.join(broken, "MSG")], cwd=broken, capture_output=True, text=True, env=env())
        assert r.returncode == 0 and "出错" in r.stderr, "an error warns and lets the commit through: " + r.stderr
        r = subprocess.run([PYTHON, "-I", MSG_GATE], cwd=broken, capture_output=True, text=True, env=env())
        assert r.returncode == 0 and "用法" in r.stderr, r.stderr
    lines, limit = size("commit_msg_gate", MSG_GATE)
    assert lines <= limit, f"commit_msg_gate.py {lines} 行，超过 policy.json 的 {limit}"
    config = Path(ROOT, ".pre-commit-config.yaml").read_text(encoding="utf-8")
    assert "entry: python3 -I scripts/gates/commit_msg_gate.py" in config, ".pre-commit-config.yaml points at the new path"
    for gone in ("scripts/hooks/commit_msg_gate.py", "scripts/hooks/task_ids.py"):
        assert not os.path.exists(os.path.join(ROOT, gone)), gone + " is deleted"
    print("commit_msg_gate self-test: ok")

FAKE = "fake-prod-db.example.invalid"  # ── install ── fictitious production host, put on the temporary repository's origin/main table only
ENTRIES = {"PreToolUse": ["scripts/gates/pre_tool_dispatch.py"], "SessionStart": ["scripts/hooks/git_ceiling_env.sh", "scripts/hooks/start_sheet.py"]}
SHELLS = ("/bin/sh", "/bin/bash")
FIRST_ONLY = """#!/bin/bash
read -r line
first=$(/usr/bin/python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["targets"][0]["patterns"][0])' "$(dirname "$0")/prod_targets.json")
case "$line" in *"$first"*) exit 2 ;; "{"*) exit 0 ;; *) exit 2 ;; esac
"""  # refuses writes to the first target only

def writable(root):
    """Gives the owner write access again under `root` (installed versions are read-only), so they can be edited or removed."""
    for base, dirs, files in os.walk(root):
        os.chmod(base, 0o755)
        for name in files:
            path = os.path.join(base, name)
            if not os.path.islink(path):
                os.chmod(path, os.stat(path).st_mode | 0o200)

def gitc(repo, *args):
    return subprocess.run(["git", "-C", repo, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "core.hooksPath=/dev/null", *args], check=True, capture_output=True, text=True).stdout.strip()

def mkrepo(path, files):  # a repository holding `files`, committed once, with origin/main at that commit
    for rel, text in files.items():
        put(os.path.join(path, rel), text, 0o644)
    for args in (["init", "-q", "-b", "main"], ["add", "-A"], ["commit", "-q", "-m", "x"], ["update-ref", "refs/remotes/origin/main", "HEAD"]):
        gitc(path, *args)

def policy():
    return json.loads(Path(ROOT, "policy.json").read_text(encoding="utf-8"))["gates"]

def settings_self_test():
    settings = json.loads(Path(ROOT, ".claude/settings.json").read_text(encoding="utf-8"))
    gates = policy()
    assert gates["install_dir"].startswith("~/"), gates["install_dir"]
    link = '"$HOME"/' + gates["install_dir"][2:] + "/" + gates["install_current_link"]
    prefix = "H=" + link + "/"
    verify = ('V="$CLAUDE_PROJECT_DIR"/scripts/gates/hooks_manifest.sh; if [ -x "$V" ]; then "$V" --check ' + link + "; "
              'else echo "【钩子告警】核对不了已装钩子（$V 缺失或不可执行）"; fi; ')  # runs from the session's repository, not from current (O7 final M2)
    hooks = settings["hooks"]
    stop = [h["command"] for g in hooks.pop("Stop") for h in g["hooks"]]
    assert len(stop) == 1 and "/.modernize/psa/tools/one-session/stop_check/stop_check.py" in stop[0] and stop[0].rstrip().endswith("; exit 0"), stop
    assert sorted(hooks) == ["PreToolUse", "SessionStart"], sorted(hooks)
    assert [g["matcher"] for g in hooks["PreToolUse"]] == ["|".join(COMMAND_TOOLS + (LAUNCH_TOOL,))], "PreToolUse hangs only the production-write gate, on the tools that run commands"
    assert json.dumps([*COMMAND_TOOLS, LAUNCH_TOOL]) in hooks["PreToolUse"][0]["hooks"][0]["command"], "the mount's own pre-filter names the same tools"
    assert [g["matcher"] for g in hooks["SessionStart"]] == ["startup|clear|compact|resume", "startup|clear|compact"]
    commands = {}
    for event, groups in hooks.items():
        assert all(len(g["hooks"]) == 1 for g in groups), event
        found = []
        for group in groups:
            hook = group["hooks"][0]
            command = hook["command"][len(verify):] if group is hooks["SessionStart"][0] else hook["command"]
            assert (group is hooks["SessionStart"][0]) == hook["command"].startswith(verify), (event, hook["command"])
            head = command.split(";")[0]
            assert hook["type"] == "command" and head.startswith(prefix), (event, hook["command"])
            found.append(head[len(prefix):])
            commands[found[-1]] = (event, hook["command"], hook["timeout"])
        assert found == ENTRIES[event], (event, found)
    gate = commands["scripts/gates/pre_tool_dispatch.py"]
    assert gate[2] == 120 and gate[1].endswith("exit 2") and "/usr/bin/python3 -I -c" in gate[1], "the gate mount: when the gate fails its own pre-filter decides, refusing by default (H4, C3)"
    assert set(re.search(r'ok=set\("([^"]*)"', gate[1]).group(1).split()) <= g.NO_CONNECT and set(re.search(r'far="([^"]*)"', gate[1]).group(1).split()) >= g.LONG_REMOTE - {"https", "mongosh"}, "pre-filter lists"
    for name in ("scripts/hooks/git_ceiling_env.sh", "scripts/hooks/start_sheet.py"):
        assert commands[name][1].endswith("; exit 0") and "【钩子告警】" in commands[name][1] and "exit 2" not in commands[name][1], name
    assert commands["scripts/hooks/git_ceiling_env.sh"][2] == 10 and commands["scripts/hooks/start_sheet.py"][2] == 15
    # a target without its exec bit never runs once installed (O6 review item 1: start_sheet.py had none)
    for name in [*commands, "scripts/gates/hooks_manifest.sh"]:
        mode = gitc(ROOT, "ls-files", "-s", "--", name).split()[:1]
        assert mode == ["100755"], (name, mode)
    for plugin in ("kotlin-lsp", "claude-code-setup", "code-modernization"):
        assert settings["enabledPlugins"][f"{plugin}@claude-plugins-official"] is False, plugin
    assert size("install", os.path.join(HERE, "install.sh"))[0] <= gates["max_lines"]["install"], "install.sh 超过 policy gates.max_lines.install"
    assert size("selftest", os.path.abspath(__file__))[0] <= gates["max_lines"]["selftest"], "gates_selftest.py 超过 policy gates.max_lines.selftest（O8：门自测 1 个文件）"
    assert max(map(len, Path(__file__).read_text(encoding="utf-8").splitlines())) <= 266, "gates_selftest.py 有超过 266 字符的行：不靠长行压行数（O8 首轮必改）"
    return {name: cmd for name, (_, cmd, _) in commands.items()}

def make_repo(repo):
    """A repository holding this working tree's scripts/gates, scripts/hooks and policy.json; returns (A, B, fail_open, refuses_all, passes_writes, first_only, not_executable, main) commits."""
    names = subprocess.run(["git", "-C", ROOT, "ls-files", "--cached", "--others", "--exclude-standard", "--", "scripts/gates", "scripts/hooks", "policy.json"], check=True, capture_output=True, text=True).stdout.split()
    os.makedirs(repo)
    gitc(repo, "init", "-q", "-b", "main")
    for name in names:
        if os.path.isfile(os.path.join(ROOT, name)):
            os.makedirs(os.path.dirname(os.path.join(repo, name)) or repo, exist_ok=True)
            shutil.copy2(os.path.join(ROOT, name), os.path.join(repo, name))
    commits = []
    def commit(message):
        gitc(repo, "add", "-A")
        gitc(repo, "commit", "-q", "-m", message)
        commits.append(gitc(repo, "rev-parse", "HEAD"))
    commit("A")
    with open(os.path.join(repo, "scripts/hooks/git_ceiling_env.sh"), "a", encoding="utf-8") as f:
        f.write("# B\n")
    commit("B")
    dispatcher = os.path.join(repo, "scripts/gates/pre_tool_dispatch.py")
    original = open(dispatcher, encoding="utf-8").read()
    put(dispatcher, "#!/bin/bash\ncat >/dev/null\nexit 0\n")  # a gate that lets everything through
    commit("fail open")
    put(dispatcher, "#!/bin/bash\ncat >/dev/null\nexit 2\n")  # a gate that refuses everything: every session's Bash would stop
    commit("refuses all")
    put(dispatcher, "#!/bin/bash\nread -r line\ncase \"$line\" in \"{\"*) exit 0 ;; *) exit 2 ;; esac\n")  # refuses unreadable input, lets production writes through
    commit("passes writes")
    put(dispatcher, FIRST_ONLY)
    commit("first target only")
    put(dispatcher, original, 0o644)
    commit("not executable")
    gitc(repo, "checkout", "-q", commits[1])
    table = os.path.join(repo, "scripts/gates/prod_targets.json")
    data = json.load(open(table, encoding="utf-8"))
    data["targets"].append({"id": "fake-only-on-main", "patterns": [FAKE]})
    put(table, json.dumps(data), 0o644)
    commit("origin/main table")
    gitc(repo, "update-ref", "refs/remotes/origin/main", commits[-1])
    gitc(repo, "checkout", "-q", commits[0])
    return commits

def install_test():
    commands = settings_self_test()
    gate_cmd, ceiling_cmd, sheet_cmd = commands["scripts/gates/pre_tool_dispatch.py"], commands["scripts/hooks/git_ceiling_env.sh"], commands["scripts/hooks/start_sheet.py"]
    with tempfile.TemporaryDirectory() as top:
        try:
            body(gate_cmd, ceiling_cmd, sheet_cmd, os.path.realpath(os.path.join(top, "dir with space")))
        finally:
            writable(top)
    print("install self-test: ok")

def body(gate_cmd, ceiling_cmd, sheet_cmd, tmp):
    home, repo = os.path.join(tmp, "home"), os.path.join(tmp, "repo")
    os.makedirs(home)
    a, b, fail_open, refuses_all, passes_writes, first_only, not_exec, on_main = make_repo(repo)
    hooks = os.path.join(home, policy()["install_dir"][2:])
    current = os.path.join(hooks, policy()["install_current_link"])
    env = {"PATH": os.environ["PATH"], "HOME": home, "LC_ALL": "C"}
    def install(*args, **extra):
        return subprocess.run([os.path.join(repo, "scripts/gates/install.sh"), *args], capture_output=True, text=True, env={**env, **extra}, cwd=tmp)
    def mount(cmd, stdin, shell="/bin/sh", **extra):
        cwd = extra.pop("cwd", repo)
        run_env = {k: v for k, v in {**env, "CLAUDE_PROJECT_DIR": repo, **extra}.items() if v is not None}
        return subprocess.run([shell, "-c", cmd], input=stdin, capture_output=True, text=True, env=run_env, cwd=cwd, timeout=60)
    def bash(command):
        return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": repo})
    def points_to(): return os.readlink(current) if os.path.islink(current) else None
    def leftovers():
        return [n for n in os.listdir(hooks) if n.startswith(".")] if os.path.isdir(hooks) else []
    def closed(why, shells=SHELLS, extra=()):  # the gate failed: settings.json's own pre-filter decides (H4, C3) - ordinary commands pass with a warning; what may concern production, git/gh (config can run programs) and VAR= prefixes are refused
        for shell in shells:
            for command, code in (("ls", 0), ("ls -la | wc -l", 0), ("git status", 2), ("gh pr list", 2), ("git -c core.pager='sh w.sh' log -1", 2), ("GIT_EXTERNAL_DIFF=./w.sh git diff", 2), ("PATH=./bin:/usr/bin ls", 2),
                                  ("echo x > /dev/tcp/10.0.0.5/6379", 2), ("sort --compress-program=./w.sh x", 2), (f"psql -h {FAKE} -c 'delete from t'", 2), ("./deploy.sh", 2), ("echo `deploy`", 2), ("rg --pre deploy x", 2), *((c, 2) for c in extra)):
                r = mount(gate_cmd, bash(command), shell)
                assert r.returncode == code and why in r.stderr and ("放行" in r.stderr) == (code == 0) and os.path.isfile(os.path.join(hooks, "gate_failures.log")) == os.path.isdir(hooks), (shell, why, command, r.returncode, r.stderr)
            for cmd in (ceiling_cmd, sheet_cmd):
                r = mount(cmd, json.dumps({"cwd": repo}), shell)
                assert r.returncode == 0 and r.stdout.splitlines()[-1].startswith("【钩子告警】"), (shell, why, r.returncode, r.stdout, r.stderr)
    # 1. nothing installed: the pre-filter decides (an input it cannot read is refused), SessionStart warns and passes
    closed("入口缺失")
    assert mount(gate_cmd, "not json").returncode == 2 and mount(gate_cmd, json.dumps({"tool_name": "Bash", "tool_input": {}})).returncode == 2
    for tool, command, code in ((t, c, r) for t in COMMAND_TOOLS[1:] for c, r in ((f"psql -h {FAKE} -c 'delete from t'", 2), ("ls", 0))):  # Monitor, the terminal panel
        assert mount(gate_cmd, bash(command).replace('"Bash"', f'"{tool}"')).returncode == code, (tool, command)
    # 2. first install: <install_dir>/<full sha>/, current a relative link to it, nothing half-made left
    r = install()
    assert r.returncode == 0 and points_to() == a and os.path.isdir(os.path.join(hooks, a)) and not leftovers(), (r.stdout, r.stderr, points_to())
    for shell in SHELLS:
        r = mount(gate_cmd, bash("ls -la"), shell)
        assert r.returncode == 0 and not r.stderr, (shell, r.stdout, r.stderr)
        r = mount(gate_cmd, bash(f"psql -h {FAKE} -c 'delete from t'"), shell)  # the installed gate asks CLAUDE_PROJECT_DIR's repository for origin/main's table
        assert r.returncode == 2 and r.stderr, ("a pattern only on origin/main is refused by the installed gate", shell, r.returncode, r.stderr)
        r = mount(gate_cmd, "not json", shell)
        assert r.returncode == 2 and "不是 JSON" in r.stderr and "异常退出" not in r.stderr, "the gate's own refusal passes through"
        r = mount(ceiling_cmd, "{}", shell, CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
        assert r.returncode == 0 and "【钩子告警】" not in r.stdout, (r.stdout, r.stderr)
    r = mount(sheet_cmd, json.dumps({"cwd": ROOT}))
    assert r.returncode == 0 and r.stdout.startswith("【开工单】") and "【钩子告警】" not in r.stdout, r.stdout[:300]
    # 2b. no CLAUDE_PROJECT_DIR: the working directory is no repository root for the installed gate, so a forged repository whose origin/main holds a
    #     script cannot make it trusted (O7 final M3); the forged script names a fictitious host that is on a fictitious local table
    forged, table = os.path.join(tmp, "forged"), os.path.join(tmp, "fake_targets.json")
    doc_script = "docs" + "/" + "modernization/new_intent.py"  # registered as an entry on the forged repository's origin/main; built, not spelled (relevant_change.py scans sources for literal docs paths)
    put(table, json.dumps({"targets": [{"id": "fake", "patterns": [FAKE]}]}), 0o644)
    registered = {"targets": [{"id": "fake", "patterns": [FAKE]}], "trusted_entries": {"entries": [{"script": "new_intent.py", "interpreter": ["python3"], "forms": [[]]}]}}  # on its origin/main
    mkrepo(forged, {doc_script: f"import os\nos.system(\"psql -h {FAKE} -c 'delete from t'\")\n", "scripts/gates/prod_targets.json": json.dumps(registered)})
    for shell in SHELLS:
        for project in (None, ""):
            r = mount(gate_cmd, json.dumps({"tool_name": "Bash", "tool_input": {"command": "python3 " + doc_script}, "cwd": forged}), shell, CLAUDE_PROJECT_DIR=project, PROD_TARGETS_FILE=table, cwd=forged)
            assert r.returncode == 2, ("a forged repository in the working directory makes no script trusted", shell, project, r.returncode, r.stderr)
        # a script is trusted only when cwd and CLAUDE_PROJECT_DIR are one repository: refused with the real repository as project and the forged one as cwd;
        # the forged repository as both passes (the control: trust follows that one repository, not the cwd alone)
        for project, where, code in ((repo, forged, 2), (forged, forged, 0)):
            r = mount(gate_cmd, json.dumps({"tool_name": "Bash", "tool_input": {"command": "python3 " + doc_script}, "cwd": where}), shell, CLAUDE_PROJECT_DIR=project, PROD_TARGETS_FILE=table, cwd=where)
            assert r.returncode == code, ("REPO_ROOT is CLAUDE_PROJECT_DIR", shell, project, where, r.returncode, r.stderr)
    # 3. a second version: installed beside the first, current switched; again: already installed, only switched
    r = install(b)
    assert r.returncode == 0 and points_to() == b and os.path.isdir(os.path.join(hooks, a)) and not leftovers(), (r.stdout, r.stderr)
    assert install("HEAD").returncode == 0 and points_to() == a and install(b).returncode == 0 and points_to() == b
    # 4. back to the old version by its directory name; a revision is no installed version
    r = install("--switch", a)
    assert r.returncode == 0 and points_to() == a and "→ " + a in r.stdout, (r.stdout, r.stderr)
    assert install("--switch", b).returncode == 0 and points_to() == b
    r = install("--switch", "HEAD")
    assert r.returncode == 1 and "没有已装的版本" in r.stderr and points_to() == b, ("--switch takes only a 40-hex installed directory, no revision (O7 first review M1)", r.stderr)
    assert install("--switch", a).returncode == 0 and points_to() == a
    os.makedirs(os.path.join(hooks, ".tmp.X"))  # a real half-made directory, a real link named like a sha: neither is an installed version
    os.symlink(a, os.path.join(hooks, on_main))  # on_main passes the self-check: only the link test refuses it
    for bad in (("--switch", "nope"), ("--switch", "../x"), ("--switch", ".tmp.X"), ("--switch", "current"), ("--switch", on_main), ("--switch", a.upper()), ("--switch",), ("--switch", a, b), ("not-a-commit",), (a, b)):
        r = install(*bad)
        assert r.returncode == 1 and "current 未改动" in r.stderr and points_to() == a, (bad, r.returncode, r.stderr)
    os.rmdir(os.path.join(hooks, ".tmp.X"))
    os.remove(os.path.join(hooks, on_main))
    r = install("--switch", "nope", HOOKS_INSTALL_DIR=os.path.join(tmp, "never"))
    assert r.returncode == 1 and not os.path.exists(os.path.join(tmp, "never")), "a refused --switch makes no install directory (O7 first review, suggestion 1)"
    for absent in ("nope", fail_open):  # not a revision; a commit that was never installed
        r = install("--switch", absent)
        assert r.returncode == 1 and "没有已装的版本" in r.stderr and points_to() == a, (absent, r.stderr)
    # 4b. two installs of one commit at once both succeed (the loser finds the directory ready) and leave current right (O7 first review, suggestion 2)
    for _ in range(3):
        runs = [subprocess.Popen([os.path.join(repo, "scripts/gates/install.sh"), b], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env, cwd=tmp) for _ in range(4)]
        outs = [(p.wait(timeout=120), p.stderr.read()) for p in runs]
        assert all(code == 0 for code, _ in outs) and points_to() == b and not leftovers(), (outs, points_to(), leftovers())
    # 4c. that fallback, deterministically: the target directory is locked (chflags uchg), so neither rename works. A target matching the fresh unpack counts as
    #     ready; one matching only its own manifest (another commit's files) does not, and current stays (O7 fix first review, suggestion 2). chflags is macOS/BSD.
    target = os.path.join(hooks, b)
    for files, code in ((None, 0), (a, 1)):
        if files:
            writable(target)
            shutil.rmtree(target)
            shutil.copytree(os.path.join(hooks, files), target, symlinks=True)
        if not shutil.which("chflags"):
            print("install self-test: 没有 chflags，跳过 4c")
            break
        subprocess.run(["chflags", "uchg", target], check=True)
        try:
            r = install(b)
        finally:
            subprocess.run(["chflags", "nouchg", target], check=True)
        assert r.returncode == code and points_to() == b and not leftovers() and (code == 0 or "放不进" in r.stderr), (files, r.returncode, r.stderr)
    assert install(b).returncode == 0 and install("--switch", a).returncode == 0 and points_to() == a
    # 5. a failing self-check leaves current and the install directory as they were
    for broken in (fail_open, refuses_all, passes_writes, not_exec):
        r = install(broken)
        assert r.returncode == 1 and "自检不过" in r.stderr and points_to() == a and not os.path.exists(os.path.join(hooks, broken)) and not leftovers(), (broken, r.stderr)
    r = install(first_only)
    assert r.returncode == 1 and "自检不过" in r.stderr and points_to() == a, "every target is probed, not only the first (O7 final, suggestion 2)"
    # 5b. installed versions are read-only: an edit fails
    gate_file = os.path.join(hooks, a, "scripts/gates/prod_write_gate.py")
    assert not os.access(gate_file, os.W_OK) and not os.access(os.path.dirname(gate_file), os.W_OK), "chmod -R a-w after install"
    try:
        open(gate_file, "a").close()
        raise AssertionError("an installed file could be opened for writing")
    except PermissionError:
        pass
    # 5c. a tampered installed version (the current one, and another one) is warned about at SessionStart, and install.sh <sha> / --switch rebuild it from git (O7 final M1, M2)
    def tamper(version, gate="#!/bin/bash\ncat >/dev/null\nexit 0\n"):
        writable(os.path.join(hooks, version))
        put(os.path.join(hooks, version, "scripts/gates/pre_tool_dispatch.py"), gate)
    def warning():
        r = mount(ceiling_cmd, "{}", CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
        assert r.returncode == 0, r
        return "\n".join(line for line in r.stdout.splitlines() if line.startswith("【钩子告警】"))
    def follow(text, sha):
        """Runs the install.sh command the warning tells one-ops to run: it must repair (O7 fix first review M1: --switch refuses a link or a missing directory)."""
        command = re.search(r"执行 (scripts/gates/install\.sh(?: [^ （]+)*)（", text)
        assert command, text
        r = install(*command.group(1).split()[1:])
        assert r.returncode == 0 and points_to() == sha and os.path.isdir(current) and not os.path.islink(os.path.join(hooks, sha)) and warning() == "" and not leftovers(), (command.group(1), r.stderr)
    fake_write = bash(f"psql -h {FAKE} -c 'delete from t'")
    assert warning() == "" and mount(gate_cmd, fake_write).returncode == 2
    tamper(a)
    assert mount(gate_cmd, fake_write).returncode == 0, "the tampering took effect"
    assert "与安装时的清单不一致" in warning() and f"install.sh {a}（" in warning(), warning()
    follow(warning(), a)
    assert mount(gate_cmd, fake_write).returncode == 2, "install.sh <sha> repairs"
    tamper(b)
    r = install("--switch", b)
    assert r.returncode == 0 and points_to() == b and mount(gate_cmd, fake_write).returncode == 2 and warning() == "", ("--switch repairs", r.stderr)
    assert install("--switch", a).returncode == 0 and points_to() == a
    writable(os.path.join(hooks, a))
    put(os.path.join(hooks, a, "scripts/hooks/extra.sh"), "#!/bin/bash\n")
    assert "不一致" in warning(), "an added file"
    os.remove(os.path.join(hooks, a, "scripts/hooks/extra.sh"))
    os.remove(os.path.join(hooks, a, "scripts/hooks/command_words.py"))
    assert "不一致" in warning(), "a removed file"
    os.rename(os.path.join(hooks, a, ".manifest"), os.path.join(tmp, "manifest"))
    assert "没有校验和清单" in warning()
    follow(warning(), a)  # the warning's command repairs a missing manifest too (O7 fix first review, suggestion 2)
    outside = os.path.join(tmp, "outside")
    shutil.copytree(os.path.join(repo, "scripts"), os.path.join(outside, "scripts"))
    for target in (outside, "../outside", "not-a-sha"):
        os.remove(current)
        os.symlink(target, current)
        assert "不是指向安装目录下 40 位 sha 目录" in warning(), target
    os.remove(current)
    assert "不是指向" in warning(), "no current at all"
    os.symlink(on_main, current)  # a sha name with nothing behind it (O7 final, suggestion 1); the warning's command repairs it
    assert "current 指向的目录" in warning() and "不存在" in warning(), warning()
    follow(warning(), on_main)
    writable(os.path.join(hooks, on_main))
    shutil.rmtree(os.path.join(hooks, on_main))
    os.symlink(os.path.join(hooks, a), os.path.join(hooks, on_main))  # the version itself a link: warned about, the session goes on (O7 first review M1), the warning's command repairs it
    r = mount(ceiling_cmd, "{}", CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
    assert r.returncode == 0 and "是符号链接" in r.stdout, (r.returncode, r.stdout)
    follow(r.stdout, on_main)
    writable(os.path.join(hooks, on_main))
    shutil.rmtree(os.path.join(hooks, on_main))
    os.symlink(os.path.join(tmp, "nowhere"), os.path.join(hooks, on_main))  # the version a dangling link: `[ -e ]` alone does not see it (O7 refix first review M1)
    assert "是符号链接" in warning(), warning()
    follow(warning(), on_main)
    os.remove(current)
    os.makedirs(os.path.join(current, "scripts"))  # current a real directory, no link: no sha to name, the warning says install.sh without one (O7 refix final M2)
    assert "不是指向" in warning() and "install.sh（" in warning(), warning()
    follow(warning(), a)
    os.remove(current)
    for project in (tmp, ""):
        r = mount(ceiling_cmd, "{}", CLAUDE_PROJECT_DIR=project)
        assert r.returncode == 0 and "核对不了已装钩子" in r.stdout, ("the checker missing from the session's repository", project, r.stdout)
    assert install(a).returncode == 0 and warning() == ""
    # 6. HOOKS_INSTALL_DIR moves the install directory; the policy one stays untouched
    other = os.path.join(tmp, "other")
    r = install(b, HOOKS_INSTALL_DIR=other)
    assert r.returncode == 0 and os.readlink(os.path.join(other, "current")) == b and points_to() == a, (r.stdout, r.stderr)
    # 7. the mount commands as written: fail closed for the gate, pass with a warning for SessionStart
    def version(name, gate, ceiling="#!/bin/bash\nexit 1\n", sheet="#!/bin/bash\nexit 1\n", gate_mode=0o755):
        root = os.path.join(hooks, name)
        if gate is not None:
            put(os.path.join(root, "scripts/gates/pre_tool_dispatch.py"), gate, gate_mode)
        put(os.path.join(root, "scripts/hooks/git_ceiling_env.sh"), ceiling)
        put(os.path.join(root, "scripts/hooks/start_sheet.py"), sheet)
        os.remove(current)
        os.symlink(name, current)
    version("missing", None)
    closed("入口缺失")
    version("not-exec", "#!/bin/bash\nexit 0\n", gate_mode=0o644)
    closed("入口缺失")
    version("crash", "#!/bin/bash\nexit 1\n")
    closed("异常退出（退出码 1")
    version("no-python", "#!/nonexistent/python3\n")
    closed("异常退出（退出码 126")
    version("killed", "#!/bin/bash\nkill -9 $$\n")
    closed("异常退出（退出码 137")
    version("syntax", "#!/usr/bin/python3\ndef (:\n")
    closed("异常退出（退出码 1")
    shutil.rmtree(os.path.join(hooks, "syntax"))
    closed("入口缺失")  # a dangling current link
    version("gate-error", "#!/bin/bash\ncat >/dev/null\nexit 3\n")  # the real dispatcher's answer when the gate cannot be imported, crashes or hangs (section 4)
    closed("异常退出（退出码 3", extra=[c for cs in seventeen(PROD_P, PROD_H, PROD_D).values() for c in cs])
    version("answers", "#!/bin/bash\ncat >/dev/null\necho no >&2\nexit 2\n", "#!/bin/bash\necho ok\n", "#!/bin/bash\necho 【开工单】x\n")
    for shell in SHELLS:
        r = mount(gate_cmd, bash("ls"), shell)
        assert r.returncode == 2 and r.stderr.strip() == "no", "the gate's own 2 passes through unchanged"
        assert mount(ceiling_cmd, "{}", shell).stdout.splitlines()[-1] == "ok" and mount(sheet_cmd, "{}", shell).stdout.strip() == "【开工单】x"
    version("passes", "#!/bin/bash\ncat >/dev/null\necho '{\"x\":1}'\nexit 0\n")
    r = mount(gate_cmd, bash("ls"))
    assert r.returncode == 0 and r.stdout.strip() == '{"x":1}' and not r.stderr, "the gate's own 0 and its stdout pass through"
    os.remove(current)
    r = install("--switch", a)
    assert r.returncode == 0 and points_to() == a and mount(gate_cmd, bash("ls")).returncode == 0

SECTIONS = {"prod_write_gate": prod_write_gate_test, "pre_tool_dispatch": pre_tool_dispatch_test, "main_checkout_gate": main_checkout_gate_test, "commit_msg_gate": commit_msg_gate_test, "install": install_test}

if __name__ == "__main__":
    unknown = [name for name in sys.argv[1:] if name not in SECTIONS]
    if unknown:
        sys.exit(f"用法：gates_selftest.py [{' | '.join(SECTIONS)} …]；不认识：{unknown}")
    for name in sys.argv[1:] or SECTIONS:
        SECTIONS[name]()
