import type { GameType } from '@qiju/core';

import { ChessBoardPreview } from './chess-board-preview';
import { GoBoardPreview } from './go-board-preview';
import { GomokuBoardPreview } from './gomoku-board-preview';
import { XiangqiBoardPreview } from './xiangqi-board-preview';

export function GameBoardPreview({ gameType }: { gameType: GameType }) {
  switch (gameType) {
    case 'XIANGQI':
      return <XiangqiBoardPreview />;
    case 'GOMOKU':
      return <GomokuBoardPreview />;
    case 'GO':
      return <GoBoardPreview />;
    case 'CHESS':
    default:
      return <ChessBoardPreview />;
  }
}
