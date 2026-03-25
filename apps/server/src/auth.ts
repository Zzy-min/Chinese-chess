import type { FastifyReply, FastifyRequest } from 'fastify';
import { z } from 'zod';

import type { AuthUser } from '@qiju/core';

import type { AppRepository } from './repository';

export const SESSION_COOKIE_NAME = 'qiju_session';

export const registerSchema = z.object({
  email: z.string().trim().email(),
  displayName: z.string().trim().min(2).max(24),
  password: z.string().min(8).max(128)
});

export const loginSchema = z.object({
  email: z.string().trim().email(),
  password: z.string().min(8).max(128)
});

function cookieOptions() {
  const secure = process.env.QIJU_COOKIE_SECURE === 'true';
  return {
    path: '/',
    httpOnly: true,
    sameSite: secure ? 'none' : 'lax',
    secure,
    signed: true,
    maxAge: 60 * 60 * 24 * 30
  } as const;
}

export function setSessionCookie(reply: FastifyReply, sessionId: string) {
  reply.setCookie(SESSION_COOKIE_NAME, sessionId, cookieOptions());
}

export function clearSessionCookie(reply: FastifyReply) {
  reply.clearCookie(SESSION_COOKIE_NAME, {
    path: '/'
  });
}

export function getSignedSessionId(request: FastifyRequest) {
  const value = request.cookies[SESSION_COOKIE_NAME];
  if (!value) {
    return undefined;
  }
  const unsigned = request.unsignCookie(value);
  return unsigned.valid ? unsigned.value : undefined;
}

export function getCurrentUser(request: FastifyRequest, repository: AppRepository): AuthUser | undefined {
  const sessionId = getSignedSessionId(request);
  if (!sessionId) {
    return undefined;
  }
  return repository.getUserForSession(sessionId);
}
