#!/usr/bin/env python3
"""生产写门的自测（临时独立文件：门本体行数上限见 policy.json gates.max_lines.prod_write_gate；O8 合并进统一自测）。由 `prod_write_gate.py --self-test` 调起，也可直接运行。
含三部分：原 command_words 解析自测、原 prod_write_gate 自测、「生产写门 17 例」（Codex 复核 #653 的 17 条 P1 缺口，每条的原样复现命令，测试表与真实特征表下都须拒绝）。
本文件不写任何真实生产样本的字面值，拆段拼接也不行（O2 复审 M3）：真实特征在运行时按 target id 从 prod_targets.json 取；门自测用虚构样本（192.0.2.x 是文档专用地址）。"""
import ast
import inspect
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import prod_write_gate as g  # noqa: E402
from prod_write_gate import (DOCS_ROOT, EXTRA_READONLY, REPO_ROOT, brace_variants, could_concern, decode_ansi_c, heredoc_bodies, judge, load_table, mask_sql, name_of,  # noqa: E402
                             plain, simple_commands, tokens_of)

HOOKS = os.path.join(REPO_ROOT, "scripts", "hooks")
TEST_IP = "192.0.2.15"  # RFC 5737 documentation address, not a production host
SAMPLE_IDS = ("legacy-rds-sqlserver", "legacy-ecs", "new-prod-names")  # the 17 cases' P, H, D: the first pattern of each of these targets


def shipped_firsts():
    """{target id: its first pattern} of the shipped prod_targets.json, read at run time (the guard sources hold no production sample)."""
    with open(os.path.join(HERE, "prod_targets.json"), encoding="utf-8") as f:
        return {t["id"]: t["patterns"][0].strip() for t in json.load(f)["targets"]}


def shipped_forms():
    """{"<target id> <form>": pattern}: the first IP-shaped and the first name-shaped pattern of every target of the shipped table (O2 round 1, note ①: each form is run)."""
    with open(os.path.join(HERE, "prod_targets.json"), encoding="utf-8") as f:
        targets = json.load(f)["targets"]
    forms = {}
    for target in targets:
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
    assert names("echo ok # <<EOF\nsqlcmd -S p -Q x") == ["echo", "sqlcmd"] and names('echo "<<EOF"; sqlcmd -S p -Q x') == ["echo", "sqlcmd"], "<<EOF in a comment or a string is not a here-document"
    assert "sqlcmd" in names("cat <<EOF\n$(sqlcmd -S p -Q x)\nEOF") and "sqlcmd" not in names("cat <<'EOF'\n$(sqlcmd -S p -Q x)\nEOF"), "an unquoted here-document body is expanded, a quoted one is text"
    assert names("psql -h x <<EOF\nselect 1\nEOF\necho after") == ["psql", "echo"] and heredoc_bodies("cat <<'A' <<B\none\nA\ntwo\nB\n") == ["one", "two"]
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

    # 1. reads and harmless mentions pass
    for command in (f'psql -h {P}.pg.rds.aliyuncs.com -U ro -c "select count(*) from t"', f'psql -h {P} -c "SELECT 1"', f'mysql -h {P} -e "show tables"', f'mysql -h {P} -e "SHOW CREATE TABLE t"', f'psql -h {P} -c "explain select 1"', f'psql -h {P} -q -c "select 1"',
                    f'sqlcmd -S {P} -U ro -Q "set nocount on; select * from t"', f'sqlcmd -S {P} -Q "set nocount on\nselect 1"', f'sqlcmd -S {P} --query "select 1"', f'sqlcmd -S {P} -i ro.sql', f"sqlcmd -S {P} -iro.sql", f'sqlcmd -S {P} < ro.sql',
                    f'sqlcmd -S {P} -Q "select [desc], [update] from [t]"', f'psql -h {P} -c "select \'drop table t\'"', f'psql -h {P} -c "select * from t -- delete me"', f'psql -h {P} -c "\\dt"', f'psql -h {P} -c "\\dt+"',
                    f"psql -h {P} <<'EOF'\nselect 1;\nEOF", f"psql -h {P} <<< 'select 1'", f"echo 'select 1' | psql -h {P}", f"sqlcmd -S {P} -?", f"psql -h {P} --version", f'redis-cli -h {P} -p 6379 get a', f'redis-cli -h {P} slowlog get 5',
                    f'bcp "select 1" queryout o.csv -S {P}', "aliyun rds DescribeDBInstances --InstanceId pgm-prod0001", "aliyun --profile x ecs DescribeInstances --InstanceIds rm-prodhost1", f"aliyun cs GET /clusters/{P}",
                    f'ssh ops@{P} "tail -n 50 /var/log/app.log"', f"ssh {P} 'df -h && uptime'", f"ssh -i key -p 22 {P} 'systemctl status app'", f"ssh {P} docker ps", f"ssh {P} 'date -u +%s'", f"ssh {P} 'date -Iseconds'",
                    f"scp ops@{P}:/var/log/a.log ./a.log", f"rsync -a ops@{P}:/data/ ./data/", f"rsync -a -e 'ssh -p 22' ops@{P}:/data/ ./data/",
                    f"curl https://{P}/health", f"curl -X GET https://db.prod.example.com/x", f"curl -XGET https://db.prod.example.com/x", f"curl -sS -o out.json https://db.prod.example.com/x", f"wget https://db.prod.example.com/x",
                    "git commit -m 'pgm-prod0001 续费说明'", "git log --grep pgm-prod0001", "gh pr comment 3 --body 'psql -h pgm-prod0001 update x'", "echo pgm-prod0001 >> notes.md", "grep pgm-prod0001 docs/x.md", "cat <<'EOF' > n.md\npgm-prod0001\nEOF",
                    f"python3 -I {intent} 'x pgm-prod0001'", "sed -i s/pgm-prod0001/X/ docs/a.md", "ls docs/pgm-prod0001", "rg pgm-prod0001 docs", "python3 clean.py", "bash -c 'echo hi'", f"bash -c \"sqlcmd -S {P} -Q 'select 1'\"",
                    "curl -X POST http://localhost:$PORT/api", 'curl -s -d "$BODY" http://localhost:8080/api', "psql -h localhost -c 'DO $$ begin null; end $$'", "psql -h localhost -c 'select 1' && curl http://localhost:$PORT", "ls $HOME",
                    "cat <<EOF\nplain text pgm-prod0001\nEOF", "echo 'cost $5 for pgm-prod0001'"):
        assert allowed(command), f"must pass: {command!r}"
    # 2. writes to production are refused, every class
    for command in (f'psql -h {P} -c "insert into t values (1)"', f'psql -h {P} -c "UPDATE t SET a=1"', f'psql -h {P} -c "delete from t"', f'psql -h {P} -c "drop table t"', f'psql -h {P} -c "ALTER TABLE t ADD c int"',
                    f'psql -h {P} -c "select 1; delete from t"', f'psql -h {P} -c "select * from t for update"', f'psql -h {P} -c "truncate t"', f"psql -h {P} -f fix.sql", f"psql -h {P} < fix.sql", f"psql -h {P} -f missing.sql",
                    f"psql -h {P}", f'mysql -h {P} -e "grant all on *.* to x"', f"mysql -h {P} < x.sql", f'redis-cli -h {P} flushall', f'redis-cli -h {P} set a 1', f'redis-cli -h {H} slowlog reset', f'redis-cli -h {H} client kill type normal',
                    f'sqlcmd -S {P} -Q "select 1; dbcc shrinkdatabase(x)"', f'sqlcmd -S {P} -Q "select 1; kill 55"', f'sqlcmd -S {P} -Q "backup database x to disk=\'y\'"', f'sqlcmd -S {P} -Q "select 1; deny select on t to u"',
                    f'sqlcmd -S {P} -Q "select 1; checkpoint; reconfigure"', f'sqlcmd -S {P} -Q "exec sp_configure"', f'sqlcmd -S {P} -Q "select * into t2 from t"', f'sqlcmd -S {P} -Q "use master; select 1"',
                    f'psql -h {P} -c "select pg_terminate_backend(1)"', f"psql -h {P} -c \"select 'drop table t' \\gexec\"", f'psql -h {P} -c "select set_config(\'a\',\'b\',false)"', f'psql -h {P} -c "select nextval(\'s\')"',
                    f'psql -h {P} -c \'SELECT pg_catalog."nextval"($$seq$$)\'', f'psql -h {P} -c "SELECT pg_logical_emit_message(false, \'x\', \'x\')"', f'psql -h {P} -c "select 1; comment on table t is \'x\'"',
                    f'psql -h {P} -c "\\copy t from f"', f'psql -h {P} -c "\\i fix.sql"', f'psql -h {P} -c "do $$ begin delete from t; end $$"',
                    f'sqlcmd -S {P} -Q "SELECT \'--\'; DELETE FROM dbo.t"', f'sqlcmd -S {P} -Q "SELECT \'/*\'; DELETE FROM dbo.t; SELECT \'*/\'"', f'sqlcmd -S {P} -Q "SET NOCOUNT ON\nDELETE FROM dbo.t"', f'sqlcmd -S {P} -Q "select \'unterminated"',
                    f'sqlcmd -S {P} -z "$NEW" -Q "select 1"', f'sqlcmd -S {P} -v op="; DELETE FROM dbo.t" -Q \'SELECT 1 $(op)\'', f'sqlcmd -S {P} -Q "select 1 $(op)"', f'mysql -h {H} --init-command="DELETE FROM t" -e "select 1"',
                    f"psql -h localhost -c 'select 1' -o '|ssh {H} touch /tmp/x'", f'psql -h {P} <<EOF\nselect 1;\ndelete from t;\nEOF', f'psql -h {P} <<< "delete from t"', f'echo "delete from t" | psql -h {P}', f'printf "delete from t" | psql -h {P}',
                    f'cat fix.sql | psql -h {P}', f'sqlcmd -S {P} -ifix.sql', f'bcp db.dbo.t in x.dat -S {P}', f'bcp db.dbo.t out x.dat -S {P} -b in', f'bcp "DELETE FROM dbo.t; SELECT 1" queryout ./out.txt -S {P}', f'tsql -H {P}', f'isql {P}',
                    f'sqlpackage /Action:Publish /TargetServerName:{P}', f'mongosh --host {P} --eval "db.t.deleteMany({{}})"',
                    "aliyun rds DeleteAccount --DBInstanceId pgm-prod0001", "aliyun rds ModifyDBInstanceSpec --DBInstanceId pgm-prod0001", "aliyun ecs StopInstance --InstanceId rm-prodhost1", "aliyun --profile pgm-prod0001 ecs RunCommand",
                    "aliyun rds --RegionId DescribeX DeleteAccount --DBInstanceId pgm-prod0001", "aliyun ecs InvokeCommand --InstanceId rm-prodhost1", f"aliyun cs DELETE /clusters/{P}",
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
    for command in ('psql -h localhost -c "insert into t values (1)"', "psql -h pgm-dev00001 -f x.sql", "aliyun rds DeleteAccount --DBInstanceId pgm-dev00001", "ssh dev 'rm -rf /tmp/x'", "scp a dev:/tmp/", "curl -X POST https://dev/x",
                    "python3 clean.py", "xargs -n1 echo", "find . -name '*.py'", "caffeinate -i ls", "psql -h localhost -c 'select 1' -o out.txt"):
        assert allowed(command), f"non-production write must pass: {command!r}"
    # 4. unknown tools that touch production, every one refused
    for command in (f"python3 -c \"import pymssql; pymssql.connect('{P}').execute('delete from t')\"", f"./gradlew flywayMigrate -Pflyway.url=jdbc:sqlserver://{P}", f"xargs -I{{}} sqlcmd -S {P} -Q {{}}",
                    f"find . -exec sqlcmd -S {P} -Q 'delete from t' \\;", f"script -q /dev/null sqlcmd -S {P} -Q 'delete from t'", f"arch -arm64 sqlcmd -S {P} -Q 'delete from t'", f"caffeinate -i sqlcmd -S {P} -Q 'delete from t'",
                    f"alias q='sqlcmd -S {P}'\nq -Q 'delete from t'", f"parallel sqlcmd -S {P} ::: x", "python3 runner.py", f"python3 deploy_x.py --db {P}", f"node -e \"x('{P}')\"", f"mvn flyway:migrate -Dflyway.url={P}",
                    "bash deploy.sh", "source deploy.sh", ". deploy.sh", f"python3 - <<EOF\nconnect('{P}')\nEOF", f"echo \"x\" > /dev/null; python3 <<EOF\nconnect('{P}')\nEOF", f"cat x.sql | python3 -c 'import sys' # {P}",
                    f"bash <<'EOF'\nsqlcmd -S {P} -Q 'delete from t'\nEOF", f'bash <<< "sqlcmd -S {P} -Q \'delete from t\'"', f'echo "sqlcmd -S {P} -Q \'delete from t\'" | bash', f"cat run.sh | bash # {P}",
                    f"python3 -X new_intent.py -c \"import os; os.system('ssh {H} touch /tmp/x')\"", f"node --title new_intent.py -e \"require('child_process').execSync('ssh {H} touch x')\"", f"python3 {cwd}/new_intent.py '{P}'",
                    f"cat > /tmp/gate_probe_x.sh <<'EOF'\nssh {H} touch /tmp/x\nEOF\nbash /tmp/gate_probe_x.sh", f"rg --pre 'ssh {H} touch /tmp/x' x README.md", f"git push ssh://{H}/srv/repo HEAD:main", f"git -c core.sshCommand='ssh {H}' fetch",
                    f"sed -n 'e ssh {H} touch /tmp/x' README.md", f"sed 's/x/ssh {H} touch x/e' README.md", f"sort --compress-program='ssh {H}' f", "bash -c 'bash -c \"bash -c \\\"bash -c \\\\\\\"ssh 192.0.2.15 touch x\\\\\\\"\\\"\\\"\"'",
                    f"eval eval eval eval ssh {H} touch /tmp/x", f"echo ok # <<EOF\nsqlcmd -S {P} -Q 'delete from t'", f'echo "<<EOF"; sqlcmd -S {P} -Q "delete from t"', f"cat <<EOF\n$(sqlcmd -S {P} -Q 'delete from t')\nEOF",
                    f'printf "%s" <(sqlcmd -S {P} -Q "delete from t")', f"export SQLCMDSERVER={P}; sqlcmd -Q 'delete from t'", f"export H={P}; sqlcmd -S\"$H\" -Q 'delete from t'", "curl -XDELETE http://192.0.{2,3}.15/x"):
        assert refused(command), f"unknown tool touching production must be refused: {command!r}"
    assert refused(f'sqlcmd -S {P} -Q "select next value for dbo.s"') and refused(f"psql -h {P} -c \"select query_to_xml('delete from t', true, false, '')\""), "sequence advance and SQL run from a string"
    assert allowed(f"cat <<'EOF'\n$(sqlcmd -S {P} -Q 'delete from t')\nEOF"), "a quoted here-document body is text"
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
    # 6. secrets (bottom line 3) and the environment
    for command in ("aliyun rds DescribeDBInstanceSSL --DBInstanceId pgm-dev00001", "aliyun kms GetSecretValue --SecretName x"):
        assert refused(command), command
    assert refused('psql -c "delete from t"', environ={"PGHOST": P}) and allowed('psql -c "select 1"', environ={"PGHOST": P}) and allowed('psql -c "delete from t"', environ={"PGHOST": "localhost"})
    assert allowed('psql -h localhost -c "delete from t"', environ={"PGHOST": P}), "an explicit host overrides the environment"
    assert refused('sqlcmd -Q "delete from t"', environ={"SQLCMDSERVER": "rm-prodhost1.sqlserver.rds.aliyuncs.com"})
    # 7. table missing or broken: writes are refused, reads pass, interpreters are refused, nothing is silently opened
    assert refused('psql -h localhost -c "insert into t values (1)"', None, "生产特征表缺失") and allowed('psql -h x -c "select 1"', None, "生产特征表缺失")
    assert refused("aliyun rds DeleteAccount --DBInstanceId x", None, "生产特征表缺失") and allowed("ls -la", None, "生产特征表缺失") and refused("python3 clean.py", None, "生产特征表缺失")
    assert refused("python3 -c 'print(1)'", None, "生产特征表缺失") and allowed("mvn -q test", None, "生产特征表缺失") and allowed(f"python3 -I {intent} x", None, "生产特征表缺失")
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
    for path in (os.path.join(HERE, "prod_write_gate.py"), os.path.join(HERE, "prod_write_gate_selftest.py"), os.path.join(HOOKS, "command_words.py"), os.path.join(HERE, "pre_tool_dispatch.py"),
                 os.path.join(HERE, "pre_tool_dispatch_selftest.py")):
        assert allowed(f"python3 -I {path} --self-test", real), path
    assert refused(f"python3 -I {os.path.join(HERE, 'prod_write_gate.py')} -c 'x' {P}")
    # 9. a script is trusted only while it is exactly the file on origin/main (opus round 2, item 2); the sources below mention the pattern "simple_commands"
    real_main_blob, mine = g.main_blob, ["simple_commands"]
    try:
        tool_script = os.path.join(HERE, "prod_write_gate.py")
        selftest = f"python3 -I {tool_script} --self-test"
        g.main_blob = lambda rel: None
        assert refused(selftest, mine), "not on origin/main: not trusted, and its text mentions the pattern"
        g.main_blob = lambda rel: subprocess.run(["git", "-C", REPO_ROOT, "hash-object", os.path.join(REPO_ROOT, rel)], capture_output=True, text=True).stdout.strip()
        assert allowed(selftest, mine), "the same content as origin/main: the guard's own --self-test"
        assert refused(f"python3 -I {tool_script} --instance x", mine), "only --self-test, no other arguments"
        g.main_blob = lambda rel: "0" * 40
        assert refused(selftest, mine), "an edited file (content differs from origin/main) is not trusted"
        # read-only evidence tools and the document scripts: a fixed path, and again only the main content
        EXTRA_READONLY.add("scripts/gates/prod_write_gate.py")
        assert refused(f"python3 -I {tool_script} --instance x", mine), "listed, but edited"
        g.main_blob = lambda rel: subprocess.run(["git", "-C", REPO_ROOT, "hash-object", os.path.join(REPO_ROOT, rel)], capture_output=True, text=True).stdout.strip()
        assert allowed(f"python3 -I {tool_script} --instance x", mine), "listed and unchanged"
        EXTRA_READONLY.discard("scripts/gates/prod_write_gate.py")
        assert refused(f"python3 -I {tool_script} --instance x", mine), "not listed"
        Path(cwd, "new_intent.py").write_text("import os\n", encoding="utf-8")
        assert allowed(f"python3 -I {intent} x", ["import"]) and refused(f"python3 -I {cwd}/new_intent.py x", ["import"]), "new_intent.py is trusted at its own path only"
        assert refused(f"python3 -I {cwd}/new_intent.py x", ["import"]) and refused("python3 new_intent.py x", ["import"]), "same name elsewhere"
        # a new, staged guard script is not on origin/main
        g.main_blob = lambda rel: None
        assert refused(f"python3 -I {intent} x", ["import"]), "new_intent.py not on origin/main"
    finally:
        g.main_blob = real_main_blob
        EXTRA_READONLY.discard("scripts/gates/prod_write_gate.py")
    for command in (f"git tag -f origin/main HEAD && echo {P}", f"git update-ref refs/remotes/origin/main HEAD; echo {P}", f"git grep -nO'sqlcmd -S {P}' foo", f"git grep -inOx {P} foo"):
        assert refused(command), f"origin/main must not be rewritable, -O in a bundle: {command!r}"
    assert "refs/remotes/origin/main" in inspect.getsource(g.main_blob) and "refs/remotes/origin/main" in inspect.getsource(g.main_table_text), "the trust anchor is read by its full ref name, a tag called origin/main cannot shadow it"
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
    print(f"prod_write_gate 17 例: ok（{sum(len(v) for v in cases.values())} 条复现命令 × {len(runs)} 轮全拦：真实表 {len(runs) - 1} 轮、测试表 1 轮）")


def layout_self_test():
    """门本体的版式与出处：行数不超 policy.json 的上限；一行一条语句、块体另起一行（O2 复审 M1）；守护源码里没有生产样本，拆段拼接也算（O2 复审 M3）。"""
    gate_path = os.path.join(HERE, "prod_write_gate.py")
    with open(os.path.join(REPO_ROOT, "policy.json"), encoding="utf-8") as f:
        limit = json.load(f)["gates"]["max_lines"]["prod_write_gate"]
    with open(gate_path, encoding="utf-8") as f:
        source = f.read()
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
        with open(path, encoding="utf-8") as f:
            text = re.sub(r"[^0-9a-z]", "", f.read().lower())
        found = sum(1 for v in folded if v in text)
        assert not found, f"{os.path.basename(path)} 含 {found} 项生产特征（拆段拼接也算），改为运行时从 prod_targets.json 按 id 取"
    print("prod_write_gate layout: ok")


def trust_self_test():
    """可信脚本只认 cwd 与 CLAUDE_PROJECT_DIR 同一仓库（同一个真实 --git-common-dir），特征表并上两者的 origin/main（调度裁定，D25）。全部在临时仓库里做，生产样本只用虚构值。
    主检出 M 与用 git worktree add 建的工作区 W 共用 .git、路径不同，算同一仓库；伪造仓库 F 内容相同但有独立的 .git，不算。"""
    fake, only = "fake-prod-db.example.invalid", "only-on-t.example.invalid"  # fictitious hosts
    script = os.path.join(HERE, "prod_write_gate.py")
    doc = DOCS_ROOT + "/new_intent.py"  # one of the gate's DOC_SCRIPTS, at its fixed path
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        main, work, forged, other, loose = (os.path.join(tmp, n) for n in ("M", "W", "F", "T", "N"))

        def repo(path, files):
            for rel, text in files.items():
                os.makedirs(os.path.dirname(os.path.join(path, rel)), exist_ok=True)
                Path(path, rel).write_text(text, encoding="utf-8")
            for args in (["init", "-q", "-b", "main"], ["add", "-A"], ["commit", "-q", "-m", "x"], ["update-ref", "refs/remotes/origin/main", "HEAD"]):
                subprocess.run(["git", "-C", path, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "core.hooksPath=/dev/null", *args], check=True, capture_output=True)

        body = {doc: f"import os\nos.system(\"psql -h {fake} -c 'delete from t'\")\n"}
        repo(main, body)
        repo(forged, body)
        os.makedirs(os.path.join(loose, DOCS_ROOT))
        Path(loose, doc).write_text(body[doc], encoding="utf-8")  # the same file in a directory that is no repository
        repo(other, {"scripts/gates/prod_targets.json": json.dumps({"targets": [{"id": "t", "patterns": [only]}]})})
        subprocess.run(["git", "-C", main, "worktree", "add", "-q", "--detach", work], check=True, capture_output=True)
        table = os.path.join(tmp, "fake_targets.json")
        Path(table).write_text(json.dumps({"targets": [{"id": "fake", "patterns": [fake]}]}), encoding="utf-8")

        def hook(command, cwd, project, process_cwd=None):
            env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", ""), "PROD_TARGETS_FILE": table, **({"CLAUDE_PROJECT_DIR": project} if project is not None else {})}
            payload = {"tool_name": "Bash", "tool_input": {"command": command}, **({"cwd": cwd} if cwd else {})}
            return subprocess.run([sys.executable, "-I", script], input=json.dumps(payload), capture_output=True, text=True, env=env, cwd=process_cwd or tmp).returncode

        run_doc = "python3 " + doc
        for cwd, project, code, why in ((work, main, 0, "a worktree of the project's repository: one .git, other path, trusted"), (main, work, 0, "the other way round"),
                                        (main, main, 0, "the project itself"), (forged, main, 2, "a forged repository with its own .git is not trusted"),
                                        (work, forged, 2, "the project forged, cwd real"), (forged, forged + "/", 0, "the forged repository as its own project (control)"),
                                        (work, None, 2, "no CLAUDE_PROJECT_DIR"), (work, "", 2, "an empty CLAUDE_PROJECT_DIR"), (loose, main, 2, "cwd in no repository")):
            assert hook(run_doc, cwd, project) == code, (why, cwd, project)
        assert hook(run_doc, None, main, process_cwd=work) == 2, "no cwd in the hook input: not trusted, even when the process runs in the worktree"
        # the table: cwd's and CLAUDE_PROJECT_DIR's origin/main are unioned in, so only ever more is refused
        write = f"psql -h {only} -c 'delete from t'"
        assert hook(write, other, main) == 2 and hook(write, main, other) == 2 and hook(write, main, main) == 0, "a pattern only on the origin/main of cwd's or the project's repository"
        assert hook(write, None, main, process_cwd=other) == 2, "no cwd in the hook input: the process cwd's repository is unioned in"
    print("prod_write_gate trust: ok")


def self_test():
    words_self_test()
    gate_self_test()
    trust_self_test()
    seventeen_self_test()
    layout_self_test()
    return 0


if __name__ == "__main__":
    sys.exit(self_test())
