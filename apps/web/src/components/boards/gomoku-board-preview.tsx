export function GomokuBoardPreview() {
  return (
    <div className="boardPreview boardPreview--gomoku" aria-label="五子棋棋盘预览">
      <div className="stoneGrid stoneGrid--gomoku">
        <span className="stone stone--dark" style={{ left: '26%', top: '22%' }} />
        <span className="stone stone--light" style={{ left: '38%', top: '33%' }} />
        <span className="stone stone--dark" style={{ left: '50%', top: '44%' }} />
        <span className="stone stone--light" style={{ left: '62%', top: '55%' }} />
      </div>
    </div>
  );
}
