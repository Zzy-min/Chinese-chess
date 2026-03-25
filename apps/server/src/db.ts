import fs from 'node:fs';
import path from 'node:path';

import Database from 'better-sqlite3';

export type AppDatabase = Database.Database;

const schemaStatements = [
  `
  CREATE TABLE IF NOT EXISTS users (
    user_id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS sessions (
    session_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(user_id)
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS active_rooms (
    room_id TEXT PRIMARY KEY,
    room_code TEXT NOT NULL UNIQUE,
    owner_user_id TEXT NOT NULL,
    room_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(owner_user_id) REFERENCES users(user_id)
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS practice_sessions (
    practice_game_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    session_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(user_id) REFERENCES users(user_id)
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS archives (
    archive_id TEXT PRIMARY KEY,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    game_type TEXT NOT NULL,
    result_text TEXT NOT NULL,
    participants_json TEXT NOT NULL,
    move_count INTEGER NOT NULL,
    review_tags_json TEXT NOT NULL,
    snapshots_json TEXT NOT NULL,
    created_at TEXT NOT NULL
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS archive_members (
    archive_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    seat TEXT NOT NULL,
    outcome TEXT NOT NULL,
    PRIMARY KEY (archive_id, user_id),
    FOREIGN KEY(archive_id) REFERENCES archives(archive_id),
    FOREIGN KEY(user_id) REFERENCES users(user_id)
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS leaderboard_stats (
    user_id TEXT NOT NULL,
    game_type TEXT NOT NULL,
    points INTEGER NOT NULL DEFAULT 0,
    wins INTEGER NOT NULL DEFAULT 0,
    draws INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0,
    games_played INTEGER NOT NULL DEFAULT 0,
    practice_completed INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (user_id, game_type),
    FOREIGN KEY(user_id) REFERENCES users(user_id)
  );
  `,
  `CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);`,
  `CREATE INDEX IF NOT EXISTS idx_active_rooms_updated_at ON active_rooms(updated_at);`,
  `CREATE INDEX IF NOT EXISTS idx_practice_sessions_user_id ON practice_sessions(user_id);`,
  `CREATE INDEX IF NOT EXISTS idx_archives_created_at ON archives(created_at);`,
  `CREATE INDEX IF NOT EXISTS idx_archive_members_user_id ON archive_members(user_id);`
];

export function resolveDatabasePath(explicitPath?: string) {
  if (explicitPath) {
    return explicitPath;
  }
  return process.env.QIJU_DB_PATH || path.resolve(process.cwd(), 'data', 'qiju.sqlite');
}

export function createDatabase(dbPath = resolveDatabasePath()) {
  if (dbPath !== ':memory:') {
    fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  }

  const db = new Database(dbPath);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');

  for (const statement of schemaStatements) {
    db.exec(statement);
  }

  return db;
}
