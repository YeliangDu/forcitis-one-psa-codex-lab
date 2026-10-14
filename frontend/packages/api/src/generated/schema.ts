// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
export interface paths {
    "/api/v1/cost/timesheets/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get_6"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/cost/timesheets/{id}:cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["cancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/cost/timesheets/{id}:submit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["submit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/cost/timesheets/{id}:withdraw": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["withdraw"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
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
        get: operations["list_1"];
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
        get: operations["list_6"];
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
        get: operations["list_5"];
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
        post: operations["release"];
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
    "/api/v1/platform/approval-tasks": {
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
        get: operations["list_3"];
        put?: never;
        post?: never;
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
        get: operations["byLegacyNumber"];
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
        get: operations["list_2"];
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
    "/api/v1/platform/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
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
        AttributeLegalEntityRequest: {
            /** Format: date */
            effectiveFrom?: string;
            /** Format: int64 */
            legalEntityId?: number;
        };
        AuditEntryResponse: {
            action: string;
            actorId?: string | null;
            afterText?: string | null;
            beforeText?: string | null;
            changeReason?: string | null;
            /** Format: date-time */
            createdAt: string;
            detail?: string | null;
            entryId: string;
            onBehalfOfId?: string | null;
            result?: string | null;
            sourceAddress?: string | null;
            subjectKey: string;
            /** Format: uuid */
            traceNumber?: string | null;
        };
        CalendarDayRequest: {
            /** Format: date */
            calendarDate?: string;
            /** @enum {string} */
            dayKind?: "HOLIDAY" | "MAKE_UP_WORKDAY";
        };
        CalendarDayResponse: {
            /** Format: date */
            calendarDate: string;
            /** @enum {string} */
            dayKind: "HOLIDAY" | "MAKE_UP_WORKDAY";
        };
        CorrectLegalEntityAttributionRequest: {
            /** Format: date */
            effectiveTo?: string;
            /** Format: date */
            periodFrom?: string;
            reason?: string;
        };
        CreateLegalEntityRequest: {
            /** Format: int64 */
            accountingOrganizationNumber?: number;
            code?: string;
            name?: string;
        };
        CurrentUserResponse: {
            displayName: string;
            identityConfirmed: boolean;
            navigation: components["schemas"]["NavigationResponse"][];
            orgUnitId?: string | null;
            orgUnitName?: string | null;
            permissionCodes: string[];
            userId: string;
        };
        CursorPageAuditEntryResponse: {
            items: components["schemas"]["AuditEntryResponse"][];
            nextCursor?: string | null;
        };
        CursorPageLegacyNumberResponse: {
            items: components["schemas"]["LegacyNumberResponse"][];
            nextCursor?: string | null;
        };
        CursorPageLegalEntityResponse: {
            items: components["schemas"]["LegalEntityResponse"][];
            nextCursor?: string | null;
        };
        CursorPageMaintainedYearResponse: {
            items: components["schemas"]["MaintainedYearResponse"][];
            nextCursor?: string | null;
        };
        CursorPageNumberingRuleResponse: {
            items: components["schemas"]["NumberingRuleResponse"][];
            nextCursor?: string | null;
        };
        CursorPageRoleResponse: {
            items: components["schemas"]["RoleResponse"][];
            nextCursor?: string | null;
        };
        CursorPageSyncRunResponse: {
            items: components["schemas"]["SyncRunResponse"][];
            nextCursor?: string | null;
        };
        DecideTaskRequest: {
            /** @enum {string} */
            outcome?: "APPROVE" | "REJECT";
            reason?: string;
        };
        DecisionResponse: {
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
        ErrorCode: "COST_TIMESHEET_CONCURRENT_CHANGE" | "COST_TIMESHEET_CUTOFF_PASSED" | "COST_TIMESHEET_EXCEEDS_REMAINING_HOURS" | "COST_TIMESHEET_NOT_FOUND" | "COST_TIMESHEET_PROJECT_MANAGER_INVALID" | "COST_TIMESHEET_REASON_REQUIRED" | "COST_TIMESHEET_TRANSITION_ILLEGAL" | "COST_TIMESHEET_UNKNOWN_WORK_PACKAGE" | "ORGANIZATION_ATTENDANCE_DAY_DUPLICATE" | "ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN" | "ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR" | "ORGANIZATION_ATTENDANCE_LOCKED_DAY_FORBIDDEN" | "ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED" | "ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY" | "ORGANIZATION_ATTENDANCE_YEAR_CONFLICT" | "ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND" | "ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED" | "ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE" | "ORGANIZATION_WORKFORCE_ACCOUNTING_NUMBER_INVALID" | "ORGANIZATION_WORKFORCE_ACCOUNTING_NUMBER_TAKEN" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_FORBIDDEN" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_FOUND" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_OPEN" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_FOUND" | "ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND" | "ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE" | "ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL" | "ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND" | "ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED" | "ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED" | "ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND" | "ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL" | "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID" | "PLATFORM_APPROVAL_CONCURRENT_CHANGE" | "PLATFORM_APPROVAL_CUTOFF_PASSED" | "PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE" | "PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK" | "PLATFORM_APPROVAL_DECISION_EMPTY" | "PLATFORM_APPROVAL_DECISION_SINGLE_ONLY" | "PLATFORM_APPROVAL_HOOK_BLOCKED" | "PLATFORM_APPROVAL_HOOK_NOT_REGISTERED" | "PLATFORM_APPROVAL_NOT_INITIATOR" | "PLATFORM_APPROVAL_NO_ROUTE" | "PLATFORM_APPROVAL_REASON_REQUIRED" | "PLATFORM_APPROVAL_REQUEST_NOT_FOUND" | "PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED" | "PLATFORM_APPROVAL_TASK_NOT_FOUND" | "PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID" | "PLATFORM_APPROVAL_TRANSITION_ILLEGAL" | "PLATFORM_AUDIT_QUERY_INVALID" | "PLATFORM_IDENTITY_ACCOUNT_DISABLED" | "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND" | "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_CONCURRENT_UPDATE" | "PLATFORM_IDENTITY_CREDENTIALS_INVALID" | "PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING" | "PLATFORM_IDENTITY_EMPLOYEE_SEPARATED" | "PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN" | "PLATFORM_IDENTITY_LOGIN_RATE_LIMITED" | "PLATFORM_IDENTITY_NETWORK_DENIED" | "PLATFORM_IDENTITY_NOT_SIGNED_IN" | "PLATFORM_IDENTITY_PERMISSION_UNKNOWN" | "PLATFORM_IDENTITY_ROLE_BLANK_NAME" | "PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE" | "PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME" | "PLATFORM_IDENTITY_ROLE_INVALID_CODE" | "PLATFORM_IDENTITY_ROLE_IN_USE" | "PLATFORM_IDENTITY_ROLE_NOT_ALLOWED" | "PLATFORM_IDENTITY_ROLE_NOT_FOUND" | "PLATFORM_IDENTITY_ROLE_RESERVED_CODE" | "PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN" | "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_SSO_UNAVAILABLE" | "PLATFORM_IDENTITY_STATE_INVALID" | "PLATFORM_IDENTITY_TICKET_INVALID" | "PLATFORM_IDENTITY_TICKET_REPLAYED" | "PLATFORM_IDENTITY_WECOM_CODE_INVALID" | "PLATFORM_IDENTITY_WECOM_NOT_BOUND" | "PLATFORM_INTEGRATION_CONCURRENT_UPDATE" | "PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL" | "PLATFORM_NUMBERING_CONCURRENT_UPDATE" | "PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND" | "PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN" | "PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID" | "PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE" | "PLATFORM_NUMBERING_RULE_INVALID" | "PLATFORM_NUMBERING_RULE_NOT_FOUND" | "PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL" | "PLATFORM_WEB_ACCESS_DENIED" | "PLATFORM_WEB_CORRELATION_INVALID" | "PLATFORM_WEB_CURSOR_INVALID" | "PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS" | "PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED" | "PLATFORM_WEB_REQUEST_INVALID" | "PLATFORM_WEB_SOURCE_BROKEN" | "PLATFORM_WEB_SOURCE_UNAVAILABLE" | "PLATFORM_WEB_UNAUTHENTICATED" | "PLATFORM_WEB_UNEXPECTED" | "PLATFORM_WEB_VERSION_MISMATCH" | "PLATFORM_WEB_VERSION_REQUIRED";
        InboxItemResponse: {
            claimed: boolean;
            /** Format: date-time */
            createdAt?: string | null;
            detailUrl?: string | null;
            nodeName?: string | null;
            source: string;
            taskId: string;
            title: string;
        };
        InboxResponse: {
            items: components["schemas"]["InboxItemResponse"][];
            legacyAvailable: boolean;
            /** Format: int32 */
            legacyCount: number;
            /** Format: int32 */
            newCount: number;
            /** Format: int32 */
            totalCount: number;
        };
        LegacyNumberResponse: {
            documentId: string;
            /** @enum {string} */
            documentType: "QUOTE" | "TIMESHEET" | "JOURNAL_VOUCHER";
            importBatch: string;
            legacyNumber: string;
            legalEntityId?: string | null;
        };
        LegalEntityAttributionResponse: {
            kind: string;
            periods: components["schemas"]["Period"][];
            subjectId: string;
        };
        LegalEntityResponse: {
            /** Format: int64 */
            accountingOrganizationNumber?: number | null;
            code: string;
            legalEntityId: string;
            name: string;
            status: string;
        };
        LoginResponse: {
            /** Format: date-time */
            absoluteExpiresAt: string;
            /** Format: date-time */
            administrationExpiresAt?: string | null;
            identityConfirmationRequired: boolean;
            userId: string;
        };
        LoginStateRequest: {
            /** @enum {string} */
            entry?: "WORKBENCH" | "MINI_PROGRAM" | "QR_CODE_LOGIN";
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
            routePath?: string | null;
        };
        NumberingRuleRequest: {
            pattern?: string;
            /** @enum {string} */
            resetPolicy?: "NEVER" | "YEARLY" | "MONTHLY" | "DAILY";
            /** Format: int64 */
            startValue?: number;
        };
        NumberingRuleResponse: {
            /** Format: int64 */
            capacity: number;
            /** @enum {string} */
            confirmationStatus: "PENDING_CONFIRMATION" | "CONFIRMED";
            /** @enum {string} */
            documentType: "QUOTE" | "TIMESHEET" | "JOURNAL_VOUCHER";
            documentTypeCode?: string | null;
            pattern: string;
            /** @enum {string} */
            resetPolicy: "NEVER" | "YEARLY" | "MONTHLY" | "DAILY";
            /** Format: int64 */
            startValue: number;
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
            headEmployeeId?: string | null;
            name: string;
            orgUnitId: string;
            parentOrgUnitId?: string | null;
            regionCategory?: string | null;
            regionalReceivablesManagerId?: string | null;
            shortName?: string | null;
            /** Format: int32 */
            sortOrder?: number | null;
            status: string;
        };
        Period: {
            /** Format: date */
            effectiveFrom: string;
            /** Format: date */
            effectiveTo?: string | null;
            legalEntityId: string;
        };
        ProblemDetail: {
            /** Format: uuid */
            correlationId?: string;
            detail?: string;
            errorCode: components["schemas"]["ErrorCode"];
            instance?: string;
            legacyLoginUrl?: string;
            /** Format: int32 */
            status: number;
            title?: string;
            type?: string;
        };
        ReleaseSyncRunRequest: {
            reason?: string;
        };
        ReplaceRolesRequest: {
            roleCodes?: string[];
        };
        RetractTimesheetRequest: {
            reason?: string;
        };
        ReturnTaskRequest: {
            reason?: string;
        };
        RoleRequest: {
            /** @enum {string} */
            accessScope?: "ALL" | "CUSTOM_ORG_UNITS" | "OWN_ORG_UNIT" | "OWN_ORG_UNIT_SUBTREE" | "SELF" | "SELF_AND_DIRECT_REPORTS";
            code?: string;
            enabled?: boolean;
            name?: string;
            permissionCodes?: string[];
        };
        RoleResponse: {
            /** @enum {string} */
            accessScope: "ALL" | "CUSTOM_ORG_UNITS" | "OWN_ORG_UNIT" | "OWN_ORG_UNIT_SUBTREE" | "SELF" | "SELF_AND_DIRECT_REPORTS";
            administrative: boolean;
            code: string;
            enabled: boolean;
            kind: string;
            name: string;
            permissionCodes: string[];
            roleId: string;
        };
        SubmitTimesheetRequest: {
            hours?: number;
            /** Format: int64 */
            projectManagerId?: number;
            /** Format: int64 */
            workPackageId?: number;
        };
        SubmitTimesheetResponse: {
            approvalInstanceId?: string | null;
            errorCode?: string | null;
            outcome: string;
            remainingHours?: number | null;
        };
        SyncDiffResponse: {
            changeKind: string;
            changedFields: string[];
            detail?: string | null;
            entityKind: string;
            issueKind?: string | null;
            subjectKey: string;
        };
        SyncRunDetailResponse: {
            diffs: components["schemas"]["SyncDiffResponse"][];
            syncRun: components["schemas"]["SyncRunResponse"];
        };
        SyncRunResponse: {
            /** Format: int32 */
            employedWithoutWecomCount?: number | null;
            /** Format: int32 */
            employeeChangeCount?: number | null;
            failureReason?: string | null;
            /** Format: date-time */
            finishedAt?: string | null;
            heldReason?: string | null;
            /** Format: int32 */
            incomingActiveOrgUnitCount?: number | null;
            /** Format: int32 */
            incomingEmployedCount?: number | null;
            /** Format: int32 */
            isolatedRowCount?: number | null;
            /** Format: int32 */
            issueCount?: number | null;
            /** Format: int32 */
            orgUnitChangeCount?: number | null;
            /** Format: int32 */
            positionChangeCount?: number | null;
            /** Format: int32 */
            previousActiveOrgUnitCount?: number | null;
            /** Format: int32 */
            previousEmployedCount?: number | null;
            /** Format: date-time */
            releasedAt?: string | null;
            releasedBy?: string | null;
            releasedByRunId?: string | null;
            /** Format: int32 */
            sourceEmployeeCount?: number | null;
            /** Format: int32 */
            sourceOrgUnitCount?: number | null;
            /** Format: date-time */
            startedAt: string;
            status: string;
            syncRunId: string;
            trigger: string;
        };
        TaskDetailResponse: {
            assignee?: string | null;
            canAct: boolean;
            candidates: string[];
            /** Format: date-time */
            createdAt: string;
            documentId: string;
            documentType: string;
            initiator: string;
            instanceId: string;
            nodeKey: string;
            nodeName: string;
            processKey: string;
            taskId: string;
        };
        TicketLoginRequest: {
            ticket?: string;
        };
        TimesheetResponse: {
            approvalInstanceId?: string | null;
            canWithdraw: boolean;
            /** Format: int32 */
            round: number;
            status: string;
            timesheetId: string;
        };
        TransferTaskRequest: {
            /** Format: int64 */
            assigneeId?: number;
        };
        UpdateLegalEntityRequest: {
            /** Format: int64 */
            accountingOrganizationNumber?: number;
            code?: string;
            name?: string;
            status?: string;
        };
        UserAccountResponse: {
            roleCodes: string[];
            status: string;
            userId: string;
        };
        WecomLoginRequest: {
            code?: string;
            /** @enum {string} */
            entry?: "WORKBENCH" | "MINI_PROGRAM" | "QR_CODE_LOGIN";
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
    get_6: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                    "*/*": components["schemas"]["TimesheetResponse"];
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
    cancel: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RetractTimesheetRequest"];
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
    submit: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SubmitTimesheetRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SubmitTimesheetResponse"];
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
    withdraw: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RetractTimesheetRequest"];
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
    ofEmployee: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                employeeId: number;
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
            header?: {
                "If-Match"?: string;
                "If-None-Match"?: string;
            };
            path: {
                employeeId: number;
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
            header?: {
                "If-Match"?: string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                employeeId: number;
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
            header?: {
                "If-Match"?: string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                employeeId: number;
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
                legalEntityId: number;
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
            header?: {
                "If-Match"?: string;
            };
            path: {
                legalEntityId: number;
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
                orgUnitId: number;
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
            header?: {
                "If-Match"?: string;
                "If-None-Match"?: string;
            };
            path: {
                orgUnitId: number;
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
            header?: {
                "If-Match"?: string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                orgUnitId: number;
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
            header?: {
                "If-Match"?: string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                orgUnitId: number;
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
    list_6: {
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
                orgUnitId: number;
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
    list_5: {
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
                syncRunId: number;
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
    release: {
        parameters: {
            query?: never;
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                syncRunId: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["ReleaseSyncRunRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SyncRunResponse"];
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
            header?: {
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
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
            header?: {
                "If-Match"?: string;
                "If-None-Match"?: string;
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
    list_4: {
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
                    "*/*": components["schemas"]["InboxResponse"];
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
            header?: {
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
            header?: {
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
            header?: {
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
            header?: {
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
                actorId?: number;
                onBehalfOfId?: number;
                action?: string;
                actionPrefix?: string;
                sourceAddress?: string;
                result?: string;
                subjectKey?: string;
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
    byLegacyNumber: {
        parameters: {
            query: {
                documentType: "QUOTE" | "TIMESHEET" | "JOURNAL_VOUCHER";
                /** @description Exactly one of legacyNumber, legacyNumberPrefix and documentId is given */
                legacyNumber?: string;
                /** @description Exactly one of legacyNumber, legacyNumberPrefix and documentId is given */
                legacyNumberPrefix?: string;
                limit?: number;
                cursor?: string;
                /** @description Exactly one of legacyNumber, legacyNumberPrefix and documentId is given */
                documentId?: number;
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
                    "*/*": components["schemas"]["CursorPageLegacyNumberResponse"] | components["schemas"]["LegacyNumberResponse"];
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
                documentType: "QUOTE" | "TIMESHEET" | "JOURNAL_VOUCHER";
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
            header?: {
                "If-Match"?: string;
            };
            path: {
                documentType: "QUOTE" | "TIMESHEET" | "JOURNAL_VOUCHER";
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
    list: {
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
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                roleId: number;
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
            header?: {
                "If-Match"?: string;
            };
            path: {
                roleId: number;
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
    delete: {
        parameters: {
            query?: never;
            header?: {
                "If-Match"?: string;
            };
            path: {
                roleId: number;
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
    account: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: number;
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
            header?: {
                "If-Match"?: string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                userId: number;
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
            header?: {
                "If-Match"?: string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                userId: number;
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
    replaceRoles: {
        parameters: {
            query?: never;
            header?: {
                "If-Match"?: string;
                /** @description The same key with the same request within 24 hours gets the first answer again */
                "Idempotency-Key"?: string;
            };
            path: {
                userId: number;
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
}
