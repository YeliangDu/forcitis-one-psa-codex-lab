"""Shared by the Bash PreToolUse gates: the shell resolves `GIT`, `gi""t` and a backslash-split name to the git command (macOS file systems ignore case), so a gate must compare command names after that normalisation."""
import os
import re

QUOTES = re.compile(r"[\\'\"]")


def plain(command):
    """Text for pre-filters: quotes and backslashes removed, lower-cased. Only ever used to decide whether a gate looks closer, never to run anything."""
    return QUOTES.sub("", command).lower() if isinstance(command, str) else ""


def name_of(token):
    """Command name of a token as the shell finds it: directory dropped, lower-cased."""
    return os.path.basename(token).lower()
