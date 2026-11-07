"""Plan exact-head verification for the private Lab, without network access."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
LANES = ("light", "shadow", "frontend", "backend")


def _identity(event, observed_head):
    try:
        pr = event["pull_request"]
        if any(repo["full_name"] != REPOSITORY for repo in (
                event["repository"], pr["head"]["repo"], pr["base"]["repo"])):
            raise ValueError("Only same-repository private Lab PRs are allowed")
        if event["repository"].get("private") is False:
            raise ValueError("Public repositories are not allowed")
        if pr["base"]["ref"] != "main":
            raise ValueError("Base must be main")
        head, base = pr["head"]["sha"], pr["base"]["sha"]
        if any(not isinstance(sha, str) or re.fullmatch(r"[0-9a-f]{40}", sha) is None
               for sha in (head, base, observed_head)):
            raise ValueError("Expected full commit SHAs")
        if observed_head != head:
            raise ValueError("Checkout is not the event head")
        return head, base
    except (KeyError, TypeError) as error:
        raise ValueError("Missing PR identity") from error


def plan(event, observed_head, paths):
    """Reject ambiguous identity/paths; unknown paths require every lane."""
    head, base = _identity(event, observed_head)
    if not isinstance(paths, (list, tuple)) or not paths:
        raise ValueError("Changed paths are required")
    required = {"light"}
    for path in paths:
        if (not isinstance(path, str) or not path or path.startswith("/")
                or "\\" in path or any(ord(c) < 32 or ord(c) == 127 for c in path)
                or any(part in ("", ".", "..") for part in path.split("/"))):
            raise ValueError("Unsafe changed path")
        if path.startswith("scripts/session/codex_lab/") or path == ".github/workflows/lab-pr-ci.yml":
            required.add("shadow")
        elif path.startswith(("frontend/", "docs/glossary/")):
            required.add("frontend")
        elif path.startswith("backend/"):
            required.add("backend")
        elif not path.startswith("docs/"):
            required.update(LANES)
    return dict(head_sha=head, base_sha=base,
                lanes={lane: "required" if lane in required else "not-applicable" for lane in LANES})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", required=True)
    parser.add_argument("--receipt", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    event = json.loads(Path(args.event).read_text())
    # Validate identity before invoking git, then validate the actual checkout
    # before reading any changed paths. No fetch, API call or shell expansion.
    _identity(event, event.get("pull_request", {}).get("head", {}).get("sha"))
    if event["repository"].get("private") is not True:
        raise ValueError("Runtime repository must be private")
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request" or os.environ.get("GITHUB_REPOSITORY") != REPOSITORY:
        raise ValueError("Unexpected workflow context")
    observed = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    head, base = _identity(event, observed)
    raw = subprocess.check_output(["git", "diff", "--name-only", "--no-renames", "-z", base, head, "--"])
    paths = raw.decode("utf-8").split("\0")
    if paths[-1] == "":
        paths.pop()
    receipt = plan(event, observed, paths)
    Path(args.receipt).write_text(json.dumps(dict(receipt, paths=paths), indent=2) + "\n")
    with Path(args.output).open("a") as output:
        for key, value in dict(head_sha=head, base_sha=base, **receipt["lanes"]).items():
            output.write(f"{key}={value}\n")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
