package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyPropertiesTest {

    @Test
    @DisplayName("P3B-L9 the text form of the short-name connection settings hides the password and still shows the rest")
    void toStringHidesThePassword() {
        String password = UUID.randomUUID().toString();
        LegacyProperties properties = new LegacyProperties(true, "jdbc:sqlserver://old:1433;databaseName=old", "names_reader", password,
                "dbo.v_one_legacy_org_short_name");

        assertThat(properties.toString()).doesNotContain(password).contains("password=***").contains("names_reader")
                .contains("databaseName=old").contains("dbo.v_one_legacy_org_short_name");
        assertThat(new LegacyProperties(false, null, null, null, "dbo.v_one_legacy_org_short_name").toString()).contains("password=none");
    }
}
