package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What SQL Server tells a login about itself; every method may fail, and a failure means the item cannot be determined. */
interface ReadOnlyCatalog {

    List<String> databasePermissions();

    List<String> serverPermissions();

    List<DatabaseLevelPermission> databaseLevelPermissions();

    /** The principals of sys.user_token other than the user itself. */
    List<String> roles();

    /** IS_SRVROLEMEMBER of every fixed server role the server lists, and of the given ones in any case; null where the server cannot tell. */
    Map<String, Integer> fixedServerRoleMemberships(Set<String> alwaysAsked);

    Ownership ownership();

    List<String> reachableDatabases();

    String currentDatabase();
}
