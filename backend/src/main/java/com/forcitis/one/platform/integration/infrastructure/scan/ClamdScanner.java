package com.forcitis.one.platform.integration.infrastructure.scan;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.MalwareScanner;
import com.forcitis.one.platform.integration.api.ScanVerdict;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Talks clamd's z-protocol: zVERSION for the definitions age, then zSCAN with the file's path. Sockets are non-blocking with a deadline,
 * since a Unix domain channel has no read timeout of its own.
 */
final class ClamdScanner implements MalwareScanner {
    private static final int MAX_REPLY_BYTES = 4096;

    private final MalwareScanProperties properties;
    private final Clock clock;
    private final ZoneId clamdZone;

    ClamdScanner(MalwareScanProperties properties, Clock clock, ZoneId clamdZone) {
        this.properties = properties;
        this.clock = clock;
        this.clamdZone = clamdZone;
    }

    @Override
    public ScanVerdict scan(Path stagedFile) {
        String path = requireStaged(stagedFile);
        Instant deadline = clock.instant().plus(properties.totalTimeout());
        Unreachable last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts() && clock.instant().isBefore(deadline); attempt++) {
            try {
                ClamdReply.Definitions definitions = ClamdReply.version(exchange("zVERSION", deadline), clamdZone);
                definitions.requireFresh(clock, properties.definitionsMaxAge());
                return ClamdReply.scan(path, exchange("zSCAN " + path, deadline), definitions);
            } catch (Unreachable e) {
                last = e;
            }
        }
        throw new ExternalSystemUnavailableException("clamd did not answer within " + properties.maxAttempts() + " attempts and "
                + properties.totalTimeout(), last);
    }

    Duration definitionsAge() {
        try {
            ClamdReply.Definitions definitions = ClamdReply.version(exchange("zVERSION", clock.instant().plus(properties.scanTimeout())), clamdZone);
            return Duration.between(definitions.builtAt(), clock.instant());
        } catch (Unreachable e) {
            throw new ExternalSystemUnavailableException("clamd is unreachable", e);
        }
    }

    private String requireStaged(Path stagedFile) {
        Path file = stagedFile.toAbsolutePath().normalize();
        String text = file.toString();
        if (!file.startsWith(properties.stagingRoot()) || file.equals(properties.stagingRoot()) || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0 || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("only a file under the scan staging root can be scanned");
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("the staged file is missing or not a regular file");
        }
        return text;
    }

    private String exchange(String command, Instant deadline) throws Unreachable {
        Instant until = min(deadline, clock.instant().plus(properties.scanTimeout()));
        try (SocketChannel channel = open(); Selector selector = Selector.open()) {
            channel.configureBlocking(false);
            if (!channel.connect(address())) {
                channel.register(selector, SelectionKey.OP_CONNECT);
                await(selector, min(until, clock.instant().plus(properties.connectTimeout())));
                channel.finishConnect();
            }
            ByteBuffer out = ByteBuffer.wrap((command + "\0").getBytes(StandardCharsets.UTF_8));
            channel.register(selector, SelectionKey.OP_WRITE);
            while (out.hasRemaining()) {
                await(selector, until);
                channel.write(out);
            }
            channel.register(selector, SelectionKey.OP_READ);
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            ByteBuffer in = ByteBuffer.allocate(512);
            while (true) {
                await(selector, until);
                in.clear();
                int read = channel.read(in);
                if (read < 0) {
                    break;
                }
                for (int i = 0; i < read; i++) {
                    byte b = in.get(i);
                    if (b == 0) {
                        return reply.toString(StandardCharsets.UTF_8);
                    }
                    reply.write(b);
                }
                if (reply.size() > MAX_REPLY_BYTES) {
                    throw new Unreachable("clamd sent an over-long reply", null);
                }
            }
            if (reply.size() == 0) {
                throw new Unreachable("clamd closed the connection without a reply", null);
            }
            return reply.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new Unreachable("clamd is unreachable", e);
        }
    }

    private SocketChannel open() throws IOException {
        return properties.socketPath() != null ? SocketChannel.open(StandardProtocolFamily.UNIX) : SocketChannel.open();
    }

    private SocketAddress address() {
        return properties.socketPath() != null ? UnixDomainSocketAddress.of(properties.socketPath())
                : new InetSocketAddress(properties.host(), properties.port());
    }

    private void await(Selector selector, Instant until) throws IOException, Unreachable {
        long millis = Duration.between(clock.instant(), until).toMillis();
        if (millis <= 0 || selector.select(millis) == 0) {
            throw new Unreachable("clamd did not answer in time", null);
        }
        selector.selectedKeys().clear();
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** Connection failures and timeouts: the only failures that are retried. */
    static final class Unreachable extends Exception {
        Unreachable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
