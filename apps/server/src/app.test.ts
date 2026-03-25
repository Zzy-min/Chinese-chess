import { afterEach, describe, expect, it } from 'vitest';

import { buildApp } from './app';

function createTestApp() {
  return buildApp({ dbPath: ':memory:', cookieSecret: 'test-secret' });
}

function extractCookie(response: Awaited<ReturnType<ReturnType<typeof buildApp>['inject']>>) {
  const header = response.headers['set-cookie'];
  const raw = Array.isArray(header) ? header[0] : header;
  return String(raw).split(';')[0];
}

async function registerUser(app: ReturnType<typeof buildApp>, email: string, displayName: string) {
  const response = await app.inject({
    method: 'POST',
    url: '/api/auth/register',
    payload: {
      email,
      displayName,
      password: 'Password123'
    }
  });
  return { response, cookie: extractCookie(response) };
}

describe('buildApp', () => {
  const apps: ReturnType<typeof buildApp>[] = [];

  afterEach(async () => {
    while (apps.length) {
      const app = apps.pop();
      if (app) {
        await app.close();
      }
    }
  });

  it('returns a health response', async () => {
    const app = createTestApp();
    apps.push(app);
    const response = await app.inject({ method: 'GET', url: '/health' });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual({ ok: true });
  });

  it('registers a user, sets a session cookie, and exposes /api/me', async () => {
    const app = createTestApp();
    apps.push(app);
    const registered = await registerUser(app, 'alice@example.com', 'Alice');
    expect(registered.response.statusCode).toBe(201);
    expect(registered.response.json().user.email).toBe('alice@example.com');

    const me = await app.inject({
      method: 'GET',
      url: '/api/me',
      headers: { cookie: registered.cookie }
    });
    expect(me.statusCode).toBe(200);
    expect(me.json().user.displayName).toBe('Alice');
  });

  it('rejects unauthenticated room creation', async () => {
    const app = createTestApp();
    apps.push(app);
    const response = await app.inject({ method: 'POST', url: '/api/rooms', payload: { gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC' } });
    expect(response.statusCode).toBe(401);
  });

  it('creates a room and exposes it through the lobby for an authenticated user', async () => {
    const app = createTestApp();
    apps.push(app);
    const host = await registerUser(app, 'host@example.com', 'Host');
    const created = await app.inject({
      method: 'POST',
      url: '/api/rooms',
      headers: { cookie: host.cookie },
      payload: { gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC' }
    });
    expect(created.statusCode).toBe(201);
    expect(created.json().host.label).toBe('Host');
    const lobby = await app.inject({ method: 'GET', url: '/api/lobby' });
    expect(lobby.statusCode).toBe(200);
    expect(lobby.json().rooms).toHaveLength(1);
    expect(lobby.json().rooms[0].roomId).toBe(created.json().roomId);
  });

  it('broadcasts room updates over websocket after subscribe', async () => {
    const app = createTestApp();
    apps.push(app);
    const host = await registerUser(app, 'ws-host@example.com', 'Host');
    const guest = await registerUser(app, 'ws-guest@example.com', 'Guest');
    await app.listen({ host: '127.0.0.1', port: 0 });
    const address = new URL(await app.listeningOrigin);
    const created = await app.inject({ method: 'POST', url: '/api/rooms', headers: { cookie: host.cookie }, payload: { gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC' } });
    const roomId = created.json().roomId as string;
    const ws = new WebSocket(`ws://127.0.0.1:${address.port}/ws`);
    const messages: any[] = [];
    const connected = new Promise<void>((resolve, reject) => {
      ws.addEventListener('open', () => resolve());
      ws.addEventListener('error', (event) => reject(event));
    });
    ws.addEventListener('message', (event) => {
      messages.push(JSON.parse(String(event.data)));
    });
    await connected;
    ws.send(JSON.stringify({ type: 'subscribe', roomId }));
    await app.inject({ method: 'POST', url: `/api/rooms/${roomId}/join`, headers: { cookie: guest.cookie }, payload: { seat: 'guest' } });
    await new Promise((resolve) => setTimeout(resolve, 150));
    expect(messages.some((message) => message.type === 'room.sync' && message.room.roomId === roomId)).toBe(true);
    expect(messages.some((message) => message.room.status === 'full')).toBe(true);
    ws.close();
  });

  it('finishes an online room by resignation and exposes leaderboard plus history', async () => {
    const app = createTestApp();
    apps.push(app);
    const host = await registerUser(app, 'play-host@example.com', 'Host');
    const guest = await registerUser(app, 'play-guest@example.com', 'Guest');
    const created = await app.inject({ method: 'POST', url: '/api/rooms', headers: { cookie: host.cookie }, payload: { gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC' } });
    const roomId = created.json().roomId as string;
    await app.inject({ method: 'POST', url: `/api/rooms/${roomId}/join`, headers: { cookie: guest.cookie }, payload: { seat: 'guest' } });
    await app.inject({ method: 'POST', url: `/api/rooms/${roomId}/ready`, headers: { cookie: host.cookie }, payload: { seat: 'host', ready: true } });
    await app.inject({ method: 'POST', url: `/api/rooms/${roomId}/ready`, headers: { cookie: guest.cookie }, payload: { seat: 'guest', ready: true } });
    const started = await app.inject({ method: 'POST', url: `/api/rooms/${roomId}/start`, headers: { cookie: host.cookie } });
    expect(started.statusCode).toBe(200);
    const resigned = await app.inject({ method: 'POST', url: `/api/rooms/${roomId}/resign`, headers: { cookie: host.cookie } });
    expect(resigned.statusCode).toBe(200);
    expect(resigned.json().archiveId).toBeTruthy();

    const history = await app.inject({ method: 'GET', url: '/api/me/history', headers: { cookie: guest.cookie } });
    expect(history.statusCode).toBe(200);
    expect(history.json().archives).toHaveLength(1);

    const leaderboard = await app.inject({ method: 'GET', url: '/api/leaderboard?gameType=GOMOKU' });
    expect(leaderboard.statusCode).toBe(200);
    expect(leaderboard.json().entries[0].displayName).toBe('Guest');
    expect(leaderboard.json().entries[0].points).toBe(3);
  });

  it('creates, finishes, and reviews a practice session for an authenticated user', async () => {
    const app = createTestApp();
    apps.push(app);
    const user = await registerUser(app, 'practice@example.com', 'PracticeUser');
    const created = await app.inject({ method: 'POST', url: '/api/practice-games', headers: { cookie: user.cookie }, payload: { gameType: 'GO', difficulty: 'EASY', humanFirst: true } });
    expect(created.statusCode).toBe(201);
    const practiceGameId = created.json().practiceGameId as string;

    const moved = await app.inject({ method: 'POST', url: `/api/practice-games/${practiceGameId}/move`, headers: { cookie: user.cookie }, payload: { row: 3, col: 3 } });
    expect(moved.statusCode).toBe(200);
    const finished = await app.inject({ method: 'POST', url: `/api/practice-games/${practiceGameId}/finish`, headers: { cookie: user.cookie } });
    expect(finished.statusCode).toBe(200);
    expect(finished.json().archiveId).toBeTruthy();

    const history = await app.inject({ method: 'GET', url: '/api/me/history?sourceType=PRACTICE', headers: { cookie: user.cookie } });
    expect(history.statusCode).toBe(200);
    expect(history.json().archives[0].archiveId).toBe(finished.json().archiveId);

    const review = await app.inject({ method: 'GET', url: `/api/reviews/${finished.json().archiveId}`, headers: { cookie: user.cookie } });
    expect(review.statusCode).toBe(200);
    expect(review.json().archive.gameType).toBe('GO');
    expect(review.json().snapshots.length).toBeGreaterThan(0);
  });

  it('returns a profile summary with stats after recorded activity', async () => {
    const app = createTestApp();
    apps.push(app);
    const user = await registerUser(app, 'profile@example.com', 'ProfileUser');
    const created = await app.inject({ method: 'POST', url: '/api/practice-games', headers: { cookie: user.cookie }, payload: { gameType: 'XIANGQI', difficulty: 'EASY', humanFirst: true } });
    await app.inject({ method: 'POST', url: `/api/practice-games/${created.json().practiceGameId}/finish`, headers: { cookie: user.cookie } });
    const profile = await app.inject({ method: 'GET', url: '/api/me/profile', headers: { cookie: user.cookie } });
    expect(profile.statusCode).toBe(200);
    expect(profile.json().user.displayName).toBe('ProfileUser');
    expect(profile.json().recentArchives.length).toBeGreaterThan(0);
    expect(profile.json().stats.some((stat: any) => stat.gameType === 'XIANGQI')).toBe(true);
  });
});
