#!/usr/bin/env python3
"""门的自测，合一（O8，D25；定稿 1.10「自测 1 个文件」）。`gates_selftest.py [段…]` 跑指定的段，不带参数跑全部；各门的 `--self-test` 只跑自己那一段。
- prod_write_gate：命令解析、生产写门（卡 #736：原先误拦的样例放行，远程工具加生产特征照拒）、「生产写门 17 例」（Codex 复核 #653 的 17 条 P1 缺口，测试表与真实特征表下都须拒绝，按计数断言）、版式。
- pre_tool_dispatch：预筛不藏拒绝、失败关闭、时限。main_checkout_gate（含 git_ceiling_env.sh）与 commit_msg_gate：在临时仓库与 worktree 里端到端提交。
- install：install.sh 与 .claude/settings.json 挂载，全部在临时目录里做，HOME 指向临时目录，不碰真实安装目录（安装、切换、并发、自检不过、只读、篡改核对与修复、挂载失败关闭）。
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
import types
from pathlib import Path
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import pre_tool_dispatch as d  # noqa: E402
import prod_write_gate as g  # noqa: E402
from prod_write_gate import (COMMAND_TOOLS, LAUNCH_TOOL, REPO_ROOT, brace_variants, could_concern, decode_ansi_c, judge, load_table, name_of, plain, simple_commands, tokens_of)  # noqa: E402

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
    assert simple_commands("export SQLCMDSERVER=prod; sqlcmd -Q x")[-1].env == {"SQLCMDSERVER": "prod"} and simple_commands("export A=1; unset A; ls")[-1].env == {}
    assert simple_commands("A=1; export A; ls")[-1].env == {"A": "1"} and simple_commands("A=1; declare -x A; ls")[-1].env == {"A": "1"} and simple_commands("A=1; ls")[-1].env == {}
    assert simple_commands("set -a; A=1; ls")[-1].env == {"A": "1"} and simple_commands("set -o allexport; A=1; ls")[-1].env == {"A": "1"} and simple_commands("set -a; set +a; A=1; ls")[-1].env == {}
    assert simple_commands("PGHOST=x; ls")[-1].env == {"PGHOST": "x"} and simple_commands("SQLCMDSERVER=x; sqlcmd")[-1].env == {"SQLCMDSERVER": "x"}, "a target variable may be exported already"
    assert simple_commands("A=1; declare -a A; ls")[-1].env == {} and simple_commands("export B; ls")[-1].env == {}, "an array declaration or an unknown name exports nothing"
    assert argvs("export H=prod; sqlcmd -S\"$H\" -Q x")[-1][:2] == ["sqlcmd", "-Sprod"]
    for bad in ("eval eval eval eval ssh h touch x", "echo 'unterminated"):  # nesting deeper than 3, unreadable input
        with contextlib.suppress(ValueError):
            simple_commands(bad)
            raise AssertionError(f"must raise: {bad!r}")
    print("command_words self-test: ok")

MISBLOCKED = (  # no production sample on the line: refused before 卡 #736, allowed now; the first four were refused in one-ops' own session on 10-28
    "grep -rnE 'Closes #' .github scripts 2>/dev/null | grep -v selftest | head -20", "grep -rnlE 'mq\\.py|入队' docs/*.md | head",
    "/usr/bin/python3 -I -c 'import json;d=json.load(open(\"s.json\"));print(d[\"hooks\"])'", "grep -n x deploy.sh",
    "cd scripts/gates && python3 gates_selftest.py", "python3 - <<'EOF'\nprint(1)\nEOF", "setopt nullglob; ls *.md", "ls *(.)", "npm --prefix frontend run build", "make -C backend test", "env -C /tmp ls",
    "echo -e 'a\\tb'", "printf 'a\\n'", "f() { ls; }; f", "x=(1 2); echo $x", "eval \"$(printf x)\"", "git -c core.pager=cat log -1", "PAGER=cat git log -1", "cd pkg && make && ./dist/app",
    "cat <<EOF > n.md\nnotes # x\nEOF", "psql -h localhost -c 'select 1'", "curl -s https://api.github.com/repos/x/y", "ssh dev-box uptime", "scp a dev:/tmp/", "curl -X POST http://localhost:8080/api")
BY_DESIGN = ("./deploy.sh", "bash deploy.sh", "python3 runner.py", "cat <<EOF > n.md\nnotes # x\nEOF\nbash deploy.sh")  # the file run names production, the line does not: allowed, the read-only account holds (用户 10-28)
ADV_HOST, ADV_IP = "prod-db.fictional-corp.example", "203.0.113.45"  # the final review's 56 adversarial samples (7cb55cdc final), fictitious host and TEST-NET address


def adversarial():
    """(refused, allowed by design) of the final review's 56 samples: every one with the host or address literally on the line (after quotes, backslashes, variables and braces) is refused;
    hidden by encoding, command substitution, a non-target session variable, or inside a file run: allowed (用户 10-28「见生产特征即拒，其余靠只读账号」)."""
    H, IP, b64 = ADV_HOST, ADV_IP, "cHJvZC1kYi5maWN0aW9uYWwtY29ycC5leGFtcGxl"
    refused = [f"psql -h {H} -c 'delete from t'", f"alias q=psql; q -h {H} -c x", f"f() {{ psql \"$@\"; }}; f -h {H} -c x", f"eval \"ps\"\"ql -h {H} -c x\"", f"a=ps; b=ql; eval \"$a$b -h {H} -c x\"",
               f"t=ql; ps$t -h {H} -c x", f"$(echo ps)ql -h {H} -c x", f"$(printf %s ps)$(printf %s ql) -h {H} -c x", f"/opt/homebrew/bin/ps?l -h {H} -c x", f"/opt/homebrew/bin/psql -h {H} -c x",
               f"./bin/psql -h {H} -c x", f"PSQL -h {H.upper()} -c x", f"$'\\x70sql' -h {H} -c x", f"X={H}; psql -h $X -c x", f"a=prod-db; b=.fictional-corp.example; psql -h $a$b",
               f"export PGHOST={H}; psql -c x", "psql -h prod-db.{fictional-corp,zz}.example", "psql -h prod-db\".fictional-corp\".example", f"bash <<< \"psql -h {H} -c x\"", f"env psql -h {H} -c x",
               f"/usr/bin/env psql -h {H} -c x", f"env PGHOST={H} psql -c x", f"env -S 'psql -h {H} -c x'", f"echo {H} | xargs -I{{}} psql -h {{}} -c x", f"echo psql -h {H} -c x | xargs -0 sh -c",
               f"python3 -c \"import subprocess;subprocess.run(['psql','-h','{H}','-c','x'])\"", f"python3 -c \"import socket;socket.create_connection(('{IP}',5432)).send(b'x')\"",
               f"python3 -c \"import urllib.request as u;u.urlopen('https://{H}/api',data=b'x')\"", f"node -e \"require('net').connect(5432,'{H}').write('x')\"", f"node -e \"fetch('https://{H}/api',{{method:'POST'}})\"",
               f"printf 'psql -h {H} -c x' > fx/a.sh && bash fx/a.sh", f"nc {IP} 5432 < dump.sql", f"socat - TCP:{H}:5432", f"telnet {H} 5432", f"mongorestore --host {H} dump/",
               f"kubectl --server=https://{H}:6443 delete pod x", f"sshpass -p x ssh root@{IP} rm -rf /data", f"git push ssh://git@{H}/srv/repo main", f"echo x > /dev/tcp/{IP}/5432",
               f"git push https://{H}/srv/repo main", f"./gradlew flywayMigrate -Pflyway.url=jdbc:sqlserver://{H}:1433", f"mvn flyway:migrate -Dflyway.url=jdbc:postgresql://{H}/app",
               f"DATABASE_URL=postgres://{H}/app npx prisma migrate deploy", f"DOCKER_HOST=tcp://{H}:2376 docker rm -f app", f"ansible all -i {IP}, -m shell -a 'rm -rf /data'",
               f"mongoimport --host {H} -c t x.json", f"pg_basebackup -h {H} -D out", f"lftp -e 'rm x' {H}", f"grep -r {H} docs/", f"git commit -m '{H} 续费说明'", f"s''sh {H} uptime 'x", f"c\\url https://{H}/"]
    allowed = ["psql -h $MYTARGET", f"psql -h $(echo {b64} | base64 -d)", "psql -h $(printf '\\x70rod-db.fictional-corp.example')", f"bash <<< \"$(echo {b64} | base64 -d)\"",
               f"echo {b64} | base64 -d | sh", "bash run.sh", "python3 run.py", "psql -h dev-db.fictional-corp.example -c x", "cd fx && python3 -c 'print(1)'"]
    return refused, allowed


def cloud_self_test(verdict):
    """阿里云命令行（卡 #736，用户「先不动，门补一条」）：本机默认 profile 是生产，只认段首裸写 aliyun 且显式给一个不在特征表的 --profile；生产 profile 按真实表取（源码里不写）。"""
    prod = shipped_firsts()["aliyun-prod-profile"]
    real = load_table()[0]
    allowed = ("aliyun --profile dev-ro ecs DescribeInstances", "aliyun ecs DescribeRegions --profile=dev-ro", "aliyun -p dev-ro ecs DescribeRegions", "aliyun --profile dev-ro ecs DescribeRegions --RegionId cn-hangzhou",
               "aliyun --profile dev-ro ecs DescribeRegions && echo ok", "echo ok; aliyun --profile dev-ro sts GetCallerIdentity", "aliyun --profile dev.ro_1 oss ls oss://bucket/a/", "cat <<EOF\naliyun --profile dev-ro ecs X\nEOF")
    for command in allowed:
        assert verdict(command) is None and judge(command, real, None, None, {}) is None, ("a non-production profile given explicitly: allowed", command)
    others = ("ls *.md", "git status", "python3 x.py", "$EDITOR notes.md", "eval \"$(printf x)\"", "echo a* | xargs ls", "find . -name '*.py' -exec wc -l {} +", "$(which python3)", "rm -f /tmp/a?c", "X=$(date) make test", "cd \"$(git rev-parse --show-toplevel)\" && ls", "echo \"$(date)\" done")
    for command in others:
        assert verdict(command, environ={"EDITOR": "vim"}) is None, ("no cloud CLI, written or hidden: allowed", command)
    # #736 首轮 M2 (a)：门读到的参数与 aliyun 实际收到的可能不同（注释、重定向、引号、反斜杠续行、变量、通配、花括号、~、here-string）
    unreadable = ("aliyun ecs DescribeRegions # --profile dev-ro", "aliyun ecs DescribeRegions #--profile dev-ro", "aliyun ecs X 2>--profile dev-ro", "aliyun ecs X >/dev/null --profile dev-ro",
                  "aliyun --profile 'dev-ro' ecs X", "aliyun 'ecs' DescribeRegions --profile \"dev-ro\"", "aliyun --profile dev-ro ecs X \\\n--profile other", "aliyun --profile dev-ro \\\necs X",
                  "aliyun --profile dev-ro ecs $X", "aliyun --profile dev-ro ecs *", "aliyun --profile dev-ro oss cp a ~/b", "aliyun --profile dev-ro ecs {a,b}", "aliyun --profile dev-ro ecs X <<< y",
                  "aliyun --profile dev-ro ecs X <(true)", "=aliyun --profile dev-ro ecs X", "aliyun --profile dev-ro ecs X!", "aliyun --profile dev-ro ecs X^", "ALIBABA_CLOUD_PROFILE=dev-ro; aliyun --profile dev-ro ecs X",
                  "export ALIBABA_CLOUD_ACCESS_KEY_ID=x; aliyun --profile dev-ro ecs X")
    # (b)：程序名不是字面写出（通配、花括号、命令替换、会话环境变量、行内变量），也包括 xargs、find -exec 之后的程序
    hidden = ("a*n ecs DescribeRegions", "/opt/homebrew/bin/aliy?n ecs X", "ali[y]un ecs X", "ali{y,y}un ecs X", "$(printf ali)yun ecs X", "`printf ali`yun ecs X", "$(printf aliy; printf un) ecs X",
              "a=ali; ${a}yun --profile dev-ro ecs X", "aliyun --profile dev-ro sts X; a=ali; ${a}yun ecs X", "echo x | xargs a*n ecs", "echo x | xargs -0 $(printf ali)yun ecs", "find . -exec ali?un ecs {} +",
              "eval \"$(printf ali)yun ecs X\"", "bash -c '$(printf ali)yun ecs X'", "X=1 $(printf ali)yun ecs X", "sudo -u me $(printf ali)yun ecs X", "timeout 9 $(printf ali)yun ecs X", "true && \"$(printf ali)yun\" ecs X")
    for command in unreadable + hidden:
        assert verdict(command) == g.CLOUD_REFUSED, ("the gate cannot tell what the CLI receives, or that it is the CLI: refused", command, verdict(command))
    for name, value in (("CLOUD", "aliyun"), ("CLOUD", "/usr/local/bin/aliyun"), ("CLI", "ALIYUN")):
        assert verdict(f"${name} ecs DescribeRegions", environ={name: value}) == g.CLOUD_REFUSED and verdict(f"echo x | xargs ${{{name}}} ecs", environ={name: value}) == g.CLOUD_REFUSED, ("a session variable", name, value)
        assert verdict(f"${name} ecs DescribeRegions", environ={name: "vim"}) is None
    refused = ("aliyun ecs DescribeRegions", "aliyun", "echo ok && aliyun ecs DescribeRegions", "aliyun --profile", "aliyun --profile a1 --profile b2 ecs X", "aliyun --profile a1 -p b2 ecs X",
               "aliyun --profile $P ecs X", "aliyun --profile \"$(echo dev)\" ecs X", "aliyun -pdev-ro ecs X", "aliyun --profile-x dev-ro ecs X", "ALIYUN --profile dev-ro ecs X",
               "/usr/local/bin/aliyun --profile dev-ro ecs X", "./aliyun --profile dev-ro ecs X", "~/bin/aliyun --profile dev-ro ecs X",
               "alias a=aliyun; a --profile dev-ro ecs X", "a=aliyun; $a --profile dev-ro ecs X", "a=ali; ${a}yun --profile dev-ro ecs X", "f() { aliyun \"$@\"; }; f --profile dev-ro ecs X",
               "env aliyun --profile dev-ro ecs X", "sudo aliyun --profile dev-ro ecs X", "command aliyun --profile dev-ro ecs X", "timeout 9 aliyun --profile dev-ro ecs X",
               "ALIBABA_CLOUD_PROFILE=dev-ro aliyun ecs X", "echo x | xargs aliyun --profile dev-ro ecs", "bash -c 'aliyun --profile dev-ro ecs X'", "eval aliyun --profile dev-ro ecs X",
               "which aliyun", "echo 'unterminated aliyun", "al\"\"iyun ecs DescribeRegions", "ali\\yun ecs DescribeRegions")
    for command in refused:
        assert verdict(command) == g.CLOUD_REFUSED, ("no explicit non-production profile, or not a bare call: refused", command, verdict(command))
    for command in (f"aliyun --profile {prod} ecs DescribeRegions", f"aliyun --profile={prod.upper()} ecs X", f"aliyun -p {prod} ecs X", f"aliyun --profile {prod}-ro ecs X"):
        assert judge(command, real, None, None, {}) is not None, ("the production profile, by the real table: refused", command)
    print(f"prod_write_gate 阿里云: ok（放行 {len(allowed)} 例、不涉及的 {len(others)} 例；拒 {len(refused)} 例、参数读不准 {len(unreadable)} 例、程序名藏起来 {len(hidden)} 例、会话变量 6 例、生产 profile 4 例）")

def gate_self_test():
    """卡 #736，用户 10-28「见生产特征即拒，其余靠只读账号」：命令文本里有生产特征就拒，不分程序；原先误拦的样例（没有生产特征）放行；终审 56 例里主机字面可见的全拒。"""
    patterns, P, H = ["pgm-prod0001", "rm-prodhost1", "db.prod.example.com", TEST_IP, ADV_HOST, ADV_IP], "pgm-prod0001", TEST_IP
    cwd = tempfile.mkdtemp()
    for name, text in (("deploy.sh", f"ssh {H} rm -rf /srv/x\n"), ("runner.py", f"connect('{P}')\n"), ("run.sh", f"psql -h {ADV_HOST} -c 'delete from t'\n"), ("run.py", f"connect('{ADV_IP}')\n")):
        Path(cwd, name).write_text(text, encoding="utf-8")  # files that name production: not read (by design)
    verdict = lambda command, table=patterns, problem=None, environ=None: judge(command, table, problem, cwd, environ or {"MYTARGET": ADV_HOST})  # noqa: E731
    for command in MISBLOCKED + BY_DESIGN:
        assert verdict(command) is None, ("no production sample on the line: allowed", command)
    still = (f"psql -h {P} -c 'select 1'", f"ssh {H} uptime", f"curl https://{P}/health", "wget https://db.prod.example.com/x", "aliyun rds DescribeDBInstances --DBInstanceId pgm-prod0001",
             "git commit -m 'pgm-prod0001 续费说明'", "echo pgm-prod0001 >> notes.md", "grep -rn pgm-prod0001 docs", f"python3 -c \"connect('{P}')\"", f"./gradlew flywayMigrate -Pflyway.url=jdbc:sqlserver://{P}",
             "H=pgm-pr; echo ${H}od0001", "T=ps; H=pgm-pr; ${T}ql -h ${H}od0001", "export SQLCMDSERVER=pgm-prod0001; ls", "curl -XDELETE http://192.0.{2,2}.15/api/item/1", "echo pgm-prod{0001,0002}",
             f"s\"\"sh {H} uptime", "PGM-PROD0001", "pgm-pr\\od0001", "echo 'unterminated pgm-prod0001", f"echo ok # x\nls {P}",
             "echo \"a pgm-pr\"\"od0001 'x")  # the last: quotes split the sample and the line cannot be parsed: only the quote-free line shows it (first round M4)
    for command in still:
        assert verdict(command) is not None and "生产特征" in verdict(command), ("a production sample on the line: refused", command)
    refused, allowed = adversarial()
    assert len(refused) + len(allowed) - 2 >= 56, "the final review's 56 samples (plus the two quote / backslash ones of the first round's M4)"
    missed = [c for c in refused if verdict(c) is None]
    assert not missed, ("adversarial samples with the host on the line must be refused", missed)
    assert all(verdict(c) is None for c in allowed), [c for c in allowed if verdict(c)]
    assert verdict("ls", environ={"PGHOST": P}) and verdict("git status", environ={"DATABASE_URL": f"sqlserver://{P}"}) and verdict("ls", environ={"PGHOST": "localhost"}) is None, "a session target variable"
    assert verdict("ls", None, "表坏了") and verdict("ls -la", None, "表坏了"), "no table: refused (fail closed)"
    cloud_self_test(verdict)
    # brace expansion and the table's format
    assert brace_variants("a{1,2}b") == ["a1b", "a2b"] and brace_variants("192.0.{2..4}.15") == [TEST_IP, "192.0.3.15", "192.0.4.15"] and brace_variants("plain") == ["plain"]
    with tempfile.TemporaryDirectory() as tmp:
        good = os.path.join(tmp, "t.json")
        for text, ok in ((json.dumps({"targets": [{"id": "a", "patterns": ["abcdef123"]}]}), True), ('{"targets": []}', True), ("not json", False), ("{}", False), ('{"targets": [{"id": "a"}]}', False),
                         ('{"targets": [{"id": "a", "patterns": []}]}', False), ('{"targets": [{"id": "a", "patterns": ["ab"]}]}', False), ('{"targets": [{"id": "a", "patterns": [1]}]}', False), ('{"targets": "x"}', False)):
            open(good, "w").write(text)
            assert (load_table(good)[1] is None) == ok, text
        assert load_table(os.path.join(tmp, "missing.json"))[1] is not None
        table, problem, _ = load_table()
        firsts = shipped_firsts()
        assert problem is None and table and firsts and all(v.lower() in table for v in firsts.values()), "the shipped table loads and every target's first pattern is in it"
        assert not could_concern("") and could_concern("ls")
        # hook process end to end, with the shipped, a missing and an empty table
        script = os.path.join(HERE, "prod_write_gate.py")
        def hook(command, table_path=None, tool="Bash", env=None, raw=None):  # `env` replaces the hook's environment, `raw` its input
            env = env or {"LC_ALL": "C", "PATH": os.environ.get("PATH", ""), **({"PROD_TARGETS_FILE": table_path} if table_path else {})}
            return subprocess.run([sys.executable, "-I", script], input=raw or json.dumps({"tool_name": tool, "tool_input": {"command": command}, "cwd": cwd}), capture_output=True, text=True, env=env)
        for name, value in firsts.items():  # every target of the real table, whatever the program: refused
            run = hook(f"grep -rn {value} docs")
            assert run.returncode == 2 and "底线 4" in run.stderr, name
        assert hook("ls").returncode == 0 and hook("x", tool="Read").returncode == 0 and hook("python3 -c \"connect('" + firsts["legacy-rds-sqlserver"] + "')\"").returncode == 2
        gone, remote = hook("python3 clean.py", os.path.join(tmp, "missing.json")), hook("psql -c 'select 1'", os.path.join(tmp, "missing.json"))
        assert gone.returncode == 2 and remote.returncode == 2 and "缺失或不合格" in gone.stderr, (gone, remote)  # no table: everything refused
        Path(tmp, "empty.json").write_text('{"targets": []}', encoding="utf-8")
        no_git = {"LC_ALL": "C", "PATH": tmp, "PROD_TARGETS_FILE": os.path.join(tmp, "empty.json")}
        assert hook("python3 clean.py", env=no_git).returncode == 2 and "为空" in hook("ls", env=no_git).stderr, "an empty table, no git: refused"
        assert [hook(None, env={"LC_ALL": "C"}, raw=raw).returncode for raw in ("not json", "[1]")] == [2, 2], "input that is no JSON object"
    print(f"prod_write_gate self-test: ok（原先误拦 {len(MISBLOCKED)} 例与按设计不管的 {len(BY_DESIGN)} 例放行，含生产特征 {len(still)} 例照拒；终审对抗样例 {len(refused)} 例拒、按设计不管 {len(allowed)} 例放行）")

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
    """门本体的版式与出处：一行一条语句、块体另起一行（O2 复审 M1）；守护源码里没有生产样本，拆段拼接也算（O2 复审 M3）。行数上限按卡 #736 不再查。"""
    source = Path(HERE, "prod_write_gate.py").read_text(encoding="utf-8")
    lines = source.split("\n")
    raw = [line.encode("utf-8") for line in lines]  # col_offset counts bytes
    crowded = [node.lineno for node in ast.walk(ast.parse(source)) if isinstance(node, ast.stmt) and node.col_offset != len(raw[node.lineno - 1]) - len(raw[node.lineno - 1].lstrip(b" "))]
    assert not crowded, f"prod_write_gate.py 有一行多条语句或块体与条件同行：{sorted(set(crowded))[:20]}"
    folded = [re.sub(r"[^0-9a-z]", "", v.lower()) for v in load_table()[0]]
    sources = [os.path.join(HERE, name) for name in sorted(os.listdir(HERE)) if name.endswith(".py")] + [os.path.join(HOOKS, "command_words.py")]
    for path in sources:
        text = re.sub(r"[^0-9a-z]", "", Path(path).read_text(encoding="utf-8").lower())
        found = sum(1 for v in folded if v in text)
        assert not found, f"{os.path.basename(path)} 含 {found} 项生产特征（拆段拼接也算），改为运行时从 prod_targets.json 按 id 取"
    print("prod_write_gate layout: ok")

def prod_write_gate_test():
    words_self_test()
    gate_self_test()
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
        # 1b. a script that names production, run by an interpreter: the gate no longer reads files it runs (卡 #736; known gap, the read-only account holds), dispatcher and gate agree
        for name in ("deploy", "deploy.lua"):
            put(os.path.join(elsewhere, name), f"ssh {PROD_H} rm -rf /srv/x\n", 0o644)
        for command in ("lua deploy.lua", "dash deploy", "ksh deploy", ". deploy", "nohup dash deploy", "lua deploy.lua && echo ok", "cat sshd_conf 'oops", "sudo -u x ksh deploy", "(. deploy)"):
            raw = payload(command, cwd=elsewhere)
            assert g.decide(json.loads(raw))[0] == 0 and d.dispatch(raw) == g.decide(json.loads(raw)), command
    # 2. every one of the 17 cases is refused through the dispatcher, with the gate's own reason
    for number, commands in seventeen(PROD_P, PROD_H, PROD_D).items():
        for command, tool in ((c, t) for c in commands for t in COMMAND_TOOLS):  # Monitor and the terminal panel run shell commands as Bash does (intent 160437)
            code, out, err = d.dispatch(payload(command, tool_name=tool))
            assert code == 2 and err.strip(), (number, command, tool)
    # 3. ordinary commands and other tools start nothing; checks that used to hang off the dispatcher are no longer here
    for command in ("ls", "echo hi", "git status", "npm test", "codex exec p", "git push --no-verify", "gh pr create --title x"):
        assert d.dispatch(payload(command)) == (0, "", ""), command
    # preview_start runs the named .claude/launch.json configuration: judged as that command; a url starts nothing; an unreadable configuration is refused
    with tempfile.TemporaryDirectory() as launch, tempfile.TemporaryDirectory() as other, mock.patch.dict(os.environ, CLAUDE_PROJECT_DIR=launch):  # the app reads the session's working directory
        configs = [{"name": "w", "runtimeExecutable": "psql", "runtimeArgs": ["-h", PROD_H, "-c", "delete from t"]}, {"name": "ok", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"]},
                   {"name": "args", "runtimeExecutable": "psql", "args": ["-h", PROD_H, "-c", "delete from t"]}, {"name": "prog", "runtimeExecutable": "sh", "runtimeArgs": ["-c"], "program": f"psql -h {PROD_H} -c 'delete from t'"},
                   {"name": "envw", "env": {"PGHOST": PROD_H}, "runtimeExecutable": "psql", "runtimeArgs": ["-c", "delete from t"]},
                   {"name": "twice", "runtimeExecutable": "npm"}, {"name": "TWICE", "runtimeExecutable": "npm"},
                   {"name": "cwdpy", "runtimeExecutable": "/usr/bin/python3", "runtimeArgs": ["run.py", PROD_H], "cwd": "sub"}, {"name": "var", "runtimeExecutable": "npm", "runtimeArgs": ["${env:X}"]}]
        put(os.path.join(launch, ".claude", "launch.json"), json.dumps({"configurations": configs}), 0o644)
        put(os.path.join(other, ".claude", "launch.json"), json.dumps({"configurations": [{"name": "w", "runtimeExecutable": "npm"}]}), 0o644)
        put(os.path.join(other, "unnamed", ".claude", "launch.json"), json.dumps({"configurations": [{"runtimeExecutable": "psql", "runtimeArgs": ["-h", PROD_H]}, {"name": "ok", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"]}]}), 0o644)
        for tool_input, where, code in (({"name": "w"}, launch, 2), ({"name": "ok"}, launch, 0), ({"url": "http://localhost:3000"}, launch, 0), ({"name": "missing"}, launch, 2),
                                        ({"name": "args"}, launch, 2), ({"name": "prog"}, launch, 2), ({"name": "w"}, other, 2), ({"name": "ok"}, other, 2), ({"name": "W"}, launch, 2),
                                        ({"name": "envw"}, launch, 2), ({"name": "twice"}, launch, 2), ({"name": "cwdpy"}, launch, 2), ({"name": "var"}, launch, 2)):  # other: a second launch.json disagrees or lacks the name; W: case ignored
            assert d.dispatch(json.dumps({"tool_name": LAUNCH_TOOL, "tool_input": tool_input, "cwd": where}))[0] == code, (tool_input, where)
        assert g.launch_command({"name": "ok"}, os.path.join(other, "unnamed"))[0] is None, "an unnamed entry: the app derives its name, so which one runs cannot be seen (69af6c8da review M2)"
        assert g.launch_command({"name": "cwdpy"}, launch)[0].startswith("cd sub && "), "the configuration's cwd runs first: a relative script there is another file"
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
    def boom(payload):
        raise RuntimeError("boom")
    Crashing, Hanging = (types.SimpleNamespace(plain=g.plain, command_of=g.command_of, could_concern=lambda text: True, decide=decide)  # the gate's own parsing: decide is reached
                         for decide in (boom, lambda payload: time.sleep(5) or (0, "", "")))  # #47: without command_of main stopped at an AttributeError, the hang went untested
    with mock.patch.object(sys, "stdin"), mock.patch.object(d, "DEADLINE", 1):  # a gate with no answer in time is a failure (O7 final, suggestion 1)
        for loader, why in ((broken_import, "ImportError: no prod_write_gate"), (lambda: Crashing, "RuntimeError: boom"), (lambda: Hanging, "Overdue: 超过 1 秒没有结论")):
            sys.stdin = io.StringIO(payload(f"psql -h {PROD_P} -c 'select 1'"))
            with contextlib.redirect_stderr(io.StringIO()) as err:
                code = d.main(loader)
            assert code == 3 and why in err.getvalue() and "生产写门出错" in err.getvalue() and "保守预筛" in err.getvalue(), (why, code, err.getvalue())
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
        refused, passed, bad = (subprocess.run([*runner, DISPATCH], input=text, capture_output=True, text=True, env=env) for text in (payload(f"aliyun rds DeleteAccount --DBInstanceId {PROD_P}"), payload("ls"), "not json"))
        assert refused.returncode == 2 and "底线 4" in refused.stderr and passed.returncode == 0 and not passed.stdout and not passed.stderr and bad.returncode == 2 and "拒绝" in bad.stderr, (refused, passed, bad)
    print("pre_tool_dispatch self-test: ok")

MAIN_GATE = os.path.join(HERE, "main_checkout_gate.py")  # ── main_checkout_gate ──
REFUSED = "拒绝：这是在主检出"
FAILED = "主检出提交保护出错"

def run(*args, cwd, **extra):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, env=env(**extra))

def checkout_gate(cwd, **extra):
    r = run(PYTHON, "-I", MAIN_GATE, "--pre-commit", cwd=cwd, **extra)
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
        lonely, empty, fake = (os.makedirs(os.path.join(d, name)) or os.path.join(d, name) for name in ("lonely", "empty-bin", "fake-bin"))
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
    # 5. wiring
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

def renamed_test():
    """卡 #736 第 8 项（用户 intent 2026-10-28-171648）：one-coordinator→one-leader、one-1a-par→one-dev-par。新名在 sessions.config、有会话文件、提交说明门认得；
    旧名只留在 aliases（指向新名），不再是会话；合入队列的分支表把新旧分支前缀都归到新名。"""
    sys.path.insert(0, HERE)
    import commit_msg_gate as cm
    sessions, root = policy("sessions"), Path(ROOT)
    for new, old, role in (("one-leader", "one-coordinator", "lead"), ("one-dev-par", "one-1a-par", "dev")):
        assert sessions["config"][new]["role"] == role and new in sessions["config_sources"] and sessions["aliases"][old] == new, new
        assert old not in sessions["config"] and old not in sessions["config_sources"] and not (root / f"docs/modernization/sessions/{old}.md").exists(), old
        assert (root / f"docs/modernization/sessions/{new}.md").is_file() and new in {f[:-3] for f in os.listdir(root / "docs/modernization/sessions")}, new
        with tempfile.TemporaryDirectory() as tmp:  # the commit-msg gate reads names from the sessions directory and the marker
            for rel in (f"docs/modernization/sessions/{new}.md", ".claude/session-name"):
                put(os.path.join(tmp, rel), new + "\n", 0o644)
            assert cm.session_name(Path(tmp), "claude/x") == new and cm.session_name(Path(tmp), f"claude/{new}-x") == new, new
    assert all(v in sessions["config"] for v in sessions["aliases"].values()), "every alias points at a session"
    prefixes = json.loads(Path(ROOT, "scripts/merge/session_map.json").read_text(encoding="utf-8"))["prefixes"]
    assert set(prefixes.values()) <= set(sessions["config"]), ("gapscan owners are sessions", set(prefixes.values()) - set(sessions["config"]))
    assert prefixes["claude/one-coordinator"] == prefixes["claude/one-leader"] == "one-leader" and prefixes["claude/one-1a-par"] == prefixes["claude/one-dev-par"] == "one-dev-par"

def commit_msg_gate_test():
    with tempfile.TemporaryDirectory() as raw:
        tmp = os.path.realpath(raw)
        repo = os.path.join(tmp, "repo")
        hooks = os.path.join(tmp, "hooks")
        put(os.path.join(hooks, "commit-msg"), f'#!/bin/sh\nexec {PYTHON} -I {MSG_GATE} "$1"\n')
        git(tmp, "init", "-q", "-b", "main", repo)
        git(repo, "config", "core.hooksPath", hooks)
        for rel, text in (*((f"docs/modernization/sessions/{name}.md", "x\n") for name in ("one-ops", "one-1a", "one-1a-next-3", "_retired")), ("docs/modernization/roles/one-platform.md", "x\n"), (".claude/session-name", "one-ops\n")):
            put(os.path.join(repo, rel), text)
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", "init", "--no-verify")
        for title, kept, why in (("feat: 改脚本，正文没有任务号也没有 PR 号", lambda msg: msg.rstrip().endswith("Session: one-ops"), "no task id or PR is accepted and gets the trailer: "),
                                 ("feat: x\n\n为什么：y\n\nCo-Authored-By: A <a@b>", lambda msg: "Co-Authored-By: A <a@b>\nSession: one-ops" in msg and msg.count("Session:") == 1, ""),
                                 ("feat: x\n\nSession: one-1a", lambda msg: msg.count("Session:") == 1 and "Session: one-1a" in msg, "an existing Session is kept: "),
                                 ("fixup! feat: x", lambda msg: "Session:" not in msg, "fixup titles are left alone: "), ("#593 修复钩子", lambda msg: "Session: one-ops" in msg, "a title starting with #<number> is not a comment: ")):
            code, err, msg = commit(repo, title)
            assert code == 0 and kept(msg), why + err + msg
        guard = [p.rstrip("/") + ("/x.py" if p.endswith("/") else "") for p in policy("review")["final_paths"][:3]]  # 卡 #736：守护文件不须单独提交
        for rel in guard + ["README.md"]:
            put(os.path.join(repo, rel), "x\n", 0o644)
        git(repo, "add", "-A")
        r = git(repo, "commit", "-q", "-m", "feat: 守护路径与普通文件同一提交", ok=False)
        files = git(repo, "show", "--name-only", "--format=", "HEAD").stdout.split()
        assert r.returncode == 0 and set(guard + ["README.md"]) <= set(files) and "Session: one-ops" in git(repo, "log", "-1", "--format=%B").stdout, ("a guard file and an ordinary one in one commit", r.stderr, files)
        put(os.path.join(repo, ".claude/session-name"), "one-nope\n")
        code, err, msg = commit(repo, "feat: 标记不是已知会话")
        assert code == 0 and "Session:" not in msg and "未识别" in err, "an unknown marker warns, never guesses: " + err + msg
        os.remove(os.path.join(repo, ".claude/session-name"))
        git(repo, "add", "-A")
        code, err, msg = commit(repo, "feat: 目录名 repo 认不出")
        assert code == 0 and "Session:" not in msg and "未识别" in err, err + msg
        for folder, branch, title, name, session, why in (("one-1a-next-3-abc", "claude/zzz", "feat: 按工作区目录名认", "b.py", "one-1a-next-3", "longest known prefix of the worktree directory: "),
                                                          ("plain", "claude/one-platform-r2", "feat: 按分支名认（roles 目录）", "c.py", "one-platform", "branch without claude/ prefix, names from roles/: "),
                                                          ("one-1a-r9", "claude/one-ops-r9", "feat: 目录名与分支名认出不同会话", "f.py", "one-1a", "the directory wins over the branch, whatever the name lengths: ")):
            git(repo, "worktree", "add", "-q", "-b", branch, os.path.join(tmp, folder))
            code, err, msg = commit(os.path.join(tmp, folder), title, name=name)
            assert code == 0 and msg.rstrip().endswith("Session: " + session), why + msg
        git(repo, "checkout", "-q", "-b", "side")
        commit(repo, "side", name="d.py")
        git(repo, "checkout", "-q", "main")
        commit(repo, "main", name="e.py")
        put(os.path.join(repo, ".claude/session-name"), "one-ops\n")
        git(repo, "merge", "-q", "--no-ff", "side", "-m", "合并 side（标题不以 Merge 开头）")
        assert "Session:" not in git(repo, "log", "-1", "--format=%B").stdout, "a merge in progress is left alone"
        broken = os.path.join(tmp, "notgit")
        put(os.path.join(broken, "MSG"), "feat: x\n")
        for args, word, why in (([os.path.join(broken, "MSG")], "出错", "an error warns and lets the commit through: "), ([], "用法", "")):
            r = subprocess.run([PYTHON, "-I", MSG_GATE, *args], cwd=broken, capture_output=True, text=True, env=env())
            assert r.returncode == 0 and word in r.stderr, why + r.stderr
    config = Path(ROOT, ".pre-commit-config.yaml").read_text(encoding="utf-8")
    assert "entry: python3 -I scripts/gates/commit_msg_gate.py" in config, ".pre-commit-config.yaml points at the new path"
    assert re.findall(r"- id: ([\w-]+)", config) == ["gitleaks", "main-checkout-gate", "commit-msg-gate"], "卡 #736：提交时没有按路径把守护文件拆开的检查"
    renamed_test()
    for gone in ("scripts/hooks/commit_msg_gate.py", "scripts/hooks/task_ids.py"):
        assert not os.path.exists(os.path.join(ROOT, gone)), gone + " is deleted"
    print("commit_msg_gate self-test: ok")

FAKE = "fake-prod-db.example.invalid"  # ── install ── fictitious production host, put on the temporary repository's origin/main table only
ENTRIES = {"PreToolUse": ["scripts/gates/pre_tool_dispatch.py"], "SessionStart": ["scripts/hooks/git_ceiling_env.sh"]}  # the start sheet hangs on the tool library (O23a)
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

def policy(section="gates"):
    return json.loads(Path(ROOT, "policy.json").read_text(encoding="utf-8"))[section]

def sheet_path():  # the tool library's start sheet as the SessionStart mount spells it (O23a: the repository's start_sheet.py and session_context.py are gone)
    return '"$HOME"/' + policy("tools_lib")["dir"][2:] + "/start_sheet/start_sheet.py"

def node_path():  # the tool library's node_ready as the clear_session mount spells it (#14, user intent 2026-10-27-171036)
    return '"$HOME"/' + policy("tools_lib")["dir"][2:] + "/node_ready/node_ready.py"

def settings_self_test():
    raw = Path(ROOT, ".claude/settings.json").read_text(encoding="utf-8")
    settings, gates = json.loads(raw), policy()
    assert gates["install_dir"].startswith("~/"), gates["install_dir"]
    link = '"$HOME"/' + gates["install_dir"][2:] + "/" + gates["install_current_link"]
    prefix = "H=" + link + "/"
    verify = ('V="$CLAUDE_PROJECT_DIR"/scripts/gates/hooks_manifest.sh; if [ -x "$V" ]; then "$V" --check ' + link + "; "
              'else echo "【钩子告警】核对不了已装钩子（$V 缺失或不可执行）"; fi; ')  # runs from the session's repository, not from current (O7 final M2)
    hooks = settings["hooks"]
    stop = [h["command"] for g in hooks.pop("Stop") for h in g["hooks"]]
    assert stop == ['test -f {0} && /usr/bin/python3 -I {0}; exit 0'.format('"$HOME"/.modernize/psa/tools/one-session/stop_check/stop_check.py')], stop  # #47: the system python, as the start sheet
    assert sorted(hooks) == ["PreToolUse", "SessionStart"], sorted(hooks)
    assert [g["matcher"] for g in hooks["PreToolUse"]] == ["|".join(COMMAND_TOOLS + (LAUNCH_TOOL,)), "mcp__ccd_session_mgmt__clear_session"], "PreToolUse: the production-write gate on the tools that run commands; the clear_session gate apart (#14)"
    assert json.dumps([*COMMAND_TOOLS, LAUNCH_TOOL]) in hooks["PreToolUse"][0]["hooks"][0]["command"], "the mount's own pre-filter names the same tools"
    assert [g["matcher"] for g in hooks["SessionStart"]] == ["startup|clear|compact|resume", "startup|clear|compact"]
    sheet = hooks["SessionStart"].pop(1)["hooks"]
    assert len(sheet) == 1 and sheet[0]["type"] == "command" and sheet[0]["timeout"] == 15, sheet
    sheet = sheet[0]["command"]
    assert sheet.startswith(f'H={sheet_path()}; [ -f "$H" ] && /usr/bin/python3 -I "$H" --hook && exit 0; echo "【钩子告警】') and sheet.endswith('"; exit 0'), sheet
    assert "exit 2" not in sheet and "/tools/hooks/" not in sheet and "~/" + policy("tools_lib")["dir"][2:] + "/start_sheet" in sheet, "warns, pointing at the tool library"
    node = hooks["PreToolUse"].pop(1)["hooks"]  # #14: the clear_session gate is the tool library's node_ready --gate; anything but 0 is a refusal
    assert len(node) == 1 and node[0]["type"] == "command" and node[0]["timeout"] == 10, node
    node = node[0]["command"]
    want = (f'H={node_path()}; if [ -f "$H" ]; then env -u HANDOFF_STATE /usr/bin/python3 -I "$H" --gate; c=$?; else c=127; fi; [ "$c" = 0 ] && exit 0; [ "$c" = 2 ] || echo "clear_session 门异常退出'
            f'（退出码 ${{c}}：工具库 node_ready.py 缺失、出错或超时，${{H}}），按拒绝处理；请 one-ops 修工具库 ~/{policy("tools_lib")["dir"][2:]}/node_ready（node_ready.py --self-test）" >&2; exit 2')
    assert node == want, ("the clear_session mount, word for word (#14 final review: a fragment inserted mid-way must fail)", node, want)
    assert node.count("exit 0") == 1 and "-I -c" not in node and "/tools/hooks/" not in node, "no pre-filter, no installed copy: the gate fails closed (#14)"
    for gone in ("scripts/hooks/start_sheet.py", "scripts/hooks/session_context.py"):
        assert not os.path.exists(os.path.join(ROOT, gone)) and gone not in raw, gone + " is deleted and mounted nowhere (O23a)"
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
    tables = ['os.path.expanduser("~/' + gates["install_dir"][2:] + "/" + gates["install_current_link"] + '/scripts/gates/prod_targets.json")', '"scripts/gates/prod_targets.json"']  # #736: the same feature table, installed and the project's
    assert all(t in gate[1] for t in tables) and "far=" not in gate[1] and "ok=set" not in gate[1] and "not t or" in gate[1], "pre-filter reads the feature table, fails closed without one"
    ceiling = commands["scripts/hooks/git_ceiling_env.sh"]
    assert ceiling[1].endswith("; exit 0") and "【钩子告警】" in ceiling[1] and "exit 2" not in ceiling[1] and ceiling[2] == 10, ceiling
    # an installed target without its exec bit never runs (O6 review item 1); the tool library's start sheet runs through python3, so it needs none
    for name in [*commands, "scripts/gates/hooks_manifest.sh"]:
        mode = gitc(ROOT, "ls-files", "-s", "--", name).split()[:1]
        assert mode == ["100755"], (name, mode)
    deny = settings.get("permissions", {}).get("deny", [])  # O21a: external model CLIs only through scripts/delegate.py (a subprocess, not the Bash tool)
    assert deny == ["Bash(codex *)", "Bash(pi *)", "mcp__terminal__run_in_terminal"], ("permissions.deny 恰为 codex、pi 直接调用（O21a）与整个终端面板工具（#723）", deny)
    for plugin in ("kotlin-lsp", "claude-code-setup", "code-modernization"):
        assert settings["enabledPlugins"][f"{plugin}@claude-plugins-official"] is False, plugin
    return {**{name: cmd for name, (_, cmd, _) in commands.items()}, "start_sheet": sheet, "node_ready": node}

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
    for gate, message, mode in (("#!/bin/bash\ncat >/dev/null\nexit 0\n", "fail open", 0o755),  # a gate that lets everything through
                                ("#!/bin/bash\ncat >/dev/null\nexit 2\n", "refuses all", 0o755),  # a gate that refuses everything: every session's Bash would stop
                                ("#!/bin/bash\nread -r line\ncase \"$line\" in \"{\"*) exit 0 ;; *) exit 2 ;; esac\n", "passes writes", 0o755),  # refuses unreadable input, lets production writes through
                                (FIRST_ONLY, "first target only", 0o755), (original, "not executable", 0o644)):
        put(dispatcher, gate, mode)
        commit(message)
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
    gate_cmd, ceiling_cmd, sheet_cmd, node_cmd = (commands[k] for k in ("scripts/gates/pre_tool_dispatch.py", "scripts/hooks/git_ceiling_env.sh", "start_sheet", "node_ready"))
    with tempfile.TemporaryDirectory() as top:
        try:
            body(gate_cmd, ceiling_cmd, (sheet_cmd, node_cmd), os.path.realpath(os.path.join(top, "dir with space")))
        finally:
            writable(top)
    print("install self-test: ok")

def sheet_test(sheet_cmd, mount, tool, repo):
    """The start-sheet mount: /usr/bin/python3 -I <tool library>/start_sheet.py --hook gets the payload (no exec bit, no planted json.py); missing, failing, killed, unparsable: warn, exit 0."""
    os.makedirs(os.path.dirname(tool))
    put(os.path.join(os.path.dirname(tool), "json.py"), "raise SystemExit(7)\n", 0o644)
    for text, ok in ((None, False), ("import json,sys\nprint('【开工单】', sys.argv[1:], json.load(sys.stdin)['cwd'])\n", True), ("import sys\nsys.exit(1)\n", False),
                     ("import os,signal\nos.kill(os.getpid(), signal.SIGKILL)\n", False), ("def (:\n", False)):
        if text is not None:
            put(tool, text, 0o644)
        for shell in SHELLS:
            r = mount(sheet_cmd, json.dumps({"cwd": repo}), shell)
            last = r.stdout.splitlines()[-1] if r.stdout.strip() else ""
            assert r.returncode == 0 and (last == f"【开工单】 ['--hook'] {repo}" if ok else last.startswith("【钩子告警】") and tool in last), (shell, text, r.stdout, r.stderr)

def node_test(node_cmd, mount, tool):
    """The clear_session mount (#14): HANDOFF_STATE cleared, /usr/bin/python3 -I <tool library>/node_ready.py --gate gets the payload; 0 passes, any other ending is 2."""
    os.makedirs(os.path.dirname(tool), exist_ok=True)
    put(os.path.join(os.path.dirname(tool), "json.py"), "raise SystemExit(0)\n", 0o644)
    ok = "import json,os,sys\nsys.exit(0 if sys.argv[1:] == ['--gate'] and 'HANDOFF_STATE' not in os.environ and json.load(sys.stdin)['tool_input'] else 9)\n"
    for text, code in ((None, 2), (ok, 0), ("import sys\nsys.exit(1)\n", 2), ("import sys\nsys.stderr.write('x')\nsys.exit(2)\n", 2), ("import os,signal\nos.kill(os.getpid(), signal.SIGKILL)\n", 2), ("def (:\n", 2)):
        if text is not None:
            put(tool, text, 0o644)
        for shell in SHELLS:
            r = mount(node_cmd, json.dumps({"tool_name": "mcp__ccd_session_mgmt__clear_session", "tool_input": {"session_id": "self"}}), shell, HANDOFF_STATE="/x")
            assert r.returncode == code and (code == 0 or r.stderr.strip()), (shell, text, r.returncode, r.stderr)

def body(gate_cmd, ceiling_cmd, sheet_cmds, tmp):
    sheet_cmd, node_cmd = sheet_cmds
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
    def bash(command): return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": repo})
    def points_to(): return os.readlink(current) if os.path.islink(current) else None
    def leftovers(): return [n for n in os.listdir(hooks) if n.startswith(".")] if os.path.isdir(hooks) else []
    def closed(why, shells=SHELLS, extra=()):  # the gate failed: settings.json's own pre-filter decides (H4, C3) with the same feature table (#736): a production sample, quotes and backslashes removed, refuses; no table refuses all
        for shell in shells:
            for command, code in (("ls", 0), ("ls -la | wc -l", 0), ("git status", 0), ("gh pr list", 0), ("git -c core.pager='sh w.sh' log -1", 0), ("GIT_EXTERNAL_DIFF=./w.sh git diff", 0), ("PATH=./bin:/usr/bin ls", 0),
                                  ("echo x > /dev/tcp/10.0.0.5/6379", 0), ("sort --compress-program=./w.sh x", 0), (f"psql -h {PROD_P} -c 'delete from t'", 2), ("./deploy.sh", 0), ("echo `deploy`", 0), ("rg --pre deploy x", 0),
                                  ("ssh dev uptime", 0), ("curl -s https://x.example.invalid", 0), (f"grep -rn {PROD_H} docs", 2), (f"s''sh {PROD_H} uptime", 2), (f"c\\url https://{PROD_P[:3]}\\{PROD_P[3:]}/", 2),
                                  (f"echo {PROD_D.upper()}", 2), ("aliyun --profile dev-ro ecs DescribeRegions", 2), ("ALI\"\"YUN ecs DescribeRegions", 2),  # 阿里云：门出错时一律拒（#736）
                                  (f"curl http://{PROD_H[:3]}{{x,{PROD_H[3]}}}{PROD_H[4:]}/", 2), ("t=ql; ps$t -h x", 2), ("a=ali; ${a}yun ecs X", 2), ("a=ps; b=ql; $a$b -h x", 2), ("echo $'\\x41'", 2),  # 首轮 S2：花括号每个选项、变量拼接、$'
                                  ("echo $HOME", 0), ("ls ${HOME} ~", 0),
                                  *((c, 0) for c in MISBLOCKED), *((c, 2) for c in extra)):
                r = mount(gate_cmd, bash(command), shell)
                assert r.returncode == code and why in r.stderr and ("放行" in r.stderr) == (code == 0) and os.path.isfile(os.path.join(hooks, "gate_failures.log")) == os.path.isdir(hooks), (shell, why, command, r.returncode, r.stderr)
            r = mount(gate_cmd, bash("ls"), shell, CLAUDE_PROJECT_DIR=tmp)  # no table anywhere (nothing installed, none in the project): every command refused
            assert r.returncode == 2 or os.path.isfile(os.path.join(current, "scripts/gates/prod_targets.json")), ("no feature table: fail closed", shell, r.returncode, r.stderr)
            r = mount(ceiling_cmd, json.dumps({"cwd": repo}), shell)
            assert r.returncode == 0 and r.stdout.splitlines()[-1].startswith("【钩子告警】"), (shell, why, r.returncode, r.stdout, r.stderr)
    # 1. nothing installed: the pre-filter decides (an input it cannot read is refused), SessionStart warns and passes
    closed("入口缺失")
    assert mount(gate_cmd, "not json").returncode == 2 and mount(gate_cmd, json.dumps({"tool_name": "Bash", "tool_input": {}})).returncode == 2
    for tool, command, code in ((t, c, r) for t in COMMAND_TOOLS[1:] for c, r in ((f"psql -h {PROD_P} -c 'delete from t'", 2), ("ls", 0))):  # Monitor, the terminal panel
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
    sheet_test(sheet_cmd, mount, os.path.join(home, sheet_path()[len('"$HOME"/'):]), repo)
    node_test(node_cmd, mount, os.path.join(home, node_path()[len('"$HOME"/'):]))
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
    with contextlib.suppress(PermissionError):
        open(gate_file, "a").close()
        raise AssertionError("an installed file could be opened for writing")
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
    def version(name, gate, ceiling="#!/bin/bash\nexit 1\n", gate_mode=0o755):
        root = os.path.join(hooks, name)
        if gate is not None:
            put(os.path.join(root, "scripts/gates/pre_tool_dispatch.py"), gate, gate_mode)
        put(os.path.join(root, "scripts/hooks/git_ceiling_env.sh"), ceiling)
        os.remove(current)
        os.symlink(name, current)
    for name, gate, mode, why in (("missing", None, 0o755, "入口缺失"), ("not-exec", "#!/bin/bash\nexit 0\n", 0o644, "入口缺失"), ("crash", "#!/bin/bash\nexit 1\n", 0o755, "异常退出（退出码 1"),
                                  ("no-python", "#!/nonexistent/python3\n", 0o755, "异常退出（退出码 126"), ("killed", "#!/bin/bash\nkill -9 $$\n", 0o755, "异常退出（退出码 137"),
                                  ("syntax", "#!/usr/bin/python3\ndef (:\n", 0o755, "异常退出（退出码 1")):
        version(name, gate, gate_mode=mode)
        closed(why)
    shutil.rmtree(os.path.join(hooks, "syntax"))
    closed("入口缺失")  # a dangling current link
    version("gate-error", "#!/bin/bash\ncat >/dev/null\nexit 3\n")  # the real dispatcher's answer when the gate cannot be imported, crashes or hangs (section 4)
    closed("异常退出（退出码 3", extra=[c for cs in seventeen(PROD_P, PROD_H, PROD_D).values() for c in cs])
    version("answers", "#!/bin/bash\ncat >/dev/null\necho no >&2\nexit 2\n", "#!/bin/bash\necho ok\n")
    for shell in SHELLS:
        r = mount(gate_cmd, bash("ls"), shell)
        assert r.returncode == 2 and r.stderr.strip() == "no", "the gate's own 2 passes through unchanged"
        assert mount(ceiling_cmd, "{}", shell).stdout.splitlines()[-1] == "ok"
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
