import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { HomePage } from './home-page';

describe('HomePage', () => {
  it('shows all four target games', () => {
    render(<HomePage showCapabilities={false} />);

    expect(screen.getByText('中国象棋')).toBeInTheDocument();
    expect(screen.getByText('五子棋')).toBeInTheDocument();
    expect(screen.getByText('围棋')).toBeInTheDocument();
    expect(screen.getByText('国际象棋')).toBeInTheDocument();
  });

  it('renders distinct xiangqi and chess board semantics', () => {
    render(<HomePage showCapabilities={false} />);

    expect(screen.getByTestId('xiangqi-board-preview')).toHaveTextContent('楚河');
    expect(screen.getByTestId('xiangqi-board-preview')).toHaveTextContent('将');
    expect(screen.getByTestId('chess-board-preview')).toHaveTextContent('a');
    expect(screen.getByTestId('chess-board-preview')).toHaveTextContent('♚');
  });
});
