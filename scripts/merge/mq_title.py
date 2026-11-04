"""The title check at enqueue (#1289): a pull request whose title has not the required shape is refused before it is queued, not held after a full CI run at the merge step.
The shape is issue_gate.title_problem, the one that post_review.py and the merge step run too; this only runs it earlier. gh not answering fails closed: nothing is queued."""
import sys

import issue_gate


def enforce(gh, numbers):
    """Exit with nothing queued when any open pull request has a bad title, or gh cannot say what it is; the message names the shape the title should have."""
    refused = []
    for n in numbers:
        v = gh.view(n)
        if not v:
            refused.append(f"#{n}: gh did not answer, so its title cannot be checked")
        elif v["state"] == "OPEN" and (why := issue_gate.title_problem(v["title"], v["body"])) is not None:
            refused.append(f"#{n}: {why}")
    if refused:
        raise SystemExit("nothing queued: fix the pull request title (and retry if gh did not answer), then enqueue again.\n" + "\n".join(refused))
