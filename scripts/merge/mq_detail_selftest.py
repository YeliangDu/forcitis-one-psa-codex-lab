"""Self-test of the failure details in the merge queue's RERUN-FAILED and HOLD log lines (#1195)."""
import mq_github
import mq_selftest as t


def make_gh():
    """The fake gh plus the log reader: failure_log maps a run id to the failing names, or to None for a log that cannot be read (built late: mq_selftest imports this file before it defines FakeGh)."""
    class Gh(t.FakeGh):
        def __init__(self):
            super().__init__()
            self.failure_log = {}

        def failure_names(self, run):
            return self.failure_log.get(str(run), [])
    return Gh()


def run(mq, d):
    # #1195: the log lines of a rerun and of the HOLD after it name the failed steps and the first failing check or test names; unreadable logs say so; the queue acts as before
    gh = make_gh(); gh.add(15, rounds=[dict(pending=0, checks=t.red(1101)), dict(pending=0, checks=t.red(1102))])
    gh.step_failures, gh.failure_log = {"1101": ["surefire"], "1102": ["surefire"]}, {"1101": ["FooIT.bar"], "1102": ["FooIT.bar", "BazTest.q"]}
    result, ev, log, _ = t.once(mq, d, "detail-names", gh, 15)
    rerun, hold = [l for l in log.lines if " RERUN-FAILED " in l][0], [l for l in log.lines if " HOLD " in l][0]
    assert result == "HOLD" and ev.count("RERUN-FAILED") == 1 and ("rerun", "1101") in gh.calls, (result, ev)
    assert rerun.endswith(" | steps: surefire | failed: FooIT.bar") and hold.endswith(" | steps: surefire | failed: FooIT.bar, BazTest.q"), (rerun, hold)
    gh = make_gh(); gh.add(16, rounds=[dict(pending=0, checks=t.red(1201)), dict(pending=0, checks=t.red(1202))])
    gh.step_failures, gh.failure_log = {"1201": ["surefire"], "1202": ["surefire"]}, {"1201": None, "1202": None}
    result, ev, log, _ = t.once(mq, d, "detail-expired", gh, 16)
    assert result == "HOLD" and ev.count("RERUN-FAILED") == 1 and " | steps: surefire | names not available" in [l for l in log.lines if " HOLD " in l][0], log.lines
    gh = make_gh(); gh.add(17, rounds=[dict(pending=0, checks=t.red(1301)), dict(pending=0, checks=t.red(1302))]); gh.failed_steps = lambda run: None
    gh.failure_log = {"1301": None, "1302": None}
    result, ev, log, _ = t.once(mq, d, "detail-none", gh, 17)
    assert result == "HOLD" and [l for l in log.lines if " HOLD " in l][0].endswith(" | details not available"), log.lines
    gh = make_gh(); gh.add(18, rounds=[dict(pending=0, checks=t.red(1401)), dict(pending=0, checks=t.red(1402))])
    gh.failure_names = lambda run: 1 / 0   # a reader that breaks must not stop the queue
    result, ev, log, _ = t.once(mq, d, "detail-raises", gh, 18)
    assert result == "HOLD" and [l for l in log.lines if " HOLD " in l][0].endswith(" | details not available"), log.lines
    log_text = "\n".join(["job\tstep\t2026-11-04T01:02:03.4Z FAIL  glossary term rules   3 s（退出 1）", "job\tstep\t2026-11-04T01:02:03.4Z [ERROR]   OrgIT.merge:42 expected 3 but was 4 " + "x" * 500,
                         "job\tstep\t2026-11-04T01:02:03.4Z [ERROR]   OrgIT.merge:42 again", "job\tstep\t2026-11-04T01:02:03.4Z some long output line with password=hunter2 and row data", "job\tstep\t2026-11-04T01:02:03.4Z In scripts/gates/install.sh line 14:", "job\tstep\t2026-11-04T01:02:03.4Z In scripts/gates/install.sh line 22:", "[ERROR]   AaaTest", "[ERROR]   BbbTest", "[ERROR]   CccTest", "[ERROR]   DddTest"])
    names = mq_github.failure_names(log_text)
    assert names == ["glossary term rules", "OrgIT.merge", "shellcheck scripts/gates/install.sh", "AaaTest"], names
    long = mq.failure_detail(type("G", (), {"failure_names": staticmethod(lambda run: ["n" * 70] * 4 + ["m" * 70])})(), ["1"], {"1": ["s" * 300]})
    assert len(long) <= 400 and "hunter2" not in long, len(long)
    # the real Gh against the fake gh binary: a readable log gives the names, an expired log None (not an empty list)
    t.fake_gh(d, [(["run", "view", "7", "--log-failed"], "j\ts\t2026-11-04T01:02:03Z FAIL  glossary term rules   3 s（退出 1）\n", "", 0), (["run", "view", "8", "--log-failed"], "", "log expired", 1)])
    real = mq.Gh()
    assert real.failure_names(7) == ["glossary term rules"] and real.failure_names(8) is None
