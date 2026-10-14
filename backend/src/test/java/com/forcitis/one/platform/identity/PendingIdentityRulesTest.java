package com.forcitis.one.platform.identity;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rules outside Phase 1a stay visible here so nobody believes they are covered; enable each in the phase named in its reason. */
class PendingIdentityRulesTest {

    @Test
    @Disabled("pending RULE-329: the KM jump itself (signed ticket built for the logged-in caller) arrives with the KM integration; 1a ships only the verifying side (ticket-login, issuers default off)")
    @DisplayName("RULE-329 jumping to KM needs a logged-in caller and takes the identity from the session")
    void kmJumpUsesTheSession() {
    }

    @Test
    @Disabled("pending RULE-347: 1a navigation is read-only (seeded by migrations); menu maintenance with tree integrity checks arrives with the first admin page that edits it")
    @DisplayName("RULE-347 a menu's parent must exist, not be itself and not be a button; a menu with children cannot be deleted")
    void menuTreeIntegrity() {
    }

    @Test
    @Disabled("pending RULE-353: the department dropdown needs the org tree and AccessScope users; first used by Phase 1b quotation lists")
    @DisplayName("RULE-353 a person who can see only themselves gets their own department and its ancestors in the dropdown")
    void departmentDropdownNarrowsForSelfOnlyUsers() {
    }

    @Test
    @Disabled("pending RULE-350/352: AccessScope is delivered as a type with its calculation (AccessScopeTest); passing it to a repository and failing closed on errors starts with the first business repository in Phase 1b (data_scope_grants table too)")
    @DisplayName("RULE-350 repositories take the AccessScope explicitly; any error in computing it refuses the read")
    void repositoriesTakeTheScopeExplicitly() {
    }
}
