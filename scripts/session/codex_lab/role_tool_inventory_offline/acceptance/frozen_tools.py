"""Independent tool allowlist acceptance. No network or engine execution."""
import copy
import importlib
import unittest


TOOL = {
    "type": "function",
    "name": "lab_read_assignment",
    "parameters": {
        "type": "object",
        "properties": {},
        "additionalProperties": False,
    },
}
ALLOWED = {
    (None, "lab_read_assignment"),
    ("functions", "lab_read_assignment"),
}


def assert_engine_allowlist(payload):
    """Validate an actual decoded engine request independently of its parser."""
    def require(condition, reason):
        if not condition:
            raise AssertionError("ENGINE_ALLOWLIST:" + reason)

    require(isinstance(payload, dict), "payload-not-object")
    declarations = []

    def inspect_tools(tools):
        require(isinstance(tools, list), "tools-not-list")
        for declaration in tools:
            require(isinstance(declaration, dict), "declaration-not-object")
            kind = declaration.get("type")
            if kind == "function":
                pair = (None, declaration.get("name"))
                require(pair in ALLOWED, "flat-function-not-allowed")
                declarations.append(pair)
            elif kind == "namespace":
                require(
                    declaration.get("name") == "functions",
                    "namespace-not-allowed",
                )
                members = declaration.get("tools")
                require(
                    isinstance(members, list) and bool(members),
                    "namespace-members-invalid",
                )
                for member in members:
                    require(isinstance(member, dict), "member-not-object")
                    require(member.get("type") == "function",
                            "member-type-not-allowed")
                    pair = ("functions", member.get("name"))
                    require(pair in ALLOWED, "member-not-allowed")
                    declarations.append(pair)
            else:
                raise AssertionError("ENGINE_ALLOWLIST:type-not-allowed")

    if "tools" in payload:
        inspect_tools(payload["tools"])

    items = payload.get("input", [])
    require(isinstance(items, (list, str)), "input-invalid")
    if isinstance(items, list):
        for item in items:
            if not isinstance(item, dict):
                continue
            require(
                "additional_tools" not in item,
                "message-wrapper-not-a-valid-carrier",
            )
            if item.get("type") == "additional_tools":
                require(item.get("role") == "developer",
                        "additional-tools-role-invalid")
                require("tools" in item, "additional-tools-list-missing")
                inspect_tools(item["tools"])

    require(bool(declarations), "no-allowed-declarations")
    return declarations


def carrier(declarations, incremental):
    declarations = copy.deepcopy(declarations)
    if incremental:
        return {
            "input": [{
                "type": "additional_tools",
                "role": "developer",
                "tools": declarations,
            }]
        }
    return {"tools": declarations, "input": []}


def positive_payloads():
    for incremental in (False, True):
        yield carrier([TOOL], incremental)
        yield carrier([{
            "type": "namespace",
            "name": "functions",
            "tools": [TOOL],
        }], incremental)


def rejected_payloads():
    # Both flat and functions-namespace forms must reject these names.
    names = (
        "Goal", "get", "create", "update", "get_goal", "create_goal",
        "update_goal", "image_gen", "imagegen",
        "exec", "wait", "unknown_function",
    )
    declarations = []
    for name in names:
        function = {"type": "function", "name": name, "parameters": {}}
        declarations.append([function])
        declarations.append([{
            "type": "namespace", "name": "functions", "tools": [function],
        }])

    for namespace, names in (
        ("Goal", ("get", "create", "update")),
        ("image_gen", ("imagegen",)),
        ("collaboration", (
            "followup_task", "interrupt_agent", "list_agents",
            "send_message", "spawn_agent", "wait_agent",
        )),
        ("unknown_namespace", ("lab_read_assignment",)),
    ):
        for name in names:
            declarations.append([{
                "type": "namespace",
                "name": namespace,
                "tools": [{"type": "function", "name": name}],
            }])

    declarations.extend([
        [{"type": "unknown", "name": "lab_read_assignment"}],
        [{"type": "namespace", "name": "functions", "tools": [{
            "type": "unknown", "name": "lab_read_assignment",
        }]}],
        [{"type": "function", "name": "other.lab_read_assignment"}],
    ])
    for declaration in declarations:
        for incremental in (False, True):
            yield carrier(declaration, incremental)
            # A permitted declaration must not hide an unsafe neighbour.
            yield carrier([TOOL] + declaration, incremental)

    yield {"input": []}
    yield {"tools": [], "input": []}
    yield {"tools": None, "input": []}
    yield {"input": [{
        "type": "message", "role": "developer", "content": [],
        "additional_tools": {"tools": [TOOL]},
    }]}
    # Checking only the top-level carrier must never suffice.
    yield {
        "tools": [copy.deepcopy(TOOL)],
        "input": [{
            "type": "additional_tools", "role": "developer",
            "tools": [{"type": "function", "name": "exec"}],
        }],
    }
    yield {
        "tools": [{"type": "function", "name": "exec"}],
        "input": [{
            "type": "additional_tools", "role": "developer",
            "tools": [copy.deepcopy(TOOL)],
        }],
    }


class ToolAllowlistAcceptance(unittest.TestCase):
    def parser(self):
        module = importlib.import_module("role_native")
        function = getattr(module, "request_tool_inventory", None)
        self.assertTrue(callable(function), "TOOL_INVENTORY_PARSER_MISSING")
        return function

    def test_01_parser_accepts_only_assignment_positive_vectors(self):
        function = self.parser()
        for index, payload in enumerate(positive_payloads()):
            with self.subTest(vector=index):
                expected = assert_engine_allowlist(payload)
                actual = function(copy.deepcopy(payload))
                self.assertEqual(
                    actual,
                    [{"namespace": namespace, "name": name}
                     for namespace, name in expected],
                )

    def test_02_parser_rejects_unsafe_vectors_in_both_carriers(self):
        function = self.parser()
        for index, payload in enumerate(rejected_payloads()):
            with self.subTest(vector=index):
                with self.assertRaises(ValueError):
                    function(copy.deepcopy(payload))

    def test_03_independent_engine_oracle_accepts_positive_vectors(self):
        for index, payload in enumerate(positive_payloads()):
            with self.subTest(vector=index):
                declarations = assert_engine_allowlist(payload)
                self.assertEqual(len(declarations), 1)
                self.assertIn(declarations[0], ALLOWED)

    def test_04_independent_engine_oracle_rejects_unsafe_vectors(self):
        for index, payload in enumerate(rejected_payloads()):
            with self.subTest(vector=index):
                with self.assertRaises(AssertionError):
                    assert_engine_allowlist(payload)


if __name__ == "__main__":
    unittest.main(verbosity=2)
