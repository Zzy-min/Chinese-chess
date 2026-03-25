import type { ChessSnapshot } from '@qiju/core';

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR';
const PIECES: Record<string, string> = {
  p: '♟',
  r: '♜',
  n: '♞',
  b: '♝',
  q: '♛',
  k: '♚',
  P: '♙',
  R: '♖',
  N: '♘',
  B: '♗',
  Q: '♕',
  K: '♔'
};

function expandFen(fen: string) {
  const boardFen = fen === 'start' ? START_FEN : fen.split(' ')[0];
  return boardFen.split('/').map((rank) => {
    const squares: string[] = [];
    for (const char of rank) {
      if (/\d/.test(char)) {
        squares.push(...Array.from({ length: Number(char) }, () => ''));
      } else {
        squares.push(char);
      }
    }
    return squares;
  });
}

export function ChessLiveBoard({ snapshot }: { snapshot: ChessSnapshot }) {
  const board = expandFen(snapshot.fen);

  return (
    <div className="chessLiveBoard" data-testid="chess-live-board">
      <div className="chessLiveBoard__files">
        {FILES.map((file) => (
          <span key={file}>{file}</span>
        ))}
      </div>
      <div className="chessLiveBoard__grid">
        {board.map((rank, rankIndex) => (
          <div key={`rank-${rankIndex}`} className="chessLiveBoard__row">
            <span className="chessLiveBoard__rank">{8 - rankIndex}</span>
            {rank.map((square, fileIndex) => {
              const squareName = `${FILES[fileIndex]}${8 - rankIndex}`;
              return (
                <span
                  key={squareName}
                  className={`chessLiveBoard__square ${(rankIndex + fileIndex) % 2 === 0 ? 'is-light' : 'is-dark'}`}
                  title={squareName}
                >
                  <span className="chessLiveBoard__piece">{square ? PIECES[square] : ''}</span>
                </span>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
