package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** C1a-04: "internal network only" - CIDR membership without any DNS lookup, fail-closed for everything unparsable. */
class NetworkAllowListTest {

    static final NetworkAllowList INTERNAL = NetworkAllowList.of(List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.1/32", "::1/128", "fd00::/8"));

    @Test
    @DisplayName("addresses inside the ranges are internal, outside are not")
    void membership() {
        assertThat(INTERNAL.allows("10.1.2.3")).isTrue();
        assertThat(INTERNAL.allows("172.31.255.255")).isTrue();
        assertThat(INTERNAL.allows("172.32.0.1")).isFalse();
        assertThat(INTERNAL.allows("192.168.100.7")).isTrue();
        assertThat(INTERNAL.allows("192.169.0.1")).isFalse();
        assertThat(INTERNAL.allows("127.0.0.1")).isTrue();
        assertThat(INTERNAL.allows("127.0.0.2")).isFalse();
        assertThat(INTERNAL.allows("203.0.113.9")).isFalse();
        assertThat(INTERNAL.allows("::1")).isTrue();
        assertThat(INTERNAL.allows("fd12:3456::1")).isTrue();
        assertThat(INTERNAL.allows("2001:db8::1")).isFalse();
    }

    @Test
    @DisplayName("an IPv4-mapped IPv6 address is judged as the IPv4 address it carries")
    void mapped() {
        assertThat(INTERNAL.allows("::ffff:10.1.2.3")).isTrue();
        assertThat(INTERNAL.allows("::ffff:203.0.113.9")).isFalse();
    }

    @Test
    @DisplayName("anything that is not an IP literal is outside the list: names, blanks, null, garbage")
    void failClosed() {
        assertThat(INTERNAL.allows(null)).isFalse();
        assertThat(INTERNAL.allows("")).isFalse();
        assertThat(INTERNAL.allows("localhost")).isFalse();
        assertThat(INTERNAL.allows("10.0.0.1.example.com")).isFalse();
        assertThat(INTERNAL.allows("999.1.1.1")).isFalse();
        assertThat(NetworkAllowList.of(List.of()).allows("127.0.0.1")).as("an empty list admits nobody").isFalse();
    }

    @Test
    @DisplayName("a bad configuration entry fails at start-up, not silently")
    void badConfiguration() {
        assertThatThrownBy(() -> NetworkAllowList.of(List.of("10.0.0.0/33"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NetworkAllowList.of(List.of("intranet.example.com"))).isInstanceOf(IllegalArgumentException.class);
    }
}
