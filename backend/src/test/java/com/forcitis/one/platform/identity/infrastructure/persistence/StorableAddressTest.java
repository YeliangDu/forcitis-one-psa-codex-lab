package com.forcitis.one.platform.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StorableAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "203.0.113.9", "2001:db8::1", "::1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "::ffff:203.0.113.9", "fe80::1%eth0", "fe80::1%en0.1"})
    @DisplayName("DATA-REPAIR-identity ID-12 an IPv4, IPv6, v4-mapped or zoned address is stored as it is")
    void dataRepairIdentityId12Accepts(String address) {
        assertThat(JdbcAccountRepository.storableAddress(address)).isEqualTo(address);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "[2001:db8::1]", "203.0.113.9:8080", "unknown", "cafe", ":::", "1.2.3", "fe80::1%", "fe80::1%a b",
            "2001:db8::1, 203.0.113.9"})
    @DisplayName("DATA-REPAIR-identity ID-12 anything that is not an address is left empty")
    void dataRepairIdentityId12Rejects(String address) {
        assertThat(JdbcAccountRepository.storableAddress(address)).isNull();
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-12 an address over 64 characters is left empty")
    void dataRepairIdentityId12RejectsTooLong() {
        assertThat(JdbcAccountRepository.storableAddress("1".repeat(65))).isNull();
        assertThat(JdbcAccountRepository.storableAddress("a:".repeat(33) + "a")).isNull();
    }
}
