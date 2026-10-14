package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourcePosition;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

final class SeeyonOrgSource implements OrgSource {

    private final JdbcClient jdbc;
    private final SeeyonMapping mapping;
    private final SeeyonViewReader mapper;

    SeeyonOrgSource(JdbcClient jdbc, SeeyonMapping mapping) {
        this.jdbc = jdbc;
        this.mapping = mapping;
        this.mapper = new SeeyonViewReader(mapping);
    }

    @Override
    public SourceOrgSnapshot readSnapshot() {
        try {
            List<OrgUnitRow> rows = present(jdbc.sql(SeeyonViewSql.ORG_UNITS).param("rootPathPrefix", mapping.rootPathPrefix()).query((rs, n) -> mapper.orgUnit(rs)).list());
            List<SourceRelationship> relationships = present(jdbc.sql(SeeyonViewSql.RELATIONSHIPS)
                    .param("leaderTypeId", mapping.headRelationshipTypeId())
                    .param("managerTypeId", mapping.regionalReceivablesManagerRelationshipTypeId())
                    .query((rs, n) -> mapper.relationship(rs)).list());
            List<SourcePosition> positions = present(jdbc.sql(SeeyonViewSql.POSITIONS).query((rs, n) -> mapper.position(rs)).list());
            List<SourcePerson> people = present(jdbc.sql(SeeyonViewSql.MEMBERS).param("rootPathPrefix", mapping.rootPathPrefix()).query((rs, n) -> mapper.person(rs)).list());
            return new SourceOrgSnapshot(SeeyonOrgTree.resolve(rows, mapping.rootPath()), relationships, positions, people);
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
    }

    @Override
    public List<SourcePerson> findStaffByWecomUserId(WecomUserId wecomUserId) {
        try {
            return present(jdbc.sql(SeeyonViewSql.MEMBERS_BY_WECOM_USER_ID)
                    .param("rootPathPrefix", mapping.rootPathPrefix())
                    .param("wecomUserId", wecomUserId.value())
                    .query((rs, n) -> mapper.person(rs)).list());
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
    }

    @Override
    public List<SourcePerson> findStaffByLoginName(String loginName) {
        try {
            return present(jdbc.sql(SeeyonViewSql.MEMBERS_BY_LOGIN_NAME)
                    .param("rootPathPrefix", mapping.rootPathPrefix())
                    .param("loginName", loginName)
                    .query((rs, n) -> mapper.person(rs)).list());
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
    }

    /** The message is stored in the sync run, so it names the failure class only, never connection details. */
    private static ExternalSystemUnavailableException unavailable(DataAccessException cause) {
        return new ExternalSystemUnavailableException("Seeyon views cannot be read (" + cause.getClass().getSimpleName() + ")", cause);
    }

    private static <T> List<T> present(List<Optional<T>> rows) {
        return rows.stream().flatMap(Optional::stream).toList();
    }
}
