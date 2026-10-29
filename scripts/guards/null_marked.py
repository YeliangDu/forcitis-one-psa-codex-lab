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
       (-XepDisableAllChecks switches off every check it does not name, so a dropped flag silently turns NullAway off), or the argument
       is not exactly the words of ALLOWED_ARGS in that order (an option such as UnannotatedSubPackages, ExcludedPaths or AllErrorsAsWarnings,
       a repeated flag, or -XepDisableAllChecks after the checks can switch NullAway off),
       or the pom does not hold exactly one compilerArgs besides default-testCompile's, inside maven-compiler-plugin's own <configuration>
       under <build><plugins> (one in an execution, a profile or another plugin replaces the main arguments or applies to nothing)

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
# Every word the main Error Prone argument may hold: an allow-list, since any other -Xep flag or NullAway option can switch NullAway off or
# narrow it (UnannotatedSubPackages, ExcludedPaths, AllErrorsAsWarnings, a second -Xep:NullAway:...); adding a check means adding it here, reviewed
# in this order: -XepDisableAllChecks turns off what is named before it, so it must come right after the plugin
ALLOWED_ARGS = ("-Xplugin:ErrorProne", "-XepDisableAllChecks", "-Xep:CollectionIncompatibleType:ERROR", "-Xep:EqualsIncompatibleType:ERROR") + REQUIRED_ARGS
QUALIFIED = re.compile(r"^\s*@org\.jspecify\.annotations\.NullMarked\b", re.M)
SIMPLE = re.compile(r"^\s*@NullMarked\b", re.M)
IMPORT = re.compile(r"^\s*import\s+org\.jspecify\.annotations\.NullMarked\s*;", re.M)


def marked(text):
    """@org.jspecify.annotations.NullMarked, written qualified or as @NullMarked with its import (another NullMarked does not count)."""
    return bool(QUALIFIED.search(text) or (SIMPLE.search(text) and IMPORT.search(text)))


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
        found[name] = os.path.isfile(info) and marked(open(info, encoding="utf-8").read())
    return found


def listed(root):
    path = os.path.join(root, UNMARKED_FILE)
    if not os.path.isfile(path):
        return []
    return [line.strip() for line in open(path, encoding="utf-8") if line.strip() and not line.startswith("#")]


def main_holds_args(text):
    """True when maven-compiler-plugin under <build><plugins> (outside <profiles>) has, in its own <configuration> (not an execution's),
    the compilerArgs block holding the -Xplugin:ErrorProne argument."""
    outside = re.sub(r"<profiles>.*?</profiles>", "", text, flags=re.S)
    for plugin in re.findall(r"<plugin>(.*?)</plugin>", outside, flags=re.S):
        if not re.search(r"<artifactId>\s*maven-compiler-plugin\s*</artifactId>", plugin):
            continue
        own = re.sub(r"<executions>.*?</executions>", "", plugin, flags=re.S)
        config = re.search(r"<configuration>(.*?)</configuration>", own, flags=re.S)
        args = re.search(r"<compilerArgs\b[^>]*>(.*?)</compilerArgs>", config.group(1), flags=re.S) if config else None
        if args and "-Xplugin:ErrorProne" in args.group(1):
            return True
    return False


def build_problems(root):
    """NM-3: the Error Prone argument of backend/pom.xml names NullAway at ERROR with both options, and nullaway is a processor path."""
    path = os.path.join(root, POM)
    text = re.sub(r"<!--.*?-->", "", open(path, encoding="utf-8").read(), flags=re.S) if os.path.isfile(path) else ""
    text = re.sub(r"<execution>\s*<id>default-testCompile</id>.*?</execution>", "", text, flags=re.S)  # tests compile without NullAway on purpose
    args = [a for a in re.findall(r"<arg>([^<]*)</arg>", text) if a.strip().startswith("-Xplugin:ErrorProne")]
    words = args[0].split() if len(args) == 1 else []
    missing = [w for w in REQUIRED_ARGS if w not in words]
    missing += [f"no {w} (not in the allow-list ALLOWED_ARGS of scripts/guards/null_marked.py; it may switch NullAway off or narrow it)"
                for w in words if w not in ALLOWED_ARGS]
    if words and not missing and tuple(words) != ALLOWED_ARGS:
        missing.append(f"the words in the order of ALLOWED_ARGS ({' '.join(ALLOWED_ARGS)}): a later -XepDisableAllChecks or a repeated flag"
                       f" switches off what came before it")
    blocks = len(re.findall(r"<compilerArgs\b", text))
    if blocks != 1:
        missing.append(f"no compilerArgs outside the main plugin configuration and default-testCompile (found {blocks} blocks: an execution or"
                       f" profile with its own compilerArgs, e.g. default-compile combine.self=\"override\", replaces the main arguments)")
    if not main_holds_args(text):
        missing.append("the compilerArgs with the Error Prone argument inside maven-compiler-plugin's own <configuration> under <build><plugins>"
                       " (not in a profile, an execution or another plugin, where it does not apply to the main compile)")
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

        good_pom = ("<project><build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><configuration><compilerArgs><arg>-XDcompilePolicy=simple</arg>"
                    "<arg>-Xplugin:ErrorProne -XepDisableAllChecks -Xep:CollectionIncompatibleType:ERROR -Xep:EqualsIncompatibleType:ERROR -Xep:NullAway:ERROR -XepOpt:NullAway:OnlyNullMarked=true"
                    " -XepOpt:NullAway:JSpecifyMode=true</arg></compilerArgs><annotationProcessorPaths>"
                    "<path><groupId>com.uber.nullaway</groupId><artifactId>nullaway</artifactId><version>1</version></path>"
                    "</annotationProcessorPaths></configuration></plugin></plugins></build></project>")
        write(POM, good_pom)
        assert build_problems(tmp) == [], "NullAway at ERROR with both options and on the processor path"
        for drop in REQUIRED_ARGS:
            write(POM, good_pom.replace(" " + drop, ""))
            assert [p.split()[0] for p in build_problems(tmp)] == ["NM-3"] and drop in build_problems(tmp)[0], ("a dropped flag", drop)
        for undo in ("-Xep:NullAway:OFF", "-Xep:NullAway:WARN", "-XepAllErrorsAsWarnings", "-XepExcludedPaths:.*/src/main/.*",
                     "-XepOpt:NullAway:OnlyNullMarked=false", "-XepOpt:NullAway:JSpecifyMode=false",
                     "-XepOpt:NullAway:UnannotatedSubPackages=com.forcitis", "-XepOpt:NullAway:ExcludedClasses=com.forcitis.X", "-Xep:NullAway",
                     "-Xep:SomeOtherCheck:ERROR"):
            write(POM, good_pom.replace(" -XepOpt:NullAway:JSpecifyMode=true</arg>", f" -XepOpt:NullAway:JSpecifyMode=true {undo}</arg>"))
            assert build_problems(tmp) and undo in build_problems(tmp)[0], ("an argument that undoes NullAway at ERROR", undo)
        write(POM, good_pom.replace("<arg>-Xplugin:ErrorProne", "<!-- <arg>-Xplugin:ErrorProne").replace("JSpecifyMode=true</arg>", "JSpecifyMode=true</arg> -->"))
        assert build_problems(tmp) and "exactly one -Xplugin:ErrorProne" in build_problems(tmp)[0], "the main argument commented out entirely"
        override = ("<executions><execution><id>default-compile</id><configuration><compilerArgs combine.self=\"override\">"
                    "<arg>-XDcompilePolicy=simple</arg></compilerArgs></configuration></execution></executions>")
        write(POM, good_pom.replace("</configuration></plugin>", "</configuration>" + override + "</plugin>"))
        assert build_problems(tmp) and "outside the main plugin configuration" in build_problems(tmp)[0], "an execution that overrides the main arguments"
        write(POM, good_pom.replace("</project>", "<profiles><profile><id>p</id><build><plugins><plugin><configuration><compilerArgs>"
                                    "<arg>-XDx</arg></compilerArgs></configuration></plugin></plugins></build></profile></profiles></project>"))
        assert build_problems(tmp), "a profile with its own compilerArgs"
        write(POM, good_pom.replace("-Xplugin:ErrorProne -XepDisableAllChecks -Xep:", "-Xplugin:ErrorProne -Xep:")
              .replace("JSpecifyMode=true</arg>", "JSpecifyMode=true -XepDisableAllChecks</arg>"))
        assert build_problems(tmp) and "in the order of ALLOWED_ARGS" in build_problems(tmp)[0], "-XepDisableAllChecks moved to the end"
        write(POM, good_pom.replace("JSpecifyMode=true</arg>", "JSpecifyMode=true -Xep:NullAway:ERROR</arg>"))
        assert build_problems(tmp), "a repeated flag"
        main_args = good_pom[good_pom.index("<compilerArgs>"):good_pom.index("</compilerArgs>") + len("</compilerArgs>")]
        moved = good_pom.replace(main_args, "").replace("</plugins>", "<plugin><artifactId>maven-resources-plugin</artifactId><configuration>"
                                                         + main_args + "</configuration></plugin></plugins>")
        write(POM, moved)
        assert build_problems(tmp) and "maven-compiler-plugin's own <configuration>" in build_problems(tmp)[0], "the main block moved to another plugin"
        moved = good_pom.replace(main_args, "").replace("</project>", "<profiles><profile><id>off</id><build><plugins><plugin>"
                                                        "<artifactId>maven-compiler-plugin</artifactId><configuration>" + main_args
                                                        + "</configuration></plugin></plugins></build></profile></profiles></project>")
        write(POM, moved)
        assert build_problems(tmp) and "maven-compiler-plugin's own <configuration>" in build_problems(tmp)[0], "the main block moved into a profile"
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
        write(src + "f/F.java", "class F {}")
        write(src + "f/package-info.java", "@NullMarked\npackage com.x.f;\n\nimport other.lib.NullMarked;\n")
        assert hit() == ["NM-1"], "a NullMarked from another library is no JSpecify marker"
        write(src + "f/package-info.java", "@NullMarked\npackage com.x.f;\n\nimport org.jspecify.annotations.NullMarked;\n")
        write(src + "c/C.java", "class C {}")
        write(UNMARKED_FILE, "# legacy packages\ncom.x.c\n")
        assert hit() == [], "marked (imported or qualified) or listed"
        write(src + "d/D.java", "class D {}")
        assert hit() == ["NM-1"], "a new package without package-info"
        write(src + "d/package-info.java", "/** @NullMarked in a comment is no annotation */\npackage com.x.d;\n")
        assert hit() == ["NM-1"], "a mention in a comment does not count"
        write(src + "d/package-info.java", "@NullMarked\npackage com.x.d;\n\nimport org.jspecify.annotations.NullMarked;\n")
        assert hit() == []
        write(src + "c/package-info.java", "@NullMarked\npackage com.x.c;\n\nimport org.jspecify.annotations.NullMarked;\n")
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
