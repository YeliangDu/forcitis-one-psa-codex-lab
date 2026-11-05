---- MODULE spec ----
EXTENDS Integers, FiniteSets

\* Leave balance ledger of organization.leave (docs/architecture/proposals/organization-leave.md, sections 3.5, 4.2, 4.3, 9b).
\* Time is four days; days 1-2 are leave year 1, days 3-4 are leave year 2 (`today` runs 1..5, 5 = after the horizon).
\* Quantity: one unit per leave day (DAY type) or per comp-time minute-bucket (COMP type).
\* Open business treatments (T-*) and the design options of the spec gaps are CONSTANTS; the model never chooses one.

CONSTANTS
    AllowNeg,       \* T-4: a DAY-type hold may exceed the available balance
    Ent,            \* T-1: entitlement of a year account, in days
    CarryCap,       \* T-7: carry-over cap in days (0 = no carry-over)
    ExpL1, ExpL2,   \* T-8: expiry day of comp-time lot l1, l2 (5 = does not expire within the horizon)
    EndCut,         \* T-3: entitlement removed when a relationship ends (proration)
    UnpaidCut,      \* T-5: entitlement removed by an unpaid-leave change
    OverlapRefuse,  \* T-11: a day taken from one relationship is refused while the other holds leave on an overlapping day
    SuccCarry,      \* T-6: the successor relationship (r2) receives the predecessor's (r1) remaining balance
    Unique,         \* section 4.2 retry safety: unique (account, source kind, source id, line kind, consumes_line_id)
    EndedMode,      \* gap G-1: "literal" (section 11.9 as written) or "cancelOutside" (proposed fix)
    WithdrawMode,   \* gap G-2: "literal" (withdraw an approved leave = ReleaseLeaveHold) or "reverse" (proposed fix)
    SealMode,       \* gap G-3: "refuse" (sealed account refuses the line, no rule says where it goes) or "redirect" (ADJUST in the open year, RULE-514)
    AdjustGuard,    \* gap G-5: AdjustLeaveBalance refuses a line that would take the balance below zero (FALSE = section 4.2 as written, no such guard)
    CarryMode,      \* gap G-4: "separate" (carry-over is its own command) or "atClose" (carry-over inside CloseLeaveYear)
    Spans,          \* leave spans checked, by name: "s12" = days 1..2, and so on (TLC configs cannot hold tuples)
    TypesOn,        \* leave types the run lets requests use: subset of {"DAY", "COMP"}
    Rels,           \* relationships present in the run (subset of {"r1", "r2"})
    Reqs,           \* leave requests the run may submit (subset of {"q1", "q2"})
    UseHR,          \* the run lets HR post one AdjustLeaveBalance line
    MaxLines,       \* state constraint: runs only explore states with at most this many ledger lines
    EndAt,          \* day a relationship ends (0 = does not end in the run)
    UnpaidY,        \* leave year an unpaid-leave change concerns (0 = none in the run)
    CorrY, CorrD, CorrSh  \* a history-only correction: year (0 = none), entitlement delta, start-date shift

R == {"r1", "r2"}
Q == {"q1", "q2"}
Lots == {"l1", "l2"}
LotOwner == [l \in Lots |-> "r1"]
Exp == [l \in Lots |-> IF l = "l1" THEN ExpL1 ELSE ExpL2]
LotQty == 2
Types == {"DAY", "COMP"}
SpanOf == [s11 |-> <<1, 1>>, s12 |-> <<1, 2>>, s22 |-> <<2, 2>>, s23 |-> <<2, 3>>, s33 |-> <<3, 3>>, s34 |-> <<3, 4>>, s44 |-> <<4, 4>>]
Start0 == [r \in R |-> IF r = "r1" THEN 1 ELSE 2]
Accts == {<<r, y>> : r \in R, y \in 0..2}     \* y = 0 is the rolling (comp-time) account
NoAcct == <<"x", 9>>
EvK == {"started", "ended", "unpaid", "corrected"}

VARIABLES
    today,     \* 1..5
    wf,        \* workforce truth per relationship (read by api at command time): start, end (0 = in service), unpaid, corrected
    sent,      \* relationship events published per relationship
    handled,   \* relationship events the leave side has handled at least once
    eff,       \* payload of each event: affected year y and entitlement delta d
    acct,      \* account state: none / OPEN / SEALED
    lines,     \* ledger lines (append only)
    rq,        \* leave requests
    apr,       \* approval platform decision per request
    hr         \* the single HR adjustment has been used

vars == <<today, wf, sent, handled, eff, acct, lines, rq, apr, hr>>

YearOf(d) == IF d <= 2 THEN 1 ELSE 2
Min(a, b) == IF a < b THEN a ELSE b
NoReq == [st |-> "none", r |-> "r1", t |-> "DAY", sp |-> <<1, 1>>, parts |-> {}]

---------------------------------------------------------------------------
\* Ledger (section 4.2)

\* A line: account, kind, source id, consumes_line_id (lot), quantity, effective day, written-on-sealed flag, occurrence number
L(a, k, s, c, q) == [a |-> a, k |-> k, s |-> s, c |-> c, q |-> q, d |-> today, n |-> 0, sl |-> (acct[a] = "SEALED")]
Key(l) == <<l.a, l.k, l.s, l.c>>
Cnt(S, l) == Cardinality({x \in S : Key(x) = Key(l)})

\* Insert with the unique key: a repeat of the same key is dropped when Unique, else it becomes occurrence n+1 (capped at 2)
Put(S, l) ==
    IF l.q = 0 THEN S
    ELSE IF Cnt(S, l) > 0 /\ Unique THEN S
    ELSE IF Cnt(S, l) >= 2 THEN S
    ELSE S \cup {[l EXCEPT !.n = Cnt(S, l) + 1]}

RECURSIVE PutAll(_, _)
PutAll(S, Ls) == IF Ls = {} THEN S
                 ELSE LET x == CHOOSE x \in Ls : TRUE IN PutAll(Put(S, x), Ls \ {x})

RECURSIVE SumQ(_)
SumQ(S) == IF S = {} THEN 0 ELSE LET x == CHOOSE x \in S : TRUE IN x.q + SumQ(S \ {x})

Sm(a, k, c) == SumQ({l \in lines : l.a = a /\ l.k = k /\ l.c = c})
SmS(a, k, s, c) == SumQ({l \in lines : l.a = a /\ l.k = k /\ l.s = s /\ l.c = c})

\* Derived balance of an account (c = "-") or of one lot (section 4.2):
\*   sum(GRANT, CARRY_IN, REVERSE_USE, ADJUST) - sum(HOLD net of RELEASE and USE, USE, EXPIRE, CARRY_OUT)
\* The USE term cancels the "net of USE" part, so the balance is grants - holds + releases + reverses - expiries - carry-outs.
Avail(a, c) ==
    Sm(a, "GRANT", c) + Sm(a, "CARRY_IN", c) + Sm(a, "REVERSE", c) + Sm(a, "ADJUST", c)
    - (Sm(a, "HOLD", c) - Sm(a, "RELEASE", c) - Sm(a, "USE", c)) - Sm(a, "USE", c)
    - Sm(a, "EXPIRE", c) - Sm(a, "CARRY_OUT", c)

AH(a, s, c) == SmS(a, "HOLD", s, c) - SmS(a, "RELEASE", s, c) - SmS(a, "USE", s, c)   \* active hold of request s
UN(a, s, c) == SmS(a, "USE", s, c) - SmS(a, "REVERSE", s, c)                          \* minutes still used by request s

IsDead(c) == c # "-" /\ today > Exp[c]
Granted(l) == \E x \in lines : x.k = "GRANT" /\ x.c = l /\ x.a = <<LotOwner[l], 0>>
Held(q) == \E p \in rq[q].parts : AH(p.a, q, p.c) > 0
HeldOn(a) == \E q \in Q : \E p \in rq[q].parts : p.a = a /\ AH(a, q, p.c) > 0

\* Where a correction goes when its year account is sealed (RULE-514: corrections are new lines in the open year)
AdjTarget(r, y) ==
    IF acct[<<r, y>>] = "OPEN" THEN <<r, y>>
    ELSE IF SealMode = "redirect" /\ y = 1 /\ acct[<<r, 1>>] = "SEALED" /\ acct[<<r, 2>>] = "OPEN" THEN <<r, 2>>
    ELSE NoAcct

\* Lines that give a request's minutes back. Every return to a lot whose expiry has passed also writes EXPIRE (RULE-516).
RelPart(q, p) ==
    IF AH(p.a, q, p.c) > 0
    THEN {L(p.a, "RELEASE", q, p.c, p.q)} \cup (IF IsDead(p.c) THEN {L(p.a, "EXPIRE", q, p.c, p.q)} ELSE {})
    ELSE {}
RevPart(q, p) ==
    IF UN(p.a, q, p.c) > 0 /\ acct[p.a] # "SEALED"
    THEN {L(p.a, "REVERSE", q, p.c, p.q)} \cup (IF IsDead(p.c) THEN {L(p.a, "EXPIRE", q, p.c, p.q)} ELSE {})
    ELSE IF UN(p.a, q, p.c) > 0 /\ SealMode = "redirect" /\ AdjTarget(p.a[1], p.a[2]) # NoAcct
    THEN {L(AdjTarget(p.a[1], p.a[2]), "ADJUST", q, "-", p.q)}
    ELSE {}
Blocked(q, p) == UN(p.a, q, p.c) > 0 /\ acct[p.a] = "SEALED" /\ AdjTarget(p.a[1], p.a[2]) = NoAcct
RelSet(q) == UNION {RelPart(q, p) : p \in rq[q].parts}
RetSet(q) == UNION {RelPart(q, p) \cup RevPart(q, p) : p \in rq[q].parts}
RetOK(q) == \A p \in rq[q].parts : ~Blocked(q, p)

---------------------------------------------------------------------------
\* Requests (section 4.3)

InSvc(r, a, b) == wf[r].start <= a /\ (wf[r].end = 0 \/ b <= wf[r].end)
Overlap(s1, s2) == s1[1] <= s2[2] /\ s2[1] <= s1[2]
YDays(y, sp) == Cardinality({d \in sp[1]..sp[2] : YearOf(d) = y})

DayParts(r, sp) == {[a |-> <<r, y>>, c |-> "-", q |-> YDays(y, sp)] : y \in {y2 \in 1..2 : YDays(y2, sp) > 0}}

\* COMP: draw earliest-expiry first from the relationship's lots that are valid on every leave day (expiry not before the last day)
CompPlan(r, last, need) ==
    LET vs == {l \in Lots : LotOwner[l] = r /\ Granted(l) /\ Exp[l] >= last /\ Avail(<<r, 0>>, l) > 0}
        f == IF Cardinality(vs) = 2 THEN (IF Exp["l1"] <= Exp["l2"] THEN "l1" ELSE "l2") ELSE CHOOSE l \in vs : TRUE
        g == CHOOSE l \in vs \ {f} : TRUE
        take1 == Min(Avail(<<r, 0>>, f), need)
    IN IF vs = {} THEN {}
       ELSE IF Cardinality(vs) = 1 THEN
            (IF Avail(<<r, 0>>, f) >= need THEN {[a |-> <<r, 0>>, c |-> f, q |-> need]} ELSE {})
       ELSE IF Avail(<<r, 0>>, g) >= need - take1
            THEN {[a |-> <<r, 0>>, c |-> f, q |-> take1]} \cup (IF need - take1 > 0 THEN {[a |-> <<r, 0>>, c |-> g, q |-> need - take1]} ELSE {})
            ELSE {}

\* SubmitLeave (4.3) + HoldLeaveBalance (4.2): refused on a day the relationship is not in service (4.3), refused when a DAY hold would exceed
\* the available balance unless T-4 allows (4.2), and a DAY hold needs an OPEN year account (never implicit, 4.2 OpenLeaveYearAccount).
\* A year-end-spanning request holds each year on that year's account (4.2 States). COMP draws earliest expiry first from lots valid on
\* every leave day (4.2 derived balance, RULE-516). T-11 refusal of overlapping leave of the other relationship (section 9).
Submit(q, r, t, sp) ==
    LET parts == IF t = "DAY" THEN DayParts(r, sp) ELSE CompPlan(r, sp[2], sp[2] - sp[1] + 1)
    IN /\ rq[q].st = "none"
       /\ InSvc(r, sp[1], sp[2])
       /\ ~(OverlapRefuse /\ \E x \in Q \ {q} : rq[x].st \in {"IN_APPROVAL", "APPROVED"} /\ rq[x].r # r /\ Overlap(sp, rq[x].sp))
       /\ parts # {}
       /\ \A p \in parts : (t = "DAY" => acct[p.a] = "OPEN" /\ (AllowNeg \/ Avail(p.a, "-") >= p.q))
       /\ rq' = [rq EXCEPT ![q] = [st |-> "IN_APPROVAL", r |-> r, t |-> t, sp |-> sp, parts |-> parts]]
       /\ lines' = PutAll(lines, {L(p.a, "HOLD", q, p.c, p.q) : p \in parts})
       /\ UNCHANGED <<today, wf, sent, handled, eff, acct, apr, hr>>

\* The approval platform delivers one result per request (4.3 Subscribed: ApprovalCompleted); arrival order is free.
Decide(q, o) ==
    /\ rq[q].st # "none"
    /\ apr[q] = "none"
    /\ apr' = [apr EXCEPT ![q] = o]
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, lines, rq, hr>>

\* ApproveLeave / RejectLeave from ApprovalCompleted. ConsumeLeaveHold needs the active hold; without it the command refuses.
ApplyResult(q) ==
    /\ rq[q].st = "IN_APPROVAL"
    /\ apr[q] # "none"
    /\ \/ /\ apr[q] = "approved"
          /\ \A p \in rq[q].parts : AH(p.a, q, p.c) = p.q
          /\ lines' = PutAll(lines, {L(p.a, "USE", q, p.c, p.q) : p \in rq[q].parts})
          /\ rq' = [rq EXCEPT ![q].st = "APPROVED"]
       \/ /\ apr[q] = "rejected"
          /\ lines' = PutAll(lines, RelSet(q))
          /\ rq' = [rq EXCEPT ![q].st = "REJECTED"]
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, apr, hr>>

\* WithdrawLeave (4.3 Commands, 4.2 ReleaseLeaveHold / ReverseLeaveUse): gap G-2 is the choice WithdrawMode.
\* WithdrawLeave: in approval -> ReleaseLeaveHold; approved before start -> see WithdrawMode
Withdraw(q) ==
    \/ /\ rq[q].st = "IN_APPROVAL"
       /\ lines' = PutAll(lines, RelSet(q))
       /\ rq' = [rq EXCEPT ![q].st = "WITHDRAWN"]
       /\ UNCHANGED <<today, wf, sent, handled, eff, acct, apr, hr>>
    \/ /\ rq[q].st = "APPROVED"
       /\ today < rq[q].sp[1]
       /\ IF WithdrawMode = "reverse" THEN RetOK(q) /\ lines' = PutAll(lines, RetSet(q))
          ELSE lines' = PutAll(lines, RelSet(q))
       /\ rq' = [rq EXCEPT ![q].st = "WITHDRAWN"]
       /\ UNCHANGED <<today, wf, sent, handled, eff, acct, apr, hr>>

\* RecallLeave (xiaojia): fact registration after the start; reverses the used minutes
Recall(q) ==
    /\ rq[q].st = "APPROVED"
    /\ today >= rq[q].sp[1]
    /\ RetOK(q)
    /\ lines' = PutAll(lines, RetSet(q))
    /\ rq' = [rq EXCEPT ![q].st = "RECALLED"]
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, apr, hr>>

\* The same command delivered again after it already ran: only the unique key stops a second line
\* 4.2 Commands: "Retry-safe: unique (account, source kind, source id, line kind, consumes_line_id)"; Unique = FALSE shows what the key prevents.
Redo(q, k) ==
    /\ \E x \in lines : x.k = k /\ x.s = q
    /\ lines' = PutAll(lines, UNION {{L(p.a, k, q, p.c, p.q)} : p \in {p2 \in rq[q].parts : SmS(p2.a, k, q, p2.c) > 0}})
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, rq, apr, hr>>

---------------------------------------------------------------------------
\* Workforce truth and the relationship events (ADR-0086 decision 6; spec 3.5)

\* Workforce facts per relationship, delivered as the five events of ADR-0086 decision 6 (employment-relationship.md 6.1); the leave side
\* learns them only through Deliver.
\* EmploymentRelationshipEnded (3.5, 4.2 Consumes): entitlement cut EndCut (T-3).
Separate(r, e) ==
    /\ e = EndAt /\ e > 0
    /\ wf[r].end = 0 /\ e >= wf[r].start
    /\ wf' = [wf EXCEPT ![r].end = e]
    /\ sent' = [sent EXCEPT ![r] = @ \cup {"ended"}]
    /\ eff' = [eff EXCEPT ![r]["ended"] = [y |-> YearOf(e), d |-> -EndCut]]
    /\ UNCHANGED <<today, handled, acct, lines, rq, apr, hr>>

\* EmploymentRelationshipUnpaidLeaveChanged (3.5, 4.2 Consumes): entitlement cut UnpaidCut (T-5).
Unpaid(r, y) ==
    /\ y = UnpaidY /\ y > 0
    /\ ~wf[r].unp
    /\ wf' = [wf EXCEPT ![r].unp = TRUE]
    /\ sent' = [sent EXCEPT ![r] = @ \cup {"unpaid"}]
    /\ eff' = [eff EXCEPT ![r]["unpaid"] = [y |-> y, d |-> -UnpaidCut]]
    /\ UNCHANGED <<today, handled, acct, lines, rq, apr, hr>>

\* EmploymentRelationshipCorrected (3.5, 4.2 Consumes).
\* A history-only correction: the start date may move later, the entitlement moves by d
Correct(r, y, d, sh) ==
    /\ y = CorrY /\ y > 0 /\ d = CorrD /\ sh = CorrSh
    /\ ~wf[r].cor
    /\ wf[r].start + sh <= 3
    /\ wf' = [wf EXCEPT ![r].cor = TRUE, ![r].start = @ + sh]
    /\ sent' = [sent EXCEPT ![r] = @ \cup {"corrected"}]
    /\ eff' = [eff EXCEPT ![r]["corrected"] = [y |-> y, d |-> d]]
    /\ UNCHANGED <<today, handled, acct, lines, rq, apr, hr>>

Out(q, r) == rq[q].r = r /\ rq[q].st \in {"IN_APPROVAL", "APPROVED"} /\ ~InSvc(r, rq[q].sp[1], rq[q].sp[2])

\* 4.2 Consumes: each event recomputes the entitlement as a new ADJUST line with its basis; 4.2 OpenLeaveYearAccount on Started;
\* gap G-1 is EndedMode (what the leave side does with requests that stick out of the service period); gap G-3 is SealMode.
\* The handler of one relationship event (at least once; any order)
Deliver(r, k) ==
    /\ k \in sent[r]
    /\ handled' = [handled EXCEPT ![r] = @ \cup {k}]
    /\ IF k = "started"
       THEN \* OpenLeaveYearAccount for the start year (never implicit by a hold)
            LET a == <<r, YearOf(Start0[r])>> IN
            /\ acct' = [acct EXCEPT ![a] = IF @ = "none" THEN "OPEN" ELSE @]
            /\ lines' = IF acct[a] = "none" THEN PutAll(lines, {L(a, "GRANT", "ent", "-", Ent)}) ELSE lines
            /\ rq' = rq
       ELSE \* recompute of entitlement as a new ADJUST line, plus the hold handling of the ended relationship
            LET e == eff[r][k]
                tgt == AdjTarget(r, e.y)
                cancel == IF EndedMode = "cancelOutside" /\ k \in {"ended", "corrected"} THEN {q \in Q : Out(q, r)} ELSE {}
                relall == IF EndedMode = "literal" /\ k = "ended" THEN {q \in Q : rq[q].r = r /\ rq[q].st = "IN_APPROVAL"} ELSE {}
                aff == cancel \cup relall
            IN /\ (e.d = 0 \/ tgt # NoAcct)
               /\ \A q \in aff : RetOK(q)
               /\ lines' = PutAll(lines, (IF e.d = 0 THEN {} ELSE {L(tgt, "ADJUST", k, "-", e.d)}) \cup UNION {RetSet(q) : q \in aff})
               /\ rq' = [q \in Q |-> IF q \in cancel THEN [rq[q] EXCEPT !.st = "CANCELLED"] ELSE rq[q]]
               /\ acct' = acct
    /\ UNCHANGED <<today, wf, sent, eff, apr, hr>>

---------------------------------------------------------------------------
\* Accounts, lots, year end (section 4.2)

\* 4.2 OpenLeaveYearAccount "on year start for the rest": opens the next year account of a relationship that is still in service.
OpenYear(r) ==
    /\ acct[<<r, 2>>] = "none"
    /\ today >= 3
    /\ "started" \in handled[r]
    /\ wf[r].end = 0 \/ wf[r].end >= 3
    /\ acct' = [acct EXCEPT ![<<r, 2>>] = "OPEN"]
    /\ lines' = PutAll(lines, {L(<<r, 2>>, "GRANT", "ent", "-", Ent)})
    /\ UNCHANGED <<today, wf, sent, handled, eff, rq, apr, hr>>

\* 4.2 GrantCompTime: a lot in the rolling account, expiry from the rule version (T-8).
GrantComp(l) ==
    /\ ~Granted(l)
    /\ today <= Exp[l]
    /\ wf[LotOwner[l]].end = 0
    /\ acct' = [acct EXCEPT ![<<LotOwner[l], 0>>] = "OPEN"]
    /\ lines' = PutAll(lines, {L(<<LotOwner[l], 0>>, "GRANT", l, l, LotQty)})
    /\ UNCHANGED <<today, wf, sent, handled, eff, rq, apr, hr>>

\* 4.2 ExpireCompTime (RULE-516).
\* ExpireCompTime: the unused balance of a dead lot
ExpireComp(l) ==
    /\ Granted(l)
    /\ today > Exp[l]
    /\ Avail(<<LotOwner[l], 0>>, l) > 0
    /\ lines' = PutAll(lines, {L(<<LotOwner[l], 0>>, "EXPIRE", "sweep", l, Avail(<<LotOwner[l], 0>>, l))})
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, rq, apr, hr>>

Continues(r) == wf[r].end = 0 \/ wf[r].end >= 3
CarryAmt(r) == Min(Avail(<<r, 1>>, "-"), CarryCap)

\* 4.2 carry-over (T-7). CarryMode "separate" = its own command; "atClose" = inside CloseLeaveYear (gap G-4).
Carry(r) ==
    /\ CarryMode = "separate"
    /\ today >= 3
    /\ acct[<<r, 1>>] = "OPEN" /\ acct[<<r, 2>>] = "OPEN"
    /\ CarryAmt(r) > 0
    /\ lines' = PutAll(lines, {L(<<r, 1>>, "CARRY_OUT", "carry", "-", CarryAmt(r)), L(<<r, 2>>, "CARRY_IN", "carry", "-", CarryAmt(r))})
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, rq, apr, hr>>

\* 4.2 CloseLeaveYear: refuses while a hold of that year is active; OPEN -> SEALED; a sealed year takes no line (ADR-0079:123).
Close(r) ==
    /\ today >= 3
    /\ acct[<<r, 1>>] = "OPEN"
    /\ ~HeldOn(<<r, 1>>)
    /\ IF CarryMode = "atClose" /\ CarryCap > 0 /\ Continues(r)
       THEN /\ acct[<<r, 2>>] = "OPEN"
            /\ lines' = PutAll(lines, IF CarryAmt(r) > 0
                                      THEN {L(<<r, 1>>, "CARRY_OUT", "carry", "-", CarryAmt(r)), L(<<r, 2>>, "CARRY_IN", "carry", "-", CarryAmt(r))}
                                      ELSE {})
       ELSE lines' = lines
    /\ acct' = [acct EXCEPT ![<<r, 1>>] = "SEALED"]
    /\ UNCHANGED <<today, wf, sent, handled, eff, rq, apr, hr>>

\* 4.2 AdjustLeaveBalance (HR manual line, signed). AdjustGuard is gap G-5: a guard against a result below zero.
AdjustHR(r, y, d) ==
    /\ UseHR
    /\ ~hr
    /\ AdjTarget(r, y) # NoAcct
    /\ (~AdjustGuard \/ AllowNeg \/ Avail(AdjTarget(r, y), "-") + d >= 0)
    /\ lines' = PutAll(lines, {L(AdjTarget(r, y), "ADJUST", "hr1", "-", d)})
    /\ hr' = TRUE
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, rq, apr>>

\* T-6: the predecessor's remaining balance moves to the successor's account of the same year
Succ ==
    /\ SuccCarry /\ CarryCap > 0
    /\ wf["r1"].end # 0 /\ "ended" \in handled["r1"]
    /\ \E y \in 1..2 :
         /\ acct[<<"r1", y>>] = "OPEN" /\ acct[<<"r2", y>>] = "OPEN"
         /\ Min(Avail(<<"r1", y>>, "-"), CarryCap) > 0
         /\ lines' = PutAll(lines, {L(<<"r1", y>>, "CARRY_OUT", "succ", "-", Min(Avail(<<"r1", y>>, "-"), CarryCap)),
                                    L(<<"r2", y>>, "CARRY_IN", "succ", "-", Min(Avail(<<"r1", y>>, "-"), CarryCap))})
    /\ UNCHANGED <<today, wf, sent, handled, eff, acct, rq, apr, hr>>

\* Time passes one day; days 1-2 are leave year 1.
Tick == /\ today < 5 /\ today' = today + 1 /\ UNCHANGED <<wf, sent, handled, eff, acct, lines, rq, apr, hr>>

Init ==
    /\ today = 1
    /\ wf = [r \in R |-> [start |-> Start0[r], end |-> 0, unp |-> FALSE, cor |-> FALSE]]
    /\ sent = [r \in R |-> IF r \in Rels THEN {"started"} ELSE {}]
    /\ handled = [r \in R |-> {}]
    /\ eff = [r \in R |-> [k \in EvK |-> [y |-> 1, d |-> 0]]]
    /\ acct = [a \in Accts |-> "none"]
    /\ lines = {}
    /\ rq = [q \in Q |-> NoReq]
    /\ apr = [q \in Q |-> "none"]
    /\ hr = FALSE

Next ==
    \/ Tick
    \/ \E r \in Rels, e \in 1..4 : Separate(r, e)
    \/ \E r \in Rels, y \in 1..2 : Unpaid(r, y)
    \/ \E r \in Rels, y \in 1..2, d \in {-1, 1}, sh \in {0, 1} : Correct(r, y, d, sh)
    \/ \E r \in Rels, k \in EvK : Deliver(r, k)
    \/ \E r \in Rels : OpenYear(r)
    \/ \E l \in Lots : GrantComp(l)
    \/ \E l \in Lots : ExpireComp(l)
    \/ \E q \in Reqs, r \in Rels, t \in TypesOn, sn \in Spans : Submit(q, r, t, SpanOf[sn])
    \/ \E q \in Reqs, o \in {"approved", "rejected"} : Decide(q, o)
    \/ \E q \in Reqs : ApplyResult(q)
    \/ \E q \in Reqs : Withdraw(q)
    \/ \E q \in Reqs : Recall(q)
    \/ \E q \in Reqs, k \in {"HOLD", "USE", "RELEASE", "REVERSE"} : Redo(q, k)
    \/ \E r \in Rels : Carry(r)
    \/ \E r \in Rels : Close(r)
    \/ \E r \in Rels, y \in 1..2, d \in {-1, 1} : AdjustHR(r, y, d)
    \/ Succ

Spec == Init /\ [][Next]_vars

---------------------------------------------------------------------------
\* Invariants. Each names the spec clause it checks.

SizeBound == Cardinality(lines) <= MaxLines

LotsAndAccts == {"-", "l1", "l2"}

\* 4.2 derived balance: no request releases or uses more than it holds, and none reverses more than it used
LedgerSumsConsistent ==
    \A a \in Accts, q \in Q, c \in LotsAndAccts : AH(a, q, c) >= 0 /\ UN(a, q, c) >= 0

\* 4.2 / RULE-514 / ADR-0079:123: a sealed year takes no line
NoWriteToSealed == \A l \in lines : ~l.sl

\* 4.2: CloseLeaveYear refuses while a hold of that year is active
NoHoldAtSeal == \A a \in Accts : acct[a] = "SEALED" => ~HeldOn(a)

\* 4.2 retry safety: a line kind is applied once per (account, source, consumes_line_id)
LineKeyOnce == \A l \in lines : l.n = 1

\* 4.2 / RULE-516: minutes returned to a lot whose expiry has passed come with an EXPIRE line in the same step
DeadLotReturnExpires ==
    \A l \in lines :
        (l.k \in {"RELEASE", "REVERSE"} /\ l.c # "-" /\ l.d > Exp[l.c]) =>
            \E x \in lines : x.k = "EXPIRE" /\ x.a = l.a /\ x.c = l.c /\ x.s = l.s /\ x.d = l.d /\ x.q = l.q

Settled(r) == sent[r] \subseteq handled[r]

\* 4.3 SubmitLeave + done-when 9: once the leave side has handled every relationship event, no hold lies on a day outside the service period
NoHoldOutsideService ==
    \A r \in R, q \in Q : (Settled(r) /\ rq[q].r = r /\ Held(q)) => InSvc(r, rq[q].sp[1], rq[q].sp[2])

\* 4.2 / 4.3: a request touches only accounts and lots of its own relationship
OwnRelationshipOnly ==
    /\ \A q \in Q : \A p \in rq[q].parts : p.a[1] = rq[q].r
    /\ \A l \in lines : l.s \in Q => l.a[1] = rq[l.s].r

\* T-4: year accounts stay at or above zero unless the option allows it; lots never go below zero
NotBelowZero ==
    /\ \A r \in R, y \in 1..2 : AllowNeg \/ Avail(<<r, y>>, "-") >= 0
    /\ \A l \in Lots : Avail(<<LotOwner[l], 0>>, l) >= 0

\* 4.3 RULE-512: a rejected, withdrawn or cancelled request gives back all it held or used (sealed years excepted: RULE-514)
TerminalGivesBack ==
    \A q \in Q : rq[q].st \in {"REJECTED", "WITHDRAWN", "CANCELLED"} =>
        \A p \in rq[q].parts : AH(p.a, q, p.c) = 0 /\ (UN(p.a, q, p.c) = 0 \/ acct[p.a] = "SEALED")

\* 4.3 ApproveLeave: an approved request has used exactly what it held
ApprovedHasUse ==
    \A q \in Q : rq[q].st = "APPROVED" => \A p \in rq[q].parts : UN(p.a, q, p.c) = p.q

\* 4.3 ApplyApprovalResult behaviour (done-when 6): an approval result that arrived can be applied
ApprovalResultApplicable ==
    \A q \in Q : (rq[q].st = "IN_APPROVAL" /\ apr[q] = "approved") => \A p \in rq[q].parts : AH(p.a, q, p.c) = p.q

\* 4.2 carry-out / carry-in: what leaves one account enters another
CarryBalanced ==
    SumQ({l \in lines : l.k = "CARRY_OUT"}) = SumQ({l \in lines : l.k = "CARRY_IN"})

\* T-7: a sealed year does not strand minutes that the carry-over rule would have carried
NoStrandedCarry ==
    \A r \in R : (CarryCap > 0 /\ acct[<<r, 1>>] = "SEALED" /\ Continues(r)) =>
        ~(Avail(<<r, 1>>, "-") > 0 /\ Sm(<<r, 1>>, "CARRY_OUT", "-") < CarryCap)

\* 3.5 / RULE-515: every relationship event that reached the leave side moved the entitlement once
EntitlementFollowsEvents ==
    \A r \in R, k \in {"ended", "unpaid", "corrected"} :
        (k \in handled[r] /\ eff[r][k].d # 0) =>
            \E l \in lines : l.k = "ADJUST" /\ l.s = k /\ l.a[1] = r

\* T-11: with the refusing option, two relationships of one person never hold overlapping leave
NoCrossOverlap ==
    OverlapRefuse =>
        \A x, y \in Q : (x # y /\ rq[x].st \in {"IN_APPROVAL", "APPROVED"} /\ rq[y].st \in {"IN_APPROVAL", "APPROVED"} /\ rq[x].r # rq[y].r)
                        => ~Overlap(rq[x].sp, rq[y].sp)

\* Reachability checks (an expected-to-fail invariant means the situation is reachable)
\* 4.2 / RULE-514: a recall that finds its year sealed
NotRecallBlocked ==
    \A q \in Q : ~(rq[q].st = "APPROVED" /\ today >= rq[q].sp[1] /\ ~RetOK(q))

\* 3.5 / 4.2: an event whose target account is sealed or not yet open waits forever at the end of the horizon
NotEventStuck ==
    \A r \in R, k \in {"ended", "unpaid", "corrected"} :
        ~(today = 5 /\ k \in sent[r] /\ k \notin handled[r] /\ eff[r][k].d # 0 /\ AdjTarget(r, eff[r][k].y) = NoAcct)

\* 4.3: an approved leave on a day the relationship is not in service (no rule reverses it)
NotApprovedOutside ==
    \A r \in R, q \in Q : ~(Settled(r) /\ rq[q].r = r /\ rq[q].st = "APPROVED" /\ ~InSvc(r, rq[q].sp[1], rq[q].sp[2]))

====
