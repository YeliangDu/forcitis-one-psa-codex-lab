"""Pure next-card decision; caller must supply authenticated observations."""
from datetime import datetime
import re
from uuid import UUID


HOLD = {"action":"HOLD", "card":None}


def sha(value, length):
    return type(value) is str and re.fullmatch(f"[0-9a-f]{{{length}}}",value) is not None


def utc(value):
    if type(value) is not str or not re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?(?:Z|\+00:00)",value):
        raise ValueError("expected UTC timestamp")
    return datetime.fromisoformat(value)


def native(value):
    try:
        return type(value) is str and str(UUID(value)) == value
    except (ValueError,TypeError):
        return False


def candidate(row, now, main):
    try:
        sig=row["signature"]
        return (type(row) is type(sig) is dict and type(row["card"]) is type(row["pr"]) is int
                and row["card"]>0 and row["pr"]>0 and row["refresh_required"] is True
                and native(row["author_session"]) and sha(row["acceptance_sha256"],64)
                and type(sig["card"]) is int and sig["card"]==row["card"] and sig["base"]==main
                and sig["status"]=="signed" and now<utc(sig["expires_at"]))
    except (TypeError,KeyError,ValueError):
        return False


def decide(trigger, candidates, state):
    """Choose at most one eligible card; an issued effect only permits readback."""
    try:
        if not (type(trigger) is type(state) is dict and type(candidates) is list
                and trigger["kind"]=="card-completed" and type(trigger["card"]) is int
                and trigger["card"]>0 and sha(trigger["merge_sha"],40)):
            return dict(HOLD)
        # GitHub issues and PRs share a number namespace. A successor's PR
        # cannot also be the completed predecessor card in this repository.
        if any(type(row) is dict and row.get("pr")==trigger["card"] for row in candidates):
            return dict(HOLD)
        main=trigger["merge_sha"]
        effect=state["dispatch_effect"]
        if effect is not None:
            card=effect["card"]
            bound=[r for r in candidates if type(r) is dict and r.get("card")==card]
            if (type(effect) is dict and type(card) is int and len(bound)==1 and
                    bound[0].get("signature",{}).get("base")==main and
                    type(effect["trigger_card"]) is int and effect["trigger_card"]==trigger["card"]
                    and effect["base"]==effect["trigger_merge"]==main
                    and type(effect["issue_count"]) is int and effect["issue_count"]==1
                    and effect["phase"] in ("issued","unknown","observed")
                    and type(effect["effect_id"]) is str and bool(effect["effect_id"])):
                return dict(action="READBACK",card=card)
            return dict(HOLD)
        health=state["health"]
        if not (state["main"]==main and type(health) is dict and health["head_sha"]==main
                and health["action"]=="healthy" and health.get("expired") is not True
                and state["queue"]==[] and type(state["paused"]) is bool):
            return dict(HOLD)
        now=utc(state["now"])
        eligible=[r for r in candidates if candidate(r,now,main) and r["card"]!=trigger["card"]]
        if len(eligible)!=1:
            return dict(HOLD)
        card=eligible[0]["card"]
        resume=state["resume"]
        if state["paused"] and (type(resume) is not dict or type(resume.get("card")) is not int
                or resume["card"]!=card or resume.get("base")!=main or resume.get("explicit") is not True):
            return dict(HOLD)
        return dict(action="DISPATCH",card=card)
    except (TypeError,KeyError,ValueError,AttributeError):
        return dict(HOLD)
