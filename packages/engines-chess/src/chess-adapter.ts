import { Chess } from 'chess.js';

import type { ChessSnapshot } from '@qiju/core';

const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

function toSnapshot(chess: Chess, lastMove?: string): ChessSnapshot {
  const fen = chess.fen() === START_FEN ? 'start' : chess.fen();
  const status = chess.isCheckmate()
    ? 'checkmate'
    : chess.isDraw() || chess.isStalemate()
      ? 'draw'
      : 'active';

  return {
    gameType: 'CHESS',
    fen,
    turn: chess.turn() === 'w' ? 'white' : 'black',
    legalMoves: chess.moves(),
    lastMove,
    status
  };
}

export function createChessAdapter() {
  return {
    createInitialSnapshot(): ChessSnapshot {
      return toSnapshot(new Chess());
    },

    applyMove(snapshot: ChessSnapshot, move: string): ChessSnapshot {
      const chess = snapshot.fen === 'start' ? new Chess() : new Chess(snapshot.fen);
      let result;

      try {
        result = chess.move(move);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        throw new Error(`illegal chess move: ${move}; ${message}`);
      }

      if (!result) {
        throw new Error(`illegal chess move: ${move}`);
      }

      return toSnapshot(chess, move);
    }
  };
}
