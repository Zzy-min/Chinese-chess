package com.xiangqi.web;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

public final class PublicWebMain {
    private PublicWebMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = parsePort(System.getenv("PORT"), 18388);
        String bindHost = System.getenv("BIND_HOST");
        if (bindHost == null || bindHost.trim().isEmpty()) {
            bindHost = "0.0.0.0";
        }

        URI uri = WebXiangqiServer.getInstance().start(bindHost, port);
        System.out.println("Public web started at: " + uri + " (bind " + bindHost + ":" + port + ")");
        new CountDownLatch(1).await();
    }

    private static int parsePort(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
