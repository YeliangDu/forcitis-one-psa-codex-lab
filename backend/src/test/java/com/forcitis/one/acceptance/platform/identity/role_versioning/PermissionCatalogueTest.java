package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.identity.api.Permission;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 decision 1 item 3 (目录中不再使用的码保留，标为「已停用」，不删除；历史版本中的码始终能在目录中找到) and guard rule 8 (权限目录：目录中的码只能
 * 标停用，不能删除（快照比对）); ADR-0034-IMPL card row 0034-G8-04 and card 3.2 ({@code Permission.java} 加「已停用」标记). The snapshot below is
 * the catalogue of {@link Permission} on main f62af6ae8, before this card; every code in it must stay in the catalogue for good. The
 * implementation keeps its own snapshot file (card 3.3); this one only fixes the codes that existed when the acceptance test was written.
 * The way the marker is read is an assumed contract, see PR2_ACCEPTANCE.md 「假定契约」.
 */
class PermissionCatalogueTest {

    /** Permission codes at f62af6ae8 (backend/src/main/java/com/forcitis/one/platform/identity/api/Permission.java). */
    static final List<String> SNAPSHOT = List.of(
            "cost.timesheet:timesheet:submit", "cost.timesheet:timesheet:approve",
            "platform.numbering:numbering-rule:update", "platform.numbering:numbering-rule:read", "platform.numbering:numbering-rule:approve",
            "platform.numbering:voucher-numbering-rule:approve", "platform.numbering:legacy-number:read",
            "platform.approval:task:read", "platform.approval:task:approve",
            "platform.identity:role:read", "platform.identity:role:update", "platform.identity:account-role:update", "platform.identity:session:delete",
            "platform.integration:source-reference:update", "platform.integration:source-reference:approve",
            "platform.file:file:create", "platform.file:file:read",
            "platform.audit:audit-entry:read", "platform.audit:audit-entry:export", "platform.audit:own-login-record:read",
            "organization.workforce:org-unit:read", "organization.workforce:sync-run:read", "organization.workforce:sync-run:execute",
            "organization.workforce:separation-hold:approve", "organization.workforce:legal-entity:update",
            "organization.workforce:legal-entity-attribution-correction:create",
            "organization.attendance:work-calendar:read", "organization.attendance:work-calendar:update", "organization.attendance:locked-day:update");

    /** Assumed contract (PR2_ACCEPTANCE.md): {@code public boolean deprecated()} on each Permission constant, true for a code that is kept but no longer granted. */
    static final String DEPRECATED_MARKER = "deprecated";

    static Method deprecatedMarker() {
        Method marker = Arrays.stream(Permission.class.getMethods())
                .filter(m -> m.getName().equals(DEPRECATED_MARKER) && m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()))
                .findFirst().orElse(null);
        if (marker == null || marker.getReturnType() != boolean.class) {
            throw new AssertionError("ADR-0034 decision 1 item 3 / card 3.2: Permission has no deprecated marker; assumed contract is"
                    + " 'public boolean deprecated()' on Permission (PR2_ACCEPTANCE.md), found " + marker);
        }
        return marker;
    }

    /** True when the catalogue marks the constant deprecated (kept, no longer granted). */
    static boolean isDeprecated(Permission permission) {
        try {
            return (boolean) deprecatedMarker().invoke(permission);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("reading the deprecated marker of " + permission, e);
        }
    }

    @Test
    @DisplayName("0034-G8-04 权限目录只能标停用不能删除：卡开工前目录中的每个码（快照）仍在 Permission 目录中")
    void noCodeLeavesTheCatalogue() {
        Set<String> catalogue = Arrays.stream(Permission.values()).map(Permission::code).collect(Collectors.toSet());

        assertThat(catalogue).as("codes of the snapshot that the catalogue no longer has").containsAll(SNAPSHOT);
    }

    @Test
    @DisplayName("0034-G8-04 权限目录有「已停用」标记：每个码都能读出是否停用（卡 3.2）")
    void theCatalogueMarksDeprecatedCodes() {
        Method marker = deprecatedMarker();

        assertThat(marker.getReturnType()).isEqualTo(boolean.class);
        for (Permission permission : Permission.values()) {
            isDeprecated(permission);
        }
    }
}
