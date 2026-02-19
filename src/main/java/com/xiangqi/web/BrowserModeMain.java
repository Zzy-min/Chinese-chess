package com.xiangqi.web;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

public final class BrowserModeMain {
    public static final int PORT = 18388;

    private BrowserModeMain() {
    }

    public static void main(String[] args) throws Exception {
        URI uri = WebXiangqiServer.getInstance().start(PORT);
        System.out.println("Browser mode started at: " + uri);
        new CountDownLatch(1).await();
    }
}
