export function XiangqiBoardPreview() {
  return (
    <div className="boardPreview boardPreview--xiangqi" data-testid="xiangqi-board-preview">
      <div className="boardPreview__topLabels">
        <span>九宫</span>
        <span>中炮开局</span>
      </div>
      <div className="xiangqiGrid" aria-label="中国象棋棋盘预览">
        <div className="xiangqiGrid__palace xiangqiGrid__palace--top" />
        <div className="xiangqiGrid__river">楚河　漢界</div>
        <div className="xiangqiGrid__palace xiangqiGrid__palace--bottom" />
        <span className="piece piece--red" style={{ left: '11%', top: '10%' }}>車</span>
        <span className="piece piece--red" style={{ left: '44%', top: '10%' }}>帥</span>
        <span className="piece piece--red" style={{ left: '77%', top: '10%' }}>馬</span>
        <span className="piece piece--black" style={{ left: '22%', top: '71%' }}>砲</span>
        <span className="piece piece--black" style={{ left: '44%', top: '82%' }}>将</span>
      </div>
    </div>
  );
}
