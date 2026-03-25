import { describe, expect, it } from 'vitest';

import { createChessAdapter } from './chess-adapter';

describe('createChessAdapter', () => {
  it('returns the standard chess opening position', () => {
    const adapter = createChessAdapter();
    const snapshot = adapter.createInitialSnapshot();

    expect(snapshot.gameType).toBe('CHESS');
    expect(snapshot.fen).toBe('start');
    expect(snapshot.turn).toBe('white');
    expect(snapshot.legalMoves).toContain('e4');
  });

  it('accepts a legal move and changes the turn', () => {
    const adapter = createChessAdapter();
    const next = adapter.applyMove(adapter.createInitialSnapshot(), 'e4');

    expect(next.turn).toBe('black');
    expect(next.lastMove).toBe('e4');
  });

  it('rejects an illegal move', () => {
    const adapter = createChessAdapter();

    expect(() => adapter.applyMove(adapter.createInitialSnapshot(), 'e5')).toThrow(/illegal/i);
  });
});
