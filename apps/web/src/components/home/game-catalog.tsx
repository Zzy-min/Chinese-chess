import { gameCatalog } from '@qiju/core';

import { ChessBoardPreview } from '../boards/chess-board-preview';
import { GoBoardPreview } from '../boards/go-board-preview';
import { GomokuBoardPreview } from '../boards/gomoku-board-preview';
import { XiangqiBoardPreview } from '../boards/xiangqi-board-preview';

function renderPreview(type: string) {
  switch (type) {
    case 'XIANGQI':
      return <XiangqiBoardPreview />;
    case 'GOMOKU':
      return <GomokuBoardPreview />;
    case 'GO':
      return <GoBoardPreview />;
    default:
      return <ChessBoardPreview />;
  }
}

export function GameCatalog() {
  return (
    <section className="catalogSection">
      {gameCatalog.map((game) => (
        <article key={game.type} className="catalogCard">
          <div className="catalogCard__preview">{renderPreview(game.type)}</div>
          <div className="catalogCard__content">
            <span className="catalogCard__badge">{game.type}</span>
            <h2>{game.label}</h2>
            <p>{game.shortDescription}</p>
            <div className="catalogCard__flags">
              <span>{game.supportsLearning ? '学习可用' : '学习待开放'}</span>
              <span>{game.supportsReview ? '回顾可用' : '回顾待开放'}</span>
            </div>
          </div>
        </article>
      ))}
    </section>
  );
}
