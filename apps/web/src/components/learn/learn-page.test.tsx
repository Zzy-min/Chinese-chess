import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { LearnPage } from './learn-page';

describe('LearnPage', () => {
  it('shows learning cards for all four games', () => {
    render(<LearnPage showCapabilities={false} />);

    expect(screen.getByText('中国象棋')).toBeInTheDocument();
    expect(screen.getByText('五子棋')).toBeInTheDocument();
    expect(screen.getByText('围棋')).toBeInTheDocument();
    expect(screen.getByText('国际象棋')).toBeInTheDocument();
    expect(screen.getAllByText('题库').length).toBeGreaterThan(0);
  });
});
