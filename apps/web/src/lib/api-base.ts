import type { AuthUser } from '@qiju/core';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly payload?: unknown
  ) {
    super(message);
  }
}

export function getApiBase() {
  return process.env.NEXT_PUBLIC_QIJU_API_BASE || 'http://127.0.0.1:4310';
}

export function getWsBase(apiBase = getApiBase()) {
  if (apiBase.startsWith('/')) {
    if (typeof window === 'undefined') {
      return apiBase;
    }
    return `${window.location.origin.replace(/^http/i, 'ws')}${apiBase}`;
  }
  return apiBase.replace(/^http/i, 'ws');
}

async function readPayload(response: Response) {
  const maybeText = (response as Response & { text?: () => Promise<string> }).text;
  if (typeof maybeText === 'function') {
    const text = await maybeText.call(response);
    return text ? JSON.parse(text) : null;
  }
  const maybeJson = (response as Response & { json?: () => Promise<unknown> }).json;
  if (typeof maybeJson === 'function') {
    return maybeJson.call(response);
  }
  return null;
}

export async function fetchApiJson<T>(apiBase: string, path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers ?? {});
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${apiBase}${path}`, {
    credentials: 'include',
    ...init,
    headers
  });
  const payload = await readPayload(response);
  if (!response.ok) {
    throw new ApiError((payload as { message?: string } | null)?.message || `request failed: ${response.status}`, response.status, payload);
  }
  return payload as T;
}

export async function fetchSessionUser(apiBase = getApiBase()) {
  try {
    const payload = await fetchApiJson<{ user: AuthUser | null }>(apiBase, '/api/me', {
      method: 'GET',
      headers: {}
    });
    return payload.user;
  } catch {
    return null;
  }
}
