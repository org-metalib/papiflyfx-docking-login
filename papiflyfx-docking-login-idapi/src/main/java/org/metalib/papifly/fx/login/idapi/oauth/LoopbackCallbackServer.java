package org.metalib.papifly.fx.login.idapi.oauth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LoopbackCallbackServer implements AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final String RESPONSE_HTML = """
        <html><body><h2>Authentication complete</h2><p>You can close this tab.</p></body></html>""";

    private final ServerSocket serverSocket;
    private final CompletableFuture<Map<String, String>> callbackFuture;
    private volatile boolean closed;

    private LoopbackCallbackServer(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        this.callbackFuture = new CompletableFuture<>();
    }

    public static LoopbackCallbackServer start() throws IOException {
        return start(DEFAULT_TIMEOUT);
    }

    public static LoopbackCallbackServer start(Duration timeout) throws IOException {
        ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        LoopbackCallbackServer server = new LoopbackCallbackServer(socket);
        Thread listener = new Thread(() -> server.acceptConnection(), "oauth-callback-listener");
        listener.setDaemon(true);
        listener.start();
        server.callbackFuture.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return server;
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public String getRedirectUri() {
        return "http://127.0.0.1:" + getPort() + "/callback";
    }

    public CompletableFuture<Map<String, String>> getCallbackParams() {
        return callbackFuture;
    }

    public Optional<String> getParam(Map<String, String> params, String name) {
        return Optional.ofNullable(params.get(name));
    }

    @Override
    public void close() {
        closed = true;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    private void acceptConnection() {
        try (Socket client = serverSocket.accept()) {
            serverSocket.setSoTimeout(1000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                callbackFuture.completeExceptionally(new IOException("Empty request"));
                return;
            }

            Map<String, String> params = parseQueryParams(requestLine);
            sendResponse(client);
            callbackFuture.complete(params);
        } catch (Exception e) {
            if (!closed) {
                callbackFuture.completeExceptionally(e);
            }
        } finally {
            close();
        }
    }

    private Map<String, String> parseQueryParams(String requestLine) {
        Map<String, String> params = new LinkedHashMap<>();
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return params;
        }
        String path = parts[1];
        int queryStart = path.indexOf('?');
        if (queryStart < 0) {
            return params;
        }
        String query = path.substring(queryStart + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private void sendResponse(Socket client) throws IOException {
        byte[] body = RESPONSE_HTML.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
