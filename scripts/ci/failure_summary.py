#!/usr/bin/env python3
"""What a failed backend step says, in one block at the end of its log (scripts/ci/backend_verify.sh): CI sometimes fails the step with every unit test green and no failure line anywhere in a log of hundreds of thousands of lines.

Usage: failure_summary.py <label>=<exit code>=<log file> [...]  |  failure_summary.py --self-test
Prints nothing when every exit code is 0. For each failed step: its exit code, the lines that name a failed test, a crashed or killed fork, a timeout or a build failure (first 30), and the last 50 lines of its log; a failed
step whose log holds no such line says so ("no failure line"), which is the case to look at the timeout, the watchdog and the engine evidence for. Exit code 0: it only reports.
"""
import re
import sys
import tempfile

SIGNS = re.compile(r"<<< (FAILURE|ERROR)!|There are test failures|BUILD FAILURE|forked VM terminated|ExecutionException|Crashed|crash|Timeout|timed out|OutOfMemory|Killed|\[ERROR\] (Failed|Tests run:.*Failures: [1-9]|Tests run:.*Errors: [1-9])|No tests were executed|produced no report")
TAIL = 50
FIRST = 30


def summarize(entries):
    failed = [(label, int(code), path) for label, code, path in entries if int(code) != 0]
    if not failed:
        return ""
    out = ["backend failure summary: " + ", ".join(f"{label} exit {code}" for label, code, _ in failed)]
    for label, code, path in failed:
        try:
            lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
        except OSError as e:
            out.append(f"--- {label}: log unreadable ({e})")
            continue
        hits = [l for l in lines if SIGNS.search(l)]
        out.append(f"--- {label} (exit {code}): " + (f"{len(hits)} line(s) name a failure" if hits else "no failure line in the log (look at the timeout, the watchdog and docker-diagnostics)"))
        out += [f"  {l}" for l in hits[:FIRST]]
        if len(hits) > FIRST:
            out.append(f"  ... {len(hits) - FIRST} more")
        out.append(f"--- {label}: last {min(TAIL, len(lines))} of {len(lines)} lines")
        out += [f"  {l}" for l in lines[-TAIL:]]
    return "\n".join(out)


def self_test():
    with tempfile.TemporaryDirectory() as d:
        def log(name, lines):
            path = f"{d}/{name}"
            open(path, "w").write("\n".join(lines) + "\n")
            return path
        ok = log("ok.log", ["fine"])
        assert summarize([("surefire", 0, ok), ("failsafe", "0", ok)]) == ""
        bad = log("bad.log", [f"noise {i}" for i in range(100)] + ["[ERROR] com.x.YIT.a -- Time elapsed: 1 s <<< FAILURE!", "[ERROR] The forked VM terminated without properly saying goodbye", "last"])
        text = summarize([("surefire", 0, ok), ("failsafe", 1, bad)])
        assert text.startswith("backend failure summary: failsafe exit 1") and "surefire" not in text.splitlines()[0]
        assert "2 line(s) name a failure" in text and "<<< FAILURE!" in text and "forked VM terminated" in text and "last 50 of 103 lines" in text and text.rstrip().endswith("last"), text
        assert "noise 52" not in text and "noise 53" in text, "exactly the last 50 lines"
        quiet = log("quiet.log", ["Tests run: 10, Failures: 0, Errors: 0, Skipped: 0", "BUILD SUCCESS?"])
        assert "no failure line in the log" in summarize([("surefire", 124, quiet)])
        many = log("many.log", [f"[ERROR] c{i} <<< ERROR!" for i in range(45)])
        assert "45 line(s) name a failure" in summarize([("x", 1, many)]) and "... 15 more" in summarize([("x", 1, many)])
        assert "log unreadable" in summarize([("x", 1, f"{d}/missing.log")])
    print("failure_summary self-test: ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    entries = []
    for arg in sys.argv[1:]:
        parts = arg.split("=", 2)
        if len(parts) != 3 or not parts[1].isdigit():
            sys.exit(__doc__)
        entries.append(tuple(parts))
    text = summarize(entries)
    if text:
        print(text)


main()
