---- MODULE spec ----
EXTENDS Naturals, FiniteSets

\* People: p1 and p2 are administrators (hold super_admin, hence account-role:update); p3 is an ordinary colleague.
P == {"p1", "p2", "p3"}

VARIABLES
    sep,   \* separation progress per person: in = employed / pend = separation in effect, identity has not revoked yet / done = revoked and the account marked separated (#1326: every account is marked, one without roles too; the aggregate then refuses any grant, and the directory view above covers the window before the mark)
    adm,   \* whether the person holds the administrator role (may request, confirm, reject)
    any,   \* whether the person holds any role (whether revocation moves the account version)
    rq     \* the single authorization request

vars == <<sep, adm, any, rq>>

NoReq == [st |-> "none", r |-> "none", s |-> "none", d |-> "none", base |-> "ok",
          rg |-> FALSE, cg |-> FALSE, sg |-> FALSE]

Init ==
    /\ sep = [p \in P |-> "in"]
    /\ adm = [p \in P |-> p \in {"p1", "p2"}]
    /\ any = [p \in P |-> TRUE]
    /\ rq = NoReq

\* Employed holders of the administrator role (directory view: a person whose separation is in effect does not count, revoked or not)
Avail == {q \in P : adm[q] /\ sep[q] = "in"}

\* ReplaceAccountRoles.execute (#1322): a role is not given to a person the directory shows as separated; nobody grants to themselves
Request(r, s) ==
    /\ rq.st = "none"
    /\ adm[r]
    /\ r # s
    /\ ~adm[s]
    /\ sep[s] = "in"
    /\ rq' = [NoReq EXCEPT !.st = "pending", !.r = r, !.s = s]
    /\ UNCHANGED <<sep, adm, any>>

\* ConfirmAuthorizationRequest.execute: confirm and apply in one transaction; a moved base voids the request.
\* #1322: the requester, the confirmer and the subject must be employed in the directory view (a separation in effect counts even before identity revoked);
\* otherwise the confirmation is refused and nothing is written.
Confirm(c) ==
    /\ rq.st = "pending"
    /\ adm[c]
    /\ c # rq.r
    /\ c # rq.s
    /\ sep[c] = "in"
    /\ sep[rq.r] = "in"
    /\ sep[rq.s] = "in"
    /\ IF rq.base = "moved"
       THEN /\ rq' = [rq EXCEPT !.st = "voided", !.d = c]
            /\ UNCHANGED <<sep, adm, any>>
       ELSE /\ rq' = [rq EXCEPT !.st = "confirmed", !.d = c,
                      !.rg = (sep[rq.r] # "in"), !.cg = (sep[c] # "in"), !.sg = (sep[rq.s] # "in")]
            /\ adm' = [adm EXCEPT ![rq.s] = TRUE]
            /\ any' = [any EXCEPT ![rq.s] = TRUE]
            /\ UNCHANGED <<sep>>

\* RejectAuthorizationRequest.execute: any holder (the requester included) may reject
Reject(c) ==
    /\ rq.st = "pending"
    /\ adm[c]
    /\ rq' = [rq EXCEPT !.st = "rejected"]
    /\ UNCHANGED <<sep, adm, any>>

\* A separation command takes effect (workforce judges the administrator floor under the separation lock; below the floor it is held until released; folded into a guard here)
Separate(p) ==
    /\ sep[p] = "in"
    /\ Cardinality(Avail \ {p}) >= 1
    /\ sep' = [sep EXCEPT ![p] = "pend"]
    /\ UNCHANGED <<adm, any, rq>>

\* HandleEmployeeSeparated (#1322): every request that still waits and names the person as subject or requester ends voided, whether or not the person held a role
Closed(p) ==
    /\ adm' = [adm EXCEPT ![p] = FALSE]
    /\ any' = [any EXCEPT ![p] = FALSE]
    /\ rq' = IF rq.st = "pending" /\ (rq.s = p \/ rq.r = p) THEN [rq EXCEPT !.st = "voided"] ELSE rq

\* HandleEmployeeSeparated.on: roles are revoked only when the separation event arrives (the account version moves with it)
Handle(p) ==
    /\ sep[p] = "pend"
    /\ sep' = [sep EXCEPT ![p] = "done"]
    /\ Closed(p)

\* The same separation event delivered again
Redeliver(p) ==
    /\ sep[p] = "done"
    /\ Closed(p)
    /\ UNCHANGED <<sep>>

\* Someone revised the definition of the requested role (the role version moves)
Revise ==
    /\ rq.st = "pending"
    /\ rq.base = "ok"
    /\ rq' = [rq EXCEPT !.base = "moved"]
    /\ UNCHANGED <<sep, adm, any>>

\* Terminal: the request has an outcome; nothing follows
Done ==
    /\ rq.st \in {"confirmed", "voided", "rejected"}
    /\ UNCHANGED vars

Next ==
    \/ \E r \in P, s \in P : Request(r, s)
    \/ \E c \in P : Confirm(c)
    \/ \E c \in P : Reject(c)
    \/ \E p \in P : Separate(p)
    \/ \E p \in P : Handle(p)
    \/ \E p \in P : Redeliver(p)
    \/ Revise
    \/ Done

Spec == Init /\ [][Next]_vars

\* Rule 1: neither the requester nor the subject confirms
ConfirmerIsAnotherPerson ==
    rq.st \in {"confirmed", "voided"} => (rq.d # rq.r /\ rq.d # rq.s)

\* Rule 2: a grant takes effect only on the base it was requested against
OnlyOnUnchangedBase ==
    rq.st = "confirmed" => rq.base = "ok"

\* Rule 3: once revocation is done, the leaver holds no role
LeaverHoldsNoRole ==
    \A p \in P : sep[p] = "done" => (~adm[p] /\ ~any[p])

\* Rule 4: a request whose requester or confirmer has separated at confirmation does not take effect
NoEffectByDepartedRequesterOrConfirmer ==
    rq.st = "confirmed" => (~rq.rg /\ ~rq.cg)

\* Rule 5: no role is granted to a person who has separated
NoGrantToLeaver ==
    rq.st = "confirmed" => ~rq.sg

\* Rule 6: at every moment at least one employed administrator remains
AtLeastOneAdmin ==
    Cardinality(Avail) >= 1
====
