package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestPostgresNameTest {
    @Test
    @DisplayName("a database name is the lower-cased class name as a valid identifier, at most 63 bytes, and differs between classes whose cut names are equal")
    void databaseNamesAreValidShortAndDistinct() {
        String plain = TestPostgres.databaseName(TestPostgres.MIGRATED, TestPostgresNameTest.class);
        assertThat(plain).isEqualTo("migrated_com_forcitis_one_testpostgresnametest");

        String longName = TestPostgres.databaseName(TestPostgres.MIGRATED_TESTDATA, LongNamedFirstVariantOfTheClassUnderTestForDatabaseNaming.class);
        String other = TestPostgres.databaseName(TestPostgres.MIGRATED_TESTDATA, LongNamedSecondVariantOfTheClassUnderTestForDatabaseNaming.class);
        assertThat(longName).hasSizeLessThanOrEqualTo(63).matches("[a-z0-9_]+");
        assertThat(other).hasSizeLessThanOrEqualTo(63).matches("[a-z0-9_]+").isNotEqualTo(longName);
    }

    static class LongNamedFirstVariantOfTheClassUnderTestForDatabaseNaming {
    }

    static class LongNamedSecondVariantOfTheClassUnderTestForDatabaseNaming {
    }
}
