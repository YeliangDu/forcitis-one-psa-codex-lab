// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
export interface paths {
    "/api/v1/organization/employee-legal-entities/{employeeId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ofEmployee"];
        put: operations["attributeEmployee"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/employee-legal-entities/{employeeId}:correct": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["correctEmployee"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/employee-legal-entities/{employeeId}:end": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["endEmployee"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/legal-entities": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_2"];
        put?: never;
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/legal-entities/{legalEntityId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get_2"];
        put: operations["update_1"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/org-unit-legal-entities/{orgUnitId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ofOrgUnit"];
        put: operations["attributeOrgUnit"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/org-unit-legal-entities/{orgUnitId}:correct": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["correctOrgUnit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/org-unit-legal-entities/{orgUnitId}:end": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["endOrgUnit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/org-units": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_11"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/org-units/{orgUnitId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get_5"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/sync-runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_10"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/sync-runs/{syncRunId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/sync-runs/{syncRunId}:release": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["release_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/sync-runs:reconcile": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reconcile"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/work-calendars": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["maintainedYears"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/organization/work-calendars/{calendarYear}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["view"];
        put: operations["maintain"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-task-overviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_9"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-task-records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_8"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_7"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks/{taskId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks/{taskId}:claim": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["claim"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks/{taskId}:decide": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["decide"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks/{taskId}:reassign": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reassign"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks/{taskId}:release": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["release"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks/{taskId}:return": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["returnTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/approval-tasks/{taskId}:transfer": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["transfer"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/audit-entries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** @description Newest first, in the fixed order created_at desc, id desc (id only breaks ties between entries of the same instant). The order cannot be chosen, so there is no sort parameter. */
        get: operations["list_6"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/audit-entries:export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** @description Takes the same filter parameters as the list and answers every match in the same fixed order, created_at desc, id desc, without paging. Answers 422 PLATFORM_AUDIT_EXPORT_TOO_LARGE when more entries match than the export limit allows. */
        get: operations["export"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/authorization-requests": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["pending"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/authorization-requests/{requestId}:confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirm_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/authorization-requests/{requestId}:reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reject_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/legacy-numbers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_5"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/legacy-numbers:resolve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["resolve"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/login-states": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["beginWecomLogin"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/numbering-rules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/numbering-rules/{documentType}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get_1"];
        put: operations["put"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/numbering-rules/{documentType}:confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirm_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/numbering-rules/{documentType}:confirm-finance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirmByFinance"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/own-login-records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_1"];
        put?: never;
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/roles/{roleId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get"];
        put: operations["update"];
        post?: never;
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:confirm-identity": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirmIdentity"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:current": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["current"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:emergency-login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["loginAsEmergencyAdmin"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:enter-administrator-mode": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["enter"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:exit-administrator-mode": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["exit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:sso-login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["loginWithSso"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:ticket-login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["loginWithSignedTicket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/sessions:wecom-login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["loginWithWecom"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/source-references": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post: operations["register"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/source-references/{sourceReferenceId}:confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirm"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/source-references/{sourceReferenceId}:reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/user-accounts/{userId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["account"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/user-accounts/{userId}:disable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["disable"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/user-accounts/{userId}:enable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["enable"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/user-accounts/{userId}:force-logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["forceLogout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/user-accounts/{userId}:replace-roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["replaceRoles"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @enum {string} */
        AccessScopeKind: "ALL" | "CUSTOM_ORG_UNITS" | "OWN_ORG_UNIT" | "OWN_ORG_UNIT_SUBTREE" | "SELF" | "SELF_AND_DIRECT_REPORTS";
        AdministratorModeResponse: {
            /** Format: date-time */
            administratorModeExpiresAt: string;
        };
        AttributeLegalEntityRequest: {
            /** Format: date */
            effectiveFrom?: string;
            /** Format: uuid */
            legalEntityId?: string;
        };
        AuditEntryResponse: {
            action: string;
            /** Format: uuid */
            actorId?: string;
            afterText?: string;
            beforeText?: string;
            changeReason?: string;
            /** Format: date-time */
            createdAt: string;
            detail?: string;
            /** Format: uuid */
            entryId: string;
            loginMethod?: string;
            /** Format: uuid */
            onBehalfOfId?: string;
            outcome?: components["schemas"]["AuditOutcome"] | (string & {});
            sourceAddress?: string;
            /** Format: uuid */
            subjectId?: string;
            subjectKind: components["schemas"]["AuditSubjectKind"] | (string & {});
            /** Format: uuid */
            traceNumber?: string;
        };
        /** @enum {string} */
        AuditOutcome: "SUCCEEDED" | "FAILED";
        /** @enum {string} */
        AuditSubjectKind: "USER_ACCOUNT" | "EMPLOYEE" | "ORG_UNIT" | "EMERGENCY_ADMIN" | "ROLE" | "APPROVAL_TASK" | "APPROVAL_REQUEST" | "SYNC_RUN" | "LEGAL_ENTITY" | "LEGAL_ENTITY_ATTRIBUTION" | "TIMESHEET" | "WORK_CALENDAR_YEAR" | "NUMBERING_RULE" | "SOURCE_REFERENCE" | "SOURCE_REFERENCE_REFUSAL" | "AUTHORIZATION_REQUEST" | "STORED_FILE" | "REQUEST" | "OPERATION" | "LOGIN_ATTEMPT";
        AuthorizationRequestView: {
            kind?: string;
            permissionCodes?: string[];
            /** Format: uuid */
            requestId?: string;
            /** Format: date-time */
            requestedAt?: string;
            /** Format: uuid */
            requestedBy?: string;
            roleCodes?: string[];
            /** Format: uuid */
            roleId?: string;
            roleName?: string;
            /** Format: uuid */
            subjectUserId?: string;
        };
        CalendarDayRequest: {
            /** Format: date */
            calendarDate?: string;
            dayKind?: components["schemas"]["DayKind"] | (string & {});
        };
        CalendarDayResponse: {
            /** Format: date */
            calendarDate: string;
            dayKind: components["schemas"]["DayKind"] | (string & {});
        };
        ConfirmNumberingRuleRequest: {
            /** Format: int32 */
            templateVersion?: number;
        };
        /** @enum {string} */
        ConfirmationStatus: "PENDING_CONFIRMATION" | "CONFIRMED";
        CorrectLegalEntityAttributionRequest: {
            /** Format: date */
            effectiveTo?: string;
            /** Format: date */
            periodFrom?: string;
            reason?: string;
        };
        CreateLegalEntityRequest: {
            code?: string;
            name?: string;
        };
        CurrentUserResponse: {
            /** Format: date-time */
            administratorModeExpiresAt?: string;
            displayName: string;
            identityConfirmed: boolean;
            mode: string;
            navigation: components["schemas"]["NavigationResponse"][];
            /** Format: uuid */
            orgUnitId?: string;
            orgUnitName?: string;
            permissionCodes: string[];
            /** Format: uuid */
            userId: string;
        };
        CursorPageAuditEntryResponse: {
            items: components["schemas"]["AuditEntryResponse"][];
            nextCursor?: string;
        };
        CursorPageAuthorizationRequestView: {
            items: components["schemas"]["AuthorizationRequestView"][];
            nextCursor?: string;
        };
        CursorPageLegacyNumberResponse: {
            items: components["schemas"]["LegacyNumberResponse"][];
            nextCursor?: string;
        };
        CursorPageLegalEntityResponse: {
            items: components["schemas"]["LegalEntityResponse"][];
            nextCursor?: string;
        };
        CursorPageMaintainedYearResponse: {
            items: components["schemas"]["MaintainedYearResponse"][];
            nextCursor?: string;
        };
        CursorPageNumberingRuleResponse: {
            items: components["schemas"]["NumberingRuleResponse"][];
            nextCursor?: string;
        };
        CursorPageOwnLoginRecordResponse: {
            items: components["schemas"]["OwnLoginRecordResponse"][];
            nextCursor?: string;
        };
        CursorPageRoleResponse: {
            items: components["schemas"]["RoleResponse"][];
            nextCursor?: string;
        };
        CursorPageSourceReferenceResponse: {
            items: components["schemas"]["SourceReferenceResponse"][];
            nextCursor?: string;
        };
        CursorPageSyncRunResponse: {
            items: components["schemas"]["SyncRunResponse"][];
            nextCursor?: string;
        };
        /** @enum {string} */
        DayKind: "HOLIDAY" | "MAKE_UP_WORKDAY";
        DecideTaskRequest: {
            outcome?: components["schemas"]["DecisionOutcome"] | (string & {});
            reason?: string;
        };
        /** @enum {string} */
        DecisionOutcome: "APPROVE" | "REJECT";
        DecisionResponse: {
            /** Format: uuid */
            instanceId: string;
            status: string;
        };
        EmergencyLoginRequest: {
            loginName?: string;
            password?: string;
            totpCode?: string;
        };
        EndLegalEntityAttributionRequest: {
            /** Format: date */
            effectiveTo?: string;
        };
        /** @enum {string} */
        ErrorCode: "COST_TIMESHEET_CONCURRENT_CHANGE" | "COST_TIMESHEET_CUTOFF_PASSED" | "COST_TIMESHEET_EMPLOYEE_UNKNOWN" | "COST_TIMESHEET_NOT_FOUND" | "COST_TIMESHEET_NOT_OWNER" | "COST_TIMESHEET_PROJECT_MANAGER_INVALID" | "COST_TIMESHEET_PROJECT_MANAGER_UNKNOWN" | "COST_TIMESHEET_REASON_REQUIRED" | "COST_TIMESHEET_RESULT_PENDING" | "COST_TIMESHEET_TRANSITION_ILLEGAL" | "COST_TIMESHEET_WORK_DATE_DUPLICATE" | "COST_TIMESHEET_WORK_DATE_FIXED" | "COST_TIMESHEET_WORK_DATE_IN_FUTURE" | "COST_TIMESHEET_WORK_DATE_REQUIRED" | "ORGANIZATION_ATTENDANCE_DAY_DUPLICATE" | "ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN" | "ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR" | "ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED" | "ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY" | "ORGANIZATION_ATTENDANCE_YEAR_CONFLICT" | "ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND" | "ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED" | "ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_FORBIDDEN" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_FOUND" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_OPEN" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_FOUND" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND" | "ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE" | "ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL" | "ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND" | "ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN" | "ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED" | "ORGANIZATION_WORKFORCE_SEPARATION_HOLD_FORBIDDEN" | "ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED" | "ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED" | "ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND" | "ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL" | "PLATFORM_APPROVAL_ALREADY_APPROVED" | "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID" | "PLATFORM_APPROVAL_CONCURRENT_CHANGE" | "PLATFORM_APPROVAL_CUTOFF_PASSED" | "PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE" | "PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK" | "PLATFORM_APPROVAL_DECISION_EMPTY" | "PLATFORM_APPROVAL_DECISION_SINGLE_ONLY" | "PLATFORM_APPROVAL_DOCUMENT_TYPE_UNREGISTERED" | "PLATFORM_APPROVAL_ESCALATION_TARGET_MISSING" | "PLATFORM_APPROVAL_FORM_FIELD_UNKNOWN" | "PLATFORM_APPROVAL_HOOK_BLOCKED" | "PLATFORM_APPROVAL_HOOK_NOT_REGISTERED" | "PLATFORM_APPROVAL_NODE_CONFIG_INVALID" | "PLATFORM_APPROVAL_NOT_ADMINISTRATOR" | "PLATFORM_APPROVAL_NOT_INITIATOR" | "PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND" | "PLATFORM_APPROVAL_NO_PROCESS" | "PLATFORM_APPROVAL_NO_ROUTE" | "PLATFORM_APPROVAL_PAGE_INVALID" | "PLATFORM_APPROVAL_REASON_REQUIRED" | "PLATFORM_APPROVAL_REQUEST_NOT_FOUND" | "PLATFORM_APPROVAL_ROUND_MISMATCH" | "PLATFORM_APPROVAL_ROUTING_FLAG_UNKNOWN" | "PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED" | "PLATFORM_APPROVAL_TASK_NOT_FOUND" | "PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID" | "PLATFORM_APPROVAL_TRANSITION_ILLEGAL" | "PLATFORM_AUDIT_EXPORT_TOO_LARGE" | "PLATFORM_FILE_BINDING_CONFLICT" | "PLATFORM_FILE_CONTENT_READ_FORBIDDEN" | "PLATFORM_FILE_DISPOSED" | "PLATFORM_FILE_ENCRYPTED_REJECTED" | "PLATFORM_FILE_FALSE_POSITIVE_SAME_REVIEWER" | "PLATFORM_FILE_HASH_MISMATCH" | "PLATFORM_FILE_LINK_VALIDITY_EXCEEDED" | "PLATFORM_FILE_MEDIA_TYPE_NOT_ALLOWED" | "PLATFORM_FILE_NOT_AVAILABLE" | "PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT" | "PLATFORM_FILE_NOT_FOUND" | "PLATFORM_FILE_PRESERVATION_CATEGORY_UNKNOWN" | "PLATFORM_FILE_PREVIOUS_VERSION_HAS_SUCCESSOR" | "PLATFORM_FILE_PREVIOUS_VERSION_NOT_BOUND" | "PLATFORM_FILE_QUARANTINED" | "PLATFORM_FILE_QUARANTINE_CONTENT_PURGED" | "PLATFORM_FILE_QUARANTINE_READ_NOT_APPROVED" | "PLATFORM_FILE_SCAN_UNAVAILABLE" | "PLATFORM_FILE_SPI_NOT_ACCEPTED" | "PLATFORM_FILE_TOO_LARGE" | "PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE" | "PLATFORM_IDENTITY_ACCOUNT_DISABLED" | "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND" | "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_ADMINISTRATOR_MODE_DENIED" | "PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED" | "PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_FOUND" | "PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING" | "PLATFORM_IDENTITY_CONCURRENT_UPDATE" | "PLATFORM_IDENTITY_CREDENTIALS_INVALID" | "PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING" | "PLATFORM_IDENTITY_EMERGENCY_ADMIN_DUPLICATE" | "PLATFORM_IDENTITY_EMERGENCY_ADMIN_NOT_FOUND" | "PLATFORM_IDENTITY_EMERGENCY_ADMIN_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID" | "PLATFORM_IDENTITY_EMPLOYEE_SEPARATED" | "PLATFORM_IDENTITY_FORCED_LOGOUT_REASON_REQUIRED" | "PLATFORM_IDENTITY_FORCED_LOGOUT_SELF_FORBIDDEN" | "PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN" | "PLATFORM_IDENTITY_LOGIN_RATE_LIMITED" | "PLATFORM_IDENTITY_NETWORK_DENIED" | "PLATFORM_IDENTITY_NOT_SIGNED_IN" | "PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED" | "PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN" | "PLATFORM_IDENTITY_OWN_ROLE_DEFINITION_FORBIDDEN" | "PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY" | "PLATFORM_IDENTITY_PERMISSION_UNKNOWN" | "PLATFORM_IDENTITY_ROLE_BLANK_NAME" | "PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE" | "PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME" | "PLATFORM_IDENTITY_ROLE_INVALID_CODE" | "PLATFORM_IDENTITY_ROLE_IN_USE" | "PLATFORM_IDENTITY_ROLE_NOT_ALLOWED" | "PLATFORM_IDENTITY_ROLE_NOT_FOUND" | "PLATFORM_IDENTITY_ROLE_RESERVED_CODE" | "PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN" | "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_SSO_UNAVAILABLE" | "PLATFORM_IDENTITY_STATE_INVALID" | "PLATFORM_IDENTITY_TICKET_INVALID" | "PLATFORM_IDENTITY_TICKET_REPLAYED" | "PLATFORM_IDENTITY_WECOM_CODE_INVALID" | "PLATFORM_IDENTITY_WECOM_NOT_BOUND" | "PLATFORM_INTEGRATION_CONCURRENT_UPDATE" | "PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR" | "PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL" | "PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED" | "PLATFORM_INTEGRATION_SOURCE_REFERENCE_ALREADY_DECIDED" | "PLATFORM_INTEGRATION_SOURCE_REFERENCE_CONFLICT" | "PLATFORM_INTEGRATION_SOURCE_REFERENCE_INVALID" | "PLATFORM_INTEGRATION_SOURCE_REFERENCE_NOT_FOUND" | "PLATFORM_NUMBERING_CONCURRENT_UPDATE" | "PLATFORM_NUMBERING_CONFIRMATION_REPEATED" | "PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR" | "PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED" | "PLATFORM_NUMBERING_EFFECTIVE_DATE_PASSED" | "PLATFORM_NUMBERING_FINANCE_CONFIRMATION_NOT_REQUIRED" | "PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND" | "PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN" | "PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID" | "PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE" | "PLATFORM_NUMBERING_NOTHING_TO_CONFIRM" | "PLATFORM_NUMBERING_RULE_INVALID" | "PLATFORM_NUMBERING_RULE_NOT_CONFIRMED" | "PLATFORM_NUMBERING_RULE_NOT_FOUND" | "PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL" | "PLATFORM_NUMBERING_VERSION_MISMATCH" | "PLATFORM_SIGNING_ACTIVE_ENTRY_EXISTS" | "PLATFORM_SIGNING_CONFIRMER_IS_AUTHOR" | "PLATFORM_SIGNING_LEDGER_ENTRY_NOT_FOUND" | "PLATFORM_SIGNING_NO_VERSION_AT" | "PLATFORM_SIGNING_SEAL_TYPE_UNKNOWN" | "PLATFORM_SIGNING_VERSION_NOT_EDITABLE" | "PLATFORM_WEB_ACCESS_DENIED" | "PLATFORM_WEB_CORRELATION_INVALID" | "PLATFORM_WEB_CURSOR_INVALID" | "PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS" | "PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED" | "PLATFORM_WEB_REQUEST_INVALID" | "PLATFORM_WEB_SOURCE_BROKEN" | "PLATFORM_WEB_SOURCE_UNAVAILABLE" | "PLATFORM_WEB_UNAUTHENTICATED" | "PLATFORM_WEB_UNEXPECTED" | "PLATFORM_WEB_VERSION_MALFORMED" | "PLATFORM_WEB_VERSION_MISMATCH" | "PLATFORM_WEB_VERSION_REQUIRED";
        /** @enum {string} */
        ExternalSource: "ORG_SOURCE" | "LEGACY_SYSTEM" | "ACCOUNTING_SYSTEM";
        ForcedLogoutRequest: {
            reason?: string;
        };
        HandledTaskPageResponse: {
            items: components["schemas"]["HandledTaskResponse"][];
            nextCursor?: string;
        };
        HandledTaskResponse: {
            action: components["schemas"]["TaskRecordAction"] | (string & {});
            /** Format: uuid */
            actorId: string;
            actorName: string;
            /** Format: date-time */
            decidedAt: string;
            /** Format: uuid */
            documentId: string;
            documentType: string;
            /** Format: uuid */
            instanceId: string;
            nodeKey: string;
            nodeName: string;
            onBehalfOfName?: string;
            processKey: components["schemas"]["ProcessKey"] | (string & {});
            reason?: string;
            /** Format: uuid */
            recordId: string;
            /** Format: int32 */
            round: number;
            /** Format: uuid */
            taskId: string;
        };
        HistoryEntry: {
            action: components["schemas"]["TaskRecordAction"] | (string & {});
            /** Format: uuid */
            actorId: string;
            actorName: string;
            assigneeName?: string;
            /** Format: date-time */
            decidedAt: string;
            nodeKey: string;
            nodeName: string;
            onBehalfOfName?: string;
            reason?: string;
            /** Format: uuid */
            recordId: string;
            /** Format: int32 */
            round: number;
            /** Format: uuid */
            taskId: string;
        };
        InboxItemResponse: {
            category: string;
            claimed: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            deadlineAt?: string;
            /** Format: uuid */
            documentId?: string;
            nodeName?: string;
            overdue: boolean;
            processKey?: components["schemas"]["ProcessKey"] | (string & {});
            taskId: string;
            title: string;
        };
        InboxResponse: {
            items: components["schemas"]["InboxItemResponse"][];
            /** Format: int32 */
            newCount: number;
            nextCursor?: string;
            /** Format: int32 */
            noticeCount: number;
            /** Format: int32 */
            totalCount: number;
        };
        LegacyNumberResponse: {
            /** Format: uuid */
            documentId: string;
            documentType: string;
            legacyNumber: string;
            /** Format: uuid */
            legalEntityId?: string;
            /** Format: uuid */
            migrationBatchId: string;
            sourceSystem: components["schemas"]["ExternalSource"] | (string & {});
        };
        LegalEntityAttributionResponse: {
            kind: string;
            periods: components["schemas"]["Period"][];
            /** Format: uuid */
            subjectId: string;
        };
        LegalEntityResponse: {
            code: string;
            /** Format: uuid */
            legalEntityId: string;
            name: string;
            status: string;
        };
        LoginResponse: {
            /** Format: date-time */
            absoluteExpiresAt: string;
            /** Format: date-time */
            administratorModeExpiresAt?: string;
            identityConfirmationRequired: boolean;
            /** Format: uuid */
            userId: string;
        };
        LoginStateRequest: {
            entry?: components["schemas"]["WecomEntry"] | (string & {});
        };
        LoginStateResponse: {
            state: string;
        };
        MaintainWorkCalendarRequest: {
            days?: components["schemas"]["CalendarDayRequest"][];
            lockedDaysConfirmed?: boolean;
        };
        MaintainedYearResponse: {
            /** Format: int32 */
            calendarYear: number;
        };
        NavigationResponse: {
            children: components["schemas"]["NavigationResponse"][];
            name: string;
            routePath?: string;
        };
        NumberingRuleRequest: {
            /** Format: date */
            effectiveFrom?: string;
            pattern?: string;
            resetPolicy?: components["schemas"]["ResetPolicy"] | (string & {});
            /** Format: int64 */
            startValue?: number;
        };
        NumberingRuleResponse: {
            /** Format: int64 */
            capacity: number;
            confirmationStatus: components["schemas"]["ConfirmationStatus"] | (string & {});
            documentType: string;
            documentTypeCode?: string;
            /** Format: date */
            effectiveFrom: string;
            pattern: string;
            pending?: components["schemas"]["PendingTemplate"];
            requiresFinanceConfirmation: boolean;
            resetPolicy: components["schemas"]["ResetPolicy"] | (string & {});
            /** Format: int64 */
            startValue: number;
            /** Format: int32 */
            templateVersion: number;
            /** Format: int64 */
            version: number;
        };
        OrgReconciliationResponse: {
            missingInSnapshot: string[];
            parentMismatches: string[];
            reportingLineMismatches: string[];
            responsibilityMismatches: string[];
            /** Format: int32 */
            snapshotEmployedCount: number;
            /** Format: int32 */
            snapshotOrgUnitCount: number;
            /** Format: int32 */
            sourceEmployedCount: number;
            /** Format: int32 */
            sourceOrgUnitCount: number;
            unexpectedInSnapshot: string[];
            wecomUserIdMismatches: string[];
            zeroDifference: boolean;
        };
        OrgUnitDetailResponse: {
            ancestorOrgUnitIds: string[];
            orgUnit: components["schemas"]["OrgUnitResponse"];
            orgUnitAndBelowIds: string[];
        };
        OrgUnitResponse: {
            code: string;
            deliveryUnit: boolean;
            /** Format: uuid */
            headEmployeeId?: string;
            name: string;
            /** Format: uuid */
            orgUnitId: string;
            /** Format: uuid */
            parentOrgUnitId?: string;
            regionCategory?: string;
            /** Format: uuid */
            regionalReceivablesManagerId?: string;
            shortName?: string;
            /** Format: int32 */
            sortOrder?: number;
            status: string;
        };
        OwnLoginRecordResponse: {
            action: string;
            loginMethod?: string;
            /** Format: date-time */
            occurredAt: string;
            outcome?: string;
            sourceAddress?: string;
        };
        PendingTemplate: {
            /** Format: uuid */
            authorId?: string;
            /** Format: date-time */
            authoredAt: string;
            /** Format: int64 */
            capacity: number;
            /** Format: uuid */
            confirmedBy?: string;
            /** Format: date */
            effectiveFrom: string;
            /** Format: uuid */
            financeConfirmedBy?: string;
            pattern: string;
            resetPolicy: components["schemas"]["ResetPolicy"] | (string & {});
            /** Format: int64 */
            startValue: number;
            /** Format: int32 */
            templateVersion: number;
        };
        Period: {
            /** Format: date */
            effectiveFrom: string;
            /** Format: date */
            effectiveTo?: string;
            /** Format: uuid */
            legalEntityId: string;
        };
        ProblemDetail: {
            /** Format: uuid */
            correlationId?: string;
            detail?: string;
            errorCode: components["schemas"]["ErrorCode"] | (string & {});
            instance?: string;
            invalidParameters?: string;
            legacyLoginUrl?: string;
            /** Format: int32 */
            status: number;
            title?: string;
            type?: string;
        };
        /** @enum {string} */
        ProcessKey: "COST_TIMESHEET_APPROVAL";
        ReassignTaskRequest: {
            /** Format: uuid */
            assigneeId?: string;
            reason?: string;
        };
        RegisterSourceReferenceRequest: {
            /** Format: date */
            effectiveFrom?: string;
            /** Format: uuid */
            ownId?: string;
            sourceId?: string;
            sourceObjectKind?: components["schemas"]["SourceObjectKind"] | (string & {});
            sourceSystem?: components["schemas"]["ExternalSource"] | (string & {});
        };
        ReleaseRequest: {
            reason?: string;
        };
        ReleaseResponse: {
            message?: string;
            outcome: string;
            syncRun?: components["schemas"]["SyncRunResponse"];
        };
        ReplaceRolesRequest: {
            roleCodes?: string[];
        };
        /** @enum {string} */
        ResetPolicy: "NEVER" | "YEARLY" | "MONTHLY" | "DAILY";
        ReturnTaskRequest: {
            reason?: string;
        };
        RoleRequest: {
            accessScope?: components["schemas"]["AccessScopeKind"] | (string & {});
            code?: string;
            enabled?: boolean;
            name?: string;
            permissionCodes?: string[];
        };
        RoleResponse: {
            accessScope: components["schemas"]["AccessScopeKind"] | (string & {});
            administrative: boolean;
            code: string;
            enabled: boolean;
            kind: string;
            name: string;
            permissionCodes: string[];
            /** Format: uuid */
            roleId: string;
        };
        /** @enum {string} */
        SourceObjectKind: "EMPLOYEE" | "ORG_UNIT" | "POSITION" | "LEGAL_ENTITY";
        /** @enum {string} */
        SourceReferenceConfirmationStatus: "PENDING" | "CONFIRMED" | "REJECTED";
        SourceReferenceResponse: {
            confirmationStatus: components["schemas"]["SourceReferenceConfirmationStatus"] | (string & {});
            /** Format: date */
            effectiveFrom?: string;
            /** Format: date */
            effectiveTo?: string;
            /** Format: uuid */
            ownId: string;
            sourceId: string;
            sourceObjectKind: components["schemas"]["SourceObjectKind"] | (string & {});
            /** Format: uuid */
            sourceReferenceId: string;
            sourceSystem: components["schemas"]["ExternalSource"] | (string & {});
        };
        SyncDiffResponse: {
            changeKind: string;
            changedFields: string[];
            detail?: string;
            entityKind: string;
            issueKind?: string;
            subjectKey: string;
        };
        SyncRunDetailResponse: {
            diffs: components["schemas"]["SyncDiffResponse"][];
            syncRun: components["schemas"]["SyncRunResponse"];
        };
        SyncRunResponse: {
            /** Format: int32 */
            employedWithoutWecomCount?: number;
            /** Format: int32 */
            employeeChangeCount?: number;
            failureReason?: string;
            /** Format: date-time */
            finishedAt?: string;
            heldReason?: string;
            heldSeparations: string[];
            holdKinds: string[];
            /** Format: int32 */
            incomingActiveOrgUnitCount?: number;
            /** Format: int32 */
            incomingEmployedCount?: number;
            /** Format: int32 */
            isolatedRowCount?: number;
            /** Format: int32 */
            issueCount?: number;
            /** Format: int32 */
            orgUnitChangeCount?: number;
            /** Format: int32 */
            positionChangeCount?: number;
            /** Format: int32 */
            previousActiveOrgUnitCount?: number;
            /** Format: int32 */
            previousEmployedCount?: number;
            /** Format: date-time */
            releasedAt?: string;
            /** Format: uuid */
            releasedBy?: string;
            /** Format: uuid */
            releasedByRunId?: string;
            releasedKinds: string[];
            /** Format: int32 */
            sourceEmployeeCount?: number;
            /** Format: int32 */
            sourceOrgUnitCount?: number;
            /** Format: date-time */
            startedAt: string;
            status: string;
            /** Format: uuid */
            syncRunId: string;
            trigger: string;
        };
        TaskDetailResponse: {
            /** Format: uuid */
            assignee?: string;
            assigneeName?: string;
            canAct: boolean;
            candidateNames: string[];
            candidates: string[];
            /** Format: date-time */
            completedAt?: string;
            /** Format: date-time */
            createdAt: string;
            /** Format: date-time */
            deadlineAt?: string;
            /** Format: uuid */
            documentId: string;
            documentType: string;
            /** Format: date-time */
            dueAt?: string;
            effect?: string;
            history: components["schemas"]["HistoryEntry"][];
            /** Format: uuid */
            initiator: string;
            initiatorName: string;
            initiatorOrgUnitName?: string;
            /** Format: uuid */
            instanceId: string;
            nodeKey: string;
            nodeName?: string;
            outcome?: string;
            overdue: boolean;
            processKey: components["schemas"]["ProcessKey"] | (string & {});
            reason?: string;
            status: string;
            summary?: string;
            /** Format: uuid */
            taskId: string;
        };
        TaskOverviewPageResponse: {
            items: components["schemas"]["TaskOverviewResponse"][];
            nextCursor?: string;
        };
        TaskOverviewResponse: {
            /** Format: uuid */
            assignee?: string;
            /** Format: date-time */
            createdAt: string;
            /** Format: uuid */
            documentId: string;
            documentType: string;
            /** Format: date-time */
            dueAt?: string;
            nodeKey: string;
            overdue: boolean;
            processKey: components["schemas"]["ProcessKey"] | (string & {});
            status: string;
            /** Format: uuid */
            taskId: string;
        };
        /** @enum {string} */
        TaskRecordAction: "CLAIMED" | "RELEASED" | "APPROVED" | "REJECTED" | "RETURNED" | "ADVISED" | "PASSED_AUTOMATICALLY" | "TRANSFERRED" | "REASSIGNED";
        TicketLoginRequest: {
            ticket?: string;
        };
        TransferTaskRequest: {
            /** Format: uuid */
            assigneeId?: string;
            reason?: string;
        };
        UpdateLegalEntityRequest: {
            code?: string;
            name?: string;
            status?: string;
        };
        UserAccountResponse: {
            roleCodes: string[];
            status: string;
            /** Format: uuid */
            userId: string;
        };
        /** @enum {string} */
        WecomEntry: "WORKBENCH" | "MINI_PROGRAM" | "QR_CODE_LOGIN";
        WecomLoginRequest: {
            code?: string;
            entry?: components["schemas"]["WecomEntry"] | (string & {});
            state?: string;
        };
        WorkCalendarYearResponse: {
            /** Format: int32 */
            calendarYear: number;
            days: components["schemas"]["CalendarDayResponse"][];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    ofEmployee: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                employeeId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    attributeEmployee: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
            };
            path: {
                employeeId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AttributeLegalEntityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    correctEmployee: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                employeeId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CorrectLegalEntityAttributionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    endEmployee: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                employeeId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EndLegalEntityAttributionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_2: {
        parameters: {
            query?: {
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageLegalEntityResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateLegalEntityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    get_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                legalEntityId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    update_1: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
            };
            path: {
                legalEntityId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateLegalEntityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    ofOrgUnit: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                orgUnitId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    attributeOrgUnit: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
            };
            path: {
                orgUnitId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AttributeLegalEntityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    correctOrgUnit: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                orgUnitId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CorrectLegalEntityAttributionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    endOrgUnit: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                orgUnitId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EndLegalEntityAttributionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegalEntityAttributionResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_11: {
        parameters: {
            query?: {
                includeRevoked?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OrgUnitResponse"][];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    get_5: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                orgUnitId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OrgUnitDetailResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_10: {
        parameters: {
            query?: {
                limit?: number;
                cursor?: string;
                status?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageSyncRunResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    get_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                syncRunId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SyncRunDetailResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    release_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                syncRunId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReleaseRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReleaseResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    reconcile: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OrgReconciliationResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    maintainedYears: {
        parameters: {
            query?: {
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageMaintainedYearResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    view: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                calendarYear: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["WorkCalendarYearResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    maintain: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
            };
            path: {
                calendarYear: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MaintainWorkCalendarRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["WorkCalendarYearResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_9: {
        parameters: {
            query?: {
                overdueOnly?: boolean;
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskOverviewPageResponse"];
                };
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_8: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HandledTaskPageResponse"];
                };
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_7: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["InboxResponse"];
                };
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    get_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskDetailResponse"];
                };
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    claim: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    decide: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DecideTaskRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DecisionResponse"];
                };
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    reassign: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReassignTaskRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    release: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    returnTask: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReturnTaskRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DecisionResponse"];
                };
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    transfer: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TransferTaskRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request is malformed, or If-Match is not one strong ETag */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Nobody is signed in */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The caller may not do this */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No such task, or the caller may not know it */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The task or the approval is not in a state that allows this */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The approval changed since the caller read it; the ETag of a task is the version of its approval request (If-Match) */
            412: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request breaks a rule; errorCode says which */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description If-Match with the ETag of the task as read (the version of its approval request) is required */
            428: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_6: {
        parameters: {
            query?: {
                actorId?: string;
                onBehalfOfId?: string;
                action?: string;
                actionPrefix?: string;
                sourceAddress?: string;
                outcome?: components["schemas"]["AuditOutcome"];
                subjectKind?: components["schemas"]["AuditSubjectKind"];
                /** @description Id of the audited object. Must be given together with subjectKind: subjectId alone answers 400, because one id can name several kinds of object; kinds without an object (REQUEST, OPERATION, LOGIN_ATTEMPT) take no subjectId. */
                subjectId?: string;
                from?: string;
                to?: string;
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageAuditEntryResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    export: {
        parameters: {
            query?: {
                actorId?: string;
                onBehalfOfId?: string;
                action?: string;
                actionPrefix?: string;
                sourceAddress?: string;
                outcome?: components["schemas"]["AuditOutcome"];
                subjectKind?: components["schemas"]["AuditSubjectKind"];
                /** @description Id of the audited object. Must be given together with subjectKind: subjectId alone answers 400, because one id can name several kinds of object; kinds without an object (REQUEST, OPERATION, LOGIN_ATTEMPT) take no subjectId. */
                subjectId?: string;
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    pending: {
        parameters: {
            query?: {
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageAuthorizationRequestView"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    confirm_2: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                requestId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    reject_1: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                requestId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_5: {
        parameters: {
            query: {
                documentType: string;
                /** @description Required with legacyNumberPrefix: original numbers are unique per source system */
                sourceSystem?: components["schemas"]["ExternalSource"];
                /** @description Exactly one of legacyNumberPrefix and documentId is given */
                legacyNumberPrefix?: string;
                /** @description Exactly one of legacyNumberPrefix and documentId is given */
                documentId?: string;
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageLegacyNumberResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    resolve: {
        parameters: {
            query: {
                sourceSystem: components["schemas"]["ExternalSource"];
                documentType: string;
                legacyNumber: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LegacyNumberResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    beginWecomLogin: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginStateRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoginStateResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_4: {
        parameters: {
            query?: {
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageNumberingRuleResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    get_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                documentType: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["NumberingRuleResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    put: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
            };
            path: {
                documentType: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NumberingRuleRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["NumberingRuleResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    confirm_1: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                documentType: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConfirmNumberingRuleRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["NumberingRuleResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    confirmByFinance: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                documentType: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConfirmNumberingRuleRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["NumberingRuleResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_3: {
        parameters: {
            query?: {
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageOwnLoginRecordResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageRoleResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                roleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["RoleResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
            };
            path: {
                roleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RoleRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    delete: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
            };
            path: {
                roleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    confirmIdentity: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    current: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CurrentUserResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    loginAsEmergencyAdmin: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EmergencyLoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoginResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    enter: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: {
                loginBinding?: string;
            };
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WecomLoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AdministratorModeResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    exit: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    loginWithSso: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TicketLoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoginResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    loginWithSignedTicket: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TicketLoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoginResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    loginWithWecom: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: {
                loginBinding?: string;
            };
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WecomLoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoginResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    list: {
        parameters: {
            query: {
                sourceSystem: components["schemas"]["ExternalSource"];
                sourceObjectKind: components["schemas"]["SourceObjectKind"];
                /** @description PENDING is the approver's to-do list */
                confirmationStatus?: components["schemas"]["SourceReferenceConfirmationStatus"];
                /** @description Every row of one own object, confirmed, pending and rejected, closed periods included */
                ownId?: string;
                limit?: number;
                cursor?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CursorPageSourceReferenceResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    register: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RegisterSourceReferenceRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SourceReferenceResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    confirm: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sourceReferenceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SourceReferenceResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    reject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sourceReferenceId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SourceReferenceResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    account: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["UserAccountResponse"];
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    disable: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    enable: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    forceLogout: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ForcedLogoutRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    replaceRoles: {
        parameters: {
            query?: never;
            header: {
                "If-Match": string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReplaceRolesRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description The request was refused */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
}
