package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyInboxPropertiesTest {

    @Test
    @DisplayName("P3B-L9 the text form of the to-do connection settings hides the password and still shows the rest")
    void toStringHidesThePassword() {
        String password = UUID.randomUUID().toString();
        LegacyInboxProperties properties = new LegacyInboxProperties("jdbc:sqlserver://old:1433;databaseName=old", "todo_reader", password, null,
                "https://old.example.test/{taskId}");

        assertThat(properties.toString()).doesNotContain(password).contains("password=***").contains("todo_reader")
                .contains("databaseName=old").contains(LegacyInboxProperties.DEFAULT_VIEW);
        assertThat(LegacyInboxProperties.none().toString()).contains("password=none");
    }
}
