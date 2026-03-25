import bcrypt from 'bcryptjs';
import Fastify from 'fastify';
import cookie from '@fastify/cookie';
import websocket from '@fastify/websocket';

import type { ArchiveSourceType, AuthUser, CapabilityPayload, GameType } from '@qiju/core';
import { gameCatalog } from '@qiju/core';

import { clearSessionCookie, getCurrentUser, getSignedSessionId, loginSchema, registerSchema, setSessionCookie } from './auth';
import { createDatabase } from './db';
import { AppRepository } from './repository';
import { createInMemoryStore } from './store';

type RoomSocketMessage = {
  type: 'subscribe';
  roomId: string;
};

type RoomSocketEvent = {
  type: 'room.sync';
  room: ReturnType<ReturnType<typeof createInMemoryStore>['getRoom']>;
};

class WsHub {
  private readonly byRoom = new Map<string, Set<any>>();
  private readonly bySocket = new WeakMap<any, string>();

  subscribe(socket: any, roomId: string) {
    this.unsubscribe(socket);
    const set = this.byRoom.get(roomId) ?? new Set<any>();
    set.add(socket);
    this.byRoom.set(roomId, set);
    this.bySocket.set(socket, roomId);
  }

  unsubscribe(socket: any) {
    const roomId = this.bySocket.get(socket);
    if (!roomId) {
      return;
    }
    const set = this.byRoom.get(roomId);
    if (!set) {
      return;
    }
    set.delete(socket);
    if (!set.size) {
      this.byRoom.delete(roomId);
    }
    this.bySocket.delete(socket);
  }

  broadcastRoom(roomId: string, payload: RoomSocketEvent) {
    const set = this.byRoom.get(roomId);
    if (!set) {
      return;
    }
    const text = JSON.stringify(payload);
    for (const socket of set) {
      if (socket.readyState === 1) {
        socket.send(text);
      }
    }
  }
}

function wsBasePayload(type: 'room.sync', room: ReturnType<ReturnType<typeof createInMemoryStore>['getRoom']>) {
  return { type, room } satisfies RoomSocketEvent;
}

function isGameType(value: unknown): value is GameType {
  return value === 'XIANGQI' || value === 'GOMOKU' || value === 'CHESS' || value === 'GO';
}

function isArchiveSource(value: unknown): value is ArchiveSourceType {
  return value === 'ONLINE' || value === 'PRACTICE';
}

export function buildApp(options?: { dbPath?: string; cookieSecret?: string }) {
  const app = Fastify({ logger: false });
  const db = createDatabase(options?.dbPath);
  const repository = new AppRepository(db);
  const store = createInMemoryStore(repository);
  const wsHub = new WsHub();

  app.register(cookie, {
    secret: options?.cookieSecret ?? process.env.QIJU_COOKIE_SECRET ?? 'qiju-local-dev-secret'
  });
  app.register(websocket);

  app.addHook('onClose', async () => {
    db.close();
  });

  app.addHook('onRequest', async (request, reply) => {
    const origin = request.headers.origin ?? 'http://127.0.0.1:3020';
    reply.header('Access-Control-Allow-Origin', origin);
    reply.header('Vary', 'Origin');
    reply.header('Access-Control-Allow-Credentials', 'true');
    reply.header('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
    reply.header('Access-Control-Allow-Headers', 'Content-Type');
    if (request.method === 'OPTIONS') {
      reply.code(204).send();
    }
  });

  const requireUser = (request: Parameters<typeof getCurrentUser>[0], reply: any): AuthUser | undefined => {
    const user = getCurrentUser(request, repository);
    if (!user) {
      reply.code(401);
      return undefined;
    }
    return user;
  };

  app.get('/health', async () => ({ ok: true }));

  app.register(async (wsApp) => {
    wsApp.get('/ws', { websocket: true }, (socket) => {
      socket.on('message', (raw: Buffer | ArrayBuffer | string) => {
        try {
          const text = typeof raw === 'string' ? raw : raw.toString();
          const payload = JSON.parse(text) as RoomSocketMessage;
          if (payload.type === 'subscribe' && payload.roomId) {
            wsHub.subscribe(socket, payload.roomId);
            const room = store.getRoom(payload.roomId);
            if (room) {
              socket.send(JSON.stringify(wsBasePayload('room.sync', room)));
            }
          }
        } catch {
          socket.send(JSON.stringify({ type: 'error', message: 'bad message' }));
        }
      });

      socket.on('close', () => wsHub.unsubscribe(socket));
      socket.on('error', () => wsHub.unsubscribe(socket));
    });
  });

  app.get('/api/catalog', async () => ({ games: gameCatalog }));
  app.get('/api/site/bootstrap', async () => store.bootstrap());
  app.get('/api/site/capabilities', async () => ({
    onlineGames: ['XIANGQI', 'GOMOKU', 'CHESS', 'GO'],
    onlineStatus: 'WebSocket room sync',
    practiceGames: ['XIANGQI', 'GOMOKU', 'GO', 'CHESS'],
    learnGames: ['XIANGQI', 'GOMOKU', 'GO', 'CHESS'],
    authStatus: 'First-party cookie auth',
    persistenceStatus: 'SQLite durable store (PostgreSQL-ready seam)',
    reviewStatus: 'Archived playback and lightweight review tags'
  } satisfies CapabilityPayload));
  app.get('/api/lobby', async () => ({ rooms: store.listRooms() }));

  app.post('/api/auth/register', async (request, reply) => {
    const parsed = registerSchema.safeParse(request.body);
    if (!parsed.success) {
      reply.code(400);
      return { message: 'invalid registration payload', issues: parsed.error.flatten() };
    }
    if (repository.getUserByEmail(parsed.data.email)) {
      reply.code(409);
      return { message: 'email already registered' };
    }
    const passwordHash = await bcrypt.hash(parsed.data.password, 10);
    const user = repository.createUser({
      email: parsed.data.email,
      displayName: parsed.data.displayName,
      passwordHash
    });
    const session = repository.createSession(user.userId);
    setSessionCookie(reply, session.sessionId);
    reply.code(201);
    return { user };
  });

  app.post('/api/auth/login', async (request, reply) => {
    const parsed = loginSchema.safeParse(request.body);
    if (!parsed.success) {
      reply.code(400);
      return { message: 'invalid login payload', issues: parsed.error.flatten() };
    }
    const user = repository.getUserByEmail(parsed.data.email);
    if (!user || !(await bcrypt.compare(parsed.data.password, user.passwordHash))) {
      reply.code(401);
      return { message: 'invalid credentials' };
    }
    const session = repository.createSession(user.userId);
    setSessionCookie(reply, session.sessionId);
    return { user: repository.getUserById(user.userId) };
  });

  app.post('/api/auth/logout', async (request, reply) => {
    const sessionId = getSignedSessionId(request);
    if (sessionId) {
      repository.deleteSession(sessionId);
    }
    clearSessionCookie(reply);
    return { ok: true };
  });

  app.get('/api/me', async (request) => ({ user: getCurrentUser(request, repository) ?? null }));

  app.get('/api/me/profile', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    return store.getProfile(user.userId) ?? { message: 'profile not found' };
  });

  app.get('/api/me/history', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    const query = request.query as { gameType?: string; sourceType?: string };
    const gameType = isGameType(query.gameType) ? query.gameType : undefined;
    const sourceType = isArchiveSource(query.sourceType) ? query.sourceType : undefined;
    return { archives: store.listHistory(user.userId, { gameType, sourceType }) };
  });

  app.get('/api/leaderboard', async (request) => {
    const query = request.query as { gameType?: string };
    const gameType = isGameType(query.gameType) ? query.gameType : 'XIANGQI';
    return { entries: store.listLeaderboard(gameType), gameType };
  });

  app.get('/api/reviews/:archiveId', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    const review = store.getReview(user.userId, (request.params as { archiveId: string }).archiveId);
    if (!review) {
      reply.code(404);
      return { message: 'review not found' };
    }
    return review;
  });

  app.post('/api/rooms', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    const payload = request.body as {
      gameType: 'XIANGQI' | 'GOMOKU' | 'GO' | 'CHESS';
      timeControl: string;
      visibility: 'PUBLIC' | 'PRIVATE';
    };
    const room = store.createRoom(payload, user);
    wsHub.broadcastRoom(room.roomId, wsBasePayload('room.sync', room));
    reply.code(201);
    return room;
  });

  app.post('/api/rooms/join-by-code', async (request, reply) => {
    const payload = request.body as { roomCode: string };
    const room = store.getRoomByCode(payload.roomCode);
    if (!room) {
      reply.code(404);
      return { message: 'room not found' };
    }
    return room;
  });

  app.get('/api/rooms/:roomId', async (request, reply) => {
    const room = store.getRoom((request.params as { roomId: string }).roomId);
    if (!room) {
      reply.code(404);
      return { message: 'room not found' };
    }
    return room;
  });

  app.post('/api/rooms/:roomId/join', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    try {
      const payload = request.body as { seat: 'host' | 'guest' };
      const room = store.joinRoom((request.params as { roomId: string }).roomId, payload.seat, user);
      if (!room) {
        reply.code(404);
        return { message: 'room not found' };
      }
      wsHub.broadcastRoom(room.roomId, wsBasePayload('room.sync', room));
      return room;
    } catch (error) {
      reply.code(400);
      return { message: error instanceof Error ? error.message : 'failed to join room' };
    }
  });

  app.post('/api/rooms/:roomId/ready', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    try {
      const payload = request.body as { seat: 'host' | 'guest'; ready: boolean };
      const room = store.setReady((request.params as { roomId: string }).roomId, payload.seat, payload.ready, user);
      if (!room) {
        reply.code(404);
        return { message: 'room not found' };
      }
      wsHub.broadcastRoom(room.roomId, wsBasePayload('room.sync', room));
      return room;
    } catch (error) {
      reply.code(400);
      return { message: error instanceof Error ? error.message : 'failed to update ready state' };
    }
  });

  app.post('/api/rooms/:roomId/start', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    try {
      const room = store.startRoom((request.params as { roomId: string }).roomId, user);
      if (!room) {
        reply.code(404);
        return { message: 'room not found' };
      }
      wsHub.broadcastRoom(room.roomId, wsBasePayload('room.sync', room));
      return room;
    } catch (error) {
      reply.code(400);
      return { message: error instanceof Error ? error.message : 'failed to start room' };
    }
  });

  app.post('/api/rooms/:roomId/move', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    try {
      const room = store.applyRoomMove((request.params as { roomId: string }).roomId, (request.body as Record<string, unknown>) ?? {}, user);
      if (!room) {
        reply.code(404);
        return { message: 'room not found' };
      }
      wsHub.broadcastRoom(room.roomId, wsBasePayload('room.sync', room));
      return room;
    } catch (error) {
      reply.code(400);
      return { message: error instanceof Error ? error.message : 'illegal move' };
    }
  });

  app.post('/api/rooms/:roomId/resign', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    try {
      const room = store.resignRoom((request.params as { roomId: string }).roomId, user);
      if (!room) {
        reply.code(404);
        return { message: 'room not found' };
      }
      wsHub.broadcastRoom(room.roomId, wsBasePayload('room.sync', room));
      return room;
    } catch (error) {
      reply.code(400);
      return { message: error instanceof Error ? error.message : 'failed to resign' };
    }
  });

  app.post('/api/practice-games', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    const payload = request.body as {
      gameType: 'XIANGQI' | 'GOMOKU' | 'GO' | 'CHESS';
      difficulty: 'EASY' | 'MEDIUM' | 'HARD';
      humanFirst: boolean;
    };
    const session = store.createPracticeSession(payload, user);
    reply.code(201);
    return session;
  });

  app.get('/api/practice-games/:practiceGameId', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    const session = store.getPracticeSession((request.params as { practiceGameId: string }).practiceGameId, user.userId);
    if (!session) {
      reply.code(404);
      return { message: 'practice game not found' };
    }
    return session;
  });

  app.post('/api/practice-games/:practiceGameId/move', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    try {
      const payload = (request.body as Record<string, unknown>) ?? {};
      const session = store.applyPracticeMove((request.params as { practiceGameId: string }).practiceGameId, payload, user);
      if (!session) {
        reply.code(404);
        return { message: 'practice game not found' };
      }
      return session;
    } catch (error) {
      reply.code(400);
      return { message: error instanceof Error ? error.message : 'illegal practice move' };
    }
  });

  app.post('/api/practice-games/:practiceGameId/finish', async (request, reply) => {
    const user = requireUser(request, reply);
    if (!user) {
      return { message: 'unauthorized' };
    }
    try {
      const session = store.finishPracticeSession((request.params as { practiceGameId: string }).practiceGameId, user);
      if (!session) {
        reply.code(404);
        return { message: 'practice game not found' };
      }
      return session;
    } catch (error) {
      reply.code(400);
      return { message: error instanceof Error ? error.message : 'failed to finish practice game' };
    }
  });

  return app;
}
