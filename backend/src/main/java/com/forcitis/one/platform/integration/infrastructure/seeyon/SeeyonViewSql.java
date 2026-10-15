package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four frozen views (ADR-0037 item 6, Q1 (c)): each read here moves to the base tables when its adapter next changes. Base tables are
 * always named with dbo: the login's default schema is the view schema.
 */
final class SeeyonViewSql {

    static final String SCHEMA = "forcitis_one";
    static final String BASE_SCHEMA = "dbo";

    static final String ORG_UNIT_VIEW = "v_org_unit";
    static final String ORG_RELATIONSHIP_VIEW = "v_org_relationship";
    static final String ORG_POST_VIEW = "v_org_post";
    static final String ORG_MEMBER_VIEW = "v_org_member";

    static final String ORG_UNIT_TABLE = "ORG_UNIT";

    /** The startup check requires the database to match it exactly. */
    static final Map<String, List<String>> VIEW_COLUMNS = columns();

    /** The startup check requires each table to have at least these columns. */
    static final Map<String, List<String>> BASE_TABLE_COLUMNS = Map.of(ORG_UNIT_TABLE, List.of("ID", "TYPE"));

    static final int PATH_LEVEL_LENGTH = 4;

    /** LEFT JOIN: a view row without its base row reads TYPE as NULL and fails the run instead of dropping the unit. */
    static final String ORG_UNITS = "SELECT V.ID, V.CODE, V.NAME, V.SORT_ID, V.PATH, V.IS_DELETED, V.IS_INTERNAL, V.IS_ENABLE, V.CATEGORY, "
            + "V.DELIVERY_FLAG_ENUM_ID, B.TYPE FROM " + SCHEMA + "." + ORG_UNIT_VIEW + " V LEFT JOIN dbo.ORG_UNIT B ON B.ID = V.ID "
            + "WHERE V.PATH LIKE :rootPathPrefix";

    /** Type ids are parameters, never literals. */
    static final String RELATIONSHIPS = "SELECT SOURCE_MEMBER_ID, ORG_UNIT_ID, RELATIONSHIP_TYPE_ID, MEMBER_IS_DELETED FROM "
            + SCHEMA + "." + ORG_RELATIONSHIP_VIEW + " WHERE RELATIONSHIP_TYPE_ID IN (:leaderTypeId, :managerTypeId)";

    static final String POSITIONS = "SELECT ID, CODE, NAME, SORT_ID, STATUS, IS_DELETED FROM " + SCHEMA + "." + ORG_POST_VIEW;

    private static final String MEMBER_SELECT = "SELECT ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, "
            + "WE_COM_ID, IS_INTERNAL, LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID FROM " + SCHEMA + "." + ORG_MEMBER_VIEW;

    /** Same scope for bulk and single lookups. */
    static final String MEMBER_SCOPE = " WHERE ID IS NOT NULL AND (IS_INTERNAL = 1 OR IS_INTERNAL IS NULL) AND CODE IS NOT NULL AND CODE <> '' "
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

    /** Only the view schema: the login may read the whole database (ADR-0037), the column whitelist applies to the views alone. */
    static final String VIEW_COLUMNS_IN_SCHEMA = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA = :schema";

    static final String BASE_TABLE_COLUMNS_IN_SCHEMA = "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_SCHEMA = :schema AND UPPER(TABLE_NAME) IN (:tables)";

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
