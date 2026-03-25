import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { HomePage } from './home-page';

describe('HomePage layout', () => {
  it('exposes the quick action area for fast entry points', () => {
    render(<HomePage />);

    expect(screen.getByRole('link', { name: '开始在线对局' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '进入 AI 练习' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '打开学习中心' })).toBeInTheDocument();
  });
});
