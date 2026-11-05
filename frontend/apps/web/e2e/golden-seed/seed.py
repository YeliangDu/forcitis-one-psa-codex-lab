#!/usr/bin/env python3
"""Seed runner for the golden mainline (cards #1479 slice S1, #1518 any supported node).

Drives the local stack (card #1478) only through its public HTTP API, as the seeded synthetic admin, and only with synthetic data.
It seeds every supported step that comes before a chosen node, so that the node is then done by hand in the web client:

    python3 seed.py --stop-before <node>      (e.g. 016, 030; the node must be in the NODES table below)
    python3 seed.py --list                     (the supported nodes, their pack day and what they need)
    python3 seed.py                            (default: run every supported node)

Everything before the stop point is done through the API; the local clock is set to each node's pack day first.
Supported nodes are declared in the one NODES table; a node outside it is refused naming the first missing step, never skipped silently.
Pack nodes between supported ones that belong to other owners (leave, identity, competency, payroll) change no workforce state and are listed as not seeded.
Refuses to talk to anything but localhost. Python standard library only; no secrets are read or printed.
"""
import argparse
import http.cookiejar
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

# Supported nodes, in pack order: node -> pack day (evidence, NOT_REVIEWED), the nodes whose state it builds on, and what it does through the API.
# 009 enters nothing (the ordinary grant comes with the hire); 023 only moves the clock (the due job applies the promotion); the rest enter one personnel command.
NODES = {
    "007": {"day": "2026-01-05", "needs": [], "primary": ("nanjing", "junior"), "does": "HIRE F-P01 into the Nanjing unit as junior engineer on probation, employer LE-A"},
    "009": {"day": "2026-01-05", "needs": ["007"], "does": "nothing to enter: the ordinary access role comes with the hire (checked in the UI)"},
    "016": {"day": "2026-04-05", "needs": ["007"], "does": "REGULARIZE"},
    "022": {"day": "2026-06-15", "needs": ["016"], "does": "TRANSFER effective 2026-07-01 to the senior engineer post (Nanjing)"},
    "023": {"day": "2026-07-01", "needs": ["022"], "primary": ("nanjing", "senior"), "does": "nothing to enter: the clock move runs the due job that applies the promotion"},
    "026": {"day": "2026-08-10", "needs": ["023"], "does": "UNPAID_LEAVE_START (study leave, relationship kept)"},
    "027": {"day": "2026-08-17", "needs": ["026"], "does": "UNPAID_LEAVE_END (same relationship, no new one)"},
    "030": {"day": "2026-10-01", "needs": ["027"], "primary": ("shanghai", "lighting"), "does": "TRANSFER to the Shanghai unit, lighting support post, same employer"},
    "031": {"day": "2026-10-02", "needs": ["030"], "does": "nothing to enter through the API yet: the authorization change is done by hand (replace-roles has no page)"},
}
# The command each node enters and the day it takes effect (the checks below read them back as EFFECTIVE).
COMMANDS = {"007": ("HIRE", "2026-01-05"), "016": ("REGULARIZE", "2026-04-05"), "022": ("TRANSFER", "2026-07-01"), "026": ("UNPAID_LEAVE_START", "2026-08-10"),
            "027": ("UNPAID_LEAVE_END", "2026-08-17"), "030": ("TRANSFER", "2026-10-01")}
# Pack checkpoints that the public API cannot show (no relationship or working-state read exists): listed in every report, never silently dropped.
NOT_READABLE = {"026": "active_relations 1, working_relations 0", "027": "relation_count 1, working_relations 1", "030": "relation_count 1 (ER-001-A unchanged)"}
# Pack nodes between the supported ones that other owners keep; they change no workforce state and are not seeded.
OTHER_OWNERS = {"024": "leave", "025": "identity", "028": "competency", "029": "payroll"}
PERSON_NAME = "Synthetic Golden Person F-P01"
ADMIN_NAME = "Synthetic Admin"
LIGHTING_POST = ("lighting", "GP-LS", "Synthetic lighting support post")


class Stack:
    def __init__(self, base):
        host = urllib.parse.urlparse(base).hostname
        if host not in ("localhost", "127.0.0.1", "::1"):
            sys.exit("refused: this runner only talks to localhost, not " + str(host))
        self.base = base.rstrip("/")
        self.no_clock = False
        self.account = ""
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def cookie(self, name):
        return next((c.value for c in self.jar if c.name == name), None)

    def call(self, method, path, body=None, expect=(200, 201, 202, 204)):
        headers = {"Accept": "application/json"}
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if method != "GET" and self.cookie("XSRF-TOKEN"):
            headers["X-XSRF-TOKEN"] = self.cookie("XSRF-TOKEN")
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with self.http.open(request) as response:
                status, text = response.status, response.read().decode()
        except urllib.error.HTTPError as failure:
            status, text = failure.code, failure.read().decode()
        if status not in expect:
            sys.exit("%s %s -> %s %s" % (method, path, status, text[:400]))
        return json.loads(text) if text.strip().startswith(("{", "[")) else None

    def login(self, name):
        self.account = name
        self.call("GET", "/api/v1/platform/local-accounts")  # the first GET hands out the XSRF cookie
        accounts = self.call("GET", "/api/v1/platform/local-accounts")
        match = [a for a in accounts if a["name"] == name]
        if not match:
            sys.exit("no local account named %r; accounts: %s" % (name, [a["name"] for a in accounts]))
        self.call("POST", "/api/v1/platform/local-sessions", {"accountId": match[0]["userId"]})
        self.call("GET", "/api/v1/platform/local-accounts")  # refreshes the XSRF cookie after the session change (else the next POST is 403)

    def set_clock(self, day):
        if self.no_clock:
            return
        target = day + "T02:00:00Z"
        answer = self.call("POST", "/api/v1/platform/local-clocks:set", {"at": target}, expect=(200, 201, 202, 204, 409))
        # After the administrator mode (card #1499) the stack refuses to move the clock back (409): then the clock stays where it is, said out loud. A fresh stack runs on the real date and must be set back to the golden day.
        if isinstance(answer, dict) and answer.get("errorCode") == "PLATFORM_WEB_LOCAL_CLOCK_HELD":
            print("clock not moved back to %s: held by the administrator mode; it stays where it is" % target)
            return
        if isinstance(answer, dict) and answer.get("status") == 409:
            sys.exit("POST /api/v1/platform/local-clocks:set -> 409 %s" % answer)
        self.login(self.account)  # sessions age with the clock: the move may have ended ours


def items(page):
    return page["items"] if isinstance(page, dict) else page


def find(stack, path, key, value):
    """Org units come back as a plain list and refuse `limit`; the other lists are pages."""
    query = "" if path.endswith("/org-units") else "?limit=200"
    return next((row for row in items(stack.call("GET", path + query)) if row.get(key) == value), None)


def ensure_world(stack):
    """The small world of the pack: employer LE-A, a Nanjing unit and a Shanghai unit, four positions, the employee numbering rule confirmed."""
    entity = find(stack, "/api/v1/organization/legal-entities", "name", "Synthetic employer A")
    if not entity:
        stack.call("POST", "/api/v1/organization/legal-entities", {"code": "GAA", "name": "Synthetic employer A"})
        entity = find(stack, "/api/v1/organization/legal-entities", "name", "Synthetic employer A")
    ids = {"entity": entity["legalEntityId"]}
    for key, code, name in (("nanjing", "GU-NJ", "Synthetic Nanjing unit"), ("shanghai", "GU-SH", "Synthetic Shanghai unit")):
        row = find(stack, "/api/v1/organization/org-units", "name", name)
        if not row:
            stack.call("POST", "/api/v1/organization/org-structure-commands", {
                "targetKind": "ORG_UNIT", "commandKind": "CREATE", "effectiveDate": "2026-01-01", "targetCode": code, "targetName": name,
                "targetRegionKind": "REGIONAL", "targetIsDeliveryUnit": True})
            row = find(stack, "/api/v1/organization/org-units", "name", name)
        ids[key] = row["orgUnitId"]
    for key, code, name in (("junior", "GP-JR", "Synthetic junior engineer"), ("senior", "GP-SR", "Synthetic senior engineer"), LIGHTING_POST):
        row = find(stack, "/api/v1/organization/positions", "name", name)
        if not row:
            stack.call("POST", "/api/v1/organization/org-structure-commands", {
                "targetKind": "POSITION", "commandKind": "CREATE", "effectiveDate": "2026-01-01", "targetCode": code, "targetName": name})
            row = find(stack, "/api/v1/organization/positions", "name", name)
        ids[key] = row["positionId"]
    rule = stack.call("GET", "/api/v1/platform/numbering-rules/EMPLOYEE")
    if rule.get("confirmationStatus") != "CONFIRMED":
        stack.call("POST", "/api/v1/platform/numbering-rules/EMPLOYEE:confirm", {"templateVersion": rule["templateVersion"]})
    ids["names"] = {"nanjing": "Synthetic Nanjing unit", "shanghai": "Synthetic Shanghai unit", "junior": "Synthetic junior engineer", "senior": "Synthetic senior engineer",
                    "lighting": LIGHTING_POST[2]}
    return ids


def person(stack):
    return find(stack, "/api/v1/organization/employees", "displayName", PERSON_NAME)


def entered(stack, who, kind, day=None):
    """True when the person already has a command of the kind that is not withdrawn (so a re-run does not enter it twice)."""
    if not who:
        return False
    page = stack.call("GET", "/api/v1/organization/personnel-commands?limit=200")
    return any(c["employeeId"] == who["id"] and c["commandKind"] == kind and c["status"] != "WITHDRAWN" and (day is None or c.get("effectiveDate") == day) for c in items(page))


def day_of(node):
    return NODES[node]["day"]


def plan(target):
    """The supported nodes to run before `target`, in pack order, each after the nodes it needs; refuses a node that is not supported."""
    if target is None:
        return list(NODES)
    if target not in NODES:
        known = sorted(set(NODES) | set(OTHER_OWNERS))
        reason = "belongs to the %s owner and seeds no workforce state" % OTHER_OWNERS[target] if target in OTHER_OWNERS else "is not in the NODES table"
        sys.exit("refused: HCM-LIFE-%s %s; the first missing step is the seed of HCM-LIFE-%s (supported: %s; known pack nodes in range: %s)"
                 % (target, reason, target, ", ".join(NODES), ", ".join(known)))
    needed, queue = set(), [target]
    while queue:
        node = queue.pop()
        for need in NODES[node]["needs"]:
            if need not in NODES:
                sys.exit("refused: HCM-LIFE-%s needs HCM-LIFE-%s, which the runner does not support; the first missing step is HCM-LIFE-%s" % (node, need, need))
            if need not in needed:
                needed.add(need)
                queue.append(need)
    return [node for node in NODES if node in needed]


def run(stack, world, node, who):
    """One node through the API. 009, 023 and 031 enter nothing of their own (see the NODES table)."""
    stack.set_clock(day_of(node))
    base = "/api/v1/organization/personnel-commands"
    if node == "007":
        if person(stack):
            return
        stack.call("POST", base, {"commandKind": "HIRE", "effectiveDate": day_of(node), "targetOrgUnitId": world["nanjing"], "targetPositionId": world["junior"],
                                  "isTargetFeeEarner": True, "targetEmploymentPhaseKind": "PROBATION", "displayName": PERSON_NAME,
                                  "email": "f-p01@golden.example", "targetEmployerLegalEntityId": world["entity"]})
    elif node == "016":
        if not entered(stack, who, "REGULARIZE"):
            stack.call("POST", base, {"employeeId": who["id"], "commandKind": "REGULARIZE", "effectiveDate": day_of(node)})
    elif node == "022":
        if not entered(stack, who, "TRANSFER"):
            stack.call("POST", base, {"employeeId": who["id"], "commandKind": "TRANSFER", "effectiveDate": day_of("023"), "targetOrgUnitId": world["nanjing"],
                                      "targetPositionId": world["senior"]})
    elif node == "026":
        if not entered(stack, who, "UNPAID_LEAVE_START"):
            stack.call("POST", base, {"employeeId": who["id"], "commandKind": "UNPAID_LEAVE_START", "effectiveDate": day_of(node)})
    elif node == "027":
        if not entered(stack, who, "UNPAID_LEAVE_END"):
            stack.call("POST", base, {"employeeId": who["id"], "commandKind": "UNPAID_LEAVE_END", "effectiveDate": day_of(node)})
    elif node == "030":
        if not entered(stack, who, "TRANSFER", day_of(node)):
            stack.call("POST", base, {"employeeId": who["id"], "commandKind": "TRANSFER", "effectiveDate": day_of(node), "targetOrgUnitId": world["shanghai"],
                                      "targetPositionId": world["lighting"]})


def verify(stack, world, done):
    """Reads the person back and compares with what the pack gives after the nodes done (pack expected values, NOT_REVIEWED). Mismatches are printed, not hidden."""
    who = person(stack)
    if not who:
        print("check: MISMATCH no person F-P01 on the stack")
        return
    expected = next((NODES[n]["primary"] for n in reversed(done) if "primary" in NODES[n]), None)
    primaries = [a for a in stack.call("GET", "/api/v1/organization/employees/" + who["employeeId"])["assignments"] if a["assignmentKind"] == "PRIMARY"]
    if len(primaries) != 1:
        print("check: MISMATCH current_primary expected 1, the API shows %d" % len(primaries))
    elif expected:
        names = {"orgUnitName": world["names"][expected[0]], "positionName": world["names"][expected[1]]}
        wrong = [k for k, v in names.items() if primaries[0].get(k) != v]
        print("check: current_primary 1, %s / %s %s" % (primaries[0].get("orgUnitName"), primaries[0].get("positionName"), "MISMATCH (expected %s)" % names if wrong else "ok"))
    commands = items(stack.call("GET", "/api/v1/organization/personnel-commands?limit=200"))
    for node in done:
        if node in COMMANDS:
            kind, day = COMMANDS[node]
            status = next((c["status"] for c in commands if c["employeeId"] == who["employeeId"] and c["commandKind"] == kind and c["effectiveDate"] == day and c["status"] != "WITHDRAWN"), None)
            print("check: HCM-LIFE-%s %s %s is %s %s" % (node, kind, day, status, "ok" if status == "EFFECTIVE" else "MISMATCH (expected EFFECTIVE)"))
            if not stack.no_clock:
                # a command entered on the wrong clock day (e.g. the real date of a fresh stack) makes every later date of the world wrong
                created = next((c["createdAt"][:10] for c in commands if c["employeeId"] == who["employeeId"] and c["commandKind"] == kind and c["effectiveDate"] == day and c["status"] != "WITHDRAWN"), None)
                print("check: HCM-LIFE-%s %s was entered on %s %s" % (node, kind, created, "ok" if created == NODES[node]["day"] else "MISMATCH (expected %s)" % NODES[node]["day"]))
    for node in done:
        if node in NOT_READABLE:
            print("check: HCM-LIFE-%s pack checkpoint not readable through the public API: %s" % (node, NOT_READABLE[node]))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--stop-before", metavar="NODE", help="seed everything the node builds on, leave the node itself to be done by hand")
    parser.add_argument("--list", action="store_true", help="print the supported nodes and exit")
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--no-clock", action="store_true", help="leave the local clock alone (start the stack with LOCAL_CLOCK_START instead)")
    args = parser.parse_args()
    if args.list:
        for node, row in NODES.items():
            print("HCM-LIFE-%s  %s  needs %-4s %s" % (node, row["day"], ",".join(row["needs"]) or "-", row["does"]))
        for node, owner in OTHER_OWNERS.items():
            print("HCM-LIFE-%s  (owner %s: not seeded, no workforce state)" % (node, owner))
        return
    todo = plan(args.stop_before)
    stack = Stack(args.base)
    stack.no_clock = args.no_clock
    stack.login(ADMIN_NAME)
    world = ensure_world(stack)
    who = {}
    for node in todo:
        if node != "007":
            row = person(stack)
            who = {"id": row["employeeId"]}
        run(stack, world, node, who)
        print("HCM-LIFE-%s done (%s)" % (node, day_of(node)))
    skipped = [n for n in OTHER_OWNERS if args.stop_before is None or n < args.stop_before]
    verify(stack, world, todo)
    if skipped:
        print("not seeded (other owners, no workforce state): " + ", ".join("HCM-LIFE-%s" % n for n in skipped))
    if args.stop_before:
        stack.set_clock(day_of(args.stop_before))
        print("stopped before HCM-LIFE-%s; clock set to %s; do the node in the web client" % (args.stop_before, day_of(args.stop_before)))


if __name__ == "__main__":
    main()
