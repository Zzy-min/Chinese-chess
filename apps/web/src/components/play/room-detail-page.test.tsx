import type { RoomSummary } from '@qiju/core';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { RoomDetailPage } from './room-detail-page';

const fetchMock = vi.fn();

describe('RoomDetailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('renders room metadata and a xiangqi-specific board preview', () => {
    render(
      <RoomDetailPage
        apiBase="http://example.test"
        room={{
          roomId: 'room-1',
          roomCode: 'ABCD1234',
          gameType: 'XIANGQI',
          timeControl: '10+5',
          visibility: 'PUBLIC',
          status: 'waiting',
          canStart: false,
          host: { label: '房主', joined: true, ready: false },
          guest: { label: '访客', joined: false, ready: false }
        }}
      />
    );

    expect(screen.getByText('ABCD1234')).toBeInTheDocument();
    expect(screen.getByTestId('xiangqi-board-preview')).toBeInTheDocument();
    expect(screen.getByText(/等待对手加入/)).toBeInTheDocument();
  });

  it('lets the guest join and both seats ready up before starting', async () => {
    fetchMock
      .mockResolvedValueOnce({ ok: true, json: async () => ({ roomId: 'room-1', roomCode: 'ABCD1234', gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC', status: 'full', canStart: false, host: { label: '房主', joined: true, ready: false }, guest: { label: '访客', joined: true, ready: false } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ roomId: 'room-1', roomCode: 'ABCD1234', gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC', status: 'full', canStart: false, host: { label: '房主', joined: true, ready: true }, guest: { label: '访客', joined: true, ready: false } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ roomId: 'room-1', roomCode: 'ABCD1234', gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC', status: 'ready', canStart: true, host: { label: '房主', joined: true, ready: true }, guest: { label: '访客', joined: true, ready: true } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ roomId: 'room-1', roomCode: 'ABCD1234', gameType: 'GOMOKU', timeControl: '10+5', visibility: 'PUBLIC', status: 'playing', canStart: false, host: { label: '房主', joined: true, ready: true }, guest: { label: '访客', joined: true, ready: true }, match: { gameType: 'GOMOKU', status: 'active', board: Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => '')), currentTurn: 'BLACK', moveHistory: [] } }) });

    render(
      <RoomDetailPage
        apiBase="http://example.test"
        room={{
          roomId: 'room-1',
          roomCode: 'ABCD1234',
          gameType: 'GOMOKU',
          timeControl: '10+5',
          visibility: 'PUBLIC',
          status: 'waiting',
          canStart: false,
          host: { label: '房主', joined: true, ready: false },
          guest: { label: '访客', joined: false, ready: false }
        }}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: '加入客位' }));
    await waitFor(() => expect(screen.getByText(/访客已加入/)).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '房主准备' }));
    await waitFor(() => expect(screen.getByText(/房主已准备/)).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '访客准备' }));
    await waitFor(() => expect(screen.getByText(/双方已准备/)).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: '开始对局' }));
    await waitFor(() => expect(screen.getByText(/对局进行中/)).toBeInTheDocument());
  });

  it('renders a live gomoku board and applies a move after the room starts', async () => {
    const empty = Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => ''));
    const afterMove = empty.map((row) => [...row]);
    afterMove[7][7] = 'BLACK';

    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        roomId: 'room-1',
        roomCode: 'ABCD1234',
        gameType: 'GOMOKU',
        timeControl: '10+5',
        visibility: 'PUBLIC',
        status: 'playing',
        canStart: false,
        host: { label: '房主', joined: true, ready: true },
        guest: { label: '访客', joined: true, ready: true },
        match: {
          gameType: 'GOMOKU',
          status: 'active',
          board: afterMove,
          currentTurn: 'WHITE',
          moveHistory: [{ side: 'BLACK', notation: 'BLACK 7,7', payload: { row: 7, col: 7 } }]
        }
      })
    });

    render(
      <RoomDetailPage
        apiBase="http://example.test"
        room={{
          roomId: 'room-1',
          roomCode: 'ABCD1234',
          gameType: 'GOMOKU',
          timeControl: '10+5',
          visibility: 'PUBLIC',
          status: 'playing',
          canStart: false,
          host: { label: '房主', joined: true, ready: true },
          guest: { label: '访客', joined: true, ready: true },
          match: {
            gameType: 'GOMOKU',
            status: 'active',
            board: empty,
            currentTurn: 'BLACK',
            moveHistory: []
          }
        }}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: '落子 7,7' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/rooms/room-1/move');
  });

  it('renders legal move controls for online chess', () => {
    render(
      <RoomDetailPage
        apiBase="http://example.test"
        room={{
          roomId: 'room-2',
          roomCode: 'EFGH5678',
          gameType: 'CHESS',
          timeControl: '10+5',
          visibility: 'PUBLIC',
          status: 'playing',
          canStart: false,
          host: { label: '房主', joined: true, ready: true },
          guest: { label: '访客', joined: true, ready: true },
          match: {
            gameType: 'CHESS',
            status: 'active',
            board: Array.from({ length: 8 }, () => Array.from({ length: 8 }, () => '')),
            currentTurn: 'WHITE',
            moveHistory: [],
            legalMoves: ['e4', 'd4'],
            fen: 'start'
          }
        }}
      />
    );

    expect(screen.getByTestId('chess-online-board')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '走子 e4' })).toBeInTheDocument();
  });

  it('renders online go controls including pass', () => {
    render(
      <RoomDetailPage
        apiBase="http://example.test"
        room={{
          roomId: 'room-3',
          roomCode: 'IJKL9012',
          gameType: 'GO',
          timeControl: '10+5',
          visibility: 'PUBLIC',
          status: 'playing',
          canStart: false,
          host: { label: '房主', joined: true, ready: true },
          guest: { label: '访客', joined: true, ready: true },
          match: {
            gameType: 'GO',
            status: 'active',
            board: Array.from({ length: 19 }, () => Array.from({ length: 19 }, () => '')),
            currentTurn: 'BLACK',
            moveHistory: [],
            boardSize: 19,
            consecutivePasses: 0
          }
        }}
      />
    );

    expect(screen.getByTestId('go-live-board')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '停一手' })).toBeInTheDocument();
  });
});
