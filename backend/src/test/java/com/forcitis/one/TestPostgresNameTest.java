package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestPostgresNameTest {
    @Test
    @DisplayName("a database name is the readable lower-cased class name plus a hash of the exact name, a valid identifier of at most 63 bytes")
    void databaseNamesAreValidAndShort() {
        String plain = TestPostgres.databaseName(TestPostgres.MIGRATED, TestPostgresNameTest.class);
        assertThat(plain).matches("migrated_com_forcitis_one_testpostgresnametest_[0-9a-f]{8}");

        String longName = TestPostgres.databaseName(TestPostgres.MIGRATED_TESTDATA, "com.forcitis.one." + "x".repeat(80));
        assertThat(longName).hasSize(63).matches("[a-z0-9_]{54}_[0-9a-f]{8}");
    }

    @Test
    @DisplayName("class names that sanitise to the same text, or differ only in case, still get different database names")
    void namesThatSanitiseAlikeStayApart() {
        assertThat(TestPostgres.databaseName(TestPostgres.MIGRATED, "a.b_c.X")).isNotEqualTo(TestPostgres.databaseName(TestPostgres.MIGRATED, "a.b.c.X"));
        assertThat(TestPostgres.databaseName(TestPostgres.MIGRATED, "a.Foo")).isNotEqualTo(TestPostgres.databaseName(TestPostgres.MIGRATED, "a.foo"));
        assertThat(TestPostgres.databaseName(TestPostgres.MIGRATED, "a.X")).isNotEqualTo(TestPostgres.databaseName(TestPostgres.MIGRATED_TESTDATA, "a.X"));
    }
}
