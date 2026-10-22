"""Shared by the Bash PreToolUse gates: the shell resolves `GIT`, `gi""t` and a backslash-split name to the git command (macOS file systems ignore case), so a gate must compare command names after that normalisation."""
import os
import re

ANSI_QUOTE = re.compile(r"\$(?=['\"])")
QUOTES = re.compile(r"[\\'\"]")


def unquote_dollar(command):
    """`$'git'` and `$"git"` run git in bash and zsh, but shlex reads them as the word `$git`: drop the `$` in front of a quote before tokenising."""
    return ANSI_QUOTE.sub("", command)


def plain(command):
    """Text for pre-filters: quotes and backslashes removed, lower-cased. Only ever used to decide whether a gate looks closer, never to run anything."""
    return QUOTES.sub("", ANSI_QUOTE.sub("", command)).lower() if isinstance(command, str) else ""


def name_of(token):
    """Command name of a token as the shell finds it: directory dropped, lower-cased."""
    return os.path.basename(token).lower()
