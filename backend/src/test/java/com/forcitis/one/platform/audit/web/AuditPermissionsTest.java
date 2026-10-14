package com.forcitis.one.platform.audit.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.identity.api.Permission;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditPermissionsTest {

    @Test
    @DisplayName("the permission code repeated in the web layer equals platform.identity's Permission.Codes (the module graph forbids the import)")
    void matchesTheIdentityCatalogue() throws Exception {
        Field field = AuditPermissions.class.getDeclaredField("ENTRY_READ");
        field.setAccessible(true);

        assertThat(field.get(null)).isEqualTo(Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);
        assertThat(Arrays.stream(AuditPermissions.class.getDeclaredFields()).filter(f -> f.getType() == String.class)).hasSize(1);
    }
}
