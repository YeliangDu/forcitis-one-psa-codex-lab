import importlib
import json
import frozen_interrupted as baseline

class CleanReplacementAcceptance(baseline.InterruptedAcceptance):
    def refuse(self,function,store,auth,*args):
        before=self.snapshot(store)
        with self.assertRaises(ValueError):
            function(store,auth,*args)
        self.assertEqual(before,self.snapshot(store))

    def replacement_seams(self):
        module = None
        if importlib.util.find_spec("role_interrupted") is not None:
            module = importlib.import_module("role_interrupted")
        functions = []
        for name in ("prepare_replacement", "bind_replacement"):
            function = getattr(module, name, None)
            self.assertTrue(
                callable(function),
                "INTERRUPTED_REPLACEMENT_CAPABILITY_MISSING:" + name,
            )
            functions.append(function)
        return tuple(functions)

    def reconciled_and_fenced(self):
        reconcile = self.seam()
        module = importlib.import_module("role_interrupted")
        fence = getattr(module, "fence_interrupted", None)
        self.assertTrue(callable(fence), "INTERRUPTED_FENCE_CAPABILITY_MISSING")
        store, auth, arguments, raw = self.fixture()
        observation = reconcile(store, auth, arguments, raw)
        fence(
            store, auth,
            dict(arguments, observation_seq=observation["observation_seq"]),
            raw,
        )
        return store, auth, arguments, raw

    def test_07_replacement_requires_interrupted_fence_and_current_identity(self):
        prepare, _ = self.replacement_seams()

        store, auth, arguments, raw = self.fixture()
        self.refuse(prepare, store, auth, arguments, raw, 2)

        # An interruption observation alone cannot authorize replacement.
        store, auth, arguments, raw = self.fixture()
        self.seam()(store, auth, arguments, raw)
        self.refuse(prepare, store, auth, arguments, raw, 2)

        for mutation in (
            "unpaused", "owner-client", "owner-generation", "owner-token",
            "old-generation", "old-epoch", "old-thread", "old-turn",
            "arguments-revision", "arguments-thread", "arguments-turn",
            "arguments-effect", "same-epoch", "fake-completed",
        ):
            with self.subTest(mutation=mutation):
                store, auth, arguments, raw = self.reconciled_and_fenced()
                epoch = 2
                if mutation == "unpaused":
                    store.db.execute("UPDATE owner SET paused=0")
                elif mutation == "owner-client":
                    auth["client"] = "wrong-owner"
                elif mutation == "owner-generation":
                    auth["generation"] = 0
                elif mutation == "owner-token":
                    auth["token"] = "wrong-token"
                elif mutation == "old-generation":
                    store.db.execute(
                        "UPDATE native_dispatch SET generation=0"
                    )
                elif mutation == "old-epoch":
                    store.db.execute("UPDATE native_dispatch SET epoch=9")
                elif mutation == "old-thread":
                    store.db.execute(
                        "UPDATE role_registration SET session_id='wrong'"
                    )
                elif mutation == "old-turn":
                    store.db.execute(
                        "UPDATE native_dispatch SET turn_id='wrong'"
                    )
                elif mutation.startswith("arguments-"):
                    key = mutation.removeprefix("arguments-")
                    arguments[key] = 2 if key == "revision" else "wrong"
                    if key == "thread":
                        arguments["thread_id"] = arguments.pop("thread")
                    elif key == "turn":
                        arguments["turn_id"] = arguments.pop("turn")
                    elif key == "effect":
                        arguments["effect_id"] = arguments.pop("effect")
                elif mutation == "same-epoch":
                    epoch = 1
                else:
                    raw = rollout([dict(type="task_complete", turn_id=TURN)])
                    arguments["rollout_sha256"] = digest(raw)
                self.refuse(
                    prepare, store, auth, arguments, raw, epoch
                )

    def test_08_replacement_archives_reserves_and_binds_once(self):
        prepare, bind = self.replacement_seams()
        store, auth, arguments, raw = self.reconciled_and_fenced()
        new_thread = "33333333-3333-4333-8333-333333333333"
        receipt_id = "synthetic-thread-start-receipt"
        epoch = 2

        self.refuse(
            bind, store, auth, arguments, epoch, new_thread, receipt_id
        )
        original_registration = dict(store.db.execute(
            "SELECT * FROM role_registration WHERE id=1"
        ).fetchone())
        before = self.snapshot(store)
        prepared = prepare(store, auth, arguments, raw, epoch)
        self.assertEqual(
            prepared["action"], "interrupted-replacement-reserved"
        )
        reserved = self.snapshot(store)
        self.assertNotEqual(before, reserved, "RESERVATION_NOT_DURABLE")
        self.assertEqual(
            reserved["events"][:len(before["events"])], before["events"]
        )
        archives = [
            json.loads(row[0]) for row in store.db.execute(
                "SELECT body FROM events WHERE kind=? ORDER BY seq",
                ("role-registration-archived",),
            )
        ]
        self.assertEqual(len(archives), 1)
        self.assertEqual(
            archives[0]["registration"], original_registration
        )
        self.assertEqual(store.db.execute(
            "SELECT phase FROM role_registration WHERE id=1"
        ).fetchone()[0], "retired")
        self.refuse(prepare, store, auth, arguments, raw, epoch)
        self.refuse(prepare, store, auth, arguments, raw, epoch + 1)

        for bad_epoch, bad_thread, bad_receipt, bad_auth in (
            (epoch + 1, new_thread, receipt_id, auth),
            (epoch, THREAD, receipt_id, auth),
            (epoch, new_thread, "", auth),
            (epoch, new_thread, receipt_id, dict(auth, generation=0)),
        ):
            with self.subTest(
                epoch=bad_epoch, thread=bad_thread,
                receipt=bad_receipt, generation=bad_auth["generation"],
            ):
                self.refuse(
                    bind, store, bad_auth, arguments,
                    bad_epoch, bad_thread, bad_receipt,
                )

        bound = bind(
            store, auth, arguments, epoch, new_thread, receipt_id
        )
        self.assertEqual(bound["action"], "interrupted-replacement-bound")
        rows = store.db.execute(
            "SELECT * FROM role_registration"
        ).fetchall()
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["id"], 1)
        self.assertEqual(rows[0]["session_id"], new_thread)
        self.assertEqual(rows[0]["phase"], "provisional")
        self.assertIsNone(rows[0]["first_turn_id"])
        self.assertIsNone(rows[0]["readback_sha256"])

        after = self.snapshot(store)
        for table in (
            "owner", "tasks", "turns", "effects", "native_dispatch",
            "native_tool_operations", "waits",
        ):
            self.assertEqual(before[table], after[table], table)
        self.assertEqual(
            after["events"][:len(reserved["events"])], reserved["events"]
        )
        self.assertEqual(store.db.execute(
            "SELECT paused FROM owner WHERE id=1"
        ).fetchone()[0], 1)
        self.assertEqual(store.db.execute(
            "SELECT issue_count FROM effects WHERE effect_id=?", (EFFECT,)
        ).fetchone()[0], 1)
        self.refuse(
            bind, store, auth, arguments, epoch, new_thread, receipt_id
        )
        self.refuse(prepare, store, auth, arguments, raw, epoch + 1)
