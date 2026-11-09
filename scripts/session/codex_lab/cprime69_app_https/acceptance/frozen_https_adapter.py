"""Synthetic HTTPS adapter acceptance; never runs Git or obtains credentials.

Baseline: 71a704bce6b2bf7b079dc2b8a60e43706ee6da27
Author: one-test
Native thread: 01a1b614-934c-7250-a715-5cf09e9c02c2
Native turn: unavailable; not fabricated
Author execution: not run

Contract:
  GitHTTPS(scope, credential_socket, runner)
  .push(args, permit_guard, lease_fd) -> {"action": "issued"}

Rejected inputs raise ValueError before runner invocation.
The socket path and askpass path are configuration, never credentials.
"""
import copy
import fcntl
import hashlib
import importlib
import importlib.util
import os
from pathlib import Path
import socket
import tempfile
import unittest


URL = "https://github.com/YeliangDu/forcitis-one-psa-codex-lab.git"
REF = "refs/heads/codex/https-adapter-probe"
OLD = "a" * 40
HEAD = "c" * 40


class HTTPSAdapterAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("lab_https_transport") is not None:
            module = importlib.import_module("lab_https_transport")
        cls = getattr(module, "GitHTTPS", None)
        self.assertTrue(callable(cls), "HTTPS_ADAPTER_CAPABILITY_MISSING")
        return cls

    def fixture(self):
        temporary = tempfile.TemporaryDirectory(prefix="https-adapter-")
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name).resolve()
        root.chmod(0o700)

        lock = root / "canonical.lock"
        fd = os.open(lock, os.O_CREAT | os.O_RDWR | os.O_EXCL, 0o600)
        self.addCleanup(os.close, fd)
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        info = os.fstat(fd)

        # Synthetic deny-only helper: no secret, no Git, no network.
        askpass = root / "trusted-askpass"
        helper = b"#!/bin/sh\nexit 1\n"
        askpass.write_bytes(helper)
        askpass.chmod(0o700)

        credential_socket = root / "credential.sock"
        server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.addCleanup(server.close)
        server.bind(str(credential_socket))
        credential_socket.chmod(0o600)

        scope = dict(
            repository="YeliangDu/forcitis-one-psa-codex-lab",
            url=URL,
            ref=REF,
            expected=OLD,
            head=HEAD,
            lock=dict(path=str(lock), device=info.st_dev, inode=info.st_ino),
            askpass=dict(
                path=str(askpass),
                sha256=hashlib.sha256(helper).hexdigest(),
            ),
        )
        args = [
            "--force-with-lease=" + REF + ":" + OLD,
            URL,
            HEAD + ":" + REF,
        ]
        calls = []
        events = []

        def runner(argv, *, env, pass_fds):
            events.append("runner")
            calls.append(dict(
                argv=copy.deepcopy(argv),
                env=copy.deepcopy(env),
                pass_fds=tuple(pass_fds),
            ))
            return dict(returncode=0)

        def guard():
            events.append("permit")
            return True

        return scope, str(credential_socket), args, fd, calls, events, runner, guard

    def test_01_exact_isolated_push_uses_final_permit_and_original_fd(self):
        cls = self.seam()
        scope, sock, args, fd, calls, events, runner, guard = self.fixture()
        original = copy.deepcopy((scope, args))
        result = cls(scope, sock, runner).push(args, guard, fd)
        self.assertEqual(result, {"action": "issued"})
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0]["argv"], ["git", "push", *args])
        self.assertEqual(calls[0]["pass_fds"], (fd,))
        self.assertEqual(events[-2:], ["permit", "runner"])
        self.assertEqual((scope, args), original)

    def test_02_main_url_lease_refspec_or_extra_arguments_are_rejected(self):
        cls = self.seam()
        mutations = (
            "main", "url", "userinfo", "extra-option", "extra-refspec",
            "wrong-lease", "missing-lease", "wrong-head",
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                scope, sock, args, fd, calls, _, runner, guard = self.fixture()
                if mutation == "main":
                    scope["ref"] = "refs/heads/main"
                    args = [
                        "--force-with-lease=refs/heads/main:" + OLD,
                        URL, HEAD + ":refs/heads/main",
                    ]
                elif mutation == "url":
                    args[1] = "https://example.invalid/other.git"
                elif mutation == "userinfo":
                    args[1] = URL.replace(
                        "https://", "https://synthetic:forbidden@"
                    )
                elif mutation == "extra-option":
                    args.insert(0, "--no-verify")
                elif mutation == "extra-refspec":
                    args.append(HEAD + ":refs/heads/other")
                elif mutation == "wrong-lease":
                    args[0] = "--force-with-lease=" + REF + ":" + "b" * 40
                elif mutation == "missing-lease":
                    args = args[1:]
                else:
                    args[2] = "d" * 40 + ":" + REF
                with self.assertRaises(ValueError):
                    cls(scope, sock, runner).push(args, guard, fd)
                self.assertEqual(calls, [])

    def test_03_failed_or_exceptional_final_permit_never_reaches_runner(self):
        cls = self.seam()
        for exceptional in (False, True):
            with self.subTest(exceptional=exceptional):
                scope, sock, args, fd, calls, _, runner, _ = self.fixture()

                def guard():
                    if exceptional:
                        raise RuntimeError("synthetic permit unavailable")
                    return False

                with self.assertRaises(ValueError):
                    cls(scope, sock, runner).push(args, guard, fd)
                self.assertEqual(calls, [])

    def test_04_wrong_fd_or_replaced_canonical_inode_is_rejected(self):
        cls = self.seam()
        for mutation in ("other-fd", "replaced-path"):
            with self.subTest(mutation=mutation):
                scope, sock, args, fd, calls, _, runner, guard = self.fixture()
                if mutation == "other-fd":
                    other = Path(scope["lock"]["path"]).with_name("other.lock")
                    other_fd = os.open(
                        other, os.O_CREAT | os.O_RDWR | os.O_EXCL, 0o600
                    )
                    self.addCleanup(os.close, other_fd)
                    fcntl.flock(other_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    supplied_fd = other_fd
                else:
                    path = Path(scope["lock"]["path"])
                    path.rename(path.with_name("original-held.lock"))
                    path.write_bytes(b"")
                    path.chmod(0o600)
                    supplied_fd = fd
                with self.assertRaises(ValueError):
                    cls(scope, sock, runner).push(args, guard, supplied_fd)
                self.assertEqual(calls, [])

    def test_05_only_pinned_askpass_socket_configuration_reaches_git(self):
        cls = self.seam()
        scope, sock, args, fd, calls, _, runner, guard = self.fixture()
        result = cls(scope, sock, runner).push(args, guard, fd)
        env = calls[0]["env"]
        self.assertEqual(set(env), {
            "PATH", "GIT_TERMINAL_PROMPT",
            "GIT_ASKPASS", "LAB_CREDENTIAL_SOCKET",
        })
        self.assertEqual(env["PATH"], "/usr/bin:/bin")
        self.assertEqual(env["GIT_TERMINAL_PROMPT"], "0")
        self.assertEqual(env["GIT_ASKPASS"], scope["askpass"]["path"])
        self.assertEqual(env["LAB_CREDENTIAL_SOCKET"], sock)
        self.assertEqual(result, {"action": "issued"})
        self.assertNotIn(sock, calls[0]["argv"])
        self.assertNotIn(scope["askpass"]["path"], calls[0]["argv"])

        # Pinned helper content must be checked, not merely its pathname.
        scope, sock, args, fd, calls, _, runner, guard = self.fixture()
        Path(scope["askpass"]["path"]).write_bytes(b"#!/bin/sh\nexit 0\n")
        with self.assertRaises(ValueError):
            cls(scope, sock, runner).push(args, guard, fd)
        self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
