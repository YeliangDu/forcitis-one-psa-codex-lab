---------------------------- MODULE spec ----------------------------
\* Employment relationship lifecycle (docs/architecture/proposals/employment-relationship.md, ADR-0086) and the three consumers
\* that act on its events: leave (account opening, closing, carry-over from a predecessor), the identity separation flag, and
\* timesheet attribution. One person, two employing entities, at most two relationships, events delivered at least once and in any order.
\* Time is a small day counter; every command takes effect on a day not later than today (future-dated entry is not modelled:
\* the event ordering problem does not depend on it).
\*
\* Business treatments and design gaps are CONSTANTS; the model never chooses. Section numbers refer to the design.
EXTENDS Naturals, FiniteSets

CONSTANTS
    Rels,                \* {"r1", "r2"}
    Ents,                \* {"e1", "e2"}
    N,                   \* last day of the horizon
    MaxEv,               \* bound on emitted events
    Payload,             \* gap option: what an event gives its consumer: "read" (a notice; the consumer reads the relationship now),
                         \*   "snapshot" (state at emission, applied as it arrives), "versioned" (snapshot applied only if newer)
    UnseenPolicy,        \* gap option: an event of a relationship the consumer has not seen started: "act" or "defer"
    IdentityCorrection,  \* gap option: identity recomputes its flag on EmployeeCorrected: "recompute" or "none"
    IdentityHires,       \* gap option: identity clears the separated flag on EmployeeHired (a HIRE by another employer): TRUE or FALSE
    CarryTrigger,        \* gap option: leave carries a predecessor's balance: "started" (when the successor's start is handled),
                         \*   "ended" (when any event of the predecessor is handled after its account closed), "both" (at whichever comes second)
    SameEmployer,        \* design 4.2 item 1 / UNIQUE(employee, employer): "unique" (a second HIRE by the same employer is refused,
                         \*   REINSTATE is used) or "newRow" (negative control: a second row for the same pair is allowed)
    HoldSeparate,        \* the separation command may be entered with a hold (5, 6.2): TRUE or FALSE
    CarryFromPred,       \* business T-6 (a): the unused balance follows a predecessor: TRUE or FALSE
    PredNoProbation      \* business B-E: a HIRE with a predecessor starts after probation: TRUE or FALSE

Days == 1..N
Cons == {"leave", "ts", "idn"}
RelKinds == {"Started", "Ended", "PhaseChanged", "UnpaidChanged", "Corrected"}
PersonKinds == {"PHired", "PSep", "PRein", "PCorr"}     \* EmployeeHired, EmployeeSeparated, EmployeeReinstated, EmployeeCorrected

VARIABLES
    today,
    days,      \* days[r]: the days on which relationship r is in service (set of days)
    ent,       \* employer of r, "-" before the first HIRE
    phase,     \* "-" | "P" probation | "R" regular
    unp,       \* days of unpaid leave
    pred,      \* predecessor relationship (employer change in continuous service) or "-"
    ver,       \* number of changes of r (0 = no row yet)
    pend,      \* day of a held SEPARATE not yet in effect, 0 = none
    pv,        \* number of person-level events emitted
    evs,       \* emitted events (never removed: at-least-once delivery means any of them can arrive again)
    handled,   \* handled[c]: ids of events consumer c has handled at least once
    lv, ts,    \* replicas per relationship, kept by leave and by timesheet
    idn,       \* identity: [sep, v]
    ghost      \* what the last delivery did, for the action-level checks

vars == <<today, days, ent, phase, unp, pred, ver, pend, pv, evs, handled, lv, ts, idn, ghost>>

Min(S) == CHOOSE x \in S : \A y \in S : x <= y
Max(S) == CHOOSE x \in S : \A y \in S : x >= y
UpFrom(d) == d..N

\* ---- truth ----
InSvcP(t) == \E r \in Rels : t \in days[r]
Hired == \E r \in Rels : ver[r] > 0
SepOf(d, v, t) == (\E r \in Rels : v[r] > 0) /\ ~(\E r \in Rels : t \in d[r])
SepTruth == SepOf(days, ver, today)
Snap(r) == [days |-> days[r], phase |-> phase[r], unp |-> unp[r], v |-> ver[r], pred |-> pred[r]]
Content(x) == [days |-> x.days, phase |-> x.phase, unp |-> x.unp, pred |-> x.pred]

NoRep == [seen |-> FALSE, days |-> {}, phase |-> "-", unp |-> {}, pred |-> "-", v |-> 0, acct |-> "none", carried |-> FALSE]

TypeOK ==
    /\ today \in Days /\ days \in [Rels -> SUBSET Days] /\ ver \in [Rels -> Nat] /\ pv \in Nat
    /\ evs \subseteq [id : Nat, kind : RelKinds \cup PersonKinds, rel : Rels, rv : Nat, pv : Nat, snap : [days : SUBSET Days, phase : {"-", "P", "R"}, unp : SUBSET Days, v : Nat, pred : Rels \cup {"-"}], sep : BOOLEAN]

\* ---- events ----
\* The event for relationship r with the post-state values (primed) and an id.
RelEv(k, r, i) == [id |-> i, kind |-> k, rel |-> r, rv |-> ver'[r], pv |-> pv, snap |-> [days |-> days'[r], phase |-> phase'[r], unp |-> unp'[r], v |-> ver'[r], pred |-> pred'[r]], sep |-> SepOf(days', ver', today)]
PerEv(k, r, i, p) == [id |-> i, kind |-> k, rel |-> r, rv |-> ver'[r], pv |-> p, snap |-> [days |-> days'[r], phase |-> phase'[r], unp |-> unp'[r], v |-> ver'[r], pred |-> pred'[r]], sep |-> SepOf(days', ver', today)]

Room(k) == Cardinality(evs) + k <= MaxEv

\* Every producer action: set r to the new values, add the relationship event, and the person-level event if the rule of 6.1 fires one.
\* pk is the person-level kind to emit or "-"; "PCorr" always accompanies a correction.
Commit(r, nd, nph, nu, np, ne, k, pk) ==
    /\ days' = [days EXCEPT ![r] = nd] /\ phase' = [phase EXCEPT ![r] = nph] /\ unp' = [unp EXCEPT ![r] = nu]
    /\ pred' = [pred EXCEPT ![r] = np] /\ ent' = [ent EXCEPT ![r] = ne] /\ ver' = [ver EXCEPT ![r] = @ + 1]
    /\ pv' = IF pk = "-" THEN pv ELSE pv + 1
    /\ evs' = evs \cup {RelEv(k, r, Cardinality(evs) + 1)} \cup (IF pk = "-" THEN {} ELSE {PerEv(pk, r, Cardinality(evs) + 2, pv + 1)})

PersonAfter(nd, r) == \E q \in Rels : today \in (IF q = r THEN nd ELSE days[q])

\* ---- producer actions (design clause in the comment) ----

\* 5: HIRE names the employer; first relationship, or an additional employer. EmployeeHired fires when the person goes from not in service to in service.
Hire(r, e) ==
    /\ ver[r] = 0 /\ Room(2)
    /\ SameEmployer = "newRow" \/ \A q \in Rels : ent[q] # e
    /\ \E d \in 1..today :
          LET nd == UpFrom(d) IN
          Commit(r, nd, "P", {}, "-", e, "Started", IF ~InSvcP(today) /\ PersonAfter(nd, r) THEN "PHired" ELSE "-")
    /\ pend' = pend /\ UNCHANGED today

\* 5: REINSTATE of an ended relationship (same employer); EmployeeReinstated fires when the person goes from not in service to in service.
Reinstate(r) ==
    /\ ver[r] > 0 /\ days[r] # {} /\ Max(days[r]) < N /\ Room(2)
    /\ \E d \in (Max(days[r]) + 1)..today :
          LET nd == days[r] \cup UpFrom(d) IN
          Commit(r, nd, phase[r], unp[r], pred[r], ent[r], "Started", IF ~InSvcP(today) /\ PersonAfter(nd, r) THEN "PRein" ELSE "-")
    /\ pend' = pend /\ UNCHANGED today

\* the end of r from day d: relationship event Ended; EmployeeSeparated if it was the last one in service (6.1)
EndFrom(r, d) ==
    LET nd == days[r] \ UpFrom(d) IN
    /\ Room(2)
    /\ Commit(r, nd, phase[r], unp[r], pred[r], ent[r], "Ended", IF InSvcP(today) /\ ~PersonAfter(nd, r) THEN "PSep" ELSE "-")

\* 5, 6.2: SEPARATE on one relationship; with a hold the command is entered but not in effect (no event, nothing changes for consumers)
Separate(r) ==
    /\ today \in days[r] /\ pend[r] = 0
    /\ \E d \in (Min(days[r]) + 1)..today :
          \/ /\ EndFrom(r, d) /\ pend' = pend
          \/ /\ HoldSeparate /\ pend' = [pend EXCEPT ![r] = d] /\ UNCHANGED <<days, ent, phase, unp, pred, ver, pv, evs>>
    /\ UNCHANGED today

\* the administrator releases the hold: the SEPARATE takes effect now
ReleaseSeparate(r) ==
    /\ pend[r] > 0 /\ pend[r] > Min(days[r]) /\ pend[r] \in days[r]
    /\ EndFrom(r, pend[r]) /\ pend' = [pend EXCEPT ![r] = 0] /\ UNCHANGED today

WithdrawSeparate(r) ==
    /\ pend[r] > 0 /\ pend' = [pend EXCEPT ![r] = 0] /\ UNCHANGED <<today, days, ent, phase, unp, pred, ver, pv, evs>>

\* 5 "employer change in continuous service": SEPARATE on the old and HIRE with predecessor on the new employer, one entry, one date.
\* The person stays in service: neither EmployeeSeparated nor EmployeeHired fires (6.1).
ChangeEmployer(o, n, e) ==
    /\ o # n /\ ver[n] = 0 /\ today \in days[o] /\ ent[o] # e /\ Room(2)
    /\ SameEmployer = "newRow" \/ \A q \in Rels : ent[q] # e
    /\ \E d \in (Min(days[o]) + 1)..today :
         /\ days' = [days EXCEPT ![o] = days[o] \ UpFrom(d), ![n] = UpFrom(d)]
         /\ phase' = [phase EXCEPT ![n] = IF PredNoProbation THEN "R" ELSE "P"]
         /\ unp' = unp /\ pred' = [pred EXCEPT ![n] = o] /\ ent' = [ent EXCEPT ![n] = e]
         /\ ver' = [ver EXCEPT ![o] = @ + 1, ![n] = @ + 1] /\ pv' = pv
         /\ evs' = evs \cup {RelEv("Ended", o, Cardinality(evs) + 1), RelEv("Started", n, Cardinality(evs) + 2)}
    /\ pend' = pend /\ UNCHANGED today

\* 6.1: regularization (EmploymentRelationshipPhaseChanged)
Regularize(r) ==
    /\ today \in days[r] /\ phase[r] = "P" /\ Room(1)
    /\ Commit(r, days[r], "R", unp[r], pred[r], ent[r], "PhaseChanged", "-") /\ pend' = pend /\ UNCHANGED today

\* 6.1: unpaid leave starts or ends (EmploymentRelationshipUnpaidLeaveChanged)
UnpaidChange(r) ==
    /\ today \in days[r] /\ Room(1)
    /\ \/ Commit(r, days[r], phase[r], unp[r] \cup UpFrom(today), pred[r], ent[r], "UnpaidChanged", "-") /\ today \notin unp[r]
       \/ Commit(r, days[r], phase[r], unp[r] \ UpFrom(today), pred[r], ent[r], "UnpaidChanged", "-") /\ today \in unp[r]
    /\ pend' = pend /\ UNCHANGED today

\* 5 CORRECTION: the start moves (later or earlier) or the end moves (later or earlier), also after the relationship has ended.
\* Relationship event Corrected and person-level EmployeeCorrected (6.1); no EmployeeSeparated/Reinstated is fired by a correction.
Correct(r) ==
    /\ ver[r] > 0 /\ days[r] # {} /\ Room(2)
    /\ \E nd \in { days[r] \ {Min(days[r])}, days[r] \cup {Min(days[r]) - 1} }
                 \cup (IF Max(days[r]) < N THEN { days[r] \ {Max(days[r])}, days[r] \cup {Max(days[r]) + 1} } ELSE {}) :
          \* a correction moves a date within the past: no start and no end after today (future-dated entries are not modelled)
          /\ nd # days[r] /\ nd # {} /\ nd \subseteq Days /\ Min(nd) <= today /\ (Max(nd) = N \/ Max(nd) < today)
          /\ Commit(r, nd, phase[r], unp[r], pred[r], ent[r], "Corrected", "PCorr")
    /\ pend' = pend /\ UNCHANGED today

Tick == today < N /\ today' = today + 1 /\ UNCHANGED <<days, ent, phase, unp, pred, ver, pend, pv, evs, handled, lv, ts, idn, ghost>>

\* ---- consumers ----
RelSub(e) == e.kind \in RelKinds
Subscribes(c, e) == IF c = "idn" THEN e.kind \in PersonKinds ELSE e.kind \in RelKinds

\* what the consumer takes from the event: read = the relationship as it is now; snapshot = the payload; versioned = payload if newer
Take(old, e) ==
    CASE Payload = "read" -> Snap(e.rel)
      [] Payload = "snapshot" -> e.snap
      [] OTHER -> IF e.snap.v > old.v THEN e.snap ELSE [days |-> old.days, phase |-> old.phase, unp |-> old.unp, v |-> old.v, pred |-> old.pred]

\* a consumer replica after handling a relationship event; leave also keeps the account state and the carry-over
RelApply(c, e) ==
    LET r == e.rel
        old == IF c = "leave" THEN lv[r] ELSE ts[r]
        t == Take(old, e)
        seenNow == old.seen \/ e.kind = "Started"
        accN == IF today \in t.days THEN "open" ELSE IF old.acct = "none" /\ t.days = {} THEN "none" ELSE "closed"
        carryStart == CarryFromPred /\ t.pred # "-" /\ CarryTrigger \in {"started", "both"} /\ e.kind = "Started" /\ lv[t.pred].acct = "closed"
    IN [seen |-> seenNow, days |-> t.days, phase |-> t.phase, unp |-> t.unp, pred |-> t.pred, v |-> t.v, acct |-> accN,
        carried |-> old.carried \/ carryStart]

\* when an event of a predecessor is handled and its account is closed, successors that were seen started are carried (trigger "ended" or "both")
CarryOnEnd(p, lvN) ==
    [q \in Rels |-> IF CarryFromPred /\ CarryTrigger \in {"ended", "both"} /\ lvN[p].acct = "closed" /\ lvN[q].seen /\ lvN[q].pred = p
                    THEN [lvN[q] EXCEPT !.carried = TRUE] ELSE lvN[q]]

DeliverRel(c, e) ==
    /\ c \in {"leave", "ts"} /\ e \in evs /\ RelSub(e)
    /\ ~(UnseenPolicy = "defer" /\ e.kind # "Started" /\ ~(IF c = "leave" THEN lv[e.rel].seen ELSE ts[e.rel].seen))
    /\ LET new == RelApply(c, e)
           old == IF c = "leave" THEN lv[e.rel] ELSE ts[e.rel]
           lvN == [lv EXCEPT ![e.rel] = new]
       IN /\ IF c = "leave" THEN lv' = CarryOnEnd(e.rel, lvN) /\ ts' = ts ELSE ts' = [ts EXCEPT ![e.rel] = new] /\ lv' = lv
          /\ ghost' = [redel |-> e.id \in handled[c], regress |-> new.v < old.v,
                       changed |-> Content(new) # Content(old),
                       towardTruth |-> Content(new) = Content(Snap(e.rel))]
    /\ handled' = [handled EXCEPT ![c] = @ \cup {e.id}]
    /\ UNCHANGED <<today, days, ent, phase, unp, pred, ver, pend, pv, evs, idn>>

\* identity: EmployeeSeparated sets the flag, EmployeeReinstated clears it; EmployeeHired and EmployeeCorrected only if the gap options say so
IdnTakes(e) == e.kind \in {"PSep", "PRein"} \/ (e.kind = "PHired" /\ IdentityHires) \/ (e.kind = "PCorr" /\ IdentityCorrection = "recompute")

DeliverIdn(e) ==
    /\ e \in evs /\ e.kind \in PersonKinds
    /\ LET newSep == CASE ~IdnTakes(e) -> idn.sep
                       [] Payload = "read" -> SepTruth
                       [] Payload = "snapshot" -> e.sep
                       [] OTHER -> IF e.pv > idn.v THEN e.sep ELSE idn.sep
           newV == CASE ~IdnTakes(e) -> idn.v
                     [] Payload = "read" -> pv
                     [] Payload = "snapshot" -> e.pv
                     [] OTHER -> IF e.pv > idn.v THEN e.pv ELSE idn.v
       IN /\ idn' = [sep |-> newSep, v |-> newV]
          /\ ghost' = [redel |-> e.id \in handled["idn"], regress |-> newV < idn.v, changed |-> newSep # idn.sep,
                       towardTruth |-> newSep = SepTruth]
    /\ handled' = [handled EXCEPT !["idn"] = @ \cup {e.id}]
    /\ UNCHANGED <<today, days, ent, phase, unp, pred, ver, pend, pv, evs, lv, ts>>

Init ==
    /\ today = 1 /\ days = [r \in Rels |-> {}] /\ ent = [r \in Rels |-> "-"] /\ phase = [r \in Rels |-> "-"]
    /\ unp = [r \in Rels |-> {}] /\ pred = [r \in Rels |-> "-"] /\ ver = [r \in Rels |-> 0] /\ pend = [r \in Rels |-> 0] /\ pv = 0
    /\ evs = {} /\ handled = [c \in Cons |-> {}] /\ lv = [r \in Rels |-> NoRep] /\ ts = [r \in Rels |-> NoRep] /\ idn = [sep |-> FALSE, v |-> 0]
    /\ ghost = [redel |-> FALSE, regress |-> FALSE, changed |-> FALSE, towardTruth |-> TRUE]

Producer ==
    \/ \E r \in Rels, e \in Ents : Hire(r, e)
    \/ \E r \in Rels : Reinstate(r) \/ Separate(r) \/ ReleaseSeparate(r) \/ WithdrawSeparate(r) \/ Regularize(r) \/ UnpaidChange(r) \/ Correct(r)
    \/ \E o \in Rels, n \in Rels, e \in Ents : ChangeEmployer(o, n, e)

Next ==
    \/ Tick
    \/ Producer /\ UNCHANGED <<handled, lv, ts, idn, ghost>>
    \/ \E c \in {"leave", "ts"}, e \in evs : DeliverRel(c, e)
    \/ \E e \in evs : DeliverIdn(e)

Spec == Init /\ [][Next]_vars

\* ---- invariants ----

\* 4.2 item 1: at most one relationship in service per (person, employer) at any instant
OneInServicePerEmployer ==
    \A q, r \in Rels : (q # r /\ ent[q] # "-" /\ ent[q] = ent[r]) => days[q] \cap days[r] = {}

\* every event of the producer has reached every subscribed consumer at least once
Quiet == \A e \in evs, c \in Cons : Subscribes(c, e) => e.id \in handled[c]

ReplicaEq(x, r) == Content(x) = Content(Snap(r))

\* convergence whatever the delivery order: when everything emitted has been delivered, every consumer holds the truth
Converged ==
    Quiet =>
        /\ \A r \in Rels : ReplicaEq(lv[r], r) /\ ReplicaEq(ts[r], r)
        /\ idn.sep = SepTruth

\* no consumer holds service days of a relationship whose start it has not seen (leave and timesheet; identity keeps no per-relationship replica)
NoActOnUnseenStrict == \A r \in Rels : (lv[r].days # {} => lv[r].seen) /\ (ts[r].days # {} => ts[r].seen)

\* a reinstatement (or a new HIRE) that puts the person back in service is visible in every consumer
ReinstatedEverywhere ==
    Quiet /\ InSvcP(today) => ~idn.sep /\ \A r \in Rels : today \in days[r] => (lv[r].acct = "open" /\ today \in ts[r].days)

\* a predecessor's balance reaches the successor, whatever the order of Ended(old) and Started(new)
PredecessorCarried ==
    Quiet /\ CarryFromPred =>
        \A s \in Rels : (pred[s] # "-" /\ today \notin days[pred[s]]) => lv[s].carried

\* the identity flag never says "separated" for a person who was never hired
NoSeparatedStranger == idn.sep => Hired

\* action-level: no consumer goes back in time, and a redelivery only moves a consumer to the current truth or changes nothing
NoRegression == ~ghost.regress
RedeliveryHarmless == ghost.redel => (~ghost.changed \/ ghost.towardTruth)

SizeBound == Cardinality(evs) <= MaxEv

\* ---- reachability checks, expected to be REACHED (invariant violated means the state is reachable) ----
NotBothInService == ~(\E q, r \in Rels : q # r /\ today \in days[q] /\ today \in days[r])
NotChangedEmployer == ~(\E r \in Rels : pred[r] # "-")
NotHeld == ~(\E r \in Rels : pend[r] > 0)
NotReinstated == ~(\E e \in evs : e.kind = "PRein")  \* needs a REINSTATE that puts the person back in service: MaxEv 6
=============================================================================
