#!/usr/bin/env python3
import importlib.util
import contextlib
import io
import json
import multiprocessing
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import time
import unittest
import sys
from unittest.mock import patch


HOOKS = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("handoff_snapshot", HOOKS / "handoff_snapshot.py")
snapshot = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(snapshot)


def concurrent_write(home, mode, barrier, result):
    original = snapshot.final_snapshots

    def synchronized_listing(*args):
        paths = original(*args)
        barrier.wait(timeout=20)
        return paths

    output = io.StringIO()
    payload = {"cwd": "/source", "session_id": f"worker-{os.getpid()}", "reason": "clear"}
    with patch.dict(os.environ, {"HOME": home}), patch.object(snapshot, "git", return_value="/source"), \
            patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", return_value="无"), \
            patch.object(snapshot, "final_snapshots", side_effect=synchronized_listing), \
            patch("sys.stdin", io.StringIO(json.dumps(payload))), contextlib.redirect_stdout(output):
        snapshot.run(mode)
    result.send(output.getvalue())
    result.close()


class StorageTests(unittest.TestCase):
    def test_r3_wrappers_warn_when_invoked_by_basename_elsewhere(self):
        for script in ["sessionend_snapshot.sh", "postcompact_context.sh", "precompact_snapshot.sh"]:
            with self.subTest(script=script), tempfile.TemporaryDirectory() as tmp:
                (Path(tmp) / script).write_bytes((HOOKS / script).read_bytes())
                result = subprocess.run(["/bin/bash", script], input="{}", cwd=tmp,
                    env={"HOME": tmp, "PATH": "/missing"}, capture_output=True, text=True, timeout=5)
                self.assertEqual(result.returncode, 0)
                self.assertEqual(result.stderr, "")
                self.assertEqual(result.stdout.splitlines(), ["【交接快照】告警：找不到钩子目录；请手动交接。"])

    def test_r3_concurrent_writers_keep_success_and_retention(self):
        context = multiprocessing.get_context("spawn")
        for mode in ["--end", "--compact"]:
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as tmp:
                directory = Path(tmp) / ".modernize/psa/handoff"
                directory.mkdir(parents=True)
                for index in range(70):
                    (directory / f"20200101-000000-old-{index:03}.md").write_text("old", encoding="utf-8")
                barrier, workers, readers = context.Barrier(24), [], []
                try:
                    for _ in range(24):
                        reader, writer = context.Pipe(duplex=False)
                        worker = context.Process(target=concurrent_write, args=(tmp, mode, barrier, writer))
                        worker.start()
                        writer.close()
                        workers.append(worker)
                        readers.append(reader)
                    deadline = time.monotonic() + 30
                    for worker, reader in zip(workers, readers):
                        worker.join(timeout=max(0, deadline - time.monotonic()))
                        self.assertEqual(worker.exitcode, 0, "concurrent writer failed or timed out")
                        self.assertTrue(reader.poll(), "concurrent writer did not report output")
                        output = reader.recv()
                        self.assertNotIn("告警", output)
                        if mode == "--compact":
                            self.assertIn("压缩前快照已写入", output)
                    self.assertEqual(len(snapshot.final_snapshots(directory)), 50)
                    self.assertEqual(len(list(directory.glob("*-worker-*.md"))), 24)
                    self.assertFalse(any(path.name.endswith(".tmp") for path in directory.iterdir()))
                finally:
                    for worker in workers:
                        if worker.is_alive():
                            worker.terminate()
                        worker.join(timeout=5)
                    for reader in readers:
                        reader.close()

    def test_r3_cleanup_errors_preserve_compact_success(self):
        for operation in ["scan", "lstat", "unlink"]:
            for error in [FileNotFoundError, PermissionError]:
                with self.subTest(operation=operation, error=error.__name__), tempfile.TemporaryDirectory() as tmp:
                    directory = Path(tmp) / ".modernize/psa/handoff"
                    directory.mkdir(parents=True)
                    for index in range(50):
                        (directory / f"20200101-000000-old-{index:03}.md").write_text("old", encoding="utf-8")
                    output, original_unlink = io.StringIO(), Path.unlink

                    def unlink(path, *args, **kwargs):
                        if path.suffix == ".md":
                            raise error()
                        return original_unlink(path, *args, **kwargs)

                    failure = patch.object(os, "scandir", side_effect=error()) if operation == "scan" else \
                        patch.object(Path, "lstat", side_effect=error()) if operation == "lstat" else \
                        patch.object(Path, "unlink", new=unlink)
                    with patch.dict(os.environ, {"HOME": tmp}), patch.object(snapshot, "git", return_value="/source"), \
                            patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", return_value="无"), \
                            patch("sys.stdin", io.StringIO(json.dumps({"cwd": "/source", "session_id": "new"}))), \
                            contextlib.redirect_stdout(output), failure:
                        snapshot.run("--compact")
                    self.assertNotIn("告警", output.getvalue())
                    self.assertIn("压缩前快照已写入", output.getvalue())
                    self.assertEqual(len(list(directory.glob("*-new.md"))), 1)

    def test_r3_missing_expired_file_does_not_stop_remaining_cleanup(self):
        for operation in ["lstat", "unlink"]:
            with self.subTest(operation=operation), tempfile.TemporaryDirectory() as tmp:
                directory = Path(tmp) / ".modernize/psa/handoff"
                directory.mkdir(parents=True)
                for index in range(55):
                    (directory / f"20200101-000000-old-{index:03}.md").write_text("old", encoding="utf-8")
                real_operation, real_unlink = getattr(Path, operation), Path.unlink

                def disappeared(path, *args, **kwargs):
                    if path.name == "20200101-000000-old-005.md":
                        real_unlink(path)
                        raise FileNotFoundError()
                    return real_operation(path, *args, **kwargs)

                with patch.dict(os.environ, {"HOME": tmp}), patch.object(snapshot, "git", return_value="/source"), \
                        patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", return_value="无"), \
                        patch.object(Path, operation, new=disappeared), contextlib.redirect_stdout(io.StringIO()):
                    snapshot.compact({"cwd": "/source", "session_id": "new"})
                self.assertEqual(len(snapshot.final_snapshots(directory)), 50)

    def test_python_hooks_compile_without_warnings(self):
        result = subprocess.run([sys.executable, "-W", "error", "-I", "-c",
            'import pathlib,sys; [compile(p.read_text(encoding="utf-8"), str(p), "exec") for p in pathlib.Path(sys.argv[1]).glob("*.py")]',
            str(HOOKS)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_claim_uses_header_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            path = directory / "20261017-120000-source-clear.md"
            path.write_text("- 工作目录：/source\n\n## 消息\n- 工作目录：/other\n", encoding="utf-8")
            self.assertIsNone(snapshot.latest(directory, "/other", "new-session"))
            self.assertEqual(snapshot.latest(directory, "/source", "new-session")[0], path)

    def test_r3_header_fields_cannot_claim_another_worktree(self):
        for field in ["transcript_path", "trigger", "root", "branch"]:
            for newline in ["\n", "\r", "\r\n", "\u2028"]:
                with self.subTest(field=field, newline=repr(newline)), tempfile.TemporaryDirectory() as tmp:
                    forged = f"original{newline}- 工作目录：/other"
                    payload = {"cwd": "/source", "session_id": "source", "trigger": "auto", "transcript_path": "original"}
                    if field in payload:
                        payload[field] = forged
                    root, branch = (forged, "main") if field == "root" else ("/source", forged if field == "branch" else "main")

                    def git(cwd, deadline, *args):
                        return root if "--show-toplevel" in args else branch if "--abbrev-ref" in args else "无"

                    with patch.dict(os.environ, {"HOME": tmp}), patch.object(snapshot, "git", side_effect=git), \
                            patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", return_value="无"), \
                            contextlib.redirect_stdout(io.StringIO()):
                        snapshot.compact(payload)
                    directory = Path(tmp) / ".modernize/psa/handoff"
                    self.assertIsNone(snapshot.latest(directory, "/other", "unrelated"))
                    text = next(directory.glob("*.md")).read_text(encoding="utf-8")
                    self.assertIn(f"original{' ' * len(newline)}- 工作目录：/other", text)
                    if field != "root":
                        self.assertIsNotNone(snapshot.latest(directory, "/source", "unrelated"))

    def test_message_cannot_impersonate_header(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "transcript.jsonl"
            path.write_text(json.dumps({"message": {"content":
                '<cross-session-message from-name="工作目录">/other\n正文</cross-session-message>'}}), encoding="utf-8")
            titles = snapshot.messages(path, time.monotonic() + 2)
            self.assertEqual(titles, "  · 工作目录：/other")
            (Path(tmp) / "20261017-120000-source-clear.md").write_text(
                "- 工作目录：/source\n\n## 消息\n" + titles, encoding="utf-8")
            self.assertIsNone(snapshot.latest(Path(tmp), "/other", "new-session"))

    def test_message_first_line_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "transcript.jsonl"
            path.write_text(json.dumps({"content": '<cross-session-message from-name="peer">' + "长" * 121 +
                '\n正文</cross-session-message>'}), encoding="utf-8")
            self.assertEqual(snapshot.messages(path, time.monotonic() + 2), "  · peer：" + "长" * 120)

    def test_atomic_publication(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp) / ".modernize/psa/handoff"
            events = []
            real_open, real_fsync, real_replace = os.open, os.fsync, os.replace

            def exclusive_open(path, flags, mode=0o777):
                if Path(path) == directory:
                    self.assertTrue(flags & os.O_DIRECTORY)
                else:
                    self.assertTrue(flags & os.O_EXCL)
                    self.assertEqual(mode, 0o600)
                return real_open(path, flags, mode)

            def fsync(fd):
                directory_fd = stat.S_ISDIR(os.fstat(fd).st_mode)
                self.assertEqual(stat.S_IMODE(os.fstat(fd).st_mode), 0o700 if directory_fd else 0o600)
                events.append("directory fsync" if directory_fd else "fsync")
                real_fsync(fd)

            def replace(source, target):
                self.assertEqual(Path(source).parent, Path(target).parent)
                self.assertEqual(events, ["fsync"])
                self.assertIn("## 最近跨会话消息标题", Path(source).read_text(encoding="utf-8"))
                events.append("replace")
                real_replace(source, target)

            def during_write(*args):
                self.assertEqual(list(directory.glob("*.md")), [], "unfinished snapshot must not have a final name")
                self.assertEqual(len(list(directory.iterdir())), 1)
                self.assertIsNone(snapshot.latest(directory, "/source", "source"))
                return "最后一节"

            with patch.dict(os.environ, {"HOME": tmp}), patch.object(snapshot, "git", return_value="/source"), \
                    patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", side_effect=during_write), \
                    patch.object(os, "open", side_effect=exclusive_open), patch.object(os, "fsync", side_effect=fsync), \
                    patch.object(os, "replace", side_effect=replace):
                snapshot.end({"cwd": "/source", "session_id": "source", "reason": "clear"})
            self.assertEqual(events, ["fsync", "replace", "directory fsync"])
            self.assertEqual(len(list(directory.iterdir())), 1)
            self.assertIn("最后一节", next(directory.glob("*.md")).read_text(encoding="utf-8"))

    def test_r3_fchmod_failure_closes_descriptor_and_removes_temporary_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path, descriptors, real_open = Path(tmp) / "snapshot.md", [], os.open

            def opened(*args):
                fd = real_open(*args)
                descriptors.append(fd)
                return fd

            try:
                with patch.object(os, "open", side_effect=opened), patch.object(os, "fchmod", side_effect=PermissionError()):
                    with self.assertRaises(PermissionError), snapshot.atomic_snapshot(path):
                        self.fail("failed fchmod must prevent writing")
                self.assertEqual(len(descriptors), 1)
                with self.assertRaises(OSError):
                    os.fstat(descriptors[0])
                self.assertEqual(list(Path(tmp).iterdir()), [])
            finally:
                for fd in descriptors:
                    try:
                        os.close(fd)
                    except OSError:
                        pass

    def test_r3_directory_sync_failure_preserves_published_file(self):
        for failure in ["open", "fsync"]:
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as tmp:
                path, descriptors = Path(tmp) / "snapshot.md", []
                real_open, real_fsync = os.open, os.fsync

                def opened(target, flags, mode=0o777):
                    if Path(target) == path.parent and failure == "open":
                        raise PermissionError()
                    fd = real_open(target, flags, mode)
                    if Path(target) == path.parent:
                        descriptors.append(fd)
                    return fd

                def fsync(fd):
                    if stat.S_ISDIR(os.fstat(fd).st_mode):
                        raise OSError("directory sync unsupported")
                    real_fsync(fd)

                with patch.object(os, "open", side_effect=opened), patch.object(os, "fsync", side_effect=fsync):
                    with snapshot.atomic_snapshot(path) as out:
                        out.write("published")
                self.assertEqual(path.read_text(encoding="utf-8"), "published")
                self.assertEqual(list(Path(tmp).iterdir()), [path])
                self.assertEqual(len(descriptors), 0 if failure == "open" else 1)
                for fd in descriptors:
                    with self.assertRaises(OSError):
                        os.fstat(fd)

    def test_partial_temporary_file_is_not_claimed(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / "20261017-120000-source-clear.md.tmp").write_text("- 工作目录：/source\n", encoding="utf-8")
            self.assertIsNone(snapshot.latest(directory, "/source", "source"))

    def test_sessionend_reasons(self):
        for fields, label in [({"reason": "clear"}, "clear"), ({"reason": "other"}, "other-end"),
                ({}, "missing-end"), ({"reason": ""}, "empty-end"), ({"reason": None}, "missing-end"),
                ({"session_end_reason": "other"}, "other-end")]:
            with self.subTest(fields=fields), tempfile.TemporaryDirectory() as tmp, \
                    patch.dict(os.environ, {"HOME": tmp}), patch.object(snapshot, "git", return_value="/source"), \
                    patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", return_value="无"):
                snapshot.end({"cwd": "/source", "session_id": "source", **fields})
                files = list((Path(tmp) / ".modernize/psa/handoff").glob("*.md"))
                self.assertEqual(len(files), 1)
                self.assertTrue(files[0].name.endswith(f"-source-{label}.md"))
        for reason in ["logout", "prompt_input_exit", "resume", "unknown", 1]:
            with self.subTest(reason=reason), tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, {"HOME": tmp}):
                snapshot.end({"reason": reason})
                self.assertFalse((Path(tmp) / ".modernize").exists())

    def test_sessionend_settings_have_no_matcher(self):
        settings = json.loads((HOOKS.parents[1] / ".claude/settings.json").read_text(encoding="utf-8"))
        self.assertTrue(settings["hooks"]["SessionEnd"])
        self.assertTrue(all("matcher" not in entry for entry in settings["hooks"]["SessionEnd"]))

    def test_existing_directory_permissions_are_tightened(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp) / ".modernize/psa/handoff"
            directory.mkdir(parents=True)
            directory.chmod(0o755)
            with patch.dict(os.environ, {"HOME": tmp}), patch.object(snapshot, "git", return_value="/source"), \
                    patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", return_value="无"):
                snapshot.end({"cwd": "/source", "reason": "clear"})
            self.assertEqual(stat.S_IMODE(directory.stat().st_mode), 0o700)

    def test_snapshot_retention(self):
        for mode in ["--end", "--compact"]:
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as tmp:
                directory = Path(tmp) / ".modernize/psa/handoff"
                directory.mkdir(parents=True)
                files = []
                for index in range(55):
                    suffix = ["-clear", "-other-end", ""][index % 3]
                    path = directory / f"20200101-0000{index:02}-session{suffix}.md"
                    path.write_text("old", encoding="utf-8")
                    files.append(path)
                os.utime(files[0], (time.time() + 3600, time.time() + 3600))
                protected = directory / "notes.md"
                protected.write_text("keep", encoding="utf-8")
                partial = directory / "20200101-000000-partial-clear.md.tmp"
                partial.write_text("half", encoding="utf-8")
                link = directory / "20190101-000000-link-clear.md"
                link.symlink_to(protected)
                folder = directory / "20190101-000000-folder-clear.md"
                folder.mkdir()
                with patch.dict(os.environ, {"HOME": tmp}), patch.object(snapshot, "git", return_value="/source"), \
                        patch.object(snapshot, "command", return_value="无"), patch.object(snapshot, "messages", return_value="无"), \
                        patch("sys.stdin", io.StringIO(json.dumps({"cwd": "/source", "session_id": "new", "reason": "clear"}))), \
                        contextlib.redirect_stdout(io.StringIO()):
                    snapshot.run(mode)
                self.assertTrue(all(not path.exists() for path in files[:6]), "oldest six must be pruned")
                self.assertTrue(all(path.exists() for path in files[6:]))
                self.assertEqual(len([path for path in directory.iterdir() if path.is_file() and not path.is_symlink() and path.suffix == ".md"]), 51)
                self.assertTrue(protected.exists() and partial.exists() and link.is_symlink() and folder.is_dir())

    def test_latest_size_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            older = directory / "20261016-120000-source-clear.md"
            older.write_text("- 工作目录：/source\nNORMAL", encoding="utf-8")
            (directory / "20261017-120000-source-clear.md").write_text("- 工作目录：/source\n" + "x" * (1024 * 1024), encoding="utf-8")
            self.assertEqual(snapshot.latest(directory, "/source", "source")[0], older)

    def test_latest_deadline(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / "20261017-120000-source-clear.md").write_text("- 工作目录：/source\n", encoding="utf-8")
            with patch.object(snapshot.time, "monotonic", return_value=20):
                with self.assertRaises(snapshot.SnapshotError):
                    snapshot.latest(directory, "/source", "source", deadline=10)

    def test_precheck_trigger_registration(self):
        script = (HOOKS.parent / "precheck.sh").read_text(encoding="utf-8")
        condition = re.search(r"(?m)^if (.*); then\n  run_step \"guards: handoff \(self-test\)", script).group(1)
        with tempfile.TemporaryDirectory() as tmp:
            for path in ["scripts/hooks/session_context.py", "scripts/hooks/precompact_snapshot.sh", "scripts/hooks/handoff_safe_selftest.py", "scripts/hooks/handoff_storage_selftest.py"]:
                with self.subTest(path=path):
                    (Path(tmp) / "changed").write_text(path + "\n", encoding="utf-8")
                    code = 'touches() { grep -Eq "$1" "$work/changed"; }; if ' + condition + '; then exit 0; else exit 1; fi'
                    result = subprocess.run(["/bin/bash", "-c", code], env={"PATH": "/usr/bin:/bin", "work": tmp}, capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, "handoff self-tests must be triggered")


if __name__ == "__main__":
    unittest.main(argv=[__file__, *([] if sys.argv[1:] == ["--self-test"] else sys.argv[1:])])
