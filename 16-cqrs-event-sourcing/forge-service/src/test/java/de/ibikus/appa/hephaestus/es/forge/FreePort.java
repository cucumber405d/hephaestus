package de.ibikus.appa.hephaestus.es.forge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

/** A free local port, chosen once per test class, for the gRPC server a test talks to. */
public final class FreePort {

    private FreePort() {}

    public static int find() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
