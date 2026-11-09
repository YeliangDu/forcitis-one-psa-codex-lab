import copy
import importlib
import importlib.util
import unittest


MAIN = "a" * 40


class SuccessorCardAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("successor_card") is not None:
            module = importlib.import_module("successor_card")
        function = getattr(module, "decide", None)
        self.assertTrue(
            callable(function), "SUCCESSOR_CARD_CAPABILITY_MISSING"
        )
        return function

    def fixture(self):
        trigger = dict(
            kind="card-completed",
            card=69,
            merge_sha=MAIN,
        )
        candidate = dict(
            card=67,
            refresh_required=True,
            pr=68,
            author_session="11111111-1111-4111-8111-111111111111",
            acceptance_sha256="b" * 64,
            signature=dict(
                card=67, base=MAIN, status="signed",
                expires_at="2030-01-01T12:00:00Z",
            ),
        )
        state = dict(
            now="2030-01-01T11:59:59Z",
            main=MAIN,
            health=dict(action="healthy", head_sha=MAIN),
            queue=[],
            paused=False,
            resume=None,
            dispatch_effect=None,
        )
        return trigger, [candidate], state

    def check(self, function, trigger, candidates, state, expected):
        before = copy.deepcopy((trigger, candidates, state))
        self.assertEqual(function(trigger, candidates, state), expected)
        self.assertEqual(
            (trigger, candidates, state), before,
            "SUCCESSOR_INPUT_OR_EXISTING_CARD_MUTATED",
        )

    def test_01_unique_current_signed_refresh_is_selected(self):
        function = self.seam()
        trigger, candidates, state = self.fixture()
        self.check(
            function, trigger, candidates, state,
            dict(action="DISPATCH", card=67),
        )

        # Unrelated unsigned work must not create a second eligible candidate.
        other = copy.deepcopy(candidates[0])
        other["card"] = other["signature"]["card"] = 70
        other["signature"]["status"] = "unsigned"
        candidates.append(other)
        self.check(
            function, trigger, candidates, state,
            dict(action="DISPATCH", card=67),
        )

    def test_02_stale_ambiguous_or_unhealthy_state_holds(self):
        function = self.seam()
        for mutation in (
            "wrong-trigger", "wrong-merge", "unhealthy", "stale-health",
            "queue", "unsigned", "expired", "wrong-base",
            "signature-card", "not-refresh", "none", "multiple",
        ):
            with self.subTest(mutation=mutation):
                trigger, candidates, state = self.fixture()
                candidate = candidates[0]
                if mutation == "wrong-trigger":
                    trigger["card"] = 68
                elif mutation == "wrong-merge":
                    trigger["merge_sha"] = "c" * 40
                elif mutation == "unhealthy":
                    state["health"]["action"] = "hold"
                elif mutation == "stale-health":
                    state["health"]["head_sha"] = "c" * 40
                elif mutation == "queue":
                    state["queue"] = [70]
                elif mutation == "unsigned":
                    candidate["signature"]["status"] = "unsigned"
                elif mutation == "expired":
                    state["now"] = candidate["signature"]["expires_at"]
                elif mutation == "wrong-base":
                    candidate["signature"]["base"] = "c" * 40
                elif mutation == "signature-card":
                    candidate["signature"]["card"] = 70
                elif mutation == "not-refresh":
                    candidate["refresh_required"] = False
                elif mutation == "none":
                    candidates.clear()
                else:
                    other = copy.deepcopy(candidate)
                    other["card"] = other["signature"]["card"] = 70
                    candidates.append(other)
                self.check(
                    function, trigger, candidates, state,
                    dict(action="HOLD", card=None),
                )

    def test_03_manual_pause_requires_explicit_current_card_resume(self):
        function = self.seam()
        for resume in (
            None,
            dict(card=70, base=MAIN, explicit=True),
            dict(card=67, base="c" * 40, explicit=True),
            dict(card=67, base=MAIN, explicit=False),
        ):
            with self.subTest(resume=resume):
                trigger, candidates, state = self.fixture()
                state.update(paused=True, resume=resume)
                self.check(
                    function, trigger, candidates, state,
                    dict(action="HOLD", card=None),
                )

        trigger, candidates, state = self.fixture()
        state.update(
            paused=True,
            resume=dict(card=67, base=MAIN, explicit=True),
        )
        self.check(
            function, trigger, candidates, state,
            dict(action="DISPATCH", card=67),
        )

    def test_04_consumed_dispatch_only_reads_back_on_repeat_or_restart(self):
        function = self.seam()
        for phase in ("issued", "unknown", "observed"):
            with self.subTest(phase=phase):
                trigger, candidates, state = self.fixture()
                state["dispatch_effect"] = dict(
                    effect_id="synthetic-successor-effect",
                    trigger_card=69,
                    trigger_merge=MAIN,
                    card=67,
                    base=MAIN,
                    issue_count=1,
                    phase=phase,
                )
                expected = dict(action="READBACK", card=67)
                self.check(function, trigger, candidates, state, expected)
                self.check(
                    function,
                    copy.deepcopy(trigger),
                    copy.deepcopy(candidates),
                    copy.deepcopy(state),
                    expected,
                )
                # Recovery cannot redispatch after eligibility changes.
                candidates[0]["signature"]["status"] = "expired"
                state["queue"] = [67]
                self.check(function, trigger, candidates, state, expected)

        trigger, candidates, state = self.fixture()
        state["dispatch_effect"] = dict(
            effect_id="other-effect",
            trigger_card=69, trigger_merge=MAIN,
            card=70, base=MAIN, issue_count=1, phase="unknown",
        )
        self.check(
            function, trigger, candidates, state,
            dict(action="HOLD", card=None),
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
