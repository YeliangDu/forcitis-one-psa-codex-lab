"""Personal Lab queue cutover: shared state, no legacy writer fallback."""
import os
from pathlib import Path
import stat
import subprocess

from mq_queue import LINE, Queue


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
REPOSITORY_ID = 1449415666
NAME = REPOSITORY.split("/")[-1]
ORIGIN = f"git@github.com:{REPOSITORY}.git"


def checkout_profile(top, remotes, fetch, push):
    """A Lab-like checkout with the wrong origin never falls back to legacy."""
    looks_like_lab = Path(top).name == NAME or any(
        str(url).removesuffix(".git").lower().endswith("/" + NAME)
        for url in [*fetch, *push]
    )
    if not looks_like_lab:
        return "legacy"
    if remotes != ["origin"] or fetch != [ORIGIN] or push != [ORIGIN]:
        raise ValueError("personal Lab requires the sole exact fetch and push origin")
    return "lab"


def _result(status, reason, present=False, queued=None):
    return dict(status=status, reason=reason, queue_present=present, queued=queued or [])


def _kind(path):
    try:
        return stat.S_IFMT(path.lstat().st_mode)
    except FileNotFoundError:
        return None


def _path(common_dir):
    common = Path(common_dir)
    if (not common.is_absolute() or common.name != ".git" or common.parent.name != NAME
            or common.resolve(strict=True) != common or _kind(common) != stat.S_IFDIR):
        raise ValueError("invalid physical Lab Git common directory")
    directory = common / "merge-queue"
    if _kind(directory) not in (None, stat.S_IFDIR):
        raise ValueError("unsafe Lab queue directory")
    path = directory / "merge.queue"
    for item in (path, Path(str(path) + ".merge-lock")):
        if _kind(item) not in (None, stat.S_IFREG):
            raise ValueError("unsafe Lab queue or lock file")
    return path


def _state(path):
    flags = os.O_RDONLY | os.O_NOFOLLOW
    try:
        fd = os.open(path, flags)
    except FileNotFoundError:
        return False, []
    with os.fdopen(fd, encoding="utf-8") as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise ValueError("nonregular Lab queue")
        numbers = []
        for line in stream:
            if line.strip() and not line.lstrip().startswith("#"):
                match = LINE.fullmatch(line.rstrip("\n"))
                if not match or int(match.group(1)) < 1:
                    raise ValueError("invalid Lab queue entry")
                numbers.append(int(match.group(1)))
        if len(set(numbers)) != len(numbers):
            raise ValueError("duplicate Lab queue entry")
        return True, numbers


def queue_command(command, common_dir, read_repo=None):
    """Observe or initialize the canonical file; all legacy writes hold."""
    try:
        path = _path(common_dir)
        present, queued = _state(path)
        if command in ("list", "status"):
            return _result("ok", "queue-present" if present else "queue-missing", present, queued)
        if command != "init":
            return _result("hold", "Lab writer is not bound; legacy queue writes are disabled", present, queued)
        repo = read_repo() if callable(read_repo) else None
        if (not isinstance(repo, dict) or repo.get("full_name") != REPOSITORY
                or repo.get("private") is not False or type(repo.get("id")) is not int or repo["id"] != REPOSITORY_ID
                or ("visibility" in repo and repo["visibility"] != "public")
                or ("fork" in repo and repo["fork"] is not False)):
            return _result("hold", "cannot verify the fixed public Lab identity", present, queued)
        queue = Queue(str(path))
        with queue.merge_lock():
            path = _path(common_dir)
            present, queued = _state(path)
            if not present:
                fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
                with os.fdopen(fd, "wb") as stream:
                    os.fsync(stream.fileno())
                return _result("ok", "queue-initialized", True)
            return _result("ok", "queue-already-initialized", True, queued)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        return _result("hold", str(error) if isinstance(error, ValueError) else "Lab queue I/O failed")


def read_repo():
    """Use the fixed private-Lab GitHub GET port, not the legacy Gh fallback."""
    import importlib.util

    path = Path(__file__).resolve().parents[1] / "session" / "codex_lab" / "lab_queue_live.py"
    spec = importlib.util.spec_from_file_location("mq_lab_read", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.GithubRead().repository()


def main_health(common_dir):
    """Validate canonical Lab identity without creating/reading queue state."""
    import importlib.util

    try:
        _path(common_dir)
        directory = Path(__file__).resolve().parents[1] / "session" / "codex_lab"
        modules = {}
        for name in ("lab_queue_live", "lab_main_health"):
            spec = importlib.util.spec_from_file_location("mq_health_" + name, directory / (name + ".py"))
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            modules[name] = module
        return modules["lab_main_health"].check_main_health(modules["lab_queue_live"].GithubRead())
    except Exception:
        return dict(action="hold", reason="main-health-unavailable")


def handle_cli(args, parser, sh):
    """Route the physical Lab before the legacy CLI constructs a worker."""
    def git(*argv):
        result = sh(["git", *argv])
        return result.stdout.strip().splitlines() if result.returncode == 0 else []

    top = git("rev-parse", "--show-toplevel")
    if not top:
        parser.error("cannot identify a Git checkout for the merge queue")
    common = git("rev-parse", "--path-format=absolute", "--git-common-dir")
    if len(top) != 1 or len(common) != 1:
        parser.error("cannot verify the Git common directory for the merge queue")
    try:
        common_path = Path(common[0])
        physical = common_path.resolve(strict=True)
        # A managed worktree can have any name. The common checkout still
        # identifies the Lab even if reading origin fails or it has changed.
        context = physical.parent if physical.parent.name == NAME else Path(top[0])
        profile = checkout_profile(context, git("remote"),
                                   git("remote", "get-url", "--all", "origin"),
                                   git("remote", "get-url", "--push", "--all", "origin"))
    except (OSError, ValueError) as error:
        parser.error(str(error))
    if profile != "lab":
        if args.cmd in ("init", "health", "plan"):
            parser.error("init/health/plan require the personal Lab checkout")
        return False
    if args.queue is not None or args.log is not None:
        parser.error("the personal Lab uses its canonical queue and log; path overrides are disabled")
    if args.cmd == "health":
        result = main_health(common[0])
        if result["action"] != "healthy":
            raise SystemExit("HOLD: " + result["reason"])
        print(f"Lab main health: healthy; head: {result['head_sha']}; main writer disabled")
        return True
    if args.cmd in ("plan", "enqueue", "once"):
        import json
        from mq_lab_operator import ProductionPorts, run_command

        if args.cmd == "enqueue":
            if len(args.prs) != 1 or args.after:
                parser.error("Lab enqueue requires one PR without dependency changes")
            number = args.prs[0]
        else:
            number = args.pr
        result = run_command(args.cmd, number, common[0], ProductionPorts(common[0], sh))
        if result["action"] == "hold":
            raise SystemExit("HOLD: " + result["reason"])
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return True
    result = queue_command(args.cmd, common[0], read_repo)
    if result["status"] != "ok":
        raise SystemExit("HOLD: " + result["reason"])
    if args.cmd == "list":
        for number in result["queued"]:
            print(number)
    else:
        print(f"Lab queue: {result['reason']}; entries: {result['queued']}; main writer disabled")
    return True
