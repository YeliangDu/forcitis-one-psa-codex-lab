package com.forcitis.one.platform.audit.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.identity.api.Permission;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditPermissionsTest {

    private static Object constant(String name) throws Exception {
        Field field = AuditPermissions.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    @Test
    @DisplayName("the permission codes repeated in the web layer equal platform.identity's Permission.Codes (the module graph forbids the import)")
    void matchesTheIdentityCatalogue() throws Exception {
        assertThat(constant("ENTRY_READ")).isEqualTo(Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);
        assertThat(constant("ENTRY_EXPORT")).isEqualTo(Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT);
        assertThat(constant("OWN_LOGIN_RECORD_READ")).isEqualTo(Permission.Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ);
        assertThat(Arrays.stream(AuditPermissions.class.getDeclaredFields()).filter(f -> f.getType() == String.class)).hasSize(3);
    }
}
