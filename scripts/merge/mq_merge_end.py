"""The end of a merge in the queue (#1392): the read-back after the merge call, and a merge call that answered with an error although GitHub merged (a 502, PR #1238).
In that case the reviewed head is already on main: the queue logs MERGED with the main commit that holds it and neither retries nor updates the branch over ssh; when GitHub still
shows the pull request as not merged it logs MERGED-UNRECORDED for lead and closes or relabels nothing. Functions take the Processor, as mq.py's methods call them."""


def read_back(proc, n, head, since):
    """After the merge call: GitHub must show the pull request MERGED, with the pinned head as the second parent of the merge commit. Anything else is an ALERT and both lanes pause (`mq.py resume`); the main checkout is not fast-forwarded then."""
    for attempt in range(3):   # the state may lag a moment behind the merge call
        if attempt:
            proc.sleep(5)
        seen = proc.gh.readback(n)
        if seen and seen[0] == "MERGED" and len(seen[1]) == 2 and seen[1][1] == head:
            return True
    why = "gh could not read the pull request back" if seen is None else f"state {seen[0]}, merge commit parents {[p[:10] for p in seen[1]] or 'none'}, expected MERGED with second parent {head[:10]}"
    proc.log.event("ALERT", n, None, since, f"READBACK: {why}; both lanes pause for `mq.py resume`")
    proc.pause.hold(f"READBACK #{n}: {why}")
    return False


def after_error(proc, n, head, since, run, message):
    """True when the merge call answered with an error (`message`) but `head` is on main: logged, main checkout brought up to date, nothing else touched."""
    landed = proc.gh.landed_on_main(proc.repo, n, head)
    if not landed:
        return False
    proc.log.event("MERGED", n, run, since, f"{head[:10]} already on main in {landed[:10]} (the merge call answered: {message[:80]})")
    seen = proc.gh.readback(n)
    if not (seen and seen[0] == "MERGED"):   # not recorded by GitHub: said for lead; the pull request is neither closed nor relabelled here
        proc.log.event("MERGED-UNRECORDED", n, None, since, f"head {head[:10]} is on main in {landed[:10]} but GitHub shows the pull request as {seen[0] if seen else 'unreadable'}: lead decides")
    proc.ff_main(n, since)
    return True
