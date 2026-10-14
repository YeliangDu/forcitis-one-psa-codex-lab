// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
export interface paths {
    "/api/v1/cost/timesheets/{id}": {
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
        get?: never;
        put: operations["update_1"];
        post?: never;
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
        get: operations["get_4"];
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
        get: operations["get_3"];
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
    "/api/v1/platform/approval-task-records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_records"];
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
        get: operations["get_2"];
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
        /** @enum {string} */
        ApprovalStatus: "APPROVED" | "CANCELLED" | "REJECTED" | "RETURNED" | "RUNNING";
        AuditEntryPageResponse: {
            items: components["schemas"]["AuditEntryResponse"][];
            nextCursor?: string;
        };
        AuditEntryResponse: {
            action: string;
            actorId?: string;
            /** Format: date-time */
            createdAt: string;
            detail?: string;
            entryId: string;
            onBehalfOfId?: string;
            subjectKey: string;
        };
        CalendarDayRequest: {
            /** Format: date */
            calendarDate: string;
            dayKind: components["schemas"]["DayKind"];
        };
        CalendarDayResponse: {
            /** Format: date */
            calendarDate: string;
            dayKind: components["schemas"]["DayKind"];
        };
        CreateLegalEntityRequest: {
            code?: string;
            name?: string;
        };
        CurrentUserResponse: {
            displayName: string;
            navigation: components["schemas"]["NavigationResponse"][];
            orgUnitId?: string;
            orgUnitName?: string;
            permissionCodes: string[];
            userId: string;
        };
        /** @enum {string} */
        DayKind: "HOLIDAY" | "MAKE_UP_WORKDAY";
        DecideTaskRequest: {
            /** @enum {string} */
            outcome?: "APPROVE" | "REJECT";
            reason?: string;
        };
        DecisionResponse: {
            instanceId: string;
            status: components["schemas"]["ApprovalStatus"];
        };
        /** @enum {string} */
        DocumentType: "JOURNAL_VOUCHER" | "QUOTE" | "TIMESHEET";
        EmergencyLoginRequest: {
            loginName?: string;
            password?: string;
            totpCode?: string;
        };
        /** @enum {string} */
        ErrorCode: "COST_TIMESHEET_CONCURRENT_CHANGE" | "COST_TIMESHEET_CUTOFF_PASSED" | "COST_TIMESHEET_EXCEEDS_REMAINING_HOURS" | "COST_TIMESHEET_NOT_FOUND" | "COST_TIMESHEET_REASON_REQUIRED" | "COST_TIMESHEET_TRANSITION_ILLEGAL" | "COST_TIMESHEET_UNKNOWN_WORK_PACKAGE" | "ORGANIZATION_ATTENDANCE_DAY_DUPLICATE" | "ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN" | "ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR" | "ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED" | "ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY" | "ORGANIZATION_ATTENDANCE_YEAR_CONFLICT" | "ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND" | "ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED" | "ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE" | "ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN" | "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL" | "ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED" | "ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND" | "ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL" | "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID" | "PLATFORM_APPROVAL_CONCURRENT_CHANGE" | "PLATFORM_APPROVAL_CUTOFF_PASSED" | "PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE" | "PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK" | "PLATFORM_APPROVAL_DECISION_EMPTY" | "PLATFORM_APPROVAL_DECISION_SINGLE_ONLY" | "PLATFORM_APPROVAL_HOOK_BLOCKED" | "PLATFORM_APPROVAL_HOOK_NOT_REGISTERED" | "PLATFORM_APPROVAL_NOT_INITIATOR" | "PLATFORM_APPROVAL_NO_ROUTE" | "PLATFORM_APPROVAL_REASON_REQUIRED" | "PLATFORM_APPROVAL_REQUEST_NOT_FOUND" | "PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED" | "PLATFORM_APPROVAL_TASK_NOT_FOUND" | "PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID" | "PLATFORM_APPROVAL_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_ACCOUNT_DISABLED" | "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND" | "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_CONCURRENT_UPDATE" | "PLATFORM_IDENTITY_CREDENTIALS_INVALID" | "PLATFORM_IDENTITY_EMPLOYEE_SEPARATED" | "PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN" | "PLATFORM_IDENTITY_NETWORK_DENIED" | "PLATFORM_IDENTITY_PERMISSION_UNKNOWN" | "PLATFORM_IDENTITY_ROLE_BLANK_NAME" | "PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE" | "PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME" | "PLATFORM_IDENTITY_ROLE_INVALID_CODE" | "PLATFORM_IDENTITY_ROLE_NOT_ALLOWED" | "PLATFORM_IDENTITY_ROLE_NOT_FOUND" | "PLATFORM_IDENTITY_ROLE_RESERVED_CODE" | "PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN" | "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL" | "PLATFORM_IDENTITY_SSO_UNAVAILABLE" | "PLATFORM_IDENTITY_STATE_INVALID" | "PLATFORM_IDENTITY_TICKET_INVALID" | "PLATFORM_IDENTITY_TICKET_REPLAYED" | "PLATFORM_IDENTITY_WECOM_CODE_INVALID" | "PLATFORM_IDENTITY_WECOM_NOT_BOUND" | "PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL" | "PLATFORM_NUMBERING_CONCURRENT_UPDATE" | "PLATFORM_NUMBERING_RULE_INVALID" | "PLATFORM_NUMBERING_RULE_NOT_FOUND" | "PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL" | "PLATFORM_WEB_ACCESS_DENIED" | "PLATFORM_WEB_CORRELATION_INVALID" | "PLATFORM_WEB_CURSOR_INVALID" | "PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED" | "PLATFORM_WEB_REQUEST_INVALID" | "PLATFORM_WEB_SOURCE_BROKEN" | "PLATFORM_WEB_SOURCE_UNAVAILABLE" | "PLATFORM_WEB_UNAUTHENTICATED" | "PLATFORM_WEB_UNEXPECTED" | "PLATFORM_WEB_VERSION_MISMATCH" | "PLATFORM_WEB_VERSION_REQUIRED";
        /** @enum {string} */
        InboxCategory: "NOTICE" | "TODO";
        InboxItemResponse: {
            category: components["schemas"]["InboxCategory"];
            claimed: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            deadlineAt?: string;
            detailUrl?: string;
            documentId?: string;
            nodeName?: string;
            overdue: boolean;
            processKey?: components["schemas"]["ProcessKey"];
            source: components["schemas"]["InboxSource"];
            taskId: string;
            title?: string;
        };
        InboxResponse: {
            items: components["schemas"]["InboxItemResponse"][];
            legacyAvailable: boolean;
            /** Format: int32 */
            legacyCount: number;
            /** Format: int32 */
            newCount: number;
            nextCursor?: string;
            /** Format: int32 */
            noticeCount: number;
            /** Format: int32 */
            totalCount: number;
        };
        /** @enum {string} */
        InboxSource: "LEGACY" | "NEW";
        LegacyNumberResponse: {
            documentId: string;
            documentType: string;
            importBatch: string;
            legacyNumber: string;
            legalEntityId: string;
        };
        LegalEntityResponse: {
            code: string;
            legalEntityId: string;
            name: string;
            status: string;
        };
        LoginStateRequest: {
            /** @enum {string} */
            entry?: "WORKBENCH" | "MINI_PROGRAM" | "QR_CODE_LOGIN";
        };
        LoginStateResponse: {
            state: string;
        };
        MaintainWorkCalendarRequest: {
            days: components["schemas"]["CalendarDayRequest"][];
            lockedDaysConfirmed?: boolean;
        };
        NavigationResponse: {
            children: components["schemas"]["NavigationResponse"][];
            name: string;
            routePath?: string;
        };
        NumberingRuleRequest: {
            pattern?: string;
            resetPolicy?: string;
            /** Format: int64 */
            startValue?: number;
        };
        NumberingRuleResponse: {
            /** Format: int64 */
            capacity: number;
            confirmationStatus: string;
            documentType: components["schemas"]["DocumentType"];
            documentTypeCode?: string;
            pattern: string;
            resetPolicy: string;
            /** Format: int64 */
            startValue: number;
        };
        OrgUnitDetailResponse: {
            ancestorOrgUnitIds: string[];
            orgUnit: components["schemas"]["OrgUnitResponse"];
            orgUnitAndBelowIds: string[];
        };
        OrgUnitResponse: {
            code: string;
            deliveryUnit: boolean;
            headEmployeeId?: string;
            name: string;
            orgUnitId: string;
            parentOrgUnitId?: string;
            regionCategory?: string;
            regionalReceivablesManagerId?: string;
            shortName?: string;
            /** Format: int32 */
            sortOrder?: number;
            status: string;
        };
        ProblemDetail: {
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
        /** @enum {string} */
        ProcessKey: "COST_TIMESHEET_APPROVAL";
        ReleaseResponse: {
            message?: string;
            outcome: string;
            syncRun?: components["schemas"]["SyncRunResponse"];
        };
        ReplaceRolesRequest: {
            roleCodes?: string[];
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
            releasedBy?: string;
            releasedByRunId?: string;
            /** Format: int32 */
            sourceEmployeeCount?: number;
            /** Format: int32 */
            sourceOrgUnitCount?: number;
            /** Format: date-time */
            startedAt: string;
            status: string;
            syncRunId: string;
            trigger: string;
        };
        TaskDetailResponse: {
            assignee?: string;
            assigneeName?: string;
            canAct: boolean;
            candidateNames: string[];
            candidates: string[];
            /** Format: date-time */
            createdAt: string;
            /** Format: date-time */
            deadlineAt?: string;
            documentId: string;
            documentType: components["schemas"]["DocumentType"];
            history: components["schemas"]["TaskRecordResponse"][];
            initiator: string;
            initiatorName: string;
            initiatorOrgUnitName?: string;
            instanceId: string;
            nodeKey: string;
            nodeName: string;
            overdue: boolean;
            processKey: components["schemas"]["ProcessKey"];
            summary?: string;
            taskId: string;
        };
        /** @enum {string} */
        TaskRecordAction: "APPROVED" | "AUTO_APPROVED" | "REJECTED" | "RETURNED" | "TRANSFERRED";
        TaskRecordPageResponse: {
            items: components["schemas"]["TaskRecordResponse"][];
            nextCursor?: string;
        };
        TaskRecordResponse: {
            action: components["schemas"]["TaskRecordAction"];
            actorId: string;
            actorName: string;
            /** Format: date-time */
            decidedAt: string;
            documentId: string;
            documentType: components["schemas"]["DocumentType"];
            instanceId: string;
            nodeKey: string;
            nodeName: string;
            onBehalfOfName?: string;
            processKey: components["schemas"]["ProcessKey"];
            reason?: string;
            recordId: string;
            /** Format: int32 */
            round: number;
            taskId: string;
        };
        TicketLoginRequest: {
            ticket?: string;
        };
        TimesheetResponse: {
            approvalInstanceId?: string;
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
            code?: string;
            name?: string;
            status?: string;
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
    get_5: {
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
            /** @description Error */
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
                    "*/*": components["schemas"]["LegalEntityResponse"][];
                };
            };
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
                    "*/*": components["schemas"]["SyncRunResponse"][];
                };
            };
            /** @description Error */
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
            /** @description Error */
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
                "Idempotency-Key"?: string;
            };
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
                    "*/*": components["schemas"]["ReleaseResponse"];
                };
            };
            /** @description Error */
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
                    "*/*": number[];
                };
            };
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
    list_records: {
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
                    "*/*": components["schemas"]["TaskRecordPageResponse"];
                };
            };
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
            /** @description Error */
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
                action?: string;
                subjectKey?: string;
                from?: string;
                to?: string;
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
                    "*/*": components["schemas"]["AuditEntryPageResponse"];
                };
            };
            /** @description Error */
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
                legacyNumber: string;
                documentId: number;
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
                    "*/*": components["schemas"]["LegacyNumberResponse"][] | components["schemas"]["LegacyNumberResponse"];
                };
            };
            /** @description Error */
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
            /** @description Error */
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
                    "*/*": components["schemas"]["NumberingRuleResponse"][];
                };
            };
            /** @description Error */
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
            /** @description Error */
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
                content?: never;
            };
            /** @description Error */
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
                    "*/*": components["schemas"]["RoleResponse"][];
                };
            };
            /** @description Error */
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
            /** @description Error */
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
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
            /** @description Error */
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
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
            /** @description Error */
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
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
            cookie?: never;
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
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
                content: {
                    "*/*": Record<string, never>;
                };
            };
            /** @description Error */
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
