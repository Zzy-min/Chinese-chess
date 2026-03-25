export function Hero() {
  return (
    <section className="heroPanel">
      <div className="heroPanel__eyebrow">轻棋局 2.0</div>
      <h1>四种棋类，一个更清爽、更快响应的对局入口</h1>
      <p>
        统一承载中国象棋、五子棋、围棋与国际象棋，强调轻量界面、顺滑互动和清晰的学习回顾链路。
      </p>
      <div className="heroPanel__actions">
        <a href="/play" className="primaryAction">开始在线对局</a>
        <a href="/practice" className="secondaryAction">进入 AI 练习</a>
        <a href="/learn" className="secondaryAction">打开学习中心</a>
      </div>
    </section>
  );
}
