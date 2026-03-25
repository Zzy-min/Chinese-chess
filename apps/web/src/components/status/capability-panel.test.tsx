import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CapabilityPanel } from './capability-panel';

const fetchMock = vi.fn();

describe('CapabilityPanel', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it('renders the capability matrix from the API', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        onlineGames: ['XIANGQI', 'GOMOKU'],
        onlineStatus: 'Near-real-time polling sync',
        practiceGames: ['CHESS'],
        learnGames: ['XIANGQI', 'GOMOKU', 'GO', 'CHESS']
      })
    });

    render(<CapabilityPanel apiBase="http://example.test" />);

    await waitFor(() => expect(screen.getByText('XIANGQI / GOMOKU')).toBeInTheDocument());
    expect(screen.getByText('CHESS')).toBeInTheDocument();
    expect(screen.getByText('Near-real-time polling sync')).toBeInTheDocument();
  });
});
