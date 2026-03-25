import type { GameType, MatchSnapshot } from '@qiju/core';

import { ChessLiveBoard } from '../practice/chess-live-board';

function XiangqiLiveBoard({ board, selected, onSelect }: { board: string[][]; selected?: { x: number; y: number } | null; onSelect: (x: number, y: number) => void }) {
  return (
    <div className="xiangqiLiveBoard" data-testid="xiangqi-live-board">
      {board.map((row, y) => (
        <div key={`row-${y}`} className="xiangqiLiveBoard__row">
          {row.map((piece, x) => (
            <button
              key={`${x}-${y}`}
              className={`xiangqiLiveBoard__cell ${selected?.x === x && selected?.y === y ? 'is-selected' : ''}`}
              type="button"
              onClick={() => onSelect(x, y)}
              aria-label={`棋位 ${x},${y}`}
            >
              <span className="xiangqiLiveBoard__piece">{piece || ''}</span>
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}

function GomokuLiveBoard({ board, onMove }: { board: string[][]; onMove: (row: number, col: number) => void }) {
  return (
    <div className="gomokuLiveBoard" data-testid="gomoku-live-board">
      {board.map((row, rowIndex) => (
        <div key={`gomoku-row-${rowIndex}`} className="gomokuLiveBoard__row">
          {row.map((cell, colIndex) => (
            <button
              key={`${rowIndex}-${colIndex}`}
              className="gomokuLiveBoard__cell"
              type="button"
              onClick={() => onMove(rowIndex, colIndex)}
              aria-label={`落子 ${rowIndex},${colIndex}`}
            >
              <span className={`gomokuLiveBoard__stone ${cell ? `is-${cell.toLowerCase()}` : ''}`}>{cell ? '●' : ''}</span>
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}

function GoLiveBoard({ board, onMove }: { board: string[][]; onMove: (row: number, col: number) => void }) {
  return (
    <div className="goLiveBoard" data-testid="go-live-board">
      {board.map((row, rowIndex) => (
        <div key={`go-row-${rowIndex}`} className="goLiveBoard__row">
          {row.map((cell, colIndex) => (
            <button
              key={`${rowIndex}-${colIndex}`}
              className="goLiveBoard__cell"
              type="button"
              onClick={() => onMove(rowIndex, colIndex)}
              aria-label={`落子 ${rowIndex},${colIndex}`}
            >
              <span className={`goLiveBoard__stone ${cell ? `is-${cell.toLowerCase()}` : ''}`}>{cell ? '●' : ''}</span>
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}

export function OnlineMatchBoard({
  match,
  selected,
  onSelectXiangqi,
  onMoveGomoku,
  onMoveGo
}: {
  match: MatchSnapshot;
  selected?: { x: number; y: number } | null;
  onSelectXiangqi: (x: number, y: number) => void;
  onMoveGomoku: (row: number, col: number) => void;
  onMoveGo: (row: number, col: number) => void;
}) {
  if (match.gameType === 'XIANGQI') {
    return <XiangqiLiveBoard board={match.board} selected={selected} onSelect={onSelectXiangqi} />;
  }
  if (match.gameType === 'GOMOKU') {
    return <GomokuLiveBoard board={match.board} onMove={onMoveGomoku} />;
  }
  if (match.gameType === 'GO') {
    return <GoLiveBoard board={match.board} onMove={onMoveGo} />;
  }

  return (
    <div data-testid="chess-online-board">
      <ChessLiveBoard
        snapshot={{
          gameType: 'CHESS',
          fen: match.fen || 'start',
          turn: match.currentTurn === 'WHITE' ? 'white' : 'black',
          legalMoves: match.legalMoves ?? [],
          status: match.status === 'finished' && match.resultText === 'checkmate' ? 'checkmate' : match.status === 'finished' ? 'draw' : 'active'
        }}
      />
    </div>
  );
}
