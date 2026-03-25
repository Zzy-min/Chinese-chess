import { randomUUID } from 'node:crypto';

import type {
  ArchiveOutcome,
  ArchiveParticipant,
  ArchiveSourceType,
  ArchiveSummary,
  AuthUser,
  GameType,
  LeaderboardEntry,
  PracticeSession,
  ProfileStat,
  ProfileSummary,
  ReviewPayload,
  RoomSummary,
  RuntimeSnapshot
} from '@qiju/core';

import type { AppDatabase } from './db';

export type PersistedRoomRecord = RoomSummary & {
  ownerUserId: string;
  timeline: RuntimeSnapshot[];
};

export type PersistedPracticeRecord = PracticeSession & {
  userId: string;
  humanSide: string;
  aiSide: string;
  timeline: RuntimeSnapshot[];
  eventPayloads: Record<string, unknown>[];
};

type UserRow = {
  user_id: string;
  email: string;
  display_name: string;
  password_hash: string;
  created_at: string;
};

type ArchiveRow = {
  archive_id: string;
  source_type: ArchiveSourceType;
  source_id: string;
  game_type: GameType;
  result_text: string;
  participants_json: string;
  move_count: number;
  review_tags_json: string;
  snapshots_json: string;
  created_at: string;
};

type LeaderboardRow = {
  user_id: string;
  display_name: string;
  game_type: GameType;
  points: number;
  wins: number;
  draws: number;
  losses: number;
  games_played: number;
  practice_completed: number;
  updated_at: string;
};

function toAuthUser(row: UserRow): AuthUser {
  return {
    userId: row.user_id,
    email: row.email,
    displayName: row.display_name,
    createdAt: row.created_at
  };
}

function parseJson<T>(value: string): T {
  return JSON.parse(value) as T;
}

export class AppRepository {
  constructor(private readonly db: AppDatabase) {}

  countUsers() {
    return Number((this.db.prepare('SELECT COUNT(*) AS count FROM users').get() as { count: number }).count);
  }

  countArchives() {
    return Number((this.db.prepare('SELECT COUNT(*) AS count FROM archives').get() as { count: number }).count);
  }

  createUser(input: { email: string; displayName: string; passwordHash: string }) {
    const now = new Date().toISOString();
    const userId = randomUUID();
    this.db.prepare(
      'INSERT INTO users (user_id, email, display_name, password_hash, created_at) VALUES (?, ?, ?, ?, ?)'
    ).run(userId, input.email.toLowerCase(), input.displayName, input.passwordHash, now);
    return this.getUserById(userId)!;
  }

  getUserByEmail(email: string) {
    const row = this.db.prepare('SELECT * FROM users WHERE email = ?').get(email.toLowerCase()) as UserRow | undefined;
    return row
      ? {
          ...toAuthUser(row),
          passwordHash: row.password_hash
        }
      : undefined;
  }

  getUserById(userId: string) {
    const row = this.db.prepare('SELECT * FROM users WHERE user_id = ?').get(userId) as UserRow | undefined;
    return row ? toAuthUser(row) : undefined;
  }

  createSession(userId: string, maxAgeDays = 30) {
    const sessionId = randomUUID();
    const createdAt = new Date().toISOString();
    const expiresAt = new Date(Date.now() + maxAgeDays * 24 * 60 * 60 * 1000).toISOString();
    this.db.prepare('INSERT INTO sessions (session_id, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)').run(sessionId, userId, expiresAt, createdAt);
    return { sessionId, expiresAt };
  }

  getUserForSession(sessionId: string) {
    const row = this.db
      .prepare(
        `SELECT users.*
         FROM sessions
         JOIN users ON users.user_id = sessions.user_id
         WHERE sessions.session_id = ? AND sessions.expires_at > ?`
      )
      .get(sessionId, new Date().toISOString()) as UserRow | undefined;
    return row ? toAuthUser(row) : undefined;
  }

  deleteSession(sessionId: string) {
    this.db.prepare('DELETE FROM sessions WHERE session_id = ?').run(sessionId);
  }

  upsertRoom(record: PersistedRoomRecord) {
    const now = new Date().toISOString();
    const createdAt = record.createdAt ?? now;
    const room = { ...record, createdAt, updatedAt: now };
    this.db
      .prepare(
        `INSERT INTO active_rooms (room_id, room_code, owner_user_id, room_json, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT(room_id)
         DO UPDATE SET room_code = excluded.room_code, owner_user_id = excluded.owner_user_id, room_json = excluded.room_json, updated_at = excluded.updated_at`
      )
      .run(room.roomId, room.roomCode, room.ownerUserId, JSON.stringify(room), createdAt, now);
    return room;
  }

  deleteRoomRecord(roomId: string) {
    this.db.prepare('DELETE FROM active_rooms WHERE room_id = ?').run(roomId);
  }

  listRoomRecords() {
    const rows = this.db.prepare('SELECT room_json FROM active_rooms ORDER BY updated_at DESC').all() as Array<{ room_json: string }>;
    return rows.map((row) => parseJson<PersistedRoomRecord>(row.room_json));
  }

  getRoomRecord(roomId: string) {
    const row = this.db.prepare('SELECT room_json FROM active_rooms WHERE room_id = ?').get(roomId) as { room_json: string } | undefined;
    return row ? parseJson<PersistedRoomRecord>(row.room_json) : undefined;
  }

  upsertPracticeSession(record: PersistedPracticeRecord) {
    const now = new Date().toISOString();
    const createdAt = record.createdAt ?? now;
    const session = { ...record, createdAt, updatedAt: now };
    this.db
      .prepare(
        `INSERT INTO practice_sessions (practice_game_id, user_id, session_json, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?)
         ON CONFLICT(practice_game_id)
         DO UPDATE SET user_id = excluded.user_id, session_json = excluded.session_json, updated_at = excluded.updated_at`
      )
      .run(session.practiceGameId, session.userId, JSON.stringify(session), createdAt, now);
    return session;
  }

  deletePracticeSessionRecord(practiceGameId: string) {
    this.db.prepare('DELETE FROM practice_sessions WHERE practice_game_id = ?').run(practiceGameId);
  }

  listPracticeSessions() {
    const rows = this.db.prepare('SELECT session_json FROM practice_sessions ORDER BY updated_at DESC').all() as Array<{ session_json: string }>;
    return rows.map((row) => parseJson<PersistedPracticeRecord>(row.session_json));
  }

  getPracticeSessionRecord(practiceGameId: string) {
    const row = this.db.prepare('SELECT session_json FROM practice_sessions WHERE practice_game_id = ?').get(practiceGameId) as { session_json: string } | undefined;
    return row ? parseJson<PersistedPracticeRecord>(row.session_json) : undefined;
  }

  createArchive(input: {
    sourceType: ArchiveSourceType;
    sourceId: string;
    gameType: GameType;
    resultText: string;
    reviewTags: string[];
    snapshots: RuntimeSnapshot[];
    participants: ArchiveParticipant[];
  }) {
    const existing = this.db.prepare('SELECT archive_id FROM archives WHERE source_type = ? AND source_id = ?').get(input.sourceType, input.sourceId) as { archive_id: string } | undefined;
    if (existing) {
      return this.getArchiveSummary(existing.archive_id)!;
    }

    const archiveId = randomUUID();
    const createdAt = new Date().toISOString();
    const moveCount = this.moveCountOf(input.snapshots[input.snapshots.length - 1]);
    this.db
      .prepare(
        `INSERT INTO archives (archive_id, source_type, source_id, game_type, result_text, participants_json, move_count, review_tags_json, snapshots_json, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(
        archiveId,
        input.sourceType,
        input.sourceId,
        input.gameType,
        input.resultText,
        JSON.stringify(input.participants),
        moveCount,
        JSON.stringify(input.reviewTags),
        JSON.stringify(input.snapshots),
        createdAt
      );

    const memberStatement = this.db.prepare(
      'INSERT INTO archive_members (archive_id, user_id, display_name, seat, outcome) VALUES (?, ?, ?, ?, ?)'
    );
    for (const participant of input.participants) {
      if (!participant.userId) {
        continue;
      }
      memberStatement.run(archiveId, participant.userId, participant.displayName, participant.seat, participant.outcome);
    }

    return this.getArchiveSummary(archiveId)!;
  }

  getArchiveSummary(archiveId: string) {
    const row = this.db.prepare('SELECT * FROM archives WHERE archive_id = ?').get(archiveId) as ArchiveRow | undefined;
    if (!row) {
      return undefined;
    }
    return {
      archiveId: row.archive_id,
      sourceType: row.source_type,
      gameType: row.game_type,
      resultText: row.result_text,
      createdAt: row.created_at,
      moveCount: row.move_count,
      reviewTags: parseJson<string[]>(row.review_tags_json),
      participants: parseJson<ArchiveParticipant[]>(row.participants_json)
    } satisfies ArchiveSummary;
  }

  listArchivesForUser(userId: string, filters?: { gameType?: GameType; sourceType?: ArchiveSourceType }) {
    const rows = this.db
      .prepare(
        `SELECT archives.archive_id
         FROM archives
         JOIN archive_members ON archive_members.archive_id = archives.archive_id
         WHERE archive_members.user_id = ?
           AND (? IS NULL OR archives.game_type = ?)
           AND (? IS NULL OR archives.source_type = ?)
         ORDER BY archives.created_at DESC`
      )
      .all(userId, filters?.gameType ?? null, filters?.gameType ?? null, filters?.sourceType ?? null, filters?.sourceType ?? null) as Array<{ archive_id: string }>;
    return rows.map((row) => this.getArchiveSummary(row.archive_id)!).filter(Boolean);
  }

  getReviewForUser(userId: string, archiveId: string) {
    const member = this.db.prepare('SELECT 1 FROM archive_members WHERE archive_id = ? AND user_id = ?').get(archiveId, userId);
    if (!member) {
      return undefined;
    }
    const row = this.db.prepare('SELECT * FROM archives WHERE archive_id = ?').get(archiveId) as ArchiveRow | undefined;
    if (!row) {
      return undefined;
    }
    return {
      archive: this.getArchiveSummary(archiveId)!,
      snapshots: parseJson<RuntimeSnapshot[]>(row.snapshots_json)
    } satisfies ReviewPayload;
  }

  incrementLeaderboard(userId: string, gameType: GameType, update: { points?: number; wins?: number; draws?: number; losses?: number; gamesPlayed?: number; practiceCompleted?: number }) {
    const now = new Date().toISOString();
    this.db
      .prepare(
        `INSERT INTO leaderboard_stats (user_id, game_type, points, wins, draws, losses, games_played, practice_completed, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(user_id, game_type)
         DO UPDATE SET
           points = leaderboard_stats.points + excluded.points,
           wins = leaderboard_stats.wins + excluded.wins,
           draws = leaderboard_stats.draws + excluded.draws,
           losses = leaderboard_stats.losses + excluded.losses,
           games_played = leaderboard_stats.games_played + excluded.games_played,
           practice_completed = leaderboard_stats.practice_completed + excluded.practice_completed,
           updated_at = excluded.updated_at`
      )
      .run(
        userId,
        gameType,
        update.points ?? 0,
        update.wins ?? 0,
        update.draws ?? 0,
        update.losses ?? 0,
        update.gamesPlayed ?? 0,
        update.practiceCompleted ?? 0,
        now
      );
  }

  listLeaderboard(gameType: GameType) {
    const rows = this.db
      .prepare(
        `SELECT leaderboard_stats.*, users.display_name
         FROM leaderboard_stats
         JOIN users ON users.user_id = leaderboard_stats.user_id
         WHERE leaderboard_stats.game_type = ?
         ORDER BY leaderboard_stats.points DESC, leaderboard_stats.wins DESC, leaderboard_stats.games_played DESC, leaderboard_stats.updated_at ASC
         LIMIT 20`
      )
      .all(gameType) as LeaderboardRow[];
    return rows.map((row, index) => ({
      rank: index + 1,
      userId: row.user_id,
      displayName: row.display_name,
      gameType: row.game_type,
      points: row.points,
      wins: row.wins,
      draws: row.draws,
      losses: row.losses,
      gamesPlayed: row.games_played,
      practiceCompleted: row.practice_completed,
      updatedAt: row.updated_at
    })) satisfies LeaderboardEntry[];
  }

  getProfile(userId: string) {
    const user = this.getUserById(userId);
    if (!user) {
      return undefined;
    }
    const rows = this.db.prepare('SELECT * FROM leaderboard_stats WHERE user_id = ? ORDER BY game_type ASC').all(userId) as Array<{ game_type: GameType; points: number; wins: number; draws: number; losses: number; games_played: number; practice_completed: number }>;
    const stats = rows.map((row) => ({
      gameType: row.game_type,
      points: row.points,
      wins: row.wins,
      draws: row.draws,
      losses: row.losses,
      gamesPlayed: row.games_played,
      practiceCompleted: row.practice_completed
    })) satisfies ProfileStat[];
    return {
      user,
      stats,
      recentArchives: this.listArchivesForUser(userId).slice(0, 12)
    } satisfies ProfileSummary;
  }

  private moveCountOf(snapshot?: RuntimeSnapshot) {
    if (!snapshot) {
      return 0;
    }
    if ('moveHistory' in snapshot) {
      return snapshot.moveHistory.length;
    }
    if ('lastMove' in snapshot && snapshot.lastMove) {
      return 1;
    }
    return 0;
  }
}
