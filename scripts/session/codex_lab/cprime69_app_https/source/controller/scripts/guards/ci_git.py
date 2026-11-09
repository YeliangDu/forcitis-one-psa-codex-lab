"""Git helpers shared by the scripts in this directory: every call runs with `git -C <repository root>`."""
import re
import subprocess


class CiError(Exception):
    pass


def run(*args, cwd):
    return subprocess.run(["git", "-C", cwd, "-c", "core.quotePath=false", *args], capture_output=True, text=True)


def git(*args, cwd):
    result = run(*args, cwd=cwd)
    if result.returncode != 0:
        raise CiError(f"git {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def git_ok(*args, cwd):
    return run(*args, cwd=cwd).returncode == 0


def toplevel(cwd="."):
    result = run("rev-parse", "--show-toplevel", cwd=cwd)
    if result.returncode != 0:
        raise CiError("not inside a git repository")
    return result.stdout.strip()


def require_full_history(root):
    if git("rev-parse", "--is-shallow-repository", cwd=root).strip() == "true":
        raise CiError("the checkout is shallow; fetch the full history (fetch-depth: 0)")


def commit_of(ref, root):
    result = run("rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}", cwd=root)
    if result.returncode != 0:
        raise CiError(f"{ref} is not a commit in this repository; refusing to compare against nothing")
    return result.stdout.strip()


def comparison_point(base_sha, root):
    parents = git("rev-list", "--parents", "-n", "1", "HEAD", cwd=root).split()
    if len(parents) > 2:
        return base_sha
    result = run("merge-base", base_sha, "HEAD", cwd=root)
    return result.stdout.strip() if result.returncode == 0 else base_sha


def require_version(minimum, root):
    text = git("--version", cwd=root).strip()
    match = re.search(r"(\d+)\.(\d+)", text)
    if not match or (int(match.group(1)), int(match.group(2))) < minimum:
        raise CiError(f"git {minimum[0]}.{minimum[1]} or newer is required (merge-tree --write-tree), found: {text}")
