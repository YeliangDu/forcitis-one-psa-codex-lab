#!/usr/bin/env python3
"""Seed runner for pilot slice S1 of the golden mainline (card #1479).

Drives the local stack (card #1478) only through its public HTTP API, as the seeded synthetic admin, and only with synthetic data.
It can stop before any of the five nodes so that the node is then done by hand in the web client:

    python3 seed_s1.py --stop-before 007|009|016|022|023   (default: run all five)

Everything before the stop point is done through the API; the local clock is set to each node's pack day first.
Refuses to talk to anything but localhost. Python standard library only; no secrets are read or printed.
"""
import argparse
import http.cookiejar
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

NODES = ["007", "009", "016", "022", "023"]
# Pack days (pack evidence, NOT_REVIEWED). The clock is set to 10:00 Shanghai (02:00Z) of the day, the way a person at work would enter things.
DAY = {"007": "2026-01-05", "009": "2026-01-05", "016": "2026-04-05", "022": "2026-06-15", "023": "2026-07-01"}
PERSON_NAME = "Synthetic Golden Person F-P01"
ADMIN_NAME = "Synthetic Admin"


class Stack:
    def __init__(self, base):
        host = urllib.parse.urlparse(base).hostname
        if host not in ("localhost", "127.0.0.1", "::1"):
            sys.exit("refused: this runner only talks to localhost, not " + str(host))
        self.base = base.rstrip("/")
        self.no_clock = False
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
        self.call("POST", "/api/v1/platform/local-clocks:set", {"at": day + "T02:00:00Z"})


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
    for key, code, name in (("junior", "GP-JR", "Synthetic junior engineer"), ("senior", "GP-SR", "Synthetic senior engineer")):
        row = find(stack, "/api/v1/organization/positions", "name", name)
        if not row:
            stack.call("POST", "/api/v1/organization/org-structure-commands", {
                "targetKind": "POSITION", "commandKind": "CREATE", "effectiveDate": "2026-01-01", "targetCode": code, "targetName": name})
            row = find(stack, "/api/v1/organization/positions", "name", name)
        ids[key] = row["positionId"]
    rule = stack.call("GET", "/api/v1/platform/numbering-rules/EMPLOYEE")
    if rule.get("confirmationStatus") != "CONFIRMED":
        stack.call("POST", "/api/v1/platform/numbering-rules/EMPLOYEE:confirm", {"templateVersion": rule["templateVersion"]})
    return ids


def person(stack):
    return find(stack, "/api/v1/organization/employees", "displayName", PERSON_NAME)


def entered(stack, who, kind):
    """True when the person already has a command of the kind that is not withdrawn (so a re-run does not enter it twice)."""
    if not who:
        return False
    page = stack.call("GET", "/api/v1/organization/personnel-commands?limit=200")
    return any(c["employeeId"] == who["id"] and c["commandKind"] == kind and c["status"] != "WITHDRAWN" for c in items(page))


def run(stack, world, node, who):
    """One node through the API. 009 needs no call of its own: the ordinary grant comes with the hire (checked in the UI)."""
    stack.set_clock(DAY[node])
    base = "/api/v1/organization/personnel-commands"
    if node == "007":
        if person(stack):
            return
        stack.call("POST", base, {"commandKind": "HIRE", "effectiveDate": DAY[node], "targetOrgUnitId": world["nanjing"], "targetPositionId": world["junior"],
                                  "isTargetFeeEarner": True, "targetEmploymentPhaseKind": "PROBATION", "displayName": PERSON_NAME,
                                  "email": "f-p01@golden.example", "targetEmployerLegalEntityId": world["entity"]})
    elif node == "016":
        if not entered(stack, who, "REGULARIZE"):
            stack.call("POST", base, {"employeeId": who["id"], "commandKind": "REGULARIZE", "effectiveDate": DAY[node]})
    elif node == "022":
        if not entered(stack, who, "TRANSFER"):
            stack.call("POST", base, {"employeeId": who["id"], "commandKind": "TRANSFER", "effectiveDate": DAY["023"], "targetOrgUnitId": world["nanjing"],
                                      "targetPositionId": world["senior"]})
    # 023: the promotion takes effect through the due job on 07-01; nothing to enter, the clock move is the step.


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--stop-before", choices=NODES, help="do everything before this node, leave the node to be done by hand")
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--no-clock", action="store_true", help="leave the local clock alone (start the stack with LOCAL_CLOCK_START instead)")
    args = parser.parse_args()
    stack = Stack(args.base)
    stack.no_clock = args.no_clock
    stack.login(ADMIN_NAME)
    world = ensure_world(stack)
    who = {}
    for node in NODES:
        if node == args.stop_before:
            stack.set_clock(DAY[node])
            print("stopped before HCM-LIFE-%s; clock set to %s; do the node in the web client" % (node, DAY[node]))
            return
        if node != "007":
            row = person(stack)
            who = {"id": row["employeeId"]}
        run(stack, world, node, who)
        print("HCM-LIFE-%s done (%s)" % (node, DAY[node]))


if __name__ == "__main__":
    main()
