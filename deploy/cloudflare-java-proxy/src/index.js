import {
  buildProxyRequest,
  isOriginLoop,
  isWebSocketRequest,
  shouldCacheAssetPath,
} from "./proxy.js";

function cacheOptionsFor(requestUrl, env) {
  const pathname = new URL(requestUrl).pathname;
  if (!shouldCacheAssetPath(pathname)) {
    return undefined;
  }

  const ttl = Number(env.STATIC_CACHE_TTL_SEC || 86400);
  return {
    cacheEverything: true,
    cacheTtl: Number.isFinite(ttl) && ttl > 0 ? ttl : 86400,
  };
}

export default {
  async fetch(request, env) {
    if (!env.ORIGIN_BASE_URL) {
      return new Response("Missing ORIGIN_BASE_URL", { status: 500 });
    }

    if (isOriginLoop(request.url, env.ORIGIN_BASE_URL)) {
      return new Response("ORIGIN_BASE_URL points back to the public Worker hostname", {
        status: 500,
      });
    }

    const upstreamRequest = buildProxyRequest(request, env.ORIGIN_BASE_URL);

    if (isWebSocketRequest(request)) {
      return fetch(upstreamRequest);
    }

    const cf = cacheOptionsFor(request.url, env);
    return cf ? fetch(upstreamRequest, { cf }) : fetch(upstreamRequest);
  },
};
