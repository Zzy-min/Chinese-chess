package com.xiangqi.web;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

public final class BrowserModeMain {
    public static final int PORT = 18388;
    public static final String TRUSTED_HOST = "127.0.0.1";

    private BrowserModeMain() {
    }

    public static void main(String[] args) throws Exception {
        URI uri = WebXiangqiServer.getInstance().start(PORT);
        URI trustedUri = URI.create("http://" + TRUSTED_HOST + ":" + PORT + "/");
        System.out.println("Browser mode started at: " + trustedUri + " (bound " + uri + ")");
        new CountDownLatch(1).await();
    }
}
