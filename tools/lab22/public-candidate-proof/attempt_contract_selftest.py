"""Small hosted-only attempt regression; never imports/executes the collector.

Run beside the same packaged proof_runtime.py, or set --source explicitly to
the exact reviewed collector file. This checks parameter plumbing, not native
proof/source authenticity or permission to write.
"""
import ast
from pathlib import Path
import sys
import unittest

SOURCE = Path(__file__).with_name('proof_runtime.py')
if '--source' in sys.argv:
    at = sys.argv.index('--source')
    SOURCE = Path(sys.argv[at + 1])
    del sys.argv[at:at + 2]


class AttemptContractTest(unittest.TestCase):
    def test_non_one_attempt_reaches_both_pin_and_observed_latest_and_download(self):
        tree = ast.parse(SOURCE.read_bytes())
        aggregate = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == 'aggregate')
        calls = {}
        for node in aggregate.body:
            if isinstance(node, ast.Assign) and len(node.targets) == 1 and isinstance(node.targets[0], ast.Name):
                if node.targets[0].id in ('pin', 'observed'):
                    calls[node.targets[0].id] = node.value
        self.assertEqual({'pin', 'observed'}, set(calls))
        for attempt in (2, 7):
            with self.subTest(attempt=attempt):
                values = {}
                for name, call in calls.items():
                    selected = [kw for kw in call.keywords if kw.arg in ('run_attempt', 'latest', 'download')]
                    for kw in selected:
                        if kw.arg == 'download':
                            expression = next(k.value for k in kw.value.keywords if k.arg == 'attempt')
                        else:
                            expression = kw.value
                        # Only the selected pure parameter expression is compiled;
                        # collector/module top-level, native GET and Git never run.
                        values[(name, kw.arg)] = eval(compile(ast.Expression(expression), '<attempt-contract>', 'eval'),
                            {'__builtins__': {}, 'dict': dict}, {'ci': {'run_id': 901, 'run_attempt': attempt}})
                self.assertEqual(attempt, values[('pin', 'run_attempt')])
                self.assertEqual({'run_id': 901, 'run_attempt': attempt}, values[('observed', 'latest')])
                self.assertEqual(attempt, values[('observed', 'download')])

    def test_no_literal_attempt_keyword_remains(self):
        tree = ast.parse(SOURCE.read_bytes())
        literals = [(n.lineno, n.arg) for n in ast.walk(tree) if isinstance(n, ast.keyword)
                    and n.arg in ('attempt', 'run_attempt') and isinstance(n.value, ast.Constant)]
        self.assertEqual([], literals, 'Attempt must come from frozen independently checked inputs')


if __name__ == '__main__':
    unittest.main(verbosity=2)
