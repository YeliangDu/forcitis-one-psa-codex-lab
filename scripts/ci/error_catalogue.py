#!/usr/bin/env python3
"""Every backend ErrorCode constant has an entry in the frontend message table, and the table has no stranger (#914).

The frontend types ERROR_MESSAGES as Record<ErrorCode, ...> over openapi.json, which the backend exports from every `enum ... implements ErrorCode`
on its classpath, test enums included; a code without an entry breaks `pnpm typecheck` only after a Maven export, which check_all does not run. This reads the same
enums from the Java sources and the table's keys from error-messages.ts, so the gap shows locally in well under a second.
Usage: error_catalogue.py [<repo>]   exit 1 and a list when the two sides differ.   error_catalogue.py --self-test
"""
import re
import sys
from pathlib import Path

TABLE = "frontend/packages/core/src/error-messages.ts"
ENUM = re.compile(r"\benum\s+\w+\s+implements\s+[^{]*\bErrorCode\b[^{]*\{")


def strip_comments(text):
    return re.sub(r"//[^\n]*|/\*.*?\*/", " ", text, flags=re.S)


def constants(source):
    """The constants of every `enum X implements ErrorCode` in one Java source."""
    source = strip_comments(source)
    found = []
    for match in ENUM.finditer(source):
        depth, entry, entries, i = 0, "", [], match.end()
        while i < len(source):
            c = source[i]
            if c in "({":
                depth += 1
            elif c in ")}":
                if depth == 0:
                    break
                depth -= 1
            if depth == 0 and c in ",;":
                entries.append(entry)
                entry = ""
                if c == ";":
                    break
            else:
                entry += c
            i += 1
        entries.append(entry)
        found += [m.group(1) for e in entries if (m := re.match(r"\s*([A-Z][A-Z0-9_]*)\b", e))]
    return found


def backend_codes(repo):
    codes = set()
    for path in Path(repo, "backend/src").rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        if "ErrorCode" in text:
            codes.update(constants(text))
    return codes


def table_keys(repo):
    text = strip_comments(Path(repo, TABLE).read_text(encoding="utf-8"))
    body = text.split("ERROR_MESSAGES", 1)[1].split("= {", 1)[1].split("\n};", 1)[0]
    return set(re.findall(r"(?m)^\s*([A-Z][A-Z0-9_]*)\s*:", body))


def compare(repo):
    code, keys = backend_codes(repo), table_keys(repo)
    return sorted(code - keys), sorted(keys - code)


def main(repo):
    missing, extra = compare(repo)
    if not missing and not extra:
        return 0
    for name in missing:
        print(f"error_catalogue：后端错误码 {name} 在 {TABLE} 里没有条目；加一条（没有专属文案写 null）")
    for name in extra:
        print(f"error_catalogue：{TABLE} 里的 {name} 在后端没有对应的错误码；删掉这条")
    return 1


def self_test():
    java = """package x; import a.ErrorCode;
public enum E implements ErrorCode {
    // ORGANIZATION_COMMENTED_OUT,
    ORGANIZATION_ONE, /* ORGANIZATION_ALSO_COMMENTED, */
    ORGANIZATION_TWO(ErrorKind.UNPROCESSABLE, "a,b;c"),
    ORGANIZATION_THREE;
    private final int x;
    E() { this.x = 1; }
}
enum Other { NOT_A_CODE }
"""
    assert constants(java) == ["ORGANIZATION_ONE", "ORGANIZATION_TWO", "ORGANIZATION_THREE"], constants(java)
    assert constants("enum F implements ErrorCode { A_B, C_D }") == ["A_B", "C_D"]
    import tempfile
    with tempfile.TemporaryDirectory() as top:
        def write(rel, text):
            Path(top, rel).parent.mkdir(parents=True, exist_ok=True)
            Path(top, rel).write_text(text, encoding="utf-8")
        write("backend/src/main/java/x/E.java", java)
        write(TABLE, "export const ERROR_MESSAGES: Readonly<Record<ErrorCode, string | null>> = {\n  ORGANIZATION_ONE: null,\n  ORGANIZATION_TWO: '文案',\n  ORGANIZATION_THREE: null,\n};\n")
        assert compare(top) == ([], []) and main(top) == 0
        write(TABLE, "export const ERROR_MESSAGES: Readonly<Record<ErrorCode, string | null>> = {\n  ORGANIZATION_ONE: null,\n  ORGANIZATION_TWO: '文案',\n  ORGANIZATION_STRANGER: null,\n};\n")
        assert compare(top) == (["ORGANIZATION_THREE"], ["ORGANIZATION_STRANGER"]) and main(top) == 1
    print("error_catalogue self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "."))
