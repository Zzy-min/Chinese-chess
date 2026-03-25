const files = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
const ranks = ['8', '7', '6', '5', '4', '3', '2', '1'];

export function ChessBoardPreview() {
  return (
    <div className="boardPreview boardPreview--chess" data-testid="chess-board-preview">
      <div className="chessBoard" aria-label="国际象棋棋盘预览">
        <div className="chessBoard__files">
          {files.map((file) => (
            <span key={file}>{file}</span>
          ))}
        </div>
        <div className="chessBoard__grid">
          {ranks.map((rank) => (
            <div key={rank} className="chessBoard__row">
              <span className="chessBoard__rank">{rank}</span>
              {files.map((file, index) => (
                <span
                  key={`${file}${rank}`}
                  className={`chessBoard__square ${(Number(rank) + index) % 2 === 0 ? 'is-dark' : 'is-light'}`}
                >
                  {file === 'e' && rank === '8' ? '♚' : ''}
                  {file === 'd' && rank === '1' ? '♕' : ''}
                  {file === 'f' && rank === '3' ? '♘' : ''}
                </span>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
