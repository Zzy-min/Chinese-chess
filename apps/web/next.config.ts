import type { NextConfig } from 'next';

const backendHostPort = process.env.QIJU_BACKEND_HOSTPORT;
const backendDestination = backendHostPort ? `http://${backendHostPort}` : undefined;

const nextConfig: NextConfig = {
  transpilePackages: ['@qiju/core', '@qiju/engines-chess'],
  async rewrites() {
    if (!backendDestination) {
      return [];
    }
    return [
      {
        source: '/backend/:path*',
        destination: `${backendDestination}/:path*`
      }
    ];
  }
};

export default nextConfig;
