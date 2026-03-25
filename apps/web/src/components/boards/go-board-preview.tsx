export function GoBoardPreview() {
  return (
    <div className="boardPreview boardPreview--go" aria-label="围棋棋盘预览">
      <div className="stoneGrid stoneGrid--go">
        <span className="stone stone--dark" style={{ left: '18%', top: '18%' }} />
        <span className="stone stone--light" style={{ left: '52%', top: '18%' }} />
        <span className="stone stone--dark" style={{ left: '34%', top: '52%' }} />
        <span className="stone stone--light" style={{ left: '68%', top: '68%' }} />
      </div>
    </div>
  );
}
