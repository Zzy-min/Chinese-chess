import test from "node:test";
import assert from "node:assert/strict";

import {
  buildOriginUrl,
  buildProxyRequest,
  isOriginLoop,
  isWebSocketRequest,
  shouldCacheAssetPath,
} from "../src/proxy.js";

test("buildOriginUrl preserves path and query against the Java origin", () => {
  const result = buildOriginUrl(
    "https://www.xiangqiarena.com/online/api/rooms/abc?tab=live",
    "http://47.80.60.26:18388"
  );

  assert.equal(result.toString(), "http://47.80.60.26:18388/online/api/rooms/abc?tab=live");
});

test("shouldCacheAssetPath caches only immutable frontend asset paths", () => {
  assert.equal(shouldCacheAssetPath("/assets/ui/app.js"), true);
  assert.equal(shouldCacheAssetPath("/assets/audio/move.wav"), true);
  assert.equal(shouldCacheAssetPath("/online/assets/site/app.css"), true);
  assert.equal(shouldCacheAssetPath("/"), false);
  assert.equal(shouldCacheAssetPath("/api/state"), false);
  assert.equal(shouldCacheAssetPath("/online/api/site/bootstrap"), false);
  assert.equal(shouldCacheAssetPath("/online/ws"), false);
});

test("isOriginLoop flags public hostnames that would proxy back into the worker", () => {
  assert.equal(
    isOriginLoop("https://www.xiangqiarena.com/online", "https://www.xiangqiarena.com"),
    true
  );
  assert.equal(
    isOriginLoop("https://chinese-chess.zzy19812007.workers.dev/", "https://chinese-chess.zzy19812007.workers.dev"),
    true
  );
  assert.equal(
    isOriginLoop("https://www.xiangqiarena.com/", "http://47.80.60.26:18388"),
    false
  );
});

test("isWebSocketRequest detects websocket upgrades", () => {
  const request = new Request("https://www.xiangqiarena.com/online/ws", {
    headers: {
      Upgrade: "websocket",
    },
  });

  assert.equal(isWebSocketRequest(request), true);
  assert.equal(isWebSocketRequest(new Request("https://www.xiangqiarena.com/")), false);
});

test("buildProxyRequest removes host and preserves forwarding headers", () => {
  const request = new Request("https://chinese-chess.zzy19812007.workers.dev/online", {
    headers: {
      Host: "chinese-chess.zzy19812007.workers.dev",
      Cookie: "sid=abc",
      "CF-Connecting-IP": "1.2.3.4",
    },
  });

  const proxied = buildProxyRequest(request, "https://www.xiangqiarena.com");

  assert.equal(proxied.url, "https://www.xiangqiarena.com/online");
  assert.equal(proxied.headers.get("host"), null);
  assert.equal(proxied.headers.get("cookie"), "sid=abc");
  assert.equal(proxied.headers.get("x-forwarded-host"), "chinese-chess.zzy19812007.workers.dev");
  assert.equal(proxied.headers.get("x-forwarded-for"), "1.2.3.4");
});
