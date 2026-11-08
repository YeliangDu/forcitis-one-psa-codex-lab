"""#45 forward refresh: actual dispatcher/store/disposable Git; old fixture API only.

Synthetic occupancy is explicitly not native evidence. No real API/rerun/delete.
"""
import copy
import fcntl
import multiprocessing
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parent
CORE_SHA = "99122154e3ce796c23e16221e83804df63dcf137ad2c998cfeac4691a40fea8e"


def canonical(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


class BranchForwardRefreshAcceptanceTest(unittest.TestCase):
    def setUp(self):
        source = HERE / "lab_branch_lifecycle_selftest.py"
        self.assertEqual(CORE_SHA, hashlib.sha256(source.read_bytes()).hexdigest())
        spec = importlib.util.spec_from_file_location("lab45_refresh_frozen_fixture", source)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.core = module.BranchLifecycleAcceptanceTest(methodName="runTest")
        self.core.setUp()
        self.addCleanup(self.core.doCleanups)
        self.core.dispatch()
        self.core.commit_publish("synthetic old approved branch head\n")
        result = self.core.command("ensure-pr", dict(binding=self.core.binding))
        self.core.binding = result["binding"]
        self.binding = copy.deepcopy(self.core.binding)
        self.old_head = self.binding["head"]
        self.new_base = None

    def state(self):
        return json.loads(self.core.state.read_text())

    def request(self, **extra):
        state = self.state()
        return dict(binding=copy.deepcopy(self.binding), expected_revision=state["revision"],
                    expected_binding_sha256=canonical(self.binding), **extra)

    def main_forward(self):
        (self.core.checkout / "synthetic-main-next.txt").write_text("next main\n")
        self.core.raw(["add", "synthetic-main-next.txt"])
        self.core.raw(["commit", "-m", "synthetic main advance"])
        self.new_base = self.core.raw(["rev-parse", "HEAD"])
        self.core.raw(["push", "origin", "HEAD:refs/heads/main"])
        self.core.github.pulls[0]["base"]["sha"] = self.new_base
        self.core.github.view["current_base"] = self.new_base
        return self.new_base

    def manual_forward(self):
        self.main_forward()
        self.core.raw(["merge", "--no-edit", self.new_base], self.core.w1)
        head = self.core.raw(["rev-parse", "HEAD"], self.core.w1)
        self.core.raw(["push", "origin", head + ":refs/heads/" + self.binding["branch"]], self.core.w1)
        self.core.github.pulls[0]["head"]["sha"] = head
        self.core.raw(["merge-base", "--is-ancestor", self.old_head, head], self.core.w1)
        self.core.raw(["merge-base", "--is-ancestor", self.new_base, head], self.core.w1)
        return head

    def rewrite(self, base):
        self.core.raw(["checkout", "--detach", base], self.core.w2)
        (self.core.w2 / "synthetic-rewritten.txt").write_text("real scratch history rewrite\n")
        self.core.raw(["add", "synthetic-rewritten.txt"], self.core.w2)
        self.core.raw(["commit", "-m", "synthetic divergent rewrite"], self.core.w2)
        rewritten = self.core.raw(["rev-parse", "HEAD"], self.core.w2)
        self.core.raw(["reset", "--hard", rewritten], self.core.w1)
        self.core.raw(["push", "--force", "origin", rewritten + ":refs/heads/" + self.binding["branch"]], self.core.w1)
        self.core.github.pulls[0]["head"]["sha"] = rewritten
        # This is an intentional fixture accident in disposable Git, not a
        # fabricated native run or an action performed by the controller.
        try:
            self.core.raw(["merge-base", "--is-ancestor", self.old_head, rewritten], self.core.w1)
        except RuntimeError:
            pass
        else:
            self.fail("REFRESH_CAPABILITY: rewrite unexpectedly contains the old head")
        return rewritten

    def snapshot(self):
        return dict(state=self.core.state.read_bytes().hex(),
                    local=self.core.raw(["rev-parse", "HEAD"], self.core.w1),
                    remote=self.core.ref("refs/heads/" + self.binding["branch"]),
                    main=self.core.ref("refs/heads/main"),
                    refs=self.core.raw(["for-each-ref", "--format=%(refname) %(objectname)"], self.core.w1))

    def assert_no_extra_send_or_delete(self, start):
        self.assertEqual([], self.core.deletes)
        self.assertEqual(0, self.core.github.reruns)
        self.assertFalse(any(args[0] == "push" for args in self.core.commands[start:]))

    def held(self, action, request, marker):
        before, start = self.snapshot(), len(self.core.commands)
        result = self.core.command(action, request)
        print("LAB45_REFRESH " + json.dumps(dict(test=self.id(), action=action, result=result,
              before=before, after=self.snapshot(), controller_commands=self.core.commands[start:]), sort_keys=True), flush=True)
        self.assertEqual("hold", result["action"], marker + ": " + repr(result))
        self.assertEqual(before, self.snapshot())
        self.assert_no_extra_send_or_delete(start)
        return result

    def test_legal_forward_refresh_preserves_same_card_pr_worktree_and_readback(self):
        head = self.manual_forward()
        updated = self.core.command("update", self.request(base=self.new_base))
        self.assertEqual("updated-local", updated["action"], "LEGAL_FORWARD_REFRESH_REFUSED")
        self.binding = updated["binding"]
        published = self.core.command("publish", self.request(head=head))
        self.assertEqual("published", published["action"])
        self.binding = published["binding"]
        detail = self.core.command("ensure-pr", self.request())
        self.assertEqual("bound-pr", detail["action"])
        self.assertEqual(self.new_base, self.binding["base"])
        self.assertEqual(head, self.binding["head"])
        self.assertEqual((45, 401, str(self.core.w1)),
                         (self.binding["card"], self.binding["pr_number"], self.binding["worktree"]))
        self.assertEqual(1, self.core.github.creates)
        self.assertEqual([], self.core.deletes)

    def test_rewrite_cannot_be_adopted_by_update_even_when_remote_already_matches(self):
        self.main_forward()
        self.rewrite(self.new_base)
        self.held("update", self.request(base=self.new_base), "REWRITE_UPDATE_WAS_ACCEPTED")

    def test_remote_equal_rewrite_cannot_skip_old_head_ancestry_on_publish(self):
        rewritten = self.rewrite(self.binding["base"])
        self.held("publish", self.request(head=rewritten), "REMOTE_EQUAL_REWRITE_WAS_ADOPTED")

    def test_explicit_wrong_pr_or_wrong_worktree_binding_is_rejected(self):
        for kind in ("pr", "worktree", "head"):
            with self.subTest(kind=kind):
                request = self.request(head=self.old_head)
                if kind == "pr":
                    request["binding"]["pr_number"] = 402
                elif kind == "worktree":
                    request["binding"]["worktree"] = str(self.core.w2)
                else:
                    request["binding"]["head"] = self.binding["base"]
                self.held("publish", request, "WRONG_REFRESH_BINDING_ACCEPTED_" + kind)

    def test_old_binding_revision_cannot_authorize_a_refresh(self):
        self.main_forward()
        stale = self.request(base=self.new_base)
        # A real durable revision change with identical binding; explicit CAS
        # precondition must not be silently ignored by the refresh operation.
        with self.core.live.life.Store(self.core.state).locked() as store:
            store.save()
        self.held("update", stale, "STALE_REFRESH_REVISION_WAS_IGNORED")

    def test_pending_rerun_effect_fence_blocks_refresh(self):
        self.main_forward()
        with self.core.live.life.Store(self.core.state).locked() as store:
            row = store.find(self.binding)
            row["operations"]["rerun"] = dict(status="pending", run_id=700, from_attempt=1, head=self.old_head)
            store.save()
        self.held("update", self.request(base=self.new_base), "PENDING_RERUN_FENCE_DID_NOT_BLOCK_REFRESH")

    def test_dirty_detached_queued_or_proof_pinned_refresh_is_held(self):
        self.main_forward()
        for kind in ("dirty", "detached", "queued", "proof-pin"):
            with self.subTest(kind=kind):
                original_view = copy.deepcopy(self.core.github.view)
                try:
                    if kind == "dirty":
                        (self.core.w1 / "untracked-refresh-accident").write_text("dirty\n")
                    elif kind == "detached":
                        self.core.raw(["checkout", "--detach", self.old_head], self.core.w1)
                    elif kind == "queued":
                        self.core.github.view["queued"] = True
                    else:
                        self.core.github.view["proof_pins"] = ["synthetic active proof reference"]
                    self.held("update", self.request(base=self.new_base), "OCCUPIED_REFRESH_ACCEPTED_" + kind)
                finally:
                    self.core.github.view = original_view
                    if kind == "dirty":
                        (self.core.w1 / "untracked-refresh-accident").unlink()
                    if kind == "detached":
                        self.core.raw(["checkout", self.binding["branch"]], self.core.w1)

    def test_local_head_change_during_refresh_readback_cannot_be_recorded_as_published(self):
        # A forward local/remote head first, then actual local ref motion at the
        # occupancy read boundary. No native JSON is fabricated by this hook.
        (self.core.w1 / "synthetic.txt").write_text("forward before race\n")
        self.core.raw(["add", "synthetic.txt"], self.core.w1)
        self.core.raw(["commit", "-m", "forward before race"], self.core.w1)
        head = self.core.raw(["rev-parse", "HEAD"], self.core.w1)
        self.core.raw(["push", "origin", head + ":refs/heads/" + self.binding["branch"]], self.core.w1)
        self.core.github.pulls[0]["head"]["sha"] = head
        self.core.raw(["checkout", "--detach", self.binding["base"]], self.core.w2)
        (self.core.w2 / "synthetic-race.txt").write_text("racing ref\n")
        self.core.raw(["add", "synthetic-race.txt"], self.core.w2)
        self.core.raw(["commit", "-m", "racing ref"], self.core.w2)
        moved = self.core.raw(["rev-parse", "HEAD"], self.core.w2)
        before_state, start = self.core.state.read_bytes(), len(self.core.commands)
        original = self.core.github.retirement_snapshot
        def move(binding):
            self.core.raw(["reset", "--hard", moved], self.core.w1)
            return original(binding)
        self.core.github.retirement_snapshot = move
        result = self.core.command("publish", self.request(head=head))
        self.assertEqual("hold", result["action"], "RACING_LOCAL_HEAD_WAS_RECORDED_AS_PUBLISHED")
        self.assertEqual(before_state, self.core.state.read_bytes())
        self.assertEqual(moved, self.core.raw(["rev-parse", "HEAD"], self.core.w1))
        self.assertEqual(head, self.core.ref("refs/heads/" + self.binding["branch"]))
        self.assert_no_extra_send_or_delete(start)


    def test_wrong_explicit_binding_hash_cannot_authorize_refresh(self):
        self.main_forward()
        request = self.request(base=self.new_base)
        request["expected_binding_sha256"] = "0" * 64
        self.held("update", request, "WRONG_REFRESH_BINDING_HASH_WAS_IGNORED")

    def test_active_state_lock_prevents_any_refresh_effect(self):
        self.main_forward()
        before = self.snapshot()
        request = self.request(base=self.new_base)
        receiver, sender = multiprocessing.get_context("fork").Pipe(duplex=False)
        def child():
            sender.send(self.core.command("update", request))
        process = multiprocessing.get_context("fork").Process(target=child)
        with Path(str(self.core.state) + ".lock").open("r+") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            process.start()
            process.join(0.3)
            try:
                if process.is_alive():
                    # Actual blocking lock is safe; do not call a killed waiter
                    # a returned HOLD. No response/output is fabricated.
                    process.terminate()
                    process.join(5)
                    self.assertFalse(process.is_alive())
                else:
                    self.assertTrue(receiver.poll(1))
                    self.assertEqual("hold", receiver.recv()["action"])
                self.assertEqual(before, self.snapshot())
            finally:
                if process.is_alive():
                    process.kill()
                    process.join(5)
                fcntl.flock(lock, fcntl.LOCK_UN)
        self.assertEqual([], self.core.deletes)
        self.assertEqual(0, self.core.github.reruns)
        receiver.close()
        sender.close()


if __name__ == "__main__":
    unittest.main(verbosity=2)
