package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceOrgUnit;
import com.forcitis.one.organization.workforce.domain.SourcePosition;
import com.forcitis.one.organization.workforce.domain.SourceRelationship;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Anti-corruption layer to A8: reads only the whitelisted read-only views through a connection that cannot write.
 * The connection is the one qualified "a8" (see {@link ReadOnlyConnection}); nothing outside
 * organization.workforce.infrastructure may reach it.
 */
final class A8OrgSource implements OrgSource {

    private final JdbcClient jdbc;
    private final A8Mapping mapping;
    private final A8ViewMapper mapper;

    A8OrgSource(JdbcClient jdbc, A8Mapping mapping) {
        this.jdbc = jdbc;
        this.mapping = mapping;
        this.mapper = new A8ViewMapper(mapping);
    }

    @Override
    public SourceSnapshot fetchAll() {
        try {
            List<SourceOrgUnit> units = present(jdbc.sql(A8ViewSql.ORG_UNITS).query((rs, n) -> mapper.orgUnit(rs)).list());
            List<SourceRelationship> relationships = present(jdbc.sql(A8ViewSql.RELATIONSHIPS)
                    .param("leaderTypeId", mapping.headRelationshipTypeId())
                    .param("managerTypeId", mapping.regionalReceivablesManagerRelationshipTypeId())
                    .query((rs, n) -> mapper.relationship(rs)).list());
            List<SourcePosition> positions = present(jdbc.sql(A8ViewSql.POSITIONS).query((rs, n) -> mapper.position(rs)).list());
            List<SourceMember> members = present(jdbc.sql(A8ViewSql.MEMBERS).query((rs, n) -> mapper.member(rs)).list());
            return new SourceSnapshot(units, relationships, positions, members);
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
    }

    @Override
    public List<SourceMember> fetchMembersByWecomUserId(WecomUserId wecomUserId) {
        try {
            return present(jdbc.sql(A8ViewSql.MEMBERS_BY_WECOM_USER_ID)
                    .param("wecomUserId", wecomUserId.value())
                    .query((rs, n) -> mapper.member(rs)).list());
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
    }

    /** The message is stored in the sync run, so it names the failure class only, never connection details. */
    private static SourceUnavailableException unavailable(DataAccessException cause) {
        return new SourceUnavailableException("A8 views cannot be read (" + cause.getClass().getSimpleName() + ")", cause);
    }

    private static <T> List<T> present(List<Optional<T>> rows) {
        return rows.stream().flatMap(Optional::stream).toList();
    }
}
