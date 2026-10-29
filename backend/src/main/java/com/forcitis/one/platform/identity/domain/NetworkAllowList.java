package com.forcitis.one.platform.identity.domain;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Only IP literals are parsed, never a host name, so a check can never trigger a DNS lookup. */
public final class NetworkAllowList {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]+");

    private record Range(BigInteger network, int prefixBits, int totalBits) {
    }

    private final List<Range> ranges;

    private NetworkAllowList(List<Range> ranges) {
        this.ranges = ranges;
    }

    public static NetworkAllowList of(List<String> cidrs) {
        return new NetworkAllowList(cidrs.stream().map(String::trim).filter(s -> !s.isEmpty()).map(NetworkAllowList::range).toList());
    }

    public boolean allows(@Nullable String address) {
        BigInteger value;
        int bits;
        byte @Nullable [] raw = literal(address);
        if (raw == null) {
            return false;
        }
        value = new BigInteger(1, raw);
        bits = raw.length * 8;
        for (Range range : ranges) {
            if (range.totalBits() == bits && matches(value, range, bits)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(BigInteger value, Range range, int bits) {
        int shift = bits - range.prefixBits();
        return value.shiftRight(shift).equals(range.network().shiftRight(shift));
    }

    private static Range range(String cidr) {
        String[] parts = cidr.split("/", 2);
        byte @Nullable [] raw = literal(parts[0]);
        if (raw == null) {
            throw new IllegalArgumentException("not an IP address: " + cidr);
        }
        int bits = raw.length * 8;
        int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bits;
        if (prefix < 0 || prefix > bits) {
            throw new IllegalArgumentException("bad prefix length: " + cidr);
        }
        return new Range(new BigInteger(1, raw), prefix, bits);
    }

    /** IPv4-mapped IPv6 addresses are returned as IPv4. */
    private static byte @Nullable [] literal(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String address = text.strip();
        if (!(IPV4.matcher(address).matches() || (address.contains(":") && IPV6.matcher(address).matches()))) {
            return null;
        }
        try {
            return InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
