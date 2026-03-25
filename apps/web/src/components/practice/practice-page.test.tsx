import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { PracticePage } from './practice-page';

const fetchMock = vi.fn();

describe('PracticePage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('creates a practice session and renders the result summary', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        practiceGameId: 'practice-1',
        gameType: 'CHESS',
        difficulty: 'HARD',
        humanFirst: false,
        initialSnapshot: {
          fen: 'start'
        }
      })
    });

    render(<PracticePage apiBase="http://example.test" />);

    fireEvent.change(screen.getByLabelText('难度'), {
      target: { value: 'HARD' }
    });
    fireEvent.click(screen.getByRole('button', { name: '开始练习' }));

    await waitFor(() => {
      expect(screen.getByText('practice-1')).toBeInTheDocument();
    });
    expect(screen.getByText(/起始局面：start/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '进入练习局 practice-1' })).toHaveAttribute('href', '/practice/practice-1');
  });
});
