"""The event payload check at enqueue (#1327): an implementation pull request (title type feat, fix, refactor or perf) that changes the payload of a message that already
exists in docs/architecture/events/<module>.yaml is refused before it is queued. The payload is signed at design (playbooks/design.md); the implementation ships what was signed
(EventCatalogueTest compares catalogue and code), so a change of an existing payload goes back to design. A new message, a changed description, or any other pull request type passes.
The base is the merge base with origin/main, so what main changed after the branch left is not counted. Fails open, said on stderr, when the head cannot be fetched or read."""
import os
import subprocess
import sys

import yaml

from mq_github import sh

IMPL_TYPES = {"feat", "fix", "refactor", "perf"}
EVENTS = "docs/architecture/events/"


def impl_type(title):
    """The title's type word (`feat(x): …` -> feat), or None."""
    head = title.split(":", 1)[0].split("(", 1)[0].strip() if isinstance(title, str) and ":" in title else ""
    return head if head in IMPL_TYPES else None


def messages(root, rev, path):
    """{message name: payload} of a catalogue fragment at a revision; {} when the file is not there."""
    shown = sh(["git", "show", f"{rev}:{path}"], cwd=root, timeout=30)
    if shown.returncode:
        return {}
    found = (yaml.safe_load(shown.stdout) or {}).get("components", {}).get("messages", {})
    return {name: (m or {}).get("payload") for name, m in found.items()}


def changed(root, number, head):
    """Names of existing messages whose payload the head changes ('<file>: <Name>'), or None when the check could not run (said on stderr)."""
    fetched = sh(["git", "fetch", "-q", "origin", "main", f"refs/pull/{number}/head"], cwd=root, timeout=60)
    base = sh(["git", "merge-base", "origin/main", head], cwd=root, timeout=30).stdout.strip() if not fetched.returncode else ""
    files = sh(["git", "diff", "--name-only", base, head, "--", EVENTS], cwd=root, timeout=30) if base else None
    if files is None or files.returncode:
        print(f"mq: event payload check not run for #{number}: cannot fetch or diff it; queued without the check", file=sys.stderr)
        return None
    out = []
    try:
        for path in files.stdout.split():
            name = os.path.basename(path)
            if path.endswith(".yaml") and name != "asyncapi.yaml" and not name.startswith("_"):   # the fragments; asyncapi.yaml is generated from them
                old, new = messages(root, base, path), messages(root, head, path)
                out += [f"{path}: {n}" for n in sorted(old) if n in new and old[n] != new[n]]
    except (yaml.YAMLError, AttributeError, subprocess.SubprocessError) as e:
        print(f"mq: event payload check not run for #{number}: {type(e).__name__}; queued without the check", file=sys.stderr)
        return None
    return out


def enforce(root, gh, numbers):
    """Exit with nothing queued when an implementation pull request changes the payload of an existing event; a pull request gh cannot read is let through, said out loud."""
    refused = []
    for n in numbers:
        v = gh.view(n)
        if not (v and v["state"] == "OPEN"):
            print(f"mq: event payload check not run for #{n}: gh cannot say its head; queued without the check", file=sys.stderr)
        elif impl_type(v["title"]) and (names := changed(root, n, v["head"])):
            refused.append(f"#{n} ({impl_type(v['title'])}): " + ", ".join(names))
    if refused:
        raise SystemExit("nothing queued: an implementation pull request must not change the payload of an existing event; the payload is signed at design. Send the change back to design "
                         "(a design PR edits docs/architecture/events/<module>.yaml), or retitle it if it is a design PR.\n" + "\n".join(refused))
