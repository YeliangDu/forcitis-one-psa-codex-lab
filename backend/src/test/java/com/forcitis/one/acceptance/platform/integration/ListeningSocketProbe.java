package com.forcitis.one.acceptance.platform.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Installed in the command's JVM as the selector provider (-Djava.nio.channels.spi.SelectorProvider, class on -Xbootclasspath/a, sun.nio.ch
 * exported): every server socket channel the process opens, which is how Tomcat (application and management port) and Netty listen, writes
 * {@link #MARKER} to standard error. Everything else goes to the platform provider unchanged. {@link #main} opens one and listens, the control
 * that the probe sees a listener at all.
 */
public final class ListeningSocketProbe extends SelectorProvider {

    static final String MARKER = "LISTENING-SOCKET-PROBE server socket channel opened";

    private final SelectorProvider platform;

    public ListeningSocketProbe() throws ReflectiveOperationException {
        platform = (SelectorProvider) Class.forName("sun.nio.ch.DefaultSelectorProvider").getMethod("get").invoke(null);
    }

    private static void seen() {
        String caller = Arrays.stream(Thread.currentThread().getStackTrace()).skip(2).limit(12).map(StackTraceElement::toString)
                .collect(Collectors.joining(" <- "));
        System.err.println(MARKER + " by " + caller);
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() throws IOException {
        seen();
        return platform.openServerSocketChannel();
    }

    @Override
    public ServerSocketChannel openServerSocketChannel(ProtocolFamily family) throws IOException {
        seen();
        return platform.openServerSocketChannel(family);
    }

    @Override
    public DatagramChannel openDatagramChannel() throws IOException {
        return platform.openDatagramChannel();
    }

    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) throws IOException {
        return platform.openDatagramChannel(family);
    }

    @Override
    public Pipe openPipe() throws IOException {
        return platform.openPipe();
    }

    @Override
    public AbstractSelector openSelector() throws IOException {
        return platform.openSelector();
    }

    @Override
    public SocketChannel openSocketChannel() throws IOException {
        return platform.openSocketChannel();
    }

    @Override
    public SocketChannel openSocketChannel(ProtocolFamily family) throws IOException {
        return platform.openSocketChannel(family);
    }

    public static void main(String[] arguments) throws IOException {
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
        }
    }
}
