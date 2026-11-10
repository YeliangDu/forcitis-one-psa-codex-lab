"""The one place that reads machine-checked `Key: value` lines out of a pull request body (#1561; #1562 reuses it for User-Decision and Incident).
A line counts when it starts with the key at the first column (leading blanks allowed), outside a fenced code block; the key is matched exactly, with its capitalisation.
Pure text: no gh, no git, no files."""
import re

FENCE = re.compile(r"^\s*(```|~~~)")


def lines(body, key):
    """The values (text after `key:`, stripped) of every line of `body` that starts with `key:`, in order; lines inside ``` or ~~~ fences are ignored (a template quoting the line is no declaration)."""
    out, fenced = [], False
    pattern = re.compile(r"^\s*" + re.escape(key) + r":[ \t]*(.*?)\s*$")
    for line in (body or "").replace("\r\n", "\n").split("\n"):
        if FENCE.match(line):
            fenced = not fenced
            continue
        m = None if fenced else pattern.match(line)
        if m:
            out.append(m.group(1))
    return out


def ids(value):
    """The comma-separated items of a value, stripped, empty items dropped."""
    return [x.strip() for x in value.split(",") if x.strip()]
