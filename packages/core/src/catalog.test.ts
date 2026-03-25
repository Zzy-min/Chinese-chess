import { describe, expect, it } from 'vitest';

import { gameCatalog } from './catalog';
import { boardThemes } from './themes';

describe('gameCatalog', () => {
  it('includes all four target games', () => {
    expect(gameCatalog.map((entry) => entry.type)).toEqual([
      'XIANGQI',
      'GOMOKU',
      'GO',
      'CHESS'
    ]);
  });

  it('provides a learn and review entry point for every game', () => {
    for (const entry of gameCatalog) {
      expect(entry.supportsLearning).toBe(true);
      expect(entry.supportsReview).toBe(true);
    }
  });
});

describe('boardThemes', () => {
  it('distinguishes xiangqi and chess boards visually', () => {
    expect(boardThemes.XIANGQI.surfacePattern).not.toBe(boardThemes.CHESS.surfacePattern);
    expect(boardThemes.XIANGQI.coordinateStyle).not.toBe(boardThemes.CHESS.coordinateStyle);
    expect(boardThemes.XIANGQI.pieceStyle).not.toBe(boardThemes.CHESS.pieceStyle);
  });
});
