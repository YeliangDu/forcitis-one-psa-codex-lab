#!/usr/bin/env python3
"""兼容层（GOV-1023-FV2 O2）：命令解析已并入 scripts/gates/prod_write_gate.py，这里只转出旧名字给还没迁走的 codex_stdin_gate（第二批乙随它删除；pre_push_gate、precheck_before_pr 已在 O6 删除，main_checkout_gate 已在 O4 搬到 scripts/gates 且不再依赖本层）。
这些钩子在 O4、O6、O7 与第二批乙重写或删除后，本文件随之删除。自测在 scripts/gates/gates_selftest.py。"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "scripts", "gates"))
import re  # noqa: E402
import shlex  # noqa: E402

from prod_write_gate import (ENV, KEYWORDS, SEPARATORS, SHELLS, WRAPPERS, Command, decode_ansi_c, expand, name_of, neutralize, plain,  # noqa: E402,F401
                             restore, simple_commands, unquote_dollar)

# codex_stdin_gate's own reading (here-document bodies skipped, a # comment ends its line), kept here from origin/main 232075dfa: the production-write gate
# no longer mimics the shell there (intent 2026-10-26-140639); this file goes with codex_stdin_gate in batch 2b.
HEREDOC = re.compile(r"<<(-?)[ \t]*(?:'([^']*)'|\"([^\"]*)\"|(\\?)([A-Za-z_][A-Za-z0-9_]*))")


def split_heredocs(command):  # (text without here-document bodies, [(body, delimiter, quoted)]). `<<` counts only outside quotes and comments; an unquoted delimiter means the body is expanded.
    text = command.replace("\\\n", "")
    out, bodies, pending, i, single, double, n = [], [], [], 0, False, False, len(text)
    while i < n:
        char = text[i]
        if single:
            out.append(char)
            single = char != "'"
            i += 1
        elif char == "\\" and i + 1 < n:
            out.append(text[i:i + 2])
            i += 2
        elif double:
            out.append(char)
            double = char != '"'
            i += 1
        elif char in "'\"":
            out.append(char)
            single, double = char == "'", char == '"'
            i += 1
        elif char == "#" and (i == 0 or text[i - 1] in " \t\n;|&("):
            end = text.find("\n", i)
            end = n if end < 0 else end
            out.append(text[i:end])
            i = end
        elif char == "<" and text.startswith("<<", i) and not text.startswith("<<<", i) and not (i and text[i - 1] == "<") and HEREDOC.match(text, i):
            found = HEREDOC.match(text, i)
            pending.append((found.group(2) or found.group(3) or found.group(5), bool(found.group(2) or found.group(3) is not None or found.group(4)), found.group(1) == "-"))
            out.append(found.group(0))
            i = found.end()
        elif char == "\n" and pending:
            out.append("\n")
            i += 1
            for delimiter, quoted, strip in pending:
                lines = []
                while i < n:
                    end = text.find("\n", i)
                    line = text[i:n if end < 0 else end]
                    i = n if end < 0 else end + 1
                    if (line.lstrip("\t") if strip else line).rstrip("\r") == delimiter:
                        break
                    lines.append(line)
                bodies.append(("\n".join(lines), delimiter, quoted))
            pending = []
        else:
            out.append(char)
            i += 1
    return "".join(out), bodies


def without_heredoc_bodies(command):
    return split_heredocs(command)[0].split("\n")


def tokens_of(command, structured=False):
    """Shell tokens, line by line (a # comment ends with its line, an open quote continues). `structured`: backticks are `$(...)`, `$` in single quotes neutralised;
    otherwise the older reading of the other hooks (a backtick starts a new command)."""
    tokens, pending = [], ""
    lines = without_heredoc_bodies(unquote_dollar(command))
    if structured:
        lines = neutralize("\n".join(lines)).split("\n")
    for line in lines:
        pending = pending + "\n" + line if pending else line
        lexer = shlex.shlex(pending if structured else pending.replace("`", " ; "), posix=True, punctuation_chars=True)
        lexer.whitespace_split, lexer.commenters = True, "#"
        try:
            found = list(lexer)
        except ValueError:
            continue
        tokens += found if found and found[-1] in ("|", "&&", "||", "|&") else found + [";"]
        pending = ""
    if pending:
        raise ValueError("unterminated quote")
    return tokens



if __name__ == "__main__":
    sys.exit(0)
