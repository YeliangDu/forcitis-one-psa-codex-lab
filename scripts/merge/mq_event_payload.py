"""The event payload check at enqueue (#1327): a pull request whose title type is not docs (the design type) that changes the payload of a message that already
exists in docs/architecture/events/<module>.yaml is refused before it is queued. The payload is signed at design (playbooks/design.md); the implementation ships what was signed
(EventCatalogueTest compares catalogue and code), so a change of an existing payload goes back to design. A new message, a changed message description, a deleted message, passes; so does any pull request of the docs type. Every other title type is refused (an allowlist, fail closed). Messages are matched by name across all fragments, so moving one to another fragment or renaming a fragment does not hide a change.
Exception (#1406): a body line `Payload-Signed: #<n>` names a docs pull request (open, or closed unmerged) whose newest review statement on its current head is a 通过 (its text is closed unmerged and lands with the code); each changed message passes only if that pull request's payload for it equals the head's, any other message stays refused.
The base is the merge base with origin/main, so what main changed after the branch left is not counted. Fails open, said on stderr, when the head cannot be fetched or git/PyYAML cannot answer; a head fragment that cannot be read as a catalogue is refused."""
import os
import re
import subprocess
import sys

import post_review
from mq_github import sh

DESIGN_TYPE = "docs"   # the only title type allowed to change a signed payload
EVENTS = "docs/architecture/events/"
SIGNED = re.compile(r"^Payload-Signed:[ \t]*#(\d+)[ \t]*$", re.M)   # in the code PR body: the docs PR whose text this PR ships


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
    found = changed_by_name(root, number, head)
    return found if found is None or isinstance(found, list) else [label for _, label in sorted(found.items())]


def changed_by_name(root, number, head):
    """changed(), as {name: label}; a list (the unreadable-fragment message) or None as changed() gives them."""
    fetched = sh(["git", "fetch", "-q", "origin", "main", f"refs/pull/{number}/head"], cwd=root, timeout=60)
    base = sh(["git", "merge-base", "origin/main", head], cwd=root, timeout=30).stdout.strip() if not fetched.returncode else ""
    if not base:
        print(f"mq: event payload check not run for #{number}: cannot fetch or diff it; queued without the check", file=sys.stderr)
        return None
    try:
        old = messages(root, base)
        try:
            new = messages(root, head)
        except Exception as e:   # a head fragment that is not a readable catalogue (bad YAML, a symlink, a wrong shape, a failed git read): EventCatalogueTest may still read it, so refuse rather than guess
            return [f"head fragment unreadable ({type(e).__name__})"]
    except Exception as e:   # PyYAML missing, git failing, or main itself unreadable: the check cannot run
        print(f"mq: event payload check not run for #{number}: {type(e).__name__}; queued without the check", file=sys.stderr)
        return None
    return {n: f"{n} ({old[n][0]})" if old[n][0] == new[n][0] else f"{n} ({old[n][0]} -> {new[n][0]})" for n in sorted(old) if n in new and old[n][1] != new[n][1]}


def signer_ok(root, gh, signer, code_head, names):
    """The names whose payload the docs pull request `signer` signs for the code pull request: it is open or closed unmerged (it is closed once the code pull request is queued, and the code pull request may be queued again), of the docs type, a 通过 review is the newest statement on
    exactly its current head, and its payload of each name equals the code head's payload (a docs head with no such message signs nothing). An empty set when any of that fails or cannot be read."""
    v = gh.view(signer)
    if not (v and v["state"] in ("OPEN", "CLOSED") and title_type(v["title"]) == DESIGN_TYPE):
        return set()
    reviews = gh.reviews(signer)
    fields = post_review.load_policy()[0]["header_fields"]
    said = []
    for r in reviews or []:
        h = post_review.parse_header(r["body"], fields)
        if h and h["commit"] == v["head"] == r["commit"]:
            said.append((r["at"], h["verdict"]))
    if not said or max(said)[1] != post_review.PASS:
        return set()
    if sh(["git", "fetch", "-q", "origin", f"refs/pull/{signer}/head"], cwd=root, timeout=60).returncode:
        return set()
    try:
        signed, shipped = messages(root, v["head"]), messages(root, code_head)
    except Exception:
        return set()
    return {n for n in names if n in signed and n in shipped and signed[n][1] == shipped[n][1]}


def enforce(root, gh, numbers):
    """Exit with nothing queued when a pull request that is not of the docs type changes the payload of an existing event; a pull request gh cannot read is let through, said out loud."""
    refused = []
    for n in numbers:
        v = gh.view(n)
        if not (v and v["state"] == "OPEN"):
            print(f"mq: event payload check not run for #{n}: gh cannot say its head; queued without the check", file=sys.stderr)
        elif title_type(v["title"]) != DESIGN_TYPE and (names := changed_by_name(root, n, v["head"])):
            if isinstance(names, dict):   # a payload signed in a docs pull request with a 通过 verdict may ship in this one, message by message
                left = set(names)
                for signer in dict.fromkeys(int(m) for m in SIGNED.findall(v["body"] or "")):
                    left -= signer_ok(root, gh, signer, v["head"], left)
                names = [names[k] for k in sorted(left)]
            if names:
                refused.append(f"#{n} ({title_type(v['title']) or 'no type'}): " + ", ".join(names))
    if refused:
        raise SystemExit("nothing queued: only a docs (design) pull request may change the payload of an existing event; the payload is signed at design. Send the change back to design "
                         "(a design PR edits docs/architecture/events/<module>.yaml), or retitle it if it is a design PR.\n" + "\n".join(refused))
