"""Synthetic GET/ZIP transport acceptance; hosted execution only."""
import importlib.util
import io
import json
from pathlib import Path
import stat
import types
import unittest
import zipfile

HEAD = "b" * 40
NESTED = "_temp/lab-pr-evidence/receipt.json"


def target():
    source = Path(__file__).with_name("lab_queue_live.py")
    if not source.exists():
        class ManualReader:
            def __init__(self, request):
                pass

            def __getattr__(self, name):
                return lambda *args: None
        return types.SimpleNamespace(GithubRead=ManualReader, decode_receipt=lambda blob: None)
    spec = importlib.util.spec_from_file_location("lab_live_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def archive(members):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as output:
        for name, content in members:
            output.writestr(name, content)
    return stream.getvalue()


class LabQueueLiveAcceptanceTest(unittest.TestCase):
    def reader(self, responses):
        calls = []
        def request(path, pages=False, binary=False):
            key = (path, pages, binary)
            calls.append(key)
            self.assertIn(key, responses, "only fixed allowlisted GET paths are expected")
            return responses[key]
        return target().GithubRead(request), calls

    def test_fixed_endpoints_complete_happy_read_and_all_pages(self):
        repo, main, pr = {"id": 123}, {"ref": "refs/heads/main"}, {"number": 14}
        responses = {
            ("", False, False): repo,
            ("git/ref/heads/main", False, False): main,
            ("pulls/14", False, False): pr,
            (f"actions/runs?event=pull_request&head_sha={HEAD}&per_page=100", True, False):
                [{"workflow_runs": [{"id": 100}]}, {"workflow_runs": [{"id": 101}]}],
            ("actions/runs/101/attempts/2/jobs?per_page=100", True, False):
                [{"jobs": [{"id": 1}]}, {"jobs": [{"id": 2}]}],
            ("pulls/14/reviews?per_page=100", True, False):
                [[{"id": 1}], [{"id": 2}]],
        }
        reader, calls = self.reader(responses)
        self.assertEqual(repo, reader.repository())
        self.assertEqual(main, reader.main_ref())
        self.assertEqual(pr, reader.pull(14))
        self.assertEqual([{"id": 100}, {"id": 101}], reader.runs(HEAD))
        self.assertEqual([{"id": 1}, {"id": 2}], reader.jobs(101, 2))
        self.assertEqual([{"id": 1}, {"id": 2}], reader.reviews(14))
        self.assertEqual(list(responses), calls)

    def test_positive_ids_and_full_sha_rejected_before_request(self):
        reader, calls = self.reader({})
        for invalid in (0, -1, True, "14", None):
            for method, args in (("pull", (invalid,)), ("reviews", (invalid,)),
                                 ("jobs", (invalid, 1)), ("jobs", (100, invalid)),
                                 ("artifacts", (invalid, 1)), ("artifacts", (100, invalid))):
                with self.subTest(method=method, args=args):
                    with self.assertRaises(ValueError):
                        getattr(reader, method)(*args)
        for sha in (None, "", "b" * 39, "B" * 40, "../outside", "g" * 40):
            with self.subTest(sha=sha):
                with self.assertRaises(ValueError):
                    reader.runs(sha)
        self.assertEqual([], calls)

    def test_malformed_or_incomplete_pages_rejected(self):
        selectors = [
            ("runs", (HEAD,), f"actions/runs?event=pull_request&head_sha={HEAD}&per_page=100", "workflow_runs"),
            ("jobs", (100, 1), "actions/runs/100/attempts/1/jobs?per_page=100", "jobs"),
            ("artifacts", (100, 1), "actions/runs/100/artifacts?per_page=100", "artifacts"),
            ("reviews", (14,), "pulls/14/reviews?per_page=100", None),
        ]
        for method, args, path, field in selectors:
            good = {field: []} if field else []
            for malformed in (None, {}, [good, None], [good, {}] if field else [good, {}]):
                with self.subTest(method=method, malformed=malformed):
                    reader, _ = self.reader({(path, True, False): malformed})
                    with self.assertRaises(ValueError):
                        getattr(reader, method)(*args)

    def test_artifact_exact_attempt_nested_receipt_and_untrusted_urls_ignored(self):
        receipt = {"head_sha": HEAD, "run_id": 101, "run_attempt": 2, "ready": True}
        chosen = dict(id=501, name=f"lab-pr-14-{HEAD}-2", expired=False,
                      archive_download_url="https://synthetic.invalid/never-follow")
        old = dict(id=502, name=f"lab-pr-14-{HEAD}-1", expired=False)
        expired = dict(id=503, name=f"lab-pr-14-{HEAD}-2", expired=True)
        foreign = dict(id=504, name="unrelated-log", expired=False)
        responses = {
            ("actions/runs/101/artifacts?per_page=100", True, False):
                [{"artifacts": [old, expired]}, {"artifacts": [foreign, chosen]}],
            ("actions/artifacts/501/zip", False, True):
                archive([(NESTED, json.dumps(receipt)), ("logs/output.txt", "synthetic")]),
        }
        reader, calls = self.reader(responses)
        rows = reader.artifacts(101, 2)
        self.assertIsInstance(rows, list)
        by_id = {row["id"]: row for row in rows}
        self.assertEqual({501, 502, 503, 504}, set(by_id))
        self.assertEqual(receipt, by_id[501]["receipt"])
        for item in (502, 503, 504):
            self.assertNotIn("receipt", by_id[item])
        self.assertEqual(list(responses), calls)

    def test_safe_receipt_paths_and_unsafe_duplicate_or_oversize_zip(self):
        decode = target().decode_receipt
        for name in ("receipt.json", NESTED):
            with self.subTest(valid=name):
                self.assertEqual({"ready": True}, decode(archive([
                    ("_temp/", ""), (name, '{"ready":true}'), ("logs/a.txt", "log")])))
        symlink = zipfile.ZipInfo("link")
        symlink.create_system = 3
        symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
        bad_members = [
            [("receipt.json", "{}"), (NESTED, "{}")],
            [("receipt.json", "{}"), ("../escape", "x")],
            [("/receipt.json", "{}")], [("a\\receipt.json", "{}")],
            [("other/receipt.json", "{}")], [("receipt.json", "[]")],
            [("receipt.json", "not json")], [("receipt.json", "{}"), (symlink, "target")],
            [("receipt.json", '"' + "x" * (256 * 1024) + '"')],
        ]
        blobs = [archive(members) for members in bad_members]
        blobs.extend([b"not zip", b"x" * (32 * 1024 * 1024 + 1)])
        encrypted = bytearray(archive([("receipt.json", "{}")]))
        # Set encryption flag in local and central ZIP headers without encrypting.
        for signature, offset in ((b"PK\x03\x04", 6), (b"PK\x01\x02", 8)):
            position = encrypted.index(signature) + offset
            encrypted[position] |= 1
        blobs.append(bytes(encrypted))
        for index, blob in enumerate(blobs):
            with self.subTest(invalid_archive=index):
                with self.assertRaises((ValueError, zipfile.BadZipFile, RuntimeError)):
                    decode(blob)


if __name__ == "__main__":
    unittest.main()
