package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Set;

/** code is read on create only; enabled defaults to true when missing. accessScope null means the least-privilege default SELF on create, "keep" on update. */
record RoleRequest(String code, String name, Boolean enabled, AccessScopeKind accessScope, Set<String> permissionCodes) {
}
