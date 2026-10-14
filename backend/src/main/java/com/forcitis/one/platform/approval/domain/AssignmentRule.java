package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.Set;

/**
 * The ONE assignment rule of a BPMN node (RULE-262). Written in the node's forcitis:assignmentRule extension element;
 * evaluated by {@link AssigneeResolver}. departmentVariable names the process variable holding an org unit id; blank
 * means the initiator's own unit (RULE-255/256/257 default to deliveryDeptId in the BPMN, not in code).
 */
public sealed interface AssignmentRule {

    /** All enabled, employed holders of a role (RULE-253). */
    record Role(RoleCode role) implements AssignmentRule {
    }

    /** Head of the unit; when it has none, the head of the nearest ancestor unit (RULE-253/257). */
    record DepartmentHead(String departmentVariable) implements AssignmentRule {
    }

    /** The initiator's 1st or 2nd level direct leader; an incomplete reporting line pushes up (RULE-250). */
    record InitiatorSupervisor(int level) implements AssignmentRule {

        public InitiatorSupervisor {
            if (level != 1 && level != 2) {
                throw new IllegalArgumentException("level must be 1 or 2, was " + level);
            }
        }
    }

    /** Named people (old rule type USER). */
    record NamedUsers(Set<UserId> users) implements AssignmentRule {

        public NamedUsers {
            users = Set.copyOf(users);
        }
    }

    /** Regional receivables manager of the unit (RULE-255). */
    record RegionalReceivablesOwner(String departmentVariable) implements AssignmentRule {
    }

    /** Holders of role BU-002 who sit directly in the unit (RULE-256). */
    record DepartmentDirector(String departmentVariable) implements AssignmentRule {
    }

    /** The people named in a business form field (RULE-258/260). */
    record FormFieldUsers(String field) implements AssignmentRule {
    }

    /** The direct leaders of the people named in a business form field (RULE-259/260). */
    record FormFieldUserSupervisors(String field) implements AssignmentRule {
    }

    /** The people named in an engine or hook variable. */
    record ProcessVariableUsers(String variable) implements AssignmentRule {
    }

    /** The initiator (rework node, old StartUserScript). Never subject to the self-approval guard. */
    record Initiator() implements AssignmentRule {
    }
}
