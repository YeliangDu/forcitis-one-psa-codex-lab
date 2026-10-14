package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Only the four whitelisted views are ever queried; business facts are not reachable through them. */
final class SeeyonViewSql {

    static final String SCHEMA = "forcitis_one";

    static final String ORG_UNIT_VIEW = "v_org_unit";
    static final String ORG_RELATIONSHIP_VIEW = "v_org_relationship";
    static final String ORG_POST_VIEW = "v_org_post";
    static final String ORG_MEMBER_VIEW = "v_org_member";

    /** The startup check requires the database to match it exactly. */
    static final Map<String, List<String>> VIEW_COLUMNS = columns();

    static final int PATH_LEVEL_LENGTH = 4;

    static final String ORG_UNITS = "SELECT ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, "
            + "DELIVERY_FLAG_ENUM_ID FROM " + SCHEMA + "." + ORG_UNIT_VIEW + " WHERE PATH LIKE :rootPathPrefix";

    /** Type ids are parameters, never literals. */
    static final String RELATIONSHIPS = "SELECT SOURCE_MEMBER_ID, ORG_UNIT_ID, RELATIONSHIP_TYPE_ID, MEMBER_IS_DELETED FROM "
            + SCHEMA + "." + ORG_RELATIONSHIP_VIEW + " WHERE RELATIONSHIP_TYPE_ID IN (:leaderTypeId, :managerTypeId)";

    static final String POSITIONS = "SELECT ID, CODE, NAME, SORT_ID, STATUS, IS_DELETED FROM " + SCHEMA + "." + ORG_POST_VIEW;

    private static final String MEMBER_SELECT = "SELECT ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, "
            + "WE_COM_ID, IS_INTERNAL, LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID FROM " + SCHEMA + "." + ORG_MEMBER_VIEW;

    /** Same scope for bulk and single lookups. */
    static final String MEMBER_SCOPE = " WHERE ID IS NOT NULL AND IS_INTERNAL = 1 AND CODE IS NOT NULL AND CODE <> '' "
            + "AND ORG_UNIT_ID IS NOT NULL AND ORG_UNIT_PATH LIKE :rootPathPrefix";

    static final String MEMBERS = MEMBER_SELECT + MEMBER_SCOPE;

    static final String MEMBERS_BY_WECOM_USER_ID = MEMBERS + " AND WE_COM_ID = :wecomUserId";

    static final String MEMBERS_BY_LOGIN_NAME = MEMBERS + " AND LOGIN_NAME = :loginName";

    static final String COUNT_RELATIONSHIPS_OF_TYPE = "SELECT COUNT(*) FROM " + SCHEMA + "." + ORG_RELATIONSHIP_VIEW
            + " WHERE RELATIONSHIP_TYPE_ID = :typeId";
    static final String COUNT_UNITS_AT_ROOT = "SELECT COUNT(*) FROM " + SCHEMA + "." + ORG_UNIT_VIEW + " WHERE PATH = :rootPath";
    static final String COUNT_DELIVERY_MARKED = "SELECT COUNT(*) FROM " + SCHEMA + "." + ORG_UNIT_VIEW
            + " WHERE DELIVERY_FLAG_ENUM_ID = :enumId";
    static final String COUNT_FEE_EARNER_MARKED = "SELECT COUNT(*) FROM " + SCHEMA + "." + ORG_MEMBER_VIEW
            + " WHERE FEE_EARNER_ENUM_ID = :enumId";

    /** INFORMATION_SCHEMA is the only catalog read; it lists exactly the objects the read-only account can see. */
    static final String VIEW_COLUMNS_IN_SCHEMA = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA = :schema";

    private SeeyonViewSql() {
    }

    private static Map<String, List<String>> columns() {
        Map<String, List<String>> views = new LinkedHashMap<>();
        views.put(ORG_UNIT_VIEW, List.of("ID", "CODE", "NAME", "SORT_ID", "PATH", "IS_DELETED", "IS_INTERNAL", "IS_ENABLE",
                "CATEGORY", "DELIVERY_FLAG_ENUM_ID"));
        views.put(ORG_RELATIONSHIP_VIEW, List.of("SOURCE_MEMBER_ID", "ORG_UNIT_ID", "RELATIONSHIP_TYPE_ID", "MEMBER_IS_DELETED"));
        views.put(ORG_POST_VIEW, List.of("ID", "CODE", "NAME", "SORT_ID", "STATUS", "IS_DELETED"));
        views.put(ORG_MEMBER_VIEW, List.of("ID", "NAME", "CODE", "ORG_UNIT_ID", "ORG_UNIT_PATH", "POST_ID", "SUPERVISOR_MEMBER_ID",
                "WE_COM_ID", "IS_INTERNAL", "LOGIN_NAME", "ENTERPRISE_EMAIL", "GENDER", "FEE_EARNER_ENUM_ID"));
        return Collections.unmodifiableMap(views);
    }
}
