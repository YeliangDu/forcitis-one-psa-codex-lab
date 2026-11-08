package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.PermissionCode;
import com.forcitis.one.kernel.PermissionDeclaration;
import com.forcitis.one.kernel.PermissionGrantMode;
import java.util.List;

/** Workforce permission metadata; annotation strings and typed values live together. */
public interface WorkforcePermissions {
    String ORG_UNIT_READ = "organization.workforce:org-unit:read";
    PermissionDeclaration ORG_UNIT_READ_DECLARATION = new PermissionDeclaration(new PermissionCode(ORG_UNIT_READ), PermissionGrantMode.INDIVIDUAL, false);
    String SEPARATION_HOLD_APPROVE = "organization.workforce:separation-hold:approve";
    PermissionDeclaration SEPARATION_HOLD_APPROVE_DECLARATION = new PermissionDeclaration(new PermissionCode(SEPARATION_HOLD_APPROVE), PermissionGrantMode.TWO_PERSON_INDIVIDUAL, false);
    String PERSONNEL_COMMAND_CREATE = "organization.workforce:personnel-command:create";
    PermissionDeclaration PERSONNEL_COMMAND_CREATE_DECLARATION = new PermissionDeclaration(new PermissionCode(PERSONNEL_COMMAND_CREATE), PermissionGrantMode.INDIVIDUAL, false);
    String EMPLOYEE_READ = "organization.workforce:employee:read";
    PermissionDeclaration EMPLOYEE_READ_DECLARATION = new PermissionDeclaration(new PermissionCode(EMPLOYEE_READ), PermissionGrantMode.INDIVIDUAL, false);
    String ORG_STRUCTURE_COMMAND_CREATE = "organization.workforce:org-structure-command:create";
    PermissionDeclaration ORG_STRUCTURE_COMMAND_CREATE_DECLARATION = new PermissionDeclaration(new PermissionCode(ORG_STRUCTURE_COMMAND_CREATE), PermissionGrantMode.INDIVIDUAL, false);
    String LEGAL_ENTITY_UPDATE = "organization.workforce:legal-entity:update";
    PermissionDeclaration LEGAL_ENTITY_UPDATE_DECLARATION = new PermissionDeclaration(new PermissionCode(LEGAL_ENTITY_UPDATE), PermissionGrantMode.INDIVIDUAL, false);
    String LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE = "organization.workforce:legal-entity-attribution-correction:create";
    PermissionDeclaration LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE_DECLARATION = new PermissionDeclaration(new PermissionCode(LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE), PermissionGrantMode.INDIVIDUAL, false);
    String LEGAL_ENTITY_ATTRIBUTION_APPROVE = "organization.workforce:legal-entity-attribution:approve";
    PermissionDeclaration LEGAL_ENTITY_ATTRIBUTION_APPROVE_DECLARATION = new PermissionDeclaration(new PermissionCode(LEGAL_ENTITY_ATTRIBUTION_APPROVE), PermissionGrantMode.INDIVIDUAL, false);
    PermissionDeclaration ORG_RECONCILIATION_EXECUTE_DECLARATION = new PermissionDeclaration(new PermissionCode("organization.workforce:org-reconciliation:execute"), PermissionGrantMode.INDIVIDUAL, true);
    PermissionDeclaration SYNC_RUN_READ_DECLARATION = new PermissionDeclaration(new PermissionCode("organization.workforce:sync-run:read"), PermissionGrantMode.INDIVIDUAL, true);
    PermissionDeclaration SYNC_RUN_EXECUTE_DECLARATION = new PermissionDeclaration(new PermissionCode("organization.workforce:sync-run:execute"), PermissionGrantMode.INDIVIDUAL, true);

    List<PermissionDeclaration> DECLARATIONS = List.of(
            ORG_UNIT_READ_DECLARATION,
            SEPARATION_HOLD_APPROVE_DECLARATION,
            PERSONNEL_COMMAND_CREATE_DECLARATION,
            EMPLOYEE_READ_DECLARATION,
            ORG_STRUCTURE_COMMAND_CREATE_DECLARATION,
            LEGAL_ENTITY_UPDATE_DECLARATION,
            LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE_DECLARATION,
            LEGAL_ENTITY_ATTRIBUTION_APPROVE_DECLARATION,
            ORG_RECONCILIATION_EXECUTE_DECLARATION,
            SYNC_RUN_READ_DECLARATION,
            SYNC_RUN_EXECUTE_DECLARATION);
}
