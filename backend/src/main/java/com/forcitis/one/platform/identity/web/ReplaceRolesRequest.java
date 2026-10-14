package com.forcitis.one.platform.identity.web;

import java.util.Set;

/** The whole set of role codes the account should hold afterwards (RULE-346). The account is named in the path. */
record ReplaceRolesRequest(Set<String> roleCodes) {
}
