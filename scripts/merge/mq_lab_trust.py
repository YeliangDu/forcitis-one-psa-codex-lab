"""Read-only loaded-source and independently pinned bootstrap attestations.

Approval parsing, physical Lab/origin verification and holding the canonical
lock are separate caller duties. These predicates never enable a writer.
"""
import re


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
INITIAL_MAIN = "a8454ced28ab18fce37d495f21d0daae2fa6ce8d"
BASELINE_RUN = 42418209077
CONTROL_HEAD = "f18fdd7265dc7450818a2303ceb630c0892deb57"
CONTROL_PATH = ".github/workflows/lab-bootstrap-baseline.yml"
CONTROL_BRANCH = "lab/14-bootstrap-baseline-ci"
CONTROL_PATHS = (
    ".github/workflows/lab-pr-ci.yml", "scripts/session/codex_lab/lab_pr_ci.py",
    "scripts/session/codex_lab/install_gitleaks.sh", "scripts/session/codex_lab/install_hosted_shellcheck.sh",
    "scripts/ci/light.sh", "scripts/ci/light_triggers.sh", "scripts/ci/backend_verify.sh",
    "scripts/codegen/generate_api.sh", "policy.json", ".gitleaks.toml", ".gitleaksignore",
)


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _positive(value):
    return type(value) is int and value > 0


def _identity(intent):
    return (isinstance(intent, dict) and set(intent) == {"number", "head", "base", "target"}
            and _positive(intent["number"]) and _sha(intent["head"]) and _sha(intent["base"])
            and intent["target"] == "main")


def _bootstrap(intent):
    return _identity(intent) and intent["number"] == 15 and intent["base"] == INITIAL_MAIN


def _git_sha(git, ref):
    output = git(["rev-parse", ref])
    if not isinstance(output, str) or not _sha(output.strip()):
        raise ValueError("Missing exact source blob/commit")
    return output.strip()


def trusted_source(intent, mode, git):
    """Require clean loaded code and preserve all fixed CI control blobs."""
    if not _identity(intent) or mode not in ("normal", "bootstrap") or not callable(git):
        return False
    if mode == "bootstrap" and not _bootstrap(intent):
        return False
    try:
        status = git(["status", "--porcelain", "--untracked-files=all"])
        if not isinstance(status, str) or status.strip():
            return False
        loaded = _git_sha(git, "HEAD")
        expected = intent["base"] if mode == "normal" else intent["head"]
        if loaded != expected:
            return False
        for path in CONTROL_PATHS:
            candidate = _git_sha(git, f"{intent['head']}:{path}")
            if mode == "normal" and candidate != _git_sha(git, f"{intent['base']}:{path}"):
                return False
        return True
    except Exception:
        return False


def _main(value):
    return (isinstance(value, dict) and value.get("ref") == "refs/heads/main"
            and isinstance(value.get("object"), dict) and value["object"].get("sha") == INITIAL_MAIN)


def _id(value, expected):
    return ((_positive(value) or isinstance(value, str) and re.fullmatch(r"[1-9][0-9]*", value) is not None)
            and str(value) == str(expected))


def bootstrap_check(intent, approval, github):
    """Accept only the known independent baseline for PR15's initial main."""
    if (not _bootstrap(intent) or not isinstance(approval, dict)
            or approval.get("mode") != "bootstrap" or type(approval.get("number")) is not int
            or any(approval.get(k) != v for k, v in intent.items())
            or type(approval.get("baseline_run_id")) is not int or approval["baseline_run_id"] != BASELINE_RUN
            or not _positive(approval.get("repo_id"))):
        return False
    try:
        repository = github.repository()
        if (not isinstance(repository, dict) or repository.get("full_name") != REPOSITORY
                or repository.get("private") is not True or not _positive(repository.get("id"))
                or repository["id"] != approval["repo_id"] or not _main(github.main_ref())):
            return False
        run = github.bootstrap_run(BASELINE_RUN)
        if (not isinstance(run, dict) or type(run.get("id")) is not int or run["id"] != BASELINE_RUN
                or type(run.get("run_attempt")) is not int or run["run_attempt"] != 1
                or run.get("path") != CONTROL_PATH or run.get("event") != "push"
                or run.get("head_branch") != CONTROL_BRANCH or run.get("head_sha") != CONTROL_HEAD
                or run.get("status") != "completed" or run.get("conclusion") != "success"):
            return False
        origin = run.get("repository")
        if (not isinstance(origin, dict) or not _positive(origin.get("id")) or origin["id"] != repository["id"]
                or origin.get("full_name", REPOSITORY) != REPOSITORY):
            return False
        jobs = github.jobs(BASELINE_RUN, 1)
        if not isinstance(jobs, list) or not all(isinstance(j, dict) for j in jobs):
            return False
        selected = [j for j in jobs if j.get("name") == "baseline"]
        if len(selected) != 1:
            return False
        job = selected[0]
        if (job.get("head_sha") != CONTROL_HEAD or job.get("conclusion") != "success"
                or job.get("runner_group_name") != "GitHub Actions" or job.get("labels") != ["ubuntu-24.04"]):
            return False
        artifacts = github.bootstrap_artifacts(BASELINE_RUN, 1)
        if not isinstance(artifacts, list) or not all(isinstance(a, dict) for a in artifacts):
            return False
        selected = [a for a in artifacts if a.get("name") == "lab-bootstrap-baseline"]
        if len(selected) != 1 or selected[0].get("expired") is not False:
            return False
        payload = selected[0].get("baseline")
        fields = {"kind", "head_sha", "repository", "run_id", "run_attempt", "light", "secret"}
        if (not isinstance(payload, dict) or set(payload) != fields
                or payload.get("kind") != "bootstrap-baseline" or payload.get("head_sha") != INITIAL_MAIN
                or payload.get("repository") != REPOSITORY or not _id(payload.get("run_id"), BASELINE_RUN)
                or not _id(payload.get("run_attempt"), 1)
                or payload.get("light") != "success" or payload.get("secret") != "success"):
            return False
        return _main(github.main_ref())
    except Exception:
        return False
