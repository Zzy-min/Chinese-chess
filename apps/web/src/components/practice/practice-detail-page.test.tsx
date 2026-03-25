import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { PracticeDetailPage } from './practice-detail-page';

const fetchMock = vi.fn();

describe('PracticeDetailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('renders a chess-specific board preview and session metadata', () => {
    render(
      <PracticeDetailPage
        apiBase="http://example.test"
        session={{
          practiceGameId: 'practice-1',
          gameType: 'CHESS',
          difficulty: 'HARD',
          humanFirst: true,
          initialSnapshot: { fen: 'start', notation: 'start' },
          currentSnapshot: { gameType: 'CHESS', fen: 'start', turn: 'white', legalMoves: ['e4', 'd4'], status: 'active' },
          moveHistory: []
        }}
      />
    );

    expect(screen.getByText('practice-1')).toBeInTheDocument();
    expect(screen.getByTestId('chess-live-board')).toBeInTheDocument();
    expect(screen.getByText(/CHESS · HARD/)).toBeInTheDocument();
  });

  it('posts a chess move and refreshes through the practice API', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        practiceGameId: 'practice-1',
        gameType: 'CHESS',
        difficulty: 'HARD',
        humanFirst: true,
        initialSnapshot: { fen: 'start', notation: 'start' },
        currentSnapshot: { gameType: 'CHESS', fen: 'rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2', turn: 'white', legalMoves: ['Nf3'], status: 'active' },
        moveHistory: [{ actor: 'player', move: 'e4' }, { actor: 'ai', move: 'e5' }]
      })
    });

    render(
      <PracticeDetailPage
        apiBase="http://example.test"
        session={{
          practiceGameId: 'practice-1',
          gameType: 'CHESS',
          difficulty: 'HARD',
          humanFirst: true,
          initialSnapshot: { fen: 'start', notation: 'start' },
          currentSnapshot: { gameType: 'CHESS', fen: 'start', turn: 'white', legalMoves: ['e4', 'd4'], status: 'active' },
          moveHistory: []
        }}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: '走子 e4' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/practice-games/practice-1/move');
  });

  it('renders a xiangqi practice board and selects a piece', () => {
    render(
      <PracticeDetailPage
        apiBase="http://example.test"
        session={{
          practiceGameId: 'practice-2',
          gameType: 'XIANGQI',
          difficulty: 'EASY',
          humanFirst: true,
          initialSnapshot: { notation: 'xiangqi:start' },
          currentSnapshot: {
            gameType: 'XIANGQI',
            status: 'active',
            board: Array.from({ length: 10 }, (_, y) => Array.from({ length: 9 }, (_, x) => (y === 7 && x === 1 ? '炮' : ''))),
            currentTurn: 'RED',
            moveHistory: []
          },
          moveHistory: []
        }}
      />
    );

    expect(screen.getByTestId('xiangqi-live-board')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '棋位 1,7' }));
    expect(screen.getByText(/已选中 炮/)).toBeInTheDocument();
  });

  it('renders a gomoku practice board and posts a move', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        practiceGameId: 'practice-3',
        gameType: 'GOMOKU',
        difficulty: 'EASY',
        humanFirst: true,
        initialSnapshot: { notation: 'gomoku:start' },
        currentSnapshot: {
          gameType: 'GOMOKU',
          status: 'active',
          board: Array.from({ length: 15 }, (_, r) => Array.from({ length: 15 }, (_, c) => (r === 7 && c === 7 ? 'BLACK' : ''))),
          currentTurn: 'WHITE',
          moveHistory: [{ side: 'BLACK', notation: 'BLACK 7,7', payload: { row: 7, col: 7 } }]
        },
        moveHistory: [{ actor: 'player', move: '7,7' }, { actor: 'ai', move: '7,8' }]
      })
    });

    render(
      <PracticeDetailPage
        apiBase="http://example.test"
        session={{
          practiceGameId: 'practice-3',
          gameType: 'GOMOKU',
          difficulty: 'EASY',
          humanFirst: true,
          initialSnapshot: { notation: 'gomoku:start' },
          currentSnapshot: {
            gameType: 'GOMOKU',
            status: 'active',
            board: Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => '')),
            currentTurn: 'BLACK',
            moveHistory: []
          },
          moveHistory: []
        }}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: '落子 7,7' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
  });

  it('renders a go practice board and exposes the pass action', () => {
    render(
      <PracticeDetailPage
        apiBase="http://example.test"
        session={{
          practiceGameId: 'practice-4',
          gameType: 'GO',
          difficulty: 'EASY',
          humanFirst: true,
          initialSnapshot: { notation: 'go:start' },
          currentSnapshot: {
            gameType: 'GO',
            status: 'active',
            board: Array.from({ length: 19 }, () => Array.from({ length: 19 }, () => '')),
            currentTurn: 'BLACK',
            moveHistory: [],
            consecutivePasses: 0,
            boardSize: 19
          },
          moveHistory: []
        }}
      />
    );

    expect(screen.getByTestId('go-live-board')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '停一手' })).toBeInTheDocument();
  });
});
