"""Closing-issues GraphQL transport regression; subprocess is always mocked."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

SLUG = "YeliangDu/forcitis-one-psa-codex-lab"


def live():
    spec = importlib.util.spec_from_file_location("closing_transport_acceptance",
                                                Path(__file__).with_name("lab_queue_live.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def node(number):
    return dict(number=number, repository=dict(name="forcitis-one-psa-codex-lab", owner=dict(login="YeliangDu")))


def page(numbers, total, more=False, cursor=None):
    return dict(data=dict(repository=dict(nameWithOwner=SLUG, pullRequest=dict(
        closingIssuesReferences=dict(totalCount=total, nodes=[node(n) for n in numbers],
                                      pageInfo=dict(hasNextPage=more, endCursor=cursor))))))


class ClosingIssuesTransportAcceptanceTest(unittest.TestCase):
    def test_fixed_post_readonly_query_scope_and_pagination_flags(self):
        module = live()
        completed = subprocess.CompletedProcess([], 0, json.dumps([page([14], 1)]).encode(), b"")
        with patch.object(module.subprocess, "run", return_value=completed) as run:
            self.assertEqual(dict(closingIssuesReferences=[node(14)]), module._closing_issues(15))
        run.assert_called_once()
        argv = run.call_args.args[0]
        self.assertEqual(["gh", "api", "--hostname", "github.com", "--method", "POST", "graphql"], argv[:7])
        self.assertIn("--paginate", argv)
        self.assertIn("--slurp", argv)
        self.assertIn("owner=YeliangDu", argv)
        self.assertIn("name=forcitis-one-psa-codex-lab", argv)
        self.assertIn("number=15", argv)
        queries = [arg[len("query="):] for arg in argv if arg.startswith("query=")]
        self.assertEqual(1, len(queries))
        query = queries[0]
        self.assertTrue(query.startswith("query("))
        self.assertNotIn("mutation", query.lower())
        self.assertIn("$endCursor", query)
        self.assertIn("closingIssuesReferences(first:100,after:$endCursor)", query)
        self.assertIn("totalCount", query)
        self.assertIn("pageInfo{hasNextPage endCursor}", query)
        self.assertEqual(subprocess.PIPE, run.call_args.kwargs["stdout"])
        self.assertFalse(run.call_args.kwargs.get("shell", False))

    def test_two_pages_return_complete_ordered_closing_set(self):
        module = live()
        pages = [page([14], 2, True, "synthetic-cursor"), page([19], 2)]
        completed = subprocess.CompletedProcess([], 0, json.dumps(pages).encode(), b"")
        with patch.object(module.subprocess, "run", return_value=completed) as run:
            result = module._closing_issues(15)
        self.assertEqual(dict(closingIssuesReferences=[node(14), node(19)]), result)
        run.assert_called_once()

    def test_missing_duplicate_truncated_or_error_response_rejected(self):
        valid = page([14], 1)
        missing_repo = dict(data={})
        foreign = copy.deepcopy(valid)
        foreign["data"]["repository"]["nameWithOwner"] = "synthetic/foreign"
        responses = [[], {}, [missing_repo], [dict(errors=[dict(message="synthetic GraphQL failure")])],
                     [foreign], [page([14], 2, True, "cursor")],
                     [page([14], 2, True, "cursor"), page([14], 2)],
                     [page([14], 3, True, "cursor"), page([19], 3)],
                     [page([14], 2, True, None), page([19], 2)],
                     [page([14], 2, False), page([19], 2)],
                     [page([14], 2, True, "cursor"), page([19], 1)]]
        module = live()
        for payload in responses:
            with self.subTest(payload=payload):
                completed = subprocess.CompletedProcess([], 0, json.dumps(payload).encode(), b"")
                with patch.object(module.subprocess, "run", return_value=completed):
                    with self.assertRaises((ValueError, KeyError, TypeError)):
                        module._closing_issues(15)
        for code, stdout in ((1, b"{}"), (0, b"not-json"), (0, b"\xff")):
            with self.subTest(code=code, stdout=stdout):
                with patch.object(module.subprocess, "run", return_value=subprocess.CompletedProcess([], code, stdout, b"")):
                    with self.assertRaises((ValueError, UnicodeError)):
                        module._closing_issues(15)

    def test_invalid_pr_number_never_invokes_subprocess(self):
        module = live()
        with patch.object(module.subprocess, "run", side_effect=AssertionError("no transport for invalid PR")) as run:
            for number in (True, 0, -1, "15", None):
                with self.subTest(number=number):
                    with self.assertRaises(ValueError): module._closing_issues(number)
            run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
