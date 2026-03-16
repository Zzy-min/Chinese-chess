package com.xiangqi.model.go;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurableGoEngineTest {

    @Test
    void shouldTreatReadyFalseHealthAsUnavailable() throws Exception {
        withHealthServer("{\"ok\":true,\"engine\":\"KataGo\",\"ready\":false}", new ThrowingRunnable() {
            @Override
            public void run() {
                ConfigurableGoEngine engine = new ConfigurableGoEngine();
                try {
                    assertFalse(engine.isAvailable());
                } finally {
                    engine.close();
                }
            }
        });
    }

    @Test
    void shouldStayCompatibleWhenReadyFieldIsMissing() throws Exception {
        withHealthServer("{\"ok\":true,\"engine\":\"KataGo\"}", new ThrowingRunnable() {
            @Override
            public void run() {
                ConfigurableGoEngine engine = new ConfigurableGoEngine();
                try {
                    assertTrue(engine.isAvailable());
                } finally {
                    engine.close();
                }
            }
        });
    }

    private void withHealthServer(String responseBody, ThrowingRunnable action) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", new JsonHandler(responseBody));
        server.start();
        String previousUrl = System.getProperty("xq.go.engine.url");
        String previousPreference = System.getProperty("xq.go.engine");
        System.setProperty("xq.go.engine.url", "http://127.0.0.1:" + server.getAddress().getPort());
        System.clearProperty("xq.go.engine");
        try {
            action.run();
        } finally {
            if (previousUrl == null) {
                System.clearProperty("xq.go.engine.url");
            } else {
                System.setProperty("xq.go.engine.url", previousUrl);
            }
            if (previousPreference == null) {
                System.clearProperty("xq.go.engine");
            } else {
                System.setProperty("xq.go.engine", previousPreference);
            }
            server.stop(0);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class JsonHandler implements HttpHandler {
        private final byte[] body;

        private JsonHandler(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }
}
