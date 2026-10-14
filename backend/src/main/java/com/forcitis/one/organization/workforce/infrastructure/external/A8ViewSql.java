package com.forcitis.one.organization.workforce.infrastructure.external;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every statement the new system sends to A8, as testable constants. Only the four whitelisted views (organisation
 * and identity columns, REIMAGINED_ARCHITECTURE §3.3 / backend/architecture/a8-view-columns.yaml) are ever queried;
 * the views themselves are built and maintained by the A8 administrator (docs/modernization/phase1a/A8_VIEWS.sql).
 * Bulk sync and single-person lookup use the same SELECT and the same scope; only the extra condition differs.
 * Business facts (contracts, revenue, cost, invoices, receipts, payments) are not reachable through these views.
 */
final class A8ViewSql {

    /** Schema in the A8 database that holds the read-only views. */
    static final String SCHEMA = "forcitis_one";

    static final String ORG_UNIT_VIEW = "v_org_unit";
    static final String ORG_RELATIONSHIP_VIEW = "v_org_relationship";
    static final String ORG_POST_VIEW = "v_org_post";
    static final String ORG_MEMBER_VIEW = "v_org_member";

    /** The whitelist: view name to its exact column list. The startup check requires the database to match it exactly. */
    static final Map<String, List<String>> VIEW_COLUMNS = columns();

    /** A8 path prefix of the sync scope: the whole subtree below the root (RULE-354 and, unified, RULE-361). */
    static final String ROOT_PATH_PREFIX = "00000001%";

    static final String ORG_UNITS = "SELECT ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, "
            + "DELIVERY_FLAG_ENUM_ID FROM " + SCHEMA + "." + ORG_UNIT_VIEW + " WHERE PATH LIKE '" + ROOT_PATH_PREFIX + "'";

    /** Only the two configured relationship types are read; the type ids are parameters, never literals. */
    static final String RELATIONSHIPS = "SELECT SOURCE_MEMBER_ID, ORG_UNIT_ID, RELATIONSHIP_TYPE_ID, MEMBER_IS_DELETED FROM "
            + SCHEMA + "." + ORG_RELATIONSHIP_VIEW + " WHERE RELATIONSHIP_TYPE_ID IN (:leaderTypeId, :managerTypeId)";

    static final String POSITIONS = "SELECT ID, CODE, NAME, SORT_ID, STATUS, IS_DELETED FROM " + SCHEMA + "." + ORG_POST_VIEW;

    private static final String MEMBER_SELECT = "SELECT ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, LEADER_MEMBER_ID, "
            + "WE_COM_ID, IS_INTERNAL, LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID FROM " + SCHEMA + "." + ORG_MEMBER_VIEW;

    /** Same scope for bulk and single lookup: internal people with a code and an org unit inside the root subtree (RULE-361). */
    static final String MEMBER_SCOPE = " WHERE ID IS NOT NULL AND IS_INTERNAL = 1 AND CODE IS NOT NULL AND CODE <> '' "
            + "AND ORG_UNIT_ID IS NOT NULL AND ORG_UNIT_PATH LIKE '" + ROOT_PATH_PREFIX + "'";

    static final String MEMBERS = MEMBER_SELECT + MEMBER_SCOPE;

    static final String MEMBERS_BY_WECOM_USER_ID = MEMBERS + " AND WE_COM_ID = :wecomUserId";

    /** Startup checks (RULE-356): the configured ids must exist in the data. */
    static final String COUNT_RELATIONSHIPS_OF_TYPE = "SELECT COUNT(*) FROM " + SCHEMA + "." + ORG_RELATIONSHIP_VIEW
            + " WHERE RELATIONSHIP_TYPE_ID = :typeId";
    static final String COUNT_DELIVERY_MARKED = "SELECT COUNT(*) FROM " + SCHEMA + "." + ORG_UNIT_VIEW
            + " WHERE DELIVERY_FLAG_ENUM_ID = :enumId";
    static final String COUNT_FEE_EARNER_MARKED = "SELECT COUNT(*) FROM " + SCHEMA + "." + ORG_MEMBER_VIEW
            + " WHERE FEE_EARNER_ENUM_ID = :enumId";

    /** INFORMATION_SCHEMA is the only catalog read; it lists exactly the objects the read-only account can see. */
    static final String VIEW_COLUMNS_IN_SCHEMA = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA = :schema";

    private A8ViewSql() {
    }

    private static Map<String, List<String>> columns() {
        Map<String, List<String>> views = new LinkedHashMap<>();
        views.put(ORG_UNIT_VIEW, List.of("ID", "CODE", "NAME", "SORT_ID", "PATH", "IS_DELETED", "IS_INTERNAL", "IS_ENABLE",
                "CATEGORY", "DELIVERY_FLAG_ENUM_ID"));
        views.put(ORG_RELATIONSHIP_VIEW, List.of("SOURCE_MEMBER_ID", "ORG_UNIT_ID", "RELATIONSHIP_TYPE_ID", "MEMBER_IS_DELETED"));
        views.put(ORG_POST_VIEW, List.of("ID", "CODE", "NAME", "SORT_ID", "STATUS", "IS_DELETED"));
        views.put(ORG_MEMBER_VIEW, List.of("ID", "NAME", "CODE", "ORG_UNIT_ID", "ORG_UNIT_PATH", "POST_ID", "LEADER_MEMBER_ID",
                "WE_COM_ID", "IS_INTERNAL", "LOGIN_NAME", "ENTERPRISE_EMAIL", "GENDER", "FEE_EARNER_ENUM_ID"));
        return java.util.Collections.unmodifiableMap(views);
    }
}
