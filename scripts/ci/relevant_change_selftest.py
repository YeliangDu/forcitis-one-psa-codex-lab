"""Self-test of relevant_change.py (run by `relevant_change.py --self-test`, which passes in its own names: importing it would run its main()).
Kept beside it so the judge stays small; relevant_change.py skips this file when it scans sources, since its fixtures name docs paths."""
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

rc = None   # the names of relevant_change.py under test, set by self_test


def mssql_inputs_self_test():
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        base = rc.TEST_ROOT + "com/forcitis/one/acceptance/platform/integration/"
        moved = base + "MovedIT.java"
        files = {
            "backend/pom.xml": '<project xmlns="http://maven.apache.org/POM/4.0.0"><properties><legacy.it.acceptance>'
                               + moved[len(rc.TEST_ROOT):] + '</legacy.it.acceptance></properties></project>',
            moved: 'class MovedIT extends WorldSupport {}',
            base + "WorldSupport.java": 'import static com.forcitis.one.fixture.Fixture.value;\nclass WorldSupport { Object x = OtherHelper.x; }',
            base + "OtherHelper.java": 'class OtherHelper { Object x = Path.of("../docs/modernization/phase1a/SEEYON_VIEWS.sql"); Object r = Path.of("architecture/external-read-registry.yaml"); }',
            rc.TEST_ROOT + "com/forcitis/one/fixture/Fixture.java": 'import com.forcitis.one.kernel.EmployeeId;\nclass Fixture { Object x = db.withInitScript("init-schemas.sql"); }',
            "backend/src/main/java/com/forcitis/one/kernel/EmployeeId.java": "record EmployeeId() {}",
            rc.TEST_ROOT + "com/forcitis/one/platform/integration/ExistingMssqlIT.java": "class ExistingMssqlIT {}",
        }
        for path, content in files.items():
            target = root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        found = set(rc.mssql_it_inputs(root))
        expected = set(files) - {"backend/pom.xml"}
        expected |= {"backend/src/test/resources/init-schemas.sql", "docs/modernization/phase1a/SEEYON_VIEWS.sql", "backend/architecture/external-read-registry.yaml"}
        assert found == expected, f"SQL Server input scan lost tests, inherited/local/imported helpers, static imports or fixtures: {expected - found}; extra={found - expected}"


def tiers_self_test(root):
    def write(base, rel, text):
        target = Path(base, rel)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")

    with tempfile.TemporaryDirectory() as tmp:
        t = "backend/src/test/java/com/forcitis/one/"
        write(tmp, t + "LightReadTest.java", 'class LightReadTest { Path p = Path.of("docs/modernization/events/light.yaml"); }')
        write(tmp, t + "HeavyReadIT.java", 'class HeavyReadIT { String p = "docs/modernization/events/heavy.yaml"; }')
        write(tmp, t + "SpringReadTest.java", '@SpringBootTest class SpringReadTest { String p = "docs/modernization/events/spring.yaml"; }')
        write(tmp, t + "Helper.java", 'class Helper { static String p = "docs/modernization/events/helper.yaml"; }')
        write(tmp, t + "UserOneTest.java", "class UserOneTest { Object h = Helper.p; }")
        write(tmp, t + "UserTwoTest.java", "class UserTwoTest { Object h = Helper.p; }")
        write(tmp, t + "HeavyHelper.java", 'class HeavyHelper { static String p = "docs/modernization/events/heavyhelper.yaml"; }')
        write(tmp, t + "HeavyUserIT.java", "class HeavyUserIT { Object h = HeavyHelper.p; }")
        write(tmp, t + "NoUserHelper.java", 'class NoUserHelper { static String p = "docs/modernization/events/nouser.yaml"; }')
        write(tmp, "backend/src/main/java/com/forcitis/one/Main.java", 'class Main { String p = "docs/modernization/events/main.yaml"; }')
        write(tmp, t + "CommentOnlyTest.java", 'class CommentOnlyTest {\n // reads docs/modernization/events/comment.yaml in theory\n /* and comment.yaml again */ }')
        write(tmp, t + "ByNameIT.java", 'class ByNameIT { String dir = base(); String f = dir + "/byname.yaml"; }')
        write(tmp, t + "ByNameTest.java", 'class ByNameTest { String f = "docs/modernization/events/byname.yaml"; }')
        write(tmp, "frontend/scripts/copy-glossary.mjs", 'const p = "docs/glossary/front.yaml";')
        write(tmp, "frontend/src/app.ts", 'const p = "docs/glossary/app.yaml";')
        write(tmp, "scripts/release/gate.py", 'P = "docs/ops/runbooks/sql/"')
        write(tmp, "scripts/other/tool.py", 'P = "docs/modernization/events/tool.yaml"')
        write(tmp, "scripts/ci/light.sh", "cat docs/modernization/events/only-light.yaml")
        write(tmp, "scripts/hooks/h.py", 'P = "docs/modernization/events/only-hook.yaml"; G = "docs/glossary/"')  # a local tool naming a whole directory hides no reader
        write(tmp, "scripts/gates/g.py", 'P = "docs/modernization/events/only-gate.yaml"; G = "docs/glossary/"')  # the local gates: no reader either
        write(tmp, "scripts/guards/db_scripts_aligned.py", 'P = "docs/modernization/events/always.yaml"')
        write(tmp, t + "StarGlobIT.java", 'class StarGlobIT { String g = "glob:**/*.java"; String p = "docs/modernization/events/after-star.yaml"; } /** */')
        write(tmp, "frontend/scripts/generate-api.mjs", 'const p = "docs/glossary/gen.yaml";')
        write(tmp, t + "GlossaryRelatedTest.java", 'class GlossaryRelatedTest { String p = "docs/glossary/terms.yaml"; }')
        write(tmp, t + "ApplicationIntegrationTest.java", '@SpringBootTest class ApplicationIntegrationTest { String p = "docs/glossary/terms.yaml"; }')
        ev = "docs/modernization/events/"
        gl = "docs/glossary/"

        def tier(*paths):
            return rc.tier_of(list(paths), tmp)

        def classes(*paths):
            kind, plan = tier(*paths)
            assert kind == "guarded", (paths, kind)
            return sorted(plan["classes"])

        assert classes(ev + "light.yaml") == ["LightReadTest"]
        assert tier(ev + "heavy.yaml") == ("code", None), "an IT reads it"
        assert tier(ev + "spring.yaml") == ("code", None), "a Spring test reads it"
        assert classes(ev + "helper.yaml") == ["UserOneTest", "UserTwoTest"], "a helper is covered by the tests that use it"
        assert tier(ev + "heavyhelper.yaml") == ("code", None), "a helper used by an IT"
        assert tier(ev + "nouser.yaml") == ("code", None), "a helper nobody uses: unknown, so everything runs"
        assert tier(ev + "main.yaml") == ("code", None), "main code reads it"
        assert tier(ev + "comment.yaml") == ("code", None), "no reader found: everything runs"
        assert tier(ev + "byname.yaml") == ("code", None), "another reader names the file without the full path"
        assert tier(ev + "tool.yaml") == ("code", None), "a script outside the covered ones reads it"
        assert tier(ev + "only-light.yaml") == ("code", None), "light.sh is a CI script outside the middle tier: a doc only it reads runs everything"
        assert tier(ev + "only-hook.yaml") == ("code", None)
        assert tier(ev + "only-gate.yaml") == ("code", None), "a doc named only by scripts/gates/ has no reader"
        assert tier(ev + "always.yaml") == ("docs", None), "read only by a script that runs in every job"
        assert tier(ev + "after-star.yaml") == ("code", None), "a /* inside a string literal must not hide what follows"
        assert tier(gl + "gen.yaml") == ("code", None), "a frontend script that no guarded pnpm script runs"
        assert rc.strip_code_comments('a "x/*y" // c\nb /* c */ "d" \'e//f\'') == 'a "x/*y"  \nb   "d" \'e//f\''
        write(tmp, t + "WalkIT.java", 'class WalkIT { Object w = Files.walk(Path.of("..", "docs")); }')
        assert tier(ev + "light.yaml") == ("code", None), "a reader that walks docs: any doc may be read"
        Path(tmp, t + "WalkIT.java").unlink()
        with tempfile.TemporaryDirectory() as tmp2:
            write(tmp2, t + "SegDirTest.java", 'class SegDirTest { Path d = Path.of("docs", "modernization", "phase1a", "infra", "runbooks", "sql"); }')
            assert rc.tier_of(["docs/ops/runbooks/sql/zz.md"], tmp2)[1]["classes"] == {"SegDirTest"}, "a directory named by path segments"
        write(tmp, t + "EventsApplicationIntegrationTest.java", "x")
        write(tmp, t + "ApplicationIntegrationTest.java", '@SpringBootTest class ApplicationIntegrationTest { String p = "docs/glossary/terms.yaml"; String q = "docs/modernization/events/other.yaml"; }')
        rc.EXEMPT_HEAVY_READERS[(gl, "ApplicationIntegrationTest")] = "test only"
        try:
            assert tier(ev + "other.yaml") == ("code", None), "the exemption is limited to its doc prefix"
        finally:
            rc.EXEMPT_HEAVY_READERS.clear()
        kind, plan = tier(gl + "front.yaml")
        assert kind == "guarded" and plan["frontend"] and not plan["classes"]
        assert tier(gl + "app.yaml") == ("code", None), "a frontend source outside frontend/scripts reads it"
        kind, plan = tier("docs/ops/runbooks/sql/x.md")
        assert kind == "guarded" and plan["release"]
        assert tier(gl + "terms.yaml") == ("code", None), "a heavy test reads it and no exemption is approved"
        rc.EXEMPT_HEAVY_READERS[(gl, "ApplicationIntegrationTest")] = "test only"
        try:
            kind, plan = tier(gl + "terms.yaml")
            assert kind == "guarded" and plan["classes"] == {"GlossaryRelatedTest"} and plan["exempt"] == {"ApplicationIntegrationTest"}, (kind, plan)
        finally:
            rc.EXEMPT_HEAVY_READERS.clear()
        # mixed changes
        assert tier(ev + "light.yaml", "backend/pom.xml") == ("code", None)
        assert tier(ev + "light.yaml", "docs/adr/ADR-0001.md")[0] == "guarded"
        assert tier(ev + "light.yaml", ev + "heavy.yaml") == ("code", None)
        assert tier("docs/adr/ADR-0001.md", "README.md") == ("docs", None)
        assert tier(gl + "issues/x.md") == ("docs", None)
        assert tier() == ("docs", None)
        lines = rc.tier_lines(*tier(ev + "helper.yaml"))
        assert lines[0] == "tier=guarded" and lines[1] == "guard_classes=UserOneTest,UserTwoTest" and lines[2:] == ["guard_frontend=false", "guard_release=false"], lines
        assert rc.tier_lines("code", None) == ["tier=code", "guard_classes=", "guard_frontend=false", "guard_release=false"]
        # the exemption list: a stale entry (class gone, or no longer a reader) must be noticed
        index, light = rc.ReaderIndex(root), rc.light_class_names(root)
        for (prefix, cls), why in rc.EXEMPT_HEAVY_READERS.items():
            assert why.strip(), f"exemption {cls} needs a reason"
            files = [rel for rel in index.texts if rel.endswith(f"/{cls}.java")]
            assert files, f"exemption {prefix} {cls}: no such class any more"
            assert cls not in light, f"{cls} is a light test now: drop the exemption"
            reached = {k for r in index.readers(prefix + "terms.yaml") for k in (rc.classify_reader(r, prefix + "terms.yaml", index, light) or [])}
            assert ("exempt", cls) in reached, f"exemption {prefix} {cls} is never reached: remove it"


def rename_self_test():
    """A doc moved out from under a read path must still list the path it left (git diff rename detection shows only the new path)."""
    env = {**os.environ, "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}
    with tempfile.TemporaryDirectory() as tmp:
        def git(*args):
            return subprocess.run(["git", *args], cwd=tmp, capture_output=True, text=True, check=True, env=env).stdout
        git("init", "-q", "-b", "main")
        old = Path(tmp, "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md")
        old.parent.mkdir(parents=True)
        old.write_text("# runbook\n" * 20, encoding="utf-8")
        git("add", "-A"); git("-c", "commit.gpgsign=false", "commit", "-q", "-m", "base")
        git("update-ref", "refs/remotes/origin/main", "HEAD")
        base = git("rev-parse", "HEAD").strip()
        new = Path(tmp, "docs/other/INFRA_RUNBOOK_OLD.md")
        new.parent.mkdir(parents=True)
        git("mv", str(old), str(new)); git("-c", "commit.gpgsign=false", "commit", "-q", "-m", "move")
        cwd = os.getcwd()
        try:
            os.chdir(tmp)
            assert sorted(rc.changed_files("main")) == ["docs/other/INFRA_RUNBOOK_OLD.md", "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md"], rc.changed_files("main")
            assert sorted(rc.files_since(base)) == sorted(rc.changed_files("main"))
        finally:
            os.chdir(cwd)


def real_tiers_self_test(root):
    """Every doc the builds read: a tier that covers every reader found, or the full run. Nothing the scan cannot place is left to the cheap tier."""
    index, light = rc.ReaderIndex(root), rc.light_class_names(root)
    tracked = subprocess.run([*rc.GIT, "ls-files", "docs"], cwd=root, capture_output=True, text=True, encoding="utf-8", check=True).stdout.splitlines()
    docs = [p for p in tracked if rc.is_guarded_doc(p)]
    assert docs, "no tracked doc under rc.READ_BY_BUILDS"
    assert sorted(index.wide_ignored) == sorted(rc.WIDE_NOT_READERS), f"rc.WIDE_NOT_READERS lists files that no longer match: {sorted(set(rc.WIDE_NOT_READERS) - set(index.wide_ignored))}"
    assert not index.wide, "sources that build docs paths in a way the scan cannot read (they may read any doc): " + ", ".join(index.wide)
    package = Path(root, "frontend/package.json").read_text(encoding="utf-8")
    assert "scripts/check-copy-glossary.mjs" in package and "scripts/*.test.mjs" in package, "frontend/package.json no longer runs the scripts listed in rc.GUARDED_FRONTEND"
    for rel in rc.GUARDED_FRONTEND:
        assert rel in index.texts, f"{rel} is listed in rc.GUARDED_FRONTEND but does not exist"
    assert "copy-glossary" in index.texts["frontend/scripts/check-copy-glossary.mjs"] and "copy-glossary" in index.texts["frontend/scripts/copy-glossary.test.mjs"]
    for entry in rc.READ_BY_BUILDS:
        assert index.readers(entry), f"rc.READ_BY_BUILDS lists {entry}, but no source reads it (stale entry?)"
    for entry in rc.READ_BY_ALWAYS_RUN_CHECKS:
        tests = sorted(r for r in index.readers(entry) if r.startswith(("backend/", "frontend/")))
        assert not tests, f"{entry} is read by {tests}: a build reads it, so it belongs in rc.READ_BY_BUILDS"
    assert rc.plan_for(["docs/ops/runbooks/sql/prod-db-verify.sql"], index, light) is None, "DataArchitectureTest (a container test) runs it: full run"
    guarded = 0
    for path in docs:
        plan = rc.plan_for([path], index, light)
        if plan is None:
            continue
        guarded += 1
        for rel in index.readers(path):
            assert rc.classify_reader(rel, path, index, light) is not None
            assert not rel.startswith("backend/src/main/"), f"{path} is read by main code {rel}"
            assert rel in rc.ALWAYS_RUN_READERS or rel in rc.GUARDED_FRONTEND or rel.startswith((rc.TEST_ROOT, "scripts/release/")), f"{path}: reader {rel} is not one the middle tier runs"
    assert guarded, "every doc went to the full run: the middle tier is empty"
    expected = {
        "docs/modernization/phase1a/infra/INFRA_RUNBOOK.md": {"ReadOnlyCheckSwitchGuardTest"},
        "docs/data/evidence/dba-handoff/credential-columns.csv": {"CredentialColumnGuardTest"},
    }
    for path, classes in expected.items():
        plan = rc.plan_for([path], index, light)
        assert plan is not None and classes <= plan["classes"], f"{path} must trigger {sorted(classes)}, plans {plan}"
    assert rc.plan_for(["docs/modernization/events/asyncapi.yaml"], index, light) is None, "ITs read the event catalogue: full run"
    for path in ("docs/glossary/terms.yaml", "docs/glossary/bcs.yaml"):
        assert rc.plan_for([path], index, light) is None, f"{path}: a heavy test (ApplicationIntegrationTest) reads it and no exemption is approved: full run"
    assert rc.plan_for(["docs/ops/deploy-gate.json"], index, light)["release"]


def self_test(module):
    global rc
    rc = module
    root = Path(rc.__file__).resolve().parents[2]
    light = "\n".join(Path(root, "scripts/ci", f).read_text(encoding="utf-8") for f in ("light.sh", "light_triggers.sh"))
    named = [p for p in rc.ALWAYS_RUN_READERS if p in light]
    for entry in rc.ALWAYS_RUN_READERS:  # uncovered_reads trusts this table: light.sh runs each entry, or a script it runs names the entry
        stem = Path(entry).stem
        assert entry in named or any(re.search(rf"\b{re.escape(stem)}\b", Path(root, p).read_text(encoding="utf-8")) for p in named), f"rc.ALWAYS_RUN_READERS lists {entry}, but scripts/ci/light.sh does not run it"
    for path in (
        "backend/src/main/resources/application.properties",
        "backend/src/main/resources/application-read-only-verification.properties",
        "backend/src/main/resources/application-read-only-verification.yml",
        "backend/src/main/resources/application-read-only-verification.yaml",
        "backend/src/main/resources/application-prod.properties",
        "backend/src/main/resources/config/platform_integration.properties",
        "backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql",
        "backend/src/main/resources/db/migration/organization_workforce/V20261014000000__organization_workforce_baseline.sql",
        "backend/src/main/java/com/forcitis/one/organization/workforce/application/ApplySourceSnapshot.java",
        "scripts/guards/ci_git.py",
    ):
        assert rc.is_legacy_it(path), f"QUAL-CARD-H-R2-3: direct SQL Server input must trigger legacy-it: {path}"
    scheduled = subprocess.run([sys.executable, str(Path(rc.__file__).resolve()), "main"],
                               env={**os.environ, "GITHUB_EVENT_NAME": "schedule", "GITHUB_OUTPUT": os.devnull},
                               capture_output=True, text=True, check=True)
    assert "legacy_it=true" in scheduled.stdout.splitlines(), "QUAL-CARD-H-R2-7: schedule must always run SQL Server"
    mssql_inputs_self_test()
    unlisted = rc.uncovered_mssql_inputs(root)
    assert not unlisted, "files the SQL Server tests or their helpers import or read are not in LEGACY_IT_PATHS (add them in scripts/ci/relevant_change.py):\n  " + "\n  ".join(unlisted)
    for path in rc.legacy_acceptance_paths(root):
        assert rc.is_legacy_it(rc.TEST_ROOT + path), f"legacy.it.acceptance class does not trigger legacy-it: {path}"
    tiers_self_test(root)
    rename_self_test()
    real_tiers_self_test(root)
    missing = rc.uncovered_reads(root)
    assert not missing, "builds read docs paths that rc.READ_BY_BUILDS does not list or NOT_READ_BY_BUILDS excludes (fix the lists in scripts/ci/relevant_change.py):\n  " + "\n  ".join(missing)
    assert rc.is_relevant("backend/pom.xml")
    assert rc.is_relevant(".github/workflows/ci.yml")
    assert rc.is_relevant("scripts/ci/relevant_change.py")
    assert rc.is_relevant("docs/glossary/terms.yaml")
    assert rc.is_relevant("docs/modernization/events/asyncapi.yaml")
    assert not rc.is_relevant("docs/glossary/issues/2026-10-15-120000-one-ops-x.md")
    assert not rc.is_relevant("docs/glossary/decision-sheets/2026-10-20.md")
    assert rc.is_covered("docs/glossary/terms.yaml")
    assert not rc.is_covered("docs/glossary/issues/x.md"), "a build reading NOT_READ_BY_BUILDS must fail the scan"
    assert not rc.is_covered("docs/glossary/issues")
    assert rc.is_covered("docs/rules/rulings/acceptance-rulings.yaml")
    assert rc.is_relevant("docs/modernization/phase1a/SEEYON_VIEWS.sql")
    assert rc.is_relevant("docs/ops/deploy-gate.json")
    assert not rc.is_relevant("docs/adr/ADR-0001.md")
    assert not rc.is_relevant("docs/legacy/psa-external-interfaces.md")
    assert not rc.is_relevant("README.md")
    assert not rc.is_relevant("docs/decisions/2026-10-15-120000-one-ops-中文.md")
    assert rc.is_relevant('"docs/q\\"x.md"'), "a path git still quotes must count as relevant"
    assert rc.is_relevant("docs/ops/runbooks/sql/dev-db.sql"), "DataArchitectureTest executes the verify scripts here"
    assert rc.is_relevant("notes.txt")
    assert not rc.is_relevant("tools/docs/new_adr.py"), "a hand-run tool whose self-test light.sh runs"
    assert rc.is_relevant("tools/ops/runner_rebuild.sh"), "the release job shellchecks tools/ops on relevant pull requests"
    assert rc.is_relevant("tools/evidence/evidence.py")
    assert rc.is_relevant("docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml")
    assert rc.is_relevant("docs/modernization/phase1a/infra/one_db_roles_schemas.sql")
    assert rc.any_relevant(["docs/rules/coding-standard.md", "docs/glossary/terms.yaml"])
    assert not rc.any_relevant(["docs/rules/coding-standard.md", "CLAUDE.md", ""])
    assert not rc.any_relevant([])
    assert rc.is_legacy_it("backend/src/main/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViews.java")
    assert rc.is_legacy_it("backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/LegacyInboxConfiguration.java")
    assert rc.is_legacy_it("backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/seeyon/SeeyonViewsMssqlIT.java")
    assert rc.is_legacy_it("backend/src/test/java/com/forcitis/one/platform/approval/infrastructure/legacy/LegacyInboxAccessIT.java")
    assert rc.is_legacy_it("backend/src/test/java/com/forcitis/one/acceptance/platform/integration/StalledExternalSourceIT.java")
    assert rc.is_legacy_it("backend/architecture/test-images.txt")
    assert rc.is_legacy_it("backend/src/test/java/com/forcitis/one/TestMssql.java")
    assert rc.is_legacy_it("backend/architecture/external-read-registry.yaml")
    assert rc.is_legacy_it("backend/pom.xml")
    assert rc.is_legacy_it("docs/modernization/phase1a/SEEYON_VIEWS.sql")
    assert rc.is_legacy_it("docs/ops/dba-scripts/read-all/a8-apply.sql")
    assert not rc.is_legacy_it("docs/ops/dba-scripts/README.md")
    assert not rc.is_legacy_it("backend/src/main/java/com/forcitis/one/platform/integrationx/Other.java")
    assert rc.is_legacy_it("backend/src/main/java/com/forcitis/one/organization/workforce/WorkforceService.java")
    assert not rc.is_legacy_it("backend/src/test/java/com/forcitis/one/organization/workforce/WorkforceServiceTest.java")
    assert not rc.is_legacy_it("frontend/package.json")
    assert rc.is_legacy_it(".github/workflows/ci.yml")
    assert rc.is_legacy_it(".github/workflows/nightly-legacy-it.yml")
    assert rc.is_legacy_it("scripts/ci/legacy_it.sh")
    assert not rc.is_legacy_it(".github/workflows/deploy.yml")
    assert rc.is_legacy_it("backend/src/main/java/com/forcitis/one/kernel/WecomUserId.java")
    assert not rc.is_legacy_it("backend/src/main/java/com/forcitis/one/kernel/Money.java")
    assert rc.any_legacy_it(["README.md", "backend/pom.xml", ""])
    assert not rc.any_legacy_it(["README.md", "", "backend/src/main/java/com/forcitis/one/cost/timesheet/Timesheet.java"])
    assert not rc.any_legacy_it([])
