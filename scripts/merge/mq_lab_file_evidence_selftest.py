"""PR file-list completeness acceptance; synthetic payloads, hosted only."""
import copy
import importlib.util
from pathlib import Path
import types
import unittest


def helpers():
    path = Path(__file__).with_name("mq_lab_operator.py")
    spec = importlib.util.spec_from_file_location("mq_file_evidence_acceptance", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return types.SimpleNamespace(
        complete_files=getattr(module, "complete_files", lambda pr, files: files),
        final_readback_count=getattr(module, "final_readback_count", lambda pr, observed_pr: False))


class FileEvidenceAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.pr = dict(number=15, changed_files=2)
        self.files = [dict(filename="docs/example.md", status="modified"),
                      dict(filename="docs/renamed.md", status="renamed",
                           previous_filename=".github/workflows/lab-pr-ci.yml")]

    def test_complete_exact_list_preserves_guarded_rename_fields(self):
        before = copy.deepcopy(self.files)
        result = helpers().complete_files(dict(self.pr), self.files)
        self.assertEqual(before, result)
        self.assertEqual(before, self.files)
        self.assertEqual(".github/workflows/lab-pr-ci.yml", result[1]["previous_filename"])
        self.assertEqual("renamed", result[1]["status"])

    def test_missing_nonpositive_or_noninteger_declared_count_rejected(self):
        for value in (None, 0, -1, True, "2", 2.0):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    helpers().complete_files(dict(self.pr, changed_files=value), self.files)
        with self.assertRaises(ValueError):
            helpers().complete_files(dict(number=15), self.files)

    def test_truncated_extra_or_duplicate_entries_rejected(self):
        for files in (self.files[:1], [], self.files + [dict(filename="docs/extra.md", status="added")],
                      [self.files[0], dict(self.files[0])], None, {}):
            with self.subTest(files=files):
                with self.assertRaises(ValueError):
                    helpers().complete_files(self.pr, files)

    def test_malformed_current_or_previous_filename_rejected(self):
        invalid = (None, "", 15, "/absolute/file", "../outside", "docs/../outside",
                   "docs\\outside", "docs/file\n.md", "docs/file\x00.md", "docs//file.md")
        for field in ("filename", "previous_filename"):
            for value in invalid:
                with self.subTest(field=field, value=value):
                    files = copy.deepcopy(self.files)
                    files[1][field] = value
                    with self.assertRaises(ValueError):
                        helpers().complete_files(self.pr, files)
        files = copy.deepcopy(self.files)
        del files[0]["filename"]
        with self.assertRaises(ValueError): helpers().complete_files(self.pr, files)
        files = copy.deepcopy(self.files)
        del files[1]["previous_filename"]
        with self.assertRaises(ValueError): helpers().complete_files(self.pr, files)
        with self.assertRaises(ValueError): helpers().complete_files(self.pr, [self.files[0], "not a record"])

    def test_final_count_requires_same_strict_positive_integer(self):
        self.assertIs(helpers().final_readback_count(self.pr, dict(self.pr)), True)
        for left, right in ((2, 3), (2, None), (None, None), (0, 0), (-1, -1),
                            (True, True), (2, "2"), ("2", "2"), (2, 2.0), (2.0, 2.0)):
            with self.subTest(left=left, right=right):
                self.assertIs(helpers().final_readback_count(
                    dict(changed_files=left), dict(changed_files=right)), False)
        self.assertIs(helpers().final_readback_count({}, {}), False)
        self.assertIs(helpers().final_readback_count(self.pr, {}), False)


if __name__ == "__main__":
    unittest.main()
