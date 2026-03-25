import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { PlayPage } from './play-page';

const fetchMock = vi.fn();

describe('PlayPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('loads lobby rooms from the API', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        rooms: [
          {
            roomId: 'room-1',
            roomCode: 'ABCD1234',
            gameType: 'XIANGQI',
            timeControl: '10+0',
            visibility: 'PUBLIC',
            status: 'waiting'
          }
        ]
      })
    });

    render(<PlayPage apiBase="http://example.test" />);

    expect(await screen.findByText('ABCD1234')).toBeInTheDocument();
    expect(screen.getByText('XIANGQI')).toBeInTheDocument();
  });

  it('submits room creation and shows the returned room code', async () => {
    fetchMock
      .mockResolvedValueOnce({ ok: true, json: async () => ({ rooms: [] }) })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          roomId: 'room-2',
          roomCode: 'ZXCV7788',
          gameType: 'GOMOKU',
          timeControl: '10+5',
          visibility: 'PUBLIC',
          status: 'waiting'
        })
      });

    render(<PlayPage apiBase="http://example.test" />);

    fireEvent.change(await screen.findByLabelText('棋种'), { target: { value: 'GOMOKU' } });
    fireEvent.click(screen.getByRole('button', { name: '创建房间' }));

    await waitFor(() => {
      expect(screen.getByText('ZXCV7788')).toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: '进入房间 ZXCV7788' })).toHaveAttribute('href', '/play/room-2');
  });
});
