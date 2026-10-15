package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.ExternalFailure;
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

    private final JdbcClient interactive;
    private final JdbcClient background;
    private final SeeyonMapping mapping;
    private final SeeyonViewReader mapper;
    private final Runnable recheckBeforeSnapshot;

    SeeyonOrgSource(JdbcClient interactive, JdbcClient background, SeeyonMapping mapping, Runnable recheckBeforeSnapshot) {
        this.interactive = interactive;
        this.background = background;
        this.mapping = mapping;
        this.mapper = new SeeyonViewReader(mapping);
        this.recheckBeforeSnapshot = recheckBeforeSnapshot;
    }

    /** Every sync and reconciliation starts here, so the login is checked again first (ADR-0037 item 4.4). */
    @Override
    public SourceOrgSnapshot readSnapshot() {
        recheckBeforeSnapshot.run();
        try {
            List<OrgUnitRow> rows = present(background.sql(SeeyonViewSql.ORG_UNITS).param("rootPathPrefix", mapping.rootPathPrefix()).query((rs, n) -> mapper.orgUnit(rs)).list());
            List<SourceRelationship> relationships = present(background.sql(SeeyonViewSql.RELATIONSHIPS)
                    .param("leaderTypeId", mapping.headRelationshipTypeId())
                    .param("managerTypeId", mapping.regionalReceivablesManagerRelationshipTypeId())
                    .query((rs, n) -> mapper.relationship(rs)).list());
            List<SourcePosition> positions = present(background.sql(SeeyonViewSql.POSITIONS).query((rs, n) -> mapper.position(rs)).list());
            List<SourcePerson> people = present(background.sql(SeeyonViewSql.MEMBERS).param("rootPathPrefix", mapping.rootPathPrefix()).query((rs, n) -> mapper.person(rs)).list());
            return new SourceOrgSnapshot(SeeyonOrgTree.resolve(rows, mapping.rootPath()), relationships, positions, people);
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
    }

    @Override
    public List<SourcePerson> findStaffByWecomUserId(WecomUserId wecomUserId) {
        try {
            return present(interactive.sql(SeeyonViewSql.MEMBERS_BY_WECOM_USER_ID)
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
            return present(interactive.sql(SeeyonViewSql.MEMBERS_BY_LOGIN_NAME)
                    .param("rootPathPrefix", mapping.rootPathPrefix())
                    .param("loginName", loginName)
                    .query((rs, n) -> mapper.person(rs)).list());
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
    }

    /** The message is stored in the sync run and the cause is dropped: SQL Server messages can carry column values (ADR-0037 item 3.5). */
    private static ExternalSystemUnavailableException unavailable(DataAccessException cause) {
        return new ExternalSystemUnavailableException("Seeyon views cannot be read (" + ExternalFailure.of(cause) + ")");
    }

    private static <T> List<T> present(List<Optional<T>> rows) {
        return rows.stream().flatMap(Optional::stream).toList();
    }
}
