package com.xiangqi.web;

import com.xiangqi.online.server.OnlineStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicSiteServerTest {

    @Test
    void redirectsRootToOnlineHomeAndKeepsLegacyHomepageOnDedicatedPath() throws Exception {
        OnlineStore store = newStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> root = client.send(request(port, "/"), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> legacy = client.send(request(port, "/home-ai"), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> online = client.send(request(port, "/online"), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> onlineBootstrap = client.send(request(port, "/online/api/site/bootstrap"), HttpResponse.BodyHandlers.ofString());

            assertEquals(302, root.statusCode());
            assertEquals("/online#/home", root.headers().firstValue("Location").orElse(""));

            assertEquals(200, legacy.statusCode());
            assertTrue(legacy.body().contains("/assets/ui/app.js"));

            assertEquals(200, online.statusCode());
            assertTrue(online.body().contains("/online/assets/site/app.js"));

            assertEquals(200, onlineBootstrap.statusCode());
            assertTrue(onlineBootstrap.body().contains("\"siteName\""));
        } finally {
            server.stop();
        }
    }

    @Test
    void initializesSchemaForPublicSiteStoreAutomatically() throws Exception {
        OnlineStore store = newUninitializedStore();
        PublicSiteServer server = new PublicSiteServer(store);
        int port = findFreePort();
        try {
            server.start("127.0.0.1", port);

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> bootstrap = client.send(request(port, "/online/api/site/bootstrap"), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, bootstrap.statusCode());
            assertTrue(bootstrap.body().contains("\"totalUsers\""));
        } finally {
            server.stop();
        }
    }

    private HttpRequest request(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private OnlineStore newStore() throws Exception {
        OnlineStore store = newUninitializedStore();
        store.initSchema();
        return store;
    }

    private OnlineStore newUninitializedStore() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return new OnlineStore(dataSource);
    }
}
