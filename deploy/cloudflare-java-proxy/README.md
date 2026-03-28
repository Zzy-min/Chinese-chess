# Cloudflare Java Proxy

This Worker is the Cloudflare front door for the current Java production site.

It does not implement application logic. It only:

- proxies `/`, `/api/*`, `/online`, `/online/api/*`, and `/online/ws`
- caches immutable frontend assets
- forwards all interactive traffic to the Java origin configured by `ORIGIN_BASE_URL`

## Local usage

1. Start the Java origin locally on `http://127.0.0.1:18388`
2. Set `ORIGIN_BASE_URL=http://127.0.0.1:18388`
3. Run `npm run dev`

## Deploy

- Root directory in Cloudflare must be `deploy/cloudflare-java-proxy`
- Production branch must stay `main`
- Non-production branch builds should remain disabled unless explicitly scoped
