package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.MsdbPrivileges.TokenPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ServerPermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.Membership;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What SQL Server tells a login about itself; every method may fail, and a failure means the item cannot be determined. */
interface ReadOnlyCatalog {

    List<String> databasePermissions();

    List<String> serverPermissions();

    List<ServerPermissionRow> serverPermissionRows();

    List<DatabaseLevelPermission> databaseLevelPermissions();

    /**
     * The login's own rows and those of public on user securables (major_id above 0). A login sees public's rows because it is a member of
     * public (SQL Server 2022 container, PR #163 review H-1); whether SQL Server 2016 on RDS shows them too is part of the real-login check C-14.
     */
    List<SecurablePermissionRow> securablePermissionRows();

    /** The principals of sys.user_token other than the user itself. */
    List<String> roles();

    /** IS_SRVROLEMEMBER of every fixed server role the server lists, and of the given ones in any case; null where the server cannot tell. */
    Map<String, Integer> fixedServerRoleMemberships(Set<String> alwaysAsked);

    Ownership ownership();

    List<ReachableDatabase> reachableDatabases();

    /**
     * The role memberships of the login's own user (SID the login's) in the given system database, empty when it has none there; the database
     * must be one {@link #reachableDatabases()} named.
     */
    List<Membership> ownUserMemberships(String systemDatabase);

    /** The rows of sys.database_permissions of the login's own user in the given system database, as for {@link #ownUserMemberships}. */
    List<OwnUserPermission> ownUserPermissions(String systemDatabase);

    /**
     * HAS_PERMS_BY_NAME('msdb', 'DATABASE', p) for each p of {@link MsdbPrivileges#EFFECTIVE}, and under {@link MsdbPrivileges#IMPERSONATE} that of
     * IMPERSONATE on the user dbo, asked as the login inside msdb; null where the server cannot tell.
     */
    Map<String, Integer> msdbEffectivePermissions();

    /** The rows of sys.database_permissions in msdb of every principal of sys.user_token there, asked as the login inside msdb. */
    List<TokenPermission> msdbTokenPermissions();

    /** The names of the roles in the login's token in msdb (sys.user_token, type ROLE), asked as the login inside msdb; a name may be null where the server withholds it. */
    List<String> msdbTokenRoles();

    /**
     * How many schemas, objects and roles in msdb the principals of the login's token own, not counting the schema guest and each SQL Agent role own
     * under their own names; null where the server cannot tell.
     */
    Long msdbOwnedByToken();

    /** The SQL Agent jobs the login owns, as msdb.dbo.sysjobs_view shows them to it. */
    long ownedAgentJobs();

    String currentDatabase();
}
