import { gameCatalog } from '@qiju/core';

import { CapabilityPanel } from '../status/capability-panel';

export function LearnPage({ showCapabilities = true }: { showCapabilities?: boolean }) {
  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Learn</div>
          <h1>学习中心</h1>
          <p>四棋统一入口仍保留，但请以后端能力矩阵为准：当前学习页主要是结构位和后续内容入口，不冒充全部已完成。</p>
        </div>
      </section>

      {showCapabilities ? <CapabilityPanel /> : null}

      <section className="catalogSection">
        {gameCatalog.map((entry) => (
          <article key={entry.type} className="surfaceCard surfaceCard--compact">
            <span className="catalogCard__badge">{entry.type}</span>
            <h2>{entry.label}</h2>
            <p>{entry.shortDescription}</p>
            <div className="learnGrid">
              <span className="surfaceChip">题库</span>
              <span className="surfaceChip">残局</span>
              <span className="surfaceChip">错题</span>
              <span className="surfaceChip">收藏局面</span>
            </div>
          </article>
        ))}
      </section>
    </div>
  );
}
