export type GameType = 'XIANGQI' | 'GOMOKU' | 'GO' | 'CHESS';

export interface GameCatalogEntry {
  type: GameType;
  label: string;
  shortDescription: string;
  supportsLearning: boolean;
  supportsReview: boolean;
}

export interface BoardTheme {
  type: GameType;
  surfacePattern: string;
  coordinateStyle: string;
  pieceStyle: string;
  accentColor: string;
}

export interface ChessSnapshot {
  gameType: 'CHESS';
  fen: string;
  turn: 'white' | 'black';
  legalMoves: string[];
  lastMove?: string;
  status: 'active' | 'checkmate' | 'draw';
}

export type RuntimeSnapshot = MatchSnapshot | ChessSnapshot;
export type Visibility = 'PUBLIC' | 'PRIVATE';
export type RoomStatus = 'waiting' | 'full' | 'ready' | 'playing' | 'finished' | 'abandoned';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type RoomSeatName = 'host' | 'guest';
export type ArchiveSourceType = 'ONLINE' | 'PRACTICE';
export type ArchiveOutcome = 'WIN' | 'DRAW' | 'LOSS' | 'PRACTICE';

export interface SiteStats {
  activeRooms: number;
  practiceGames: number;
  registeredUsers?: number;
  archivedGames?: number;
}

export interface SiteBootstrap {
  siteName: string;
  games: GameCatalogEntry[];
  stats: SiteStats;
}

export interface RoomSeat {
  label: string;
  joined: boolean;
  ready: boolean;
  userId?: string;
}

export interface MatchMoveRecord {
  side: string;
  notation: string;
  payload: Record<string, unknown>;
}

export interface MatchSnapshot {
  gameType: GameType;
  status: 'active' | 'finished';
  board: string[][];
  currentTurn: string;
  winnerSide?: string;
  resultText?: string;
  moveHistory: MatchMoveRecord[];
  legalMoves?: string[];
  fen?: string;
  consecutivePasses?: number;
  boardSize?: number;
}

export interface RoomSummary {
  roomId: string;
  roomCode: string;
  gameType: GameType;
  timeControl: string;
  visibility: Visibility;
  status: RoomStatus;
  host?: RoomSeat;
  guest?: RoomSeat;
  canStart?: boolean;
  match?: MatchSnapshot;
  archiveId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateRoomInput {
  gameType: GameType;
  timeControl: string;
  visibility: Visibility;
}

export interface JoinRoomByCodeInput {
  roomCode: string;
}

export interface PracticeInitialSnapshot {
  fen?: string;
  notation: string;
}

export interface PracticeMoveRecord {
  actor: 'player' | 'ai';
  move: string;
}

export interface PracticeSession {
  practiceGameId: string;
  gameType: GameType;
  difficulty: Difficulty;
  humanFirst: boolean;
  initialSnapshot: PracticeInitialSnapshot;
  currentSnapshot?: RuntimeSnapshot | PracticeInitialSnapshot;
  moveHistory?: PracticeMoveRecord[];
  archiveId?: string;
  status?: 'active' | 'finished' | 'archived';
  createdAt?: string;
  updatedAt?: string;
}

export interface AuthUser {
  userId: string;
  email: string;
  displayName: string;
  createdAt: string;
}

export interface ProfileStat {
  gameType: GameType;
  points: number;
  wins: number;
  draws: number;
  losses: number;
  gamesPlayed: number;
  practiceCompleted: number;
}

export interface ArchiveParticipant {
  userId?: string;
  displayName: string;
  seat: string;
  outcome: ArchiveOutcome;
}

export interface ArchiveSummary {
  archiveId: string;
  sourceType: ArchiveSourceType;
  gameType: GameType;
  resultText: string;
  createdAt: string;
  moveCount: number;
  reviewTags: string[];
  participants: ArchiveParticipant[];
}

export interface ProfileSummary {
  user: AuthUser;
  stats: ProfileStat[];
  recentArchives: ArchiveSummary[];
}

export interface ReviewPayload {
  archive: ArchiveSummary;
  snapshots: RuntimeSnapshot[];
}

export interface LeaderboardEntry {
  rank: number;
  userId: string;
  displayName: string;
  gameType: GameType;
  points: number;
  wins: number;
  draws: number;
  losses: number;
  gamesPlayed: number;
  practiceCompleted: number;
  updatedAt: string;
}

export interface CapabilityPayload {
  onlineGames: GameType[];
  onlineStatus: string;
  practiceGames: GameType[];
  learnGames: GameType[];
  authStatus?: string;
  persistenceStatus?: string;
  reviewStatus?: string;
}
