import type { BoardTheme, GameType } from './types';

export const boardThemes: Record<GameType, BoardTheme> = {
  XIANGQI: {
    type: 'XIANGQI',
    surfacePattern: 'river-grid',
    coordinateStyle: 'hanzi-ranks',
    pieceStyle: 'inscribed-discs',
    accentColor: '#b45309'
  },
  GOMOKU: {
    type: 'GOMOKU',
    surfacePattern: 'wood-grid',
    coordinateStyle: 'edge-dots',
    pieceStyle: 'flat-stones',
    accentColor: '#2563eb'
  },
  GO: {
    type: 'GO',
    surfacePattern: 'hoshi-grid',
    coordinateStyle: 'corner-dots',
    pieceStyle: 'shell-stones',
    accentColor: '#0f766e'
  },
  CHESS: {
    type: 'CHESS',
    surfacePattern: 'checkered',
    coordinateStyle: 'algebraic',
    pieceStyle: 'figurines',
    accentColor: '#1d4ed8'
  }
};
