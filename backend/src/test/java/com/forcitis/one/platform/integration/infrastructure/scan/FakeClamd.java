package com.forcitis.one.platform.integration.infrastructure.scan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/** Answers z-commands with replies chosen by the test; a null reply keeps the connection silent until the test ends. */
final class FakeClamd implements AutoCloseable {
    final List<String> commands = new CopyOnWriteArrayList<>();
    private final ServerSocketChannel server;
    private final Function<String, String> replies;
    private final CountDownLatch closed = new CountDownLatch(1);
    private final Thread thread;

    private FakeClamd(ServerSocketChannel server, Function<String, String> replies) {
        this.server = server;
        this.replies = replies;
        this.thread = Thread.ofVirtual().start(this::serve);
    }

    static FakeClamd tcp(Function<String, String> replies) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        return new FakeClamd(server, replies);
    }

    static FakeClamd unix(Path socket, Function<String, String> replies) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
        return new FakeClamd(server, replies);
    }

    int port() throws IOException {
        return ((InetSocketAddress) server.getLocalAddress()).getPort();
    }

    private void serve() {
        while (server.isOpen()) {
            try {
                SocketChannel client = server.accept();
                Thread.ofVirtual().start(() -> answer(client));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void answer(SocketChannel connection) {
        try (SocketChannel client = connection) {
            ByteArrayOutputStream command = new ByteArrayOutputStream();
            ByteBuffer in = ByteBuffer.allocate(1);
            while (client.read(in) > 0 && in.get(0) != 0) {
                command.write(in.get(0));
                in.clear();
            }
            String text = command.toString(StandardCharsets.UTF_8);
            commands.add(text);
            String reply = replies.apply(text);
            if (reply == null) {
                closed.await();
            } else {
                client.write(ByteBuffer.wrap((reply + "\0").getBytes(StandardCharsets.UTF_8)));
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        closed.countDown();
        server.close();
        thread.interrupt();
    }
}
