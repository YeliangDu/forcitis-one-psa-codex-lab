#!/usr/bin/env python3
"""Every package of backend/src/main/java is @NullMarked, so NullAway checks it (#810; NullAway runs only on @NullMarked code, OnlyNullMarked=true).

A package is a folder with .java files; it is marked when its package-info.java carries @NullMarked (imported, or written as
@org.jspecify.annotations.NullMarked). Packages not yet marked when this check arrived are listed in UNMARKED_FILE, one per line;
the list only shrinks: mark a package when a card touches it, then delete its line.

Each failure names the rule, the package and the fix:
  NM-1 a package is neither marked nor listed (a new package, or one that dropped its @NullMarked)
  NM-2 a listed package is marked now, or no longer exists (the list is stale)
  NM-3 backend/pom.xml does not run NullAway as an error on @NullMarked code: the Error Prone args lack -Xep:NullAway:ERROR,
       -XepOpt:NullAway:OnlyNullMarked=true or -XepOpt:NullAway:JSpecifyMode=true, or nullaway is not on the annotation-processor path
       (-XepDisableAllChecks switches off every check it does not name, so a dropped flag silently turns NullAway off)

  null_marked.py              check the repository (run from its root)
  null_marked.py --self-test  a red case per rule
"""
import os
import re
import sys
import tempfile

SOURCES = "backend/src/main/java"
UNMARKED_FILE = "backend/architecture/null-unmarked-packages.txt"
POM = "backend/pom.xml"
REQUIRED_ARGS = ("-Xep:NullAway:ERROR", "-XepOpt:NullAway:OnlyNullMarked=true", "-XepOpt:NullAway:JSpecifyMode=true")
MARKED = re.compile(r"^\s*@(?:org\.jspecify\.annotations\.)?NullMarked\b", re.M)


def packages(root):
    """{package name: marked?} for every folder under SOURCES that holds a .java file."""
    found = {}
    base = os.path.join(root, SOURCES)
    for folder, dirs, files in os.walk(base):
        dirs.sort()
        if not any(f.endswith(".java") and f != "package-info.java" for f in files):
            continue
        name = os.path.relpath(folder, base).replace(os.sep, ".")
        info = os.path.join(folder, "package-info.java")
        found[name] = os.path.isfile(info) and bool(MARKED.search(open(info, encoding="utf-8").read()))
    return found


def listed(root):
    path = os.path.join(root, UNMARKED_FILE)
    if not os.path.isfile(path):
        return []
    return [line.strip() for line in open(path, encoding="utf-8") if line.strip() and not line.startswith("#")]


def build_problems(root):
    """NM-3: the Error Prone argument of backend/pom.xml names NullAway at ERROR with both options, and nullaway is a processor path."""
    path = os.path.join(root, POM)
    text = re.sub(r"<!--.*?-->", "", open(path, encoding="utf-8").read(), flags=re.S) if os.path.isfile(path) else ""
    text = re.sub(r"<execution>\s*<id>default-testCompile</id>.*?</execution>", "", text, flags=re.S)  # tests compile without NullAway on purpose
    args = [a for a in re.findall(r"<arg>([^<]*)</arg>", text) if a.strip().startswith("-Xplugin:ErrorProne")]
    words = args[0].split() if len(args) == 1 else []
    missing = [w for w in REQUIRED_ARGS if w not in words]
    if not re.search(r"<path>\s*<groupId>com\.uber\.nullaway</groupId>\s*<artifactId>nullaway</artifactId>", text):
        missing.append("nullaway on annotationProcessorPaths")
    if len(args) != 1:
        missing.insert(0, f"exactly one -Xplugin:ErrorProne argument (found {len(args)})")
    return [f"NM-3 {POM}: NullAway would not fail the build, missing {', '.join(missing)}; -XepDisableAllChecks turns off every check it does"
            f" not name. fix: restore them in the maven-compiler-plugin configuration"] if missing else []


def problems(root):
    found = build_problems(root)
    pkgs, unmarked = packages(root), listed(root)
    for name, marked in sorted(pkgs.items()):
        if not marked and name not in unmarked:
            found.append(f"NM-1 {name}: not @NullMarked and not in {UNMARKED_FILE}, so NullAway does not check it."
                         f" fix: add @NullMarked to its package-info.java (create it if missing) and fix what NullAway reports")
    for name in unmarked:
        if name not in pkgs:
            found.append(f"NM-2 {name}: listed in {UNMARKED_FILE} but the package no longer exists. fix: delete the line")
        elif pkgs[name]:
            found.append(f"NM-2 {name}: listed in {UNMARKED_FILE} but now @NullMarked. fix: delete the line")
    return found


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, text):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            open(path, "w", encoding="utf-8").write(text)

        def hit():
            return sorted({p.split()[0] for p in problems(tmp)})

        good_pom = ("<project><build><plugins><plugin><configuration><compilerArgs><arg>-XDcompilePolicy=simple</arg>"
                    "<arg>-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true"
                    " -XepOpt:NullAway:JSpecifyMode=true</arg></compilerArgs><annotationProcessorPaths>"
                    "<path><groupId>com.uber.nullaway</groupId><artifactId>nullaway</artifactId><version>1</version></path>"
                    "</annotationProcessorPaths></configuration></plugin></plugins></build></project>")
        write(POM, good_pom)
        assert build_problems(tmp) == [], "NullAway at ERROR with both options and on the processor path"
        for drop in REQUIRED_ARGS:
            write(POM, good_pom.replace(" " + drop, ""))
            assert [p.split()[0] for p in build_problems(tmp)] == ["NM-3"] and drop in build_problems(tmp)[0], ("a dropped flag", drop)
        write(POM, good_pom.replace("<path><groupId>com.uber.nullaway</groupId><artifactId>nullaway</artifactId><version>1</version></path>", ""))
        assert "nullaway on annotationProcessorPaths" in build_problems(tmp)[0], "the processor is gone"
        write(POM, good_pom.replace("<arg>-Xplugin:ErrorProne", "<!-- <arg>-Xplugin:ErrorProne -XepDisableAllChecks -Xep:NullAway:ERROR --><arg>-Xplugin:ErrorProne"))
        assert build_problems(tmp) == [], "a comment holding the flags is ignored, the real argument still counts"
        write(POM, good_pom.replace(" -Xep:NullAway:ERROR", "").replace("</compilerArgs>", "<!-- -Xep:NullAway:ERROR --></compilerArgs>"))
        assert build_problems(tmp), "a flag only in a comment does not count"
        test_exec = ("<executions><execution><id>default-testCompile</id><configuration><compilerArgs combine.self=\"override\">"
                     "<arg>-Xplugin:ErrorProne -XepDisableAllChecks</arg></compilerArgs></configuration></execution></executions>")
        write(POM, good_pom.replace("</configuration></plugin>", "</configuration>" + test_exec + "</plugin>"))
        assert build_problems(tmp) == [], "the test compilation may leave NullAway out; the main one is what counts"
        write(POM, good_pom.replace(" -Xep:NullAway:ERROR", "").replace("</configuration></plugin>", "</configuration>"
              + test_exec.replace("-XepDisableAllChecks", "-XepDisableAllChecks -Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true -XepOpt:NullAway:JSpecifyMode=true") + "</plugin>"))
        assert build_problems(tmp), "flags only on the test compilation do not cover the main code"
        write(POM, good_pom)
        src = SOURCES + "/com/x/"
        write(src + "a/A.java", "class A {}")
        write(src + "a/package-info.java", "@NullMarked\npackage com.x.a;\n\nimport org.jspecify.annotations.NullMarked;\n")
        write(src + "b/B.java", "class B {}")
        write(src + "b/package-info.java", "@org.jspecify.annotations.NullMarked\npackage com.x.b;\n")
        write(src + "c/C.java", "class C {}")
        write(UNMARKED_FILE, "# legacy packages\ncom.x.c\n")
        assert hit() == [], "marked (imported or qualified) or listed"
        write(src + "d/D.java", "class D {}")
        assert hit() == ["NM-1"], "a new package without package-info"
        write(src + "d/package-info.java", "/** @NullMarked in a comment is no annotation */\npackage com.x.d;\n")
        assert hit() == ["NM-1"], "a mention in a comment does not count"
        write(src + "d/package-info.java", "@NullMarked\npackage com.x.d;\n")
        assert hit() == []
        write(src + "c/package-info.java", "@NullMarked\npackage com.x.c;\n")
        assert hit() == ["NM-2"], "a listed package that is marked now"
        write(UNMARKED_FILE, "com.x.gone\n")
        assert hit() == ["NM-2"], "a listed package that is gone"
        write(UNMARKED_FILE, "")
        write(src + "e/package-info.java", "package com.x.e;\n")
        assert hit() == [], "a folder with only package-info.java is no package to check"
        write(src + "a/package-info.java", "package com.x.a;\n")
        assert "fix: add @NullMarked" in problems(tmp)[0], "a package that dropped @NullMarked fails and names the fix"
    print("null_marked self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    found = problems(os.getcwd())
    for p in found:
        print(p)
    print(f"null_marked: {len(found)} problems")
    sys.exit(1 if found else 0)
