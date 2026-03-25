'use client';

import type { ArchiveSourceType, ArchiveSummary, GameType } from '@qiju/core';
import { useEffect, useState, useTransition } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';

export function HistoryPage({ apiBase }: { apiBase: string }) {
  const [archives, setArchives] = useState<ArchiveSummary[]>([]);
  const [gameType, setGameType] = useState<'ALL' | GameType>('ALL');
  const [sourceType, setSourceType] = useState<'ALL' | ArchiveSourceType>('ALL');
  const [status, setStatus] = useState('正在加载历史归档...');
  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    startTransition(() => {
      const params = new URLSearchParams();
      if (gameType !== 'ALL') params.set('gameType', gameType);
      if (sourceType !== 'ALL') params.set('sourceType', sourceType);
      fetchApiJson<{ archives: ArchiveSummary[] }>(apiBase, `/api/me/history${params.size ? `?${params.toString()}` : ''}`, { method: 'GET', headers: {} })
        .then((payload) => {
          setArchives(payload.archives);
          setStatus(payload.archives.length ? '以下归档均可直接进入回顾页。' : '当前筛选条件下还没有归档。');
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 401) {
            setStatus('请先登录后查看历史归档。');
            return;
          }
          setStatus('历史归档暂时不可用。');
        });
    });
  }, [apiBase, gameType, sourceType]);

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">History</div>
          <h1>历史对局与练习归档</h1>
          <p>统一查看在线对局和 AI 练习的已结束记录，并进入对应回顾页。</p>
        </div>
        <div className="subpageHero__status">{status}</div>
      </section>

      <section className="surfaceCard">
        <div className="filterBar">
          <label className="fieldLabel">
            棋种
            <select value={gameType} onChange={(event) => setGameType(event.target.value as 'ALL' | GameType)}>
              <option value="ALL">全部</option>
              <option value="XIANGQI">中国象棋</option>
              <option value="GOMOKU">五子棋</option>
              <option value="GO">围棋</option>
              <option value="CHESS">国际象棋</option>
            </select>
          </label>
          <label className="fieldLabel">
            来源
            <select value={sourceType} onChange={(event) => setSourceType(event.target.value as 'ALL' | ArchiveSourceType)}>
              <option value="ALL">全部</option>
              <option value="ONLINE">在线对局</option>
              <option value="PRACTICE">AI 练习</option>
            </select>
          </label>
        </div>
        <div className="historyList">
          {archives.map((archive) => (
            <a key={archive.archiveId} href={`/review/${archive.archiveId}`} className="historyList__item historyList__item--link">
              <strong>{archive.gameType}</strong>
              <span>{archive.sourceType} · {archive.resultText}</span>
            </a>
          ))}
          {!archives.length && !isPending ? <p className="emptyHint">还没有可展示的归档。</p> : null}
        </div>
      </section>
    </div>
  );
}
