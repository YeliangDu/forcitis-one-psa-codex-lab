package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.platform.identity.api.RoleCode;

public sealed interface AssignmentRule {

    record Role(RoleCode role) implements AssignmentRule {
    }

    record DepartmentHead(String departmentVariable) implements AssignmentRule {
    }

    record InitiatorSupervisor(int level) implements AssignmentRule {

        public InitiatorSupervisor {
            if (level != 1 && level != 2) {
                throw new IllegalArgumentException("level must be 1 or 2, was " + level);
            }
        }
    }

    record FormFieldUsers(String field) implements AssignmentRule {
    }
}
