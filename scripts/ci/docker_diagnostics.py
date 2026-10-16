#!/usr/bin/env python3
"""Evidence for a CI job that failed on a test container: written to a directory CI uploads as an artifact (scripts/ci/docker_diagnostics.py, ci.yml backend job, `if: failure()`).

Usage: docker_diagnostics.py <out dir> <job start, epoch seconds>  |  docker_diagnostics.py --self-test

Writes docker-info.txt (engine summary, disk use), containers.txt (every container: image, state, exit code, OOM flag, creation time), events.txt (`docker events` from the job start to now: create, start,
die, oom, kill) and logs/<name>.txt (the last 200 lines of every PostgreSQL, SQL Server and Ryuk container created since the job started, from any job: the engine is shared by all runners).
Only names, images, states and logs are read: `docker inspect` is asked for a fixed format, never dumped, because a container's environment holds its passwords.
It never fails the job it documents: every call has a time limit, an error is written into the file instead, the exit code is 0.
"""
import datetime
import os
import re
import subprocess
import sys
import tempfile

TEST_IMAGES = re.compile(r"^(postgres|mcr\.microsoft\.com/mssql/server|testcontainers/ryuk)[:@]")
LOG_LINES = "200"
FORMAT = "{{.Id}}\t{{.Name}}\t{{.Config.Image}}\t{{.State.Status}}\t{{.State.ExitCode}}\t{{.State.OOMKilled}}\t{{.Created}}"


def docker(*args, timeout=60):
    try:
        r = subprocess.run(["docker", *args], capture_output=True, text=True, timeout=timeout)
        return (r.stdout + (f"\n[stderr] {r.stderr}" if r.stderr.strip() else "")) if r.returncode == 0 or r.stdout else f"[docker {args[0]} exit {r.returncode}] {r.stderr.strip()}"
    except (OSError, subprocess.SubprocessError) as e:
        return f"[docker {args[0]} could not run: {e}]"


def parse_containers(text):
    """[(id, name, image, state, exit code, oom, created epoch)] from FORMAT lines; lines that do not parse are skipped."""
    rows = []
    for line in text.splitlines():
        parts = line.split("\t")
        if len(parts) != 7:
            continue
        try:
            created = datetime.datetime.fromisoformat(re.sub(r"(\.\d{6})\d*Z$", r"\1+00:00", parts[6]).replace("Z", "+00:00")).timestamp()
        except ValueError:
            continue
        rows.append((parts[0], parts[1].lstrip("/"), parts[2], parts[3], parts[4], parts[5], created))
    return rows


def wanted(rows, since):
    """Test containers created at or after `since` (epoch seconds), by any job."""
    return [r for r in rows if TEST_IMAGES.match(r[2]) and r[6] >= since]


def safe_name(name):
    return re.sub(r"[^A-Za-z0-9_.-]", "_", name)[:80] or "container"


def collect(out, since, run=docker, now=None):
    os.makedirs(os.path.join(out, "logs"), exist_ok=True)
    now = int(now if now is not None else datetime.datetime.now().timestamp())
    info = run("info", "--format", "{{.ServerVersion}} | {{.NCPU}} CPUs | {{.MemTotal}} bytes | containers {{.Containers}} (running {{.ContainersRunning}}, paused {{.ContainersPaused}}, stopped {{.ContainersStopped}}) | {{.OperatingSystem}}")
    write(out, "docker-info.txt", info + "\n" + run("system", "df"))
    ids = run("ps", "-aq", "--no-trunc").split()
    rows = parse_containers(run("inspect", "--format", FORMAT, *ids)) if ids else []
    write(out, "containers.txt", "\n".join("\t".join(map(str, r[:6])) + "\t" + datetime.datetime.fromtimestamp(r[6], datetime.timezone.utc).isoformat() for r in sorted(rows, key=lambda r: r[6])) + "\n")
    write(out, "events.txt", run("events", "--since", str(int(since)), "--until", str(now), "--format", "{{.Time}} {{.Type}} {{.Action}} {{.Actor.Attributes.name}} {{.Actor.Attributes.image}}"))
    for row in wanted(rows, since):
        write(out, f"logs/{safe_name(row[1])}.txt", f"{row[2]} {row[3]} exit={row[4]} oom={row[5]}\n" + run("logs", "--tail", LOG_LINES, row[0]))


def write(out, name, text):
    with open(os.path.join(out, name), "w", encoding="utf-8") as f:
        f.write(text)


def self_test():
    ts = "2026-10-16T17:30:00.123456789Z"
    epoch = datetime.datetime(2026, 10, 16, 17, 30, 0, 123456, tzinfo=datetime.timezone.utc).timestamp()
    rows = parse_containers("\n".join([f"id1\t/pg-1\tpostgres:18\trunning\t0\tfalse\t{ts}", "garbage", "id2\t/old\tpostgres:18\texited\t137\ttrue\t2026-10-16T10:00:00Z",
                                       f"id3\t/web\tnginx:1.28.0-alpine\trunning\t0\tfalse\t{ts}", f"id4\t/ryuk\ttestcontainers/ryuk:0.14.0\trunning\t0\tfalse\t{ts}",
                                       f"id5\t/ms\tmcr.microsoft.com/mssql/server:2022-latest\texited\t1\tfalse\t{ts}", "id6\t/x\tpostgres:18\trunning\t0\tfalse\tnot-a-date"]))
    assert [r[0] for r in rows] == ["id1", "id2", "id3", "id4", "id5"] and abs(rows[0][6] - epoch) < 1e-3, rows
    since = epoch - 60
    assert [r[0] for r in wanted(rows, since)] == ["id1", "id4", "id5"], "test images created since the job started: not the old one, not nginx"
    assert wanted(rows, epoch + 1) == []
    assert safe_name("/a b/../c") == "_a_b_.._c" and safe_name("") == "container"
    calls = []

    def fake(*args, timeout=60):
        calls.append(args)
        if args[0] == "ps":
            return "id1\nid2\n"
        if args[0] == "inspect":
            assert "--format" in args and "{{json" not in " ".join(args) and args[-2:] == ("id1", "id2"), "a fixed format, never a dump"
            return f"id1\t/pg-1\tpostgres:18\trunning\t0\tfalse\t{ts}\nid2\t/old\tpostgres:18\texited\t137\ttrue\t2026-10-16T10:00:00Z\n"
        if args[0] == "logs":
            return "last line\n"
        return f"{args[0]} output\n"
    with tempfile.TemporaryDirectory() as out:
        collect(out, since, run=fake, now=int(epoch) + 600)
        assert sorted(os.listdir(out)) == ["containers.txt", "docker-info.txt", "events.txt", "logs"] and os.listdir(os.path.join(out, "logs")) == ["pg-1.txt"], os.listdir(out)
        assert open(os.path.join(out, "logs", "pg-1.txt")).read() == "postgres:18 running exit=0 oom=false\nlast line\n"
        assert ("events", "--since", str(int(since)), "--until", str(int(epoch) + 600), "--format", "{{.Time}} {{.Type}} {{.Action}} {{.Actor.Attributes.name}} {{.Actor.Attributes.image}}") in calls
        assert "137\ttrue" in open(os.path.join(out, "containers.txt")).read(), "the OOM flag of an old container is listed"
    with tempfile.TemporaryDirectory() as out:   # no docker at all: files with the reason, no exception
        collect(out, since, run=lambda *a, timeout=60: "[docker could not run: no docker]", now=1)
        assert "no docker" in open(os.path.join(out, "docker-info.txt")).read() and open(os.path.join(out, "containers.txt")).read().strip() == ""
    assert docker("definitely-not-a-subcommand-xyz", timeout=30).startswith(("[docker ", "[docker")), "a failing docker call is reported in the text, not raised"
    bad = subprocess.run([sys.executable, __file__, "/dev/null/not-a-directory", "1"], capture_output=True, text=True)
    assert bad.returncode == 0 and "incomplete" in bad.stderr, (bad.returncode, bad.stderr)   # an error while collecting never fails the job
    print("docker_diagnostics self-test: ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    if len(sys.argv) != 3 or not sys.argv[2].isdigit():
        sys.exit(__doc__)
    try:
        collect(sys.argv[1], int(sys.argv[2]))
    except Exception as e:   # never fail the job this documents
        print(f"docker diagnostics incomplete: {e}", file=sys.stderr)


main()
