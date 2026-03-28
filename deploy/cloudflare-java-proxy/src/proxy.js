const CACHEABLE_PREFIXES = [
  "/assets/ui/",
  "/assets/audio/",
  "/online/assets/site/",
];

export function normalizeOriginBaseUrl(originBaseUrl) {
  if (!originBaseUrl || !String(originBaseUrl).trim()) {
    throw new Error("ORIGIN_BASE_URL is required");
  }

  return new URL(String(originBaseUrl).trim().replace(/\/+$/, "") + "/");
}

export function buildOriginUrl(requestUrl, originBaseUrl) {
  const publicUrl = new URL(requestUrl);
  const originUrl = normalizeOriginBaseUrl(originBaseUrl);
  return new URL(publicUrl.pathname + publicUrl.search, originUrl);
}

export function shouldCacheAssetPath(pathname) {
  return CACHEABLE_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

export function isOriginLoop(requestUrl, originBaseUrl) {
  const publicUrl = new URL(requestUrl);
  const originUrl = normalizeOriginBaseUrl(originBaseUrl);
  return publicUrl.origin === originUrl.origin;
}

export function isWebSocketRequest(request) {
  return request.headers.get("upgrade")?.toLowerCase() === "websocket";
}

export function buildProxyRequest(request, originBaseUrl) {
  const targetUrl = buildOriginUrl(request.url, originBaseUrl);
  const headers = new Headers(request.headers);
  const publicUrl = new URL(request.url);

  headers.delete("host");
  headers.set("x-forwarded-host", publicUrl.host);
  headers.set("x-forwarded-proto", publicUrl.protocol.replace(":", ""));
  headers.set("x-forwarded-for", request.headers.get("cf-connecting-ip") || "");

  const init = {
    method: request.method,
    headers,
    redirect: "manual",
  };

  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = request.body;
  }

  return new Request(targetUrl, init);
}
