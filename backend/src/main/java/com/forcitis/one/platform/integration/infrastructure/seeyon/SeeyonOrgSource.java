package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ExternalFailure;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.StaffLookup;
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
    private final SourceIdTranslation translation;

    SeeyonOrgSource(JdbcClient interactive, JdbcClient background, SeeyonMapping mapping, Runnable recheckBeforeSnapshot,
                    SourceIdTranslation translation) {
        this.interactive = interactive;
        this.background = background;
        this.mapping = mapping;
        this.mapper = new SeeyonViewReader(mapping);
        this.recheckBeforeSnapshot = recheckBeforeSnapshot;
        this.translation = translation;
    }

    /** Every sync and reconciliation starts here, so the login is checked again first (ADR-0037 item 4.4). */
    @Override
    public SourceOrgSnapshot readSnapshot() {
        recheckBeforeSnapshot.run();
        List<PlacedUnit> units;
        List<RelationshipRow> relationships;
        List<PostRow> positions;
        List<MemberRow> people;
        try {
            List<OrgUnitRow> rows = present(background.sql(SeeyonViewSql.ORG_UNITS).param("rootPathPrefix", mapping.rootPathPrefix()).query((rs, n) -> mapper.orgUnit(rs)).list());
            relationships = present(background.sql(SeeyonViewSql.RELATIONSHIPS)
                    .param("leaderTypeId", mapping.headRelationshipTypeId())
                    .param("managerTypeId", mapping.regionalReceivablesManagerRelationshipTypeId())
                    .query((rs, n) -> mapper.relationship(rs)).list());
            positions = present(background.sql(SeeyonViewSql.POSITIONS).query((rs, n) -> mapper.position(rs)).list());
            people = present(background.sql(SeeyonViewSql.MEMBERS).param("rootPathPrefix", mapping.rootPathPrefix()).query((rs, n) -> mapper.person(rs)).list());
            units = SeeyonOrgTree.resolve(rows, mapping.rootPath());
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
        return translation.snapshot(units, relationships, positions, people);
    }

    @Override
    public List<SourcePerson> findStaffByLogin(StaffLookup lookup) {
        JdbcClient.StatementSpec query = switch (lookup) {
            case StaffLookup.ByWecomUser byWecom -> interactive.sql(SeeyonViewSql.MEMBERS_BY_WECOM_USER_ID)
                    .param("wecomUserId", byWecom.wecomUserId().value());
            case StaffLookup.ByLoginName byLogin -> interactive.sql(SeeyonViewSql.MEMBERS_BY_LOGIN_NAME).param("loginName", byLogin.loginName());
        };
        List<MemberRow> found;
        try {
            found = present(query.param("rootPathPrefix", mapping.rootPathPrefix()).query((rs, n) -> mapper.person(rs)).list());
        } catch (DataAccessException unreachable) {
            throw unavailable(unreachable);
        }
        return translation.people(found);
    }

    /** The message reaches the reconciliation report and the callers of a login lookup, and the cause is dropped: SQL Server messages can carry column values (ADR-0037 item 3.5). */
    private static ExternalSystemUnavailableException unavailable(DataAccessException cause) {
        return new ExternalSystemUnavailableException("Seeyon views cannot be read (" + ExternalFailure.of(cause) + ")");
    }

    private static <T> List<T> present(List<Optional<T>> rows) {
        return rows.stream().flatMap(Optional::stream).toList();
    }
}
