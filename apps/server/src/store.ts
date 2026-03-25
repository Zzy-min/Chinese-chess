import { randomUUID } from 'node:crypto';

import { Chess } from 'chess.js';
import GoGameModule from 'go-board-indo';
import * as zh from 'zh-chess';

import type {
  ArchiveOutcome,
  ArchiveParticipant,
  AuthUser,
  ChessSnapshot,
  CreateRoomInput,
  MatchMoveRecord,
  MatchSnapshot,
  PracticeInitialSnapshot,
  PracticeMoveRecord,
  PracticeSession,
  ProfileSummary,
  ReviewPayload,
  RoomSeatName,
  RoomSummary,
  RuntimeSnapshot,
  SiteBootstrap,
  GameType,
  LeaderboardEntry,
  ArchiveSourceType,
  ArchiveSummary
} from '@qiju/core';
import { gameCatalog } from '@qiju/core';

import type { AppRepository, PersistedPracticeRecord, PersistedRoomRecord } from './repository';

const zhModule = zh as any;
const zhApi = zhModule.default ?? zhModule;
const ZhChess = zhApi.default ?? zhApi;
const ZhPoint = zhApi.Point ?? zhModule.Point;
const GoGame = (GoGameModule as any).default ?? (GoGameModule as any);
const CHESS_PIECES = new Set(['p', 'r', 'n', 'b', 'q', 'k', 'P', 'R', 'N', 'B', 'Q', 'K']);

type MatchRuntime = {
  snapshot(): MatchSnapshot;
  applyMove(payload: Record<string, unknown>): MatchSnapshot;
  suggestMoves(): Record<string, unknown>[];
};

type PracticeRuntime = {
  snapshot(): RuntimeSnapshot;
  applyMove(payload: Record<string, unknown>): RuntimeSnapshot;
  suggestMoves(): Record<string, unknown>[];
};

type ActiveRoom = PersistedRoomRecord & { matchRuntime?: MatchRuntime };
type ActivePracticeGame = PersistedPracticeRecord & { runtime: PracticeRuntime };

function makeRoomCode() {
  return randomUUID().replace(/-/g, '').slice(0, 8).toUpperCase();
}

function createInitialSnapshot(gameType: PracticeSession['gameType']): PracticeInitialSnapshot {
  if (gameType === 'CHESS') return { fen: 'start', notation: 'start' };
  return { notation: `${gameType.toLowerCase()}:start` };
}

function currentTurnOf(snapshot: RuntimeSnapshot) {
  return 'currentTurn' in snapshot ? snapshot.currentTurn : snapshot.turn === 'white' ? 'WHITE' : 'BLACK';
}

function computeRoomStatus(room: RoomSummary): RoomSummary['status'] {
  if (room.status === 'playing' || room.status === 'finished' || room.status === 'abandoned') return room.status;
  if (!room.guest?.joined) return 'waiting';
  if (room.host?.ready && room.guest?.ready) return 'ready';
  return 'full';
}

function computeCanStart(room: RoomSummary): boolean {
  return Boolean(room.host?.joined && room.guest?.joined && room.host.ready && room.guest.ready && room.status !== 'playing');
}

function cloneSnapshot(snapshot: RuntimeSnapshot | PracticeInitialSnapshot | undefined) {
  if (!snapshot) return undefined;
  if ('board' in snapshot) {
    return {
      ...snapshot,
      board: snapshot.board.map((row) => [...row]),
      moveHistory: snapshot.moveHistory.map((item) => ({ ...item })),
      legalMoves: snapshot.legalMoves ? [...snapshot.legalMoves] : undefined
    };
  }
  if ('legalMoves' in snapshot) {
    return {
      ...snapshot,
      legalMoves: [...snapshot.legalMoves]
    };
  }
  return { ...snapshot };
}

function cloneRoom(room: ActiveRoom): RoomSummary {
  return {
    roomId: room.roomId,
    roomCode: room.roomCode,
    gameType: room.gameType,
    timeControl: room.timeControl,
    visibility: room.visibility,
    status: room.status,
    host: room.host ? { ...room.host } : undefined,
    guest: room.guest ? { ...room.guest } : undefined,
    canStart: room.canStart,
    match: room.match ? (cloneSnapshot(room.match) as MatchSnapshot) : undefined,
    archiveId: room.archiveId,
    createdAt: room.createdAt,
    updatedAt: room.updatedAt
  };
}

function clonePractice(session: ActivePracticeGame): PracticeSession {
  return {
    practiceGameId: session.practiceGameId,
    gameType: session.gameType,
    difficulty: session.difficulty,
    humanFirst: session.humanFirst,
    initialSnapshot: { ...session.initialSnapshot },
    currentSnapshot: cloneSnapshot(session.currentSnapshot) as PracticeSession['currentSnapshot'],
    moveHistory: session.moveHistory ? session.moveHistory.map((item) => ({ ...item })) : [],
    archiveId: session.archiveId,
    status: session.status,
    createdAt: session.createdAt,
    updatedAt: session.updatedAt
  };
}

function createEmptyGomokuBoard() {
  return Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => ''));
}

function countDir(board: string[][], row: number, col: number, dr: number, dc: number, stone: string) {
  let steps = 0;
  let nextRow = row + dr;
  let nextCol = col + dc;
  while (nextRow >= 0 && nextRow < 15 && nextCol >= 0 && nextCol < 15 && board[nextRow][nextCol] === stone) {
    steps++;
    nextRow += dr;
    nextCol += dc;
  }
  return steps;
}

function gomokuWinner(board: string[][], row: number, col: number, stone: string) {
  const dirs = [[1, 0], [0, 1], [1, 1], [1, -1]];
  return dirs.some(([dr, dc]) => countDir(board, row, col, dr, dc, stone) + countDir(board, row, col, -dr, -dc, stone) + 1 >= 5);
}

function buildXiangqiBoard(game: any) {
  const board = Array.from({ length: 10 }, () => Array.from({ length: 9 }, () => ''));
  for (const piece of game.currentLivePieceList) {
    board[piece.y][piece.x] = piece.name;
  }
  return board;
}

function buildChessBoard(fen: string) {
  const source = fen === 'start' ? 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR' : fen.split(' ')[0];
  return source.split('/').map((rank) => {
    const row: string[] = [];
    for (const char of rank) {
      if (/\d/.test(char)) row.push(...Array.from({ length: Number(char) }, () => ''));
      else if (CHESS_PIECES.has(char)) row.push(char);
    }
    return row;
  });
}

function buildGoBoard(board: number[][]) {
  return board.map((row) => row.map((cell) => (cell === 1 ? 'BLACK' : cell === 2 ? 'WHITE' : '')));
}

function pickCenterFirstEmpty(board: string[][], center: number) {
  if (!board[center][center]) return [center, center] as const;
  for (let radius = 1; radius < board.length; radius++) {
    for (let row = Math.max(0, center - radius); row <= Math.min(board.length - 1, center + radius); row++) {
      for (let col = Math.max(0, center - radius); col <= Math.min(board.length - 1, center + radius); col++) {
        if (!board[row][col]) return [row, col] as const;
      }
    }
  }
  return null;
}

function createXiangqiRuntime(): MatchRuntime {
  const game = new ZhChess({});
  game.gameStart('RED');
  const moveHistory: MatchMoveRecord[] = [];

  return {
    snapshot(): MatchSnapshot {
      return {
        gameType: 'XIANGQI',
        status: game.gameOver() ? 'finished' : 'active',
        board: buildXiangqiBoard(game),
        currentTurn: game.currentSide,
        winnerSide: game.winnerSide || '',
        resultText: game.gameOver() ? `${game.winnerSide || 'UNKNOWN'} wins` : '',
        moveHistory: moveHistory.map((item) => ({ ...item }))
      };
    },
    applyMove(payload: Record<string, unknown>): MatchSnapshot {
      const side = game.currentSide;
      const result = game.update(new ZhPoint(Number(payload.fromX), Number(payload.fromY)), new ZhPoint(Number(payload.toX), Number(payload.toY)), side, true);
      if (!result.flag || !result.move) throw new Error(result.message || 'illegal move');
      moveHistory.push({ side, notation: `${payload.fromX},${payload.fromY} -> ${payload.toX},${payload.toY}`, payload });
      return this.snapshot();
    },
    suggestMoves() {
      const candidates: Record<string, unknown>[] = [];
      const pieces = game.currentLivePieceList.filter((piece: any) => piece.side === game.currentSide);
      for (const piece of pieces) {
        const moves = piece.getMovePoints(game.currentLivePieceList) as Array<{ x: number; y: number }>;
        for (const move of moves) {
          candidates.push({ fromX: piece.x, fromY: piece.y, toX: move.x, toY: move.y });
        }
      }
      return candidates;
    }
  };
}

function createGomokuRuntime(): MatchRuntime {
  const board = createEmptyGomokuBoard();
  const moveHistory: MatchMoveRecord[] = [];
  let currentTurn = 'BLACK';
  let status: MatchSnapshot['status'] = 'active';
  let winnerSide = '';
  let resultText = '';

  return {
    snapshot(): MatchSnapshot {
      return {
        gameType: 'GOMOKU',
        status,
        board: board.map((row) => [...row]),
        currentTurn,
        winnerSide,
        resultText,
        moveHistory: moveHistory.map((item) => ({ ...item }))
      };
    },
    applyMove(payload: Record<string, unknown>): MatchSnapshot {
      if (status === 'finished') throw new Error('game already finished');
      const row = Number(payload.row);
      const col = Number(payload.col);
      if (Number.isNaN(row) || Number.isNaN(col) || row < 0 || row >= 15 || col < 0 || col >= 15) throw new Error('illegal move');
      if (board[row][col]) throw new Error('illegal move');
      board[row][col] = currentTurn;
      moveHistory.push({ side: currentTurn, notation: `${currentTurn} ${row},${col}`, payload });
      if (gomokuWinner(board, row, col, currentTurn)) {
        status = 'finished';
        winnerSide = currentTurn;
        resultText = `${currentTurn} wins`;
      } else {
        currentTurn = currentTurn === 'BLACK' ? 'WHITE' : 'BLACK';
      }
      return this.snapshot();
    },
    suggestMoves() {
      const choice = pickCenterFirstEmpty(board, Math.floor(board.length / 2));
      return choice ? [{ row: choice[0], col: choice[1] }] : [];
    }
  };
}

function createChessRuntime(): MatchRuntime {
  const chess = new Chess();
  const moveHistory: MatchMoveRecord[] = [];

  return {
    snapshot(): MatchSnapshot {
      const fen = chess.fen();
      const normalizedFen = fen === 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1' ? 'start' : fen;
      return {
        gameType: 'CHESS',
        status: chess.isGameOver() ? 'finished' : 'active',
        board: buildChessBoard(normalizedFen),
        currentTurn: chess.turn() === 'w' ? 'WHITE' : 'BLACK',
        winnerSide: chess.isCheckmate() ? (chess.turn() === 'w' ? 'BLACK' : 'WHITE') : '',
        resultText: chess.isCheckmate() ? 'checkmate' : chess.isDraw() ? 'draw' : '',
        moveHistory: moveHistory.map((item) => ({ ...item })),
        legalMoves: chess.moves(),
        fen: normalizedFen
      };
    },
    applyMove(payload: Record<string, unknown>): MatchSnapshot {
      const move = String(payload.move || '');
      const result = chess.move(move);
      if (!result) throw new Error('illegal move');
      moveHistory.push({ side: result.color === 'w' ? 'WHITE' : 'BLACK', notation: move, payload });
      return this.snapshot();
    },
    suggestMoves() {
      return chess.moves().map((move) => ({ move }));
    }
  };
}

function createGoRuntime(): MatchRuntime {
  const game = new GoGame(19);
  const moveHistory: MatchMoveRecord[] = [];
  let status: MatchSnapshot['status'] = 'active';
  let winnerSide = '';
  let resultText = '';
  let consecutivePasses = 0;

  return {
    snapshot(): MatchSnapshot {
      const currentTurn = game.turn === 1 ? 'BLACK' : 'WHITE';
      return {
        gameType: 'GO',
        status,
        board: buildGoBoard(game.getBoard()),
        currentTurn,
        winnerSide,
        resultText,
        moveHistory: moveHistory.map((item) => ({ ...item })),
        consecutivePasses,
        boardSize: game.size
      };
    },
    applyMove(payload: Record<string, unknown>): MatchSnapshot {
      if (status === 'finished') throw new Error('game already finished');
      const side = game.turn === 1 ? 'BLACK' : 'WHITE';
      if (payload.pass === true) {
        const result = game.pass();
        if (!result.isValid) throw new Error('illegal pass');
        moveHistory.push({ side, notation: 'pass', payload });
        consecutivePasses += 1;
        if (consecutivePasses >= 2) {
          status = 'finished';
          const score = game.calculateScore(7.5);
          winnerSide = score.black > score.white ? 'BLACK' : 'WHITE';
          resultText = `B ${score.black} / W ${score.white}`;
        }
        return this.snapshot();
      }
      const row = Number(payload.row);
      const col = Number(payload.col);
      const result = game.makeMove(col, row);
      if (!result.isValid) throw new Error('illegal move');
      moveHistory.push({ side, notation: `${side} ${row},${col}`, payload });
      consecutivePasses = 0;
      return this.snapshot();
    },
    suggestMoves() {
      const board = buildGoBoard(game.getBoard());
      const center = Math.floor(board.length / 2);
      const choice = pickCenterFirstEmpty(board, center);
      return choice ? [{ row: choice[0], col: choice[1] }, { pass: true }] : [{ pass: true }];
    }
  };
}

function createChessPracticeRuntime(): PracticeRuntime {
  const chess = new Chess();

  return {
    snapshot(): ChessSnapshot {
      const fen = chess.fen();
      return {
        gameType: 'CHESS',
        fen: fen === 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1' ? 'start' : fen,
        turn: chess.turn() === 'w' ? 'white' : 'black',
        legalMoves: chess.moves(),
        lastMove: chess.history().at(-1),
        status: chess.isCheckmate() ? 'checkmate' : chess.isDraw() ? 'draw' : 'active'
      };
    },
    applyMove(payload: Record<string, unknown>): ChessSnapshot {
      const move = String(payload.move || '');
      const result = chess.move(move);
      if (!result) throw new Error('illegal move');
      return this.snapshot() as ChessSnapshot;
    },
    suggestMoves() {
      return chess.moves().map((move) => ({ move }));
    }
  };
}

function createMatchRuntime(gameType: RoomSummary['gameType']): MatchRuntime {
  if (gameType === 'XIANGQI') return createXiangqiRuntime();
  if (gameType === 'GOMOKU') return createGomokuRuntime();
  if (gameType === 'CHESS') return createChessRuntime();
  if (gameType === 'GO') return createGoRuntime();
  throw new Error(`online match not supported for ${gameType}`);
}

function createPracticeRuntime(gameType: PracticeSession['gameType']): PracticeRuntime {
  if (gameType === 'CHESS') return createChessPracticeRuntime();
  return createMatchRuntime(gameType as RoomSummary['gameType']);
}

function deriveSides(gameType: PracticeSession['gameType'], humanFirst: boolean) {
  if (gameType === 'XIANGQI') {
    return humanFirst ? { humanSide: 'RED', aiSide: 'BLACK' } : { humanSide: 'BLACK', aiSide: 'RED' };
  }
  if (gameType === 'CHESS') {
    return humanFirst ? { humanSide: 'WHITE', aiSide: 'BLACK' } : { humanSide: 'BLACK', aiSide: 'WHITE' };
  }
  return humanFirst ? { humanSide: 'BLACK', aiSide: 'WHITE' } : { humanSide: 'WHITE', aiSide: 'BLACK' };
}

function pushPracticeHistory(game: ActivePracticeGame, actor: 'player' | 'ai', snapshot: RuntimeSnapshot, payload: Record<string, unknown>) {
  if ('moveHistory' in snapshot && snapshot.moveHistory.length) {
    const last = snapshot.moveHistory[snapshot.moveHistory.length - 1];
    game.moveHistory = [...(game.moveHistory ?? []), { actor, move: last.notation }];
    return;
  }
  if ('legalMoves' in snapshot && 'move' in payload) {
    game.moveHistory = [...(game.moveHistory ?? []), { actor, move: String(payload.move) }];
    return;
  }
  if (payload.pass === true) {
    game.moveHistory = [...(game.moveHistory ?? []), { actor, move: 'pass' }];
    return;
  }
  if ('row' in payload && 'col' in payload) {
    game.moveHistory = [...(game.moveHistory ?? []), { actor, move: `${payload.row},${payload.col}` }];
    return;
  }
  if ('fromX' in payload && 'toX' in payload) {
    game.moveHistory = [...(game.moveHistory ?? []), { actor, move: `${payload.fromX},${payload.fromY} -> ${payload.toX},${payload.toY}` }];
  }
}

function sideForSeat(gameType: GameType, seat: RoomSeatName) {
  if (gameType === 'XIANGQI') {
    return seat === 'host' ? 'RED' : 'BLACK';
  }
  if (gameType === 'CHESS') {
    return seat === 'host' ? 'WHITE' : 'BLACK';
  }
  return seat === 'host' ? 'BLACK' : 'WHITE';
}

function outcomeForSide(winnerSide: string, side: string, resultText: string): ArchiveOutcome {
  const normalized = resultText.toLowerCase();
  if (!winnerSide || normalized.includes('draw')) {
    return 'DRAW';
  }
  return winnerSide === side ? 'WIN' : 'LOSS';
}

function buildReviewTags(gameType: GameType, snapshots: RuntimeSnapshot[], resultText: string) {
  const tags = new Set<string>();
  if (snapshots.length > 1) {
    tags.add('开局');
  }
  if (snapshots.length > 8) {
    tags.add('中盘转折');
  }
  const last = snapshots.at(-1);
  if (last && 'status' in last && last.status === 'finished') {
    tags.add('终局');
  }
  if (gameType === 'GO' && last && 'consecutivePasses' in last && (last.consecutivePasses ?? 0) >= 2) {
    tags.add('连续停一手结束');
  }
  if (resultText.toLowerCase().includes('resignation')) {
    tags.add('认输结束');
  }
  return [...tags];
}

function restoreRoomRuntime(record: PersistedRoomRecord) {
  const runtime = createMatchRuntime(record.gameType);
  for (const move of record.match?.moveHistory ?? []) {
    runtime.applyMove(move.payload);
  }
  return runtime;
}

function restorePracticeRuntime(record: PersistedPracticeRecord) {
  const runtime = createPracticeRuntime(record.gameType);
  for (const payload of record.eventPayloads ?? []) {
    runtime.applyMove(payload);
  }
  return runtime;
}

function seatForUser(room: ActiveRoom, userId: string): RoomSeatName | undefined {
  if (room.host?.userId === userId) return 'host';
  if (room.guest?.userId === userId) return 'guest';
  return undefined;
}

function ensureUserOwnsSeat(room: ActiveRoom, userId: string, seat: RoomSeatName) {
  const actual = seatForUser(room, userId);
  if (actual !== seat) {
    throw new Error('forbidden seat action');
  }
}

function cloneTimeline(timeline: RuntimeSnapshot[]) {
  return timeline.map((snapshot) => cloneSnapshot(snapshot) as RuntimeSnapshot);
}

export function createInMemoryStore(repository?: AppRepository) {
  const rooms: ActiveRoom[] = (repository?.listRoomRecords() ?? []).map((record) => {
    const runtime = record.match ? restoreRoomRuntime(record) : undefined;
    return {
      ...record,
      matchRuntime: runtime,
      match: runtime ? runtime.snapshot() : record.match,
      timeline: record.timeline?.length ? cloneTimeline(record.timeline) : []
    };
  });
  const practiceGames: ActivePracticeGame[] = (repository?.listPracticeSessions() ?? []).map((record) => {
    const runtime = restorePracticeRuntime(record);
    return {
      ...record,
      runtime,
      currentSnapshot: cloneSnapshot(runtime.snapshot()) as PracticeSession['currentSnapshot'],
      timeline: record.timeline?.length ? cloneTimeline(record.timeline) : []
    };
  });

  const persistRoom = (room: ActiveRoom) => {
    repository?.upsertRoom({
      ...room,
      timeline: cloneTimeline(room.timeline ?? []),
      match: room.match ? (cloneSnapshot(room.match) as MatchSnapshot) : undefined
    });
  };

  const persistPractice = (session: ActivePracticeGame) => {
    repository?.upsertPracticeSession({
      ...session,
      timeline: cloneTimeline(session.timeline ?? []),
      currentSnapshot: cloneSnapshot(session.currentSnapshot) as PracticeSession['currentSnapshot'],
      moveHistory: session.moveHistory ? session.moveHistory.map((item) => ({ ...item })) : [],
      eventPayloads: [...session.eventPayloads]
    });
  };

  const finalizeRoomArchive = (room: ActiveRoom) => {
    if (!repository || room.archiveId || !room.match) {
      return room;
    }
    const participants: ArchiveParticipant[] = [
      room.host?.userId
        ? {
            userId: room.host.userId,
            displayName: room.host.label,
            seat: 'host',
            outcome: outcomeForSide(room.match.winnerSide ?? '', sideForSeat(room.gameType, 'host'), room.match.resultText ?? '')
          }
        : undefined,
      room.guest?.userId
        ? {
            userId: room.guest.userId,
            displayName: room.guest.label,
            seat: 'guest',
            outcome: outcomeForSide(room.match.winnerSide ?? '', sideForSeat(room.gameType, 'guest'), room.match.resultText ?? '')
          }
        : undefined
    ].filter(Boolean) as ArchiveParticipant[];

    const archive = repository.createArchive({
      sourceType: 'ONLINE',
      sourceId: room.roomId,
      gameType: room.gameType,
      resultText: room.match.resultText || 'finished',
      reviewTags: buildReviewTags(room.gameType, room.timeline, room.match.resultText || ''),
      snapshots: room.timeline,
      participants
    });
    room.archiveId = archive.archiveId;
    for (const participant of participants) {
      if (!participant.userId) continue;
      const points = participant.outcome === 'WIN' ? 3 : participant.outcome === 'DRAW' ? 1 : 0;
      repository.incrementLeaderboard(participant.userId, room.gameType, {
        points,
        wins: participant.outcome === 'WIN' ? 1 : 0,
        draws: participant.outcome === 'DRAW' ? 1 : 0,
        losses: participant.outcome === 'LOSS' ? 1 : 0,
        gamesPlayed: 1
      });
    }
    persistRoom(room);
    return room;
  };

  const finalizePracticeArchive = (session: ActivePracticeGame) => {
    if (!repository || session.archiveId || !session.currentSnapshot) {
      return session;
    }
    const current = session.currentSnapshot as RuntimeSnapshot;
    const winnerSide = 'winnerSide' in current ? current.winnerSide ?? '' : current.status === 'checkmate' ? (current.turn === 'white' ? 'BLACK' : 'WHITE') : '';
    const resultText = 'resultText' in current ? current.resultText || 'practice finished' : current.status;
    const participants: ArchiveParticipant[] = [
      {
        userId: session.userId,
        displayName: '玩家',
        seat: 'player',
        outcome: winnerSide ? (winnerSide === session.humanSide ? 'WIN' : 'LOSS') : 'PRACTICE'
      },
      {
        displayName: 'AI',
        seat: 'ai',
        outcome: winnerSide ? (winnerSide === session.aiSide ? 'WIN' : 'LOSS') : 'PRACTICE'
      }
    ];

    const archive = repository.createArchive({
      sourceType: 'PRACTICE',
      sourceId: session.practiceGameId,
      gameType: session.gameType,
      resultText,
      reviewTags: buildReviewTags(session.gameType, session.timeline, resultText),
      snapshots: session.timeline,
      participants
    });
    session.archiveId = archive.archiveId;
    session.status = 'archived';
    repository.incrementLeaderboard(session.userId, session.gameType, {
      practiceCompleted: 1
    });
    persistPractice(session);
    return session;
  };

  const maybeApplyAiTurn = (game: ActivePracticeGame) => {
    const current = game.runtime.snapshot();
    if (currentTurnOf(current) !== game.aiSide) {
      game.currentSnapshot = cloneSnapshot(current) as PracticeSession['currentSnapshot'];
      return;
    }
    const suggestions = game.runtime.suggestMoves();
    for (const payload of suggestions) {
      try {
        const snapshot = game.runtime.applyMove(payload);
        game.eventPayloads.push(payload);
        pushPracticeHistory(game, 'ai', snapshot, payload);
        game.currentSnapshot = cloneSnapshot(snapshot) as PracticeSession['currentSnapshot'];
        game.timeline = [...game.timeline, cloneSnapshot(snapshot) as RuntimeSnapshot];
        persistPractice(game);
        return;
      } catch {
        continue;
      }
    }
  };

  return {
    bootstrap(): SiteBootstrap {
      return {
        siteName: '轻棋局 2.0',
        games: gameCatalog,
        stats: {
          activeRooms: rooms.filter((room) => room.status !== 'finished' && room.status !== 'abandoned').length,
          practiceGames: practiceGames.filter((session) => session.status !== 'archived').length,
          registeredUsers: repository?.countUsers() ?? 0,
          archivedGames: repository?.countArchives() ?? 0
        }
      };
    },
    listRooms(): RoomSummary[] {
      return rooms.map(cloneRoom);
    },
    createRoom(input: CreateRoomInput, user: AuthUser): RoomSummary {
      const room: ActiveRoom = {
        roomId: randomUUID(),
        roomCode: makeRoomCode(),
        gameType: input.gameType,
        timeControl: input.timeControl,
        visibility: input.visibility,
        status: 'waiting',
        host: { label: user.displayName, joined: true, ready: false, userId: user.userId },
        guest: { label: '访客', joined: false, ready: false },
        canStart: false,
        match: undefined,
        ownerUserId: user.userId,
        timeline: []
      };
      rooms.unshift(room);
      persistRoom(room);
      return cloneRoom(room);
    },
    getRoom(roomId: string): RoomSummary | undefined {
      const room = rooms.find((entry) => entry.roomId === roomId);
      return room ? cloneRoom(room) : undefined;
    },
    getRoomByCode(roomCode: string): RoomSummary | undefined {
      const room = rooms.find((entry) => entry.roomCode === roomCode);
      return room ? cloneRoom(room) : undefined;
    },
    joinRoom(roomId: string, seat: RoomSeatName, user: AuthUser): RoomSummary | undefined {
      const room = rooms.find((entry) => entry.roomId === roomId);
      if (!room) return undefined;
      if (seat !== 'guest') {
        throw new Error('unsupported seat');
      }
      if (room.guest?.joined && room.guest.userId && room.guest.userId !== user.userId) {
        throw new Error('seat already taken');
      }
      if (room.guest) {
        room.guest.joined = true;
        room.guest.label = user.displayName;
        room.guest.userId = user.userId;
      }
      room.status = computeRoomStatus(room);
      room.canStart = computeCanStart(room);
      persistRoom(room);
      return cloneRoom(room);
    },
    setReady(roomId: string, seat: RoomSeatName, ready: boolean, user: AuthUser): RoomSummary | undefined {
      const room = rooms.find((entry) => entry.roomId === roomId);
      if (!room) return undefined;
      ensureUserOwnsSeat(room, user.userId, seat);
      const target = seat === 'host' ? room.host : room.guest;
      if (!target || !target.joined) return cloneRoom(room);
      target.ready = ready;
      room.status = computeRoomStatus(room);
      room.canStart = computeCanStart(room);
      persistRoom(room);
      return cloneRoom(room);
    },
    startRoom(roomId: string, user: AuthUser): RoomSummary | undefined {
      const room = rooms.find((entry) => entry.roomId === roomId);
      if (!room) return undefined;
      ensureUserOwnsSeat(room, user.userId, 'host');
      room.canStart = computeCanStart(room);
      if (!room.canStart) {
        throw new Error('room not ready');
      }
      room.status = 'playing';
      room.canStart = false;
      room.matchRuntime = createMatchRuntime(room.gameType);
      room.match = room.matchRuntime.snapshot();
      room.timeline = [cloneSnapshot(room.match) as RuntimeSnapshot];
      persistRoom(room);
      return cloneRoom(room);
    },
    applyRoomMove(roomId: string, payload: Record<string, unknown>, user: AuthUser): RoomSummary | undefined {
      const room = rooms.find((entry) => entry.roomId === roomId);
      if (!room || !room.matchRuntime || !room.match) return undefined;
      const seat = seatForUser(room, user.userId);
      if (!seat) {
        throw new Error('forbidden move');
      }
      const expectedSide = sideForSeat(room.gameType, seat);
      if (expectedSide !== room.match.currentTurn) {
        throw new Error('not your turn');
      }
      room.match = room.matchRuntime.applyMove(payload);
      room.timeline = [...room.timeline, cloneSnapshot(room.match) as RuntimeSnapshot];
      if (room.match.status === 'finished') {
        room.status = 'finished';
        finalizeRoomArchive(room);
      } else {
        persistRoom(room);
      }
      return cloneRoom(room);
    },
    resignRoom(roomId: string, user: AuthUser): RoomSummary | undefined {
      const room = rooms.find((entry) => entry.roomId === roomId);
      if (!room || !room.match) return undefined;
      const seat = seatForUser(room, user.userId);
      if (!seat) {
        throw new Error('forbidden resign');
      }
      const winnerSide = sideForSeat(room.gameType, seat === 'host' ? 'guest' : 'host');
      room.match = {
        ...room.match,
        status: 'finished',
        winnerSide,
        resultText: `${winnerSide} wins by resignation`
      };
      room.status = 'finished';
      room.timeline = [...room.timeline, cloneSnapshot(room.match) as RuntimeSnapshot];
      finalizeRoomArchive(room);
      return cloneRoom(room);
    },
    createPracticeSession(input: Omit<PracticeSession, 'practiceGameId' | 'initialSnapshot' | 'currentSnapshot' | 'moveHistory'>, user: AuthUser): PracticeSession {
      const initialSnapshot = createInitialSnapshot(input.gameType);
      const runtime = createPracticeRuntime(input.gameType);
      const { humanSide, aiSide } = deriveSides(input.gameType, input.humanFirst);
      const active: ActivePracticeGame = {
        ...input,
        practiceGameId: randomUUID(),
        initialSnapshot,
        currentSnapshot: cloneSnapshot(runtime.snapshot()) as PracticeSession['currentSnapshot'],
        moveHistory: [],
        runtime,
        humanSide,
        aiSide,
        userId: user.userId,
        timeline: [cloneSnapshot(runtime.snapshot()) as RuntimeSnapshot],
        eventPayloads: [],
        status: 'active'
      };
      if (currentTurnOf(active.currentSnapshot as RuntimeSnapshot) === aiSide) {
        maybeApplyAiTurn(active);
      }
      practiceGames.unshift(active);
      persistPractice(active);
      return clonePractice(active);
    },
    getPracticeSession(practiceGameId: string, userId?: string): PracticeSession | undefined {
      const session = practiceGames.find((entry) => entry.practiceGameId === practiceGameId);
      if (!session) return undefined;
      if (userId && session.userId !== userId) {
        return undefined;
      }
      return clonePractice(session);
    },
    applyPracticeMove(practiceGameId: string, payload: Record<string, unknown> | string, user: AuthUser): PracticeSession | undefined {
      const session = practiceGames.find((entry) => entry.practiceGameId === practiceGameId);
      if (!session) return undefined;
      if (session.userId !== user.userId) {
        throw new Error('forbidden practice move');
      }
      const movePayload = typeof payload === 'string' ? { move: payload } : payload;
      const snapshot = session.runtime.applyMove(movePayload);
      session.eventPayloads.push(movePayload);
      pushPracticeHistory(session, 'player', snapshot as RuntimeSnapshot, movePayload);
      session.currentSnapshot = cloneSnapshot(snapshot) as PracticeSession['currentSnapshot'];
      session.timeline = [...session.timeline, cloneSnapshot(snapshot) as RuntimeSnapshot];
      if ('status' in snapshot && snapshot.status === 'active') {
        maybeApplyAiTurn(session);
        persistPractice(session);
      } else {
        session.status = 'finished';
        persistPractice(session);
        finalizePracticeArchive(session);
      }
      return clonePractice(session);
    },
    finishPracticeSession(practiceGameId: string, user: AuthUser): PracticeSession | undefined {
      const session = practiceGames.find((entry) => entry.practiceGameId === practiceGameId);
      if (!session) return undefined;
      if (session.userId !== user.userId) {
        throw new Error('forbidden practice finish');
      }
      finalizePracticeArchive(session);
      return clonePractice(session);
    },
    getProfile(userId: string): ProfileSummary | undefined {
      return repository?.getProfile(userId);
    },
    listHistory(userId: string, filters?: { gameType?: GameType; sourceType?: ArchiveSourceType }): ArchiveSummary[] {
      return repository?.listArchivesForUser(userId, filters) ?? [];
    },
    getReview(userId: string, archiveId: string): ReviewPayload | undefined {
      return repository?.getReviewForUser(userId, archiveId);
    },
    listLeaderboard(gameType: GameType): LeaderboardEntry[] {
      return repository?.listLeaderboard(gameType) ?? [];
    }
  };
}
