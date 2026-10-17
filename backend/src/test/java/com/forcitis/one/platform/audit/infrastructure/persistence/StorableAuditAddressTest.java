package com.forcitis.one.platform.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StorableAuditAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "203.0.113.9", "255.255.255.255", "2001:db8::1", "::1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "::ffff:203.0.113.9", "fe80::1%eth0"})
    @DisplayName("DATA-REPAIR-timesheet-audit TB-05 an IPv4, IPv6, v4-mapped or zoned address is recorded as it is")
    void recordsAnAddress(String address) {
        assertThat(JdbcAuditEntryRepository.storableAddress(address)).isEqualTo(address);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "999.1.1.1", "256.0.0.1", "1.2.3", "1.2.3.4.5", "abc:def:", "cafe", ":::", "1:2:3:4:5:6:7:8:9", "fe80::1%", "fe80::1%a b", "[2001:db8::1]",
            "203.0.113.9:8080", "unknown", ".::", ".1:2:3", "2001:db8::1, 203.0.113.9"})
    @DisplayName("DATA-REPAIR-timesheet-audit TB-05 anything that is not an address is left empty")
    void leavesANonAddressEmpty(String address) {
        assertThat(JdbcAuditEntryRepository.storableAddress(address)).isNull();
    }
}
