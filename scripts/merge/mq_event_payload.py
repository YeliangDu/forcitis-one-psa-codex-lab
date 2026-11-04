"""The event payload check at enqueue (#1327): a pull request whose title type is not docs (the design type) that changes the payload of a message that already
exists in docs/architecture/events/<module>.yaml is refused before it is queued. The payload is signed at design (playbooks/design.md); the implementation ships what was signed
(EventCatalogueTest compares catalogue and code), so a change of an existing payload goes back to design. A new message, a changed message description, a deleted message, passes; so does any pull request of the docs type. Every other title type is refused (an allowlist, fail closed). Messages are matched by name across all fragments, so moving one to another fragment or renaming a fragment does not hide a change.
The base is the merge base with origin/main, so what main changed after the branch left is not counted. Fails open, said on stderr, when the head cannot be fetched or read."""
import os
import subprocess
import sys

from mq_github import sh

DESIGN_TYPE = "docs"   # the only title type allowed to change a signed payload
EVENTS = "docs/architecture/events/"


def title_type(title):
    """The title's type word (`feat(x): …` -> feat); '' when there is none."""
    return title.split(":", 1)[0].split("(", 1)[0].strip() if isinstance(title, str) and ":" in title else ""


def messages(root, rev):
    """{message name: (file, payload)} over every top-level catalogue fragment at a revision (the files EventCatalogueTest reads: *.yaml, not asyncapi.yaml, not _*)."""
    import yaml   # here, not at module level: an interpreter without PyYAML must not break every mq command
    listed = sh(["git", "ls-tree", "-z", "--name-only", rev, EVENTS], cwd=root, timeout=30)
    if listed.returncode:
        raise subprocess.SubprocessError("ls-tree")
    out = {}
    for path in listed.stdout.split("\0"):
        name = os.path.basename(path)
        if path.endswith(".yaml") and name != "asyncapi.yaml" and not name.startswith("_"):
            shown = sh(["git", "show", f"{rev}:{path}"], cwd=root, timeout=30)
            if shown.returncode:
                raise subprocess.SubprocessError("show")
            found = (yaml.safe_load(shown.stdout) or {}).get("components", {}).get("messages", {})
            out.update({n: (path, (m or {}).get("payload")) for n, m in found.items()})
    return out


def changed(root, number, head):
    """Existing messages whose payload the head changes ('<Name> (<file>)', '<Name> (<old> -> <new>)' when moved), or None when the check could not run (said on stderr)."""
    fetched = sh(["git", "fetch", "-q", "origin", "main", f"refs/pull/{number}/head"], cwd=root, timeout=60)
    base = sh(["git", "merge-base", "origin/main", head], cwd=root, timeout=30).stdout.strip() if not fetched.returncode else ""
    if not base:
        print(f"mq: event payload check not run for #{number}: cannot fetch or diff it; queued without the check", file=sys.stderr)
        return None
    try:
        old, new = messages(root, base), messages(root, head)
    except Exception as e:   # YAML that does not parse, a shape that is not a catalogue, git failing
        print(f"mq: event payload check not run for #{number}: {type(e).__name__}; queued without the check", file=sys.stderr)
        return None
    return [f"{n} ({old[n][0]})" if old[n][0] == new[n][0] else f"{n} ({old[n][0]} -> {new[n][0]})" for n in sorted(old) if n in new and old[n][1] != new[n][1]]


def enforce(root, gh, numbers):
    """Exit with nothing queued when a pull request that is not of the docs type changes the payload of an existing event; a pull request gh cannot read is let through, said out loud."""
    refused = []
    for n in numbers:
        v = gh.view(n)
        if not (v and v["state"] == "OPEN"):
            print(f"mq: event payload check not run for #{n}: gh cannot say its head; queued without the check", file=sys.stderr)
        elif title_type(v["title"]) != DESIGN_TYPE and (names := changed(root, n, v["head"])):
            refused.append(f"#{n} ({title_type(v['title']) or 'no type'}): " + ", ".join(names))
    if refused:
        raise SystemExit("nothing queued: only a docs (design) pull request may change the payload of an existing event; the payload is signed at design. Send the change back to design "
                         "(a design PR edits docs/architecture/events/<module>.yaml), or retitle it if it is a design PR.\n" + "\n".join(refused))
