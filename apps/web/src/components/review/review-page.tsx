'use client';

import type { ChessSnapshot, MatchSnapshot, ReviewPayload, RuntimeSnapshot } from '@qiju/core';
import { useEffect, useMemo, useState } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';
import { OnlineMatchBoard } from '../play/online-match-board';
import { ChessLiveBoard } from '../practice/chess-live-board';

function isMatchSnapshot(snapshot: RuntimeSnapshot): snapshot is MatchSnapshot {
  return 'board' in snapshot && 'currentTurn' in snapshot;
}

function isChessSnapshot(snapshot: RuntimeSnapshot): snapshot is ChessSnapshot {
  return 'fen' in snapshot && 'legalMoves' in snapshot && !('board' in snapshot);
}

export function ReviewPage({ apiBase, archiveId }: { apiBase: string; archiveId: string }) {
  const [review, setReview] = useState<ReviewPayload | null>(null);
  const [step, setStep] = useState(0);
  const [status, setStatus] = useState('正在加载回顾页...');

  useEffect(() => {
    let active = true;
    fetchApiJson<ReviewPayload>(apiBase, `/api/reviews/${archiveId}`, { method: 'GET', headers: {} })
      .then((payload) => {
        if (!active) return;
        setReview(payload);
        setStep(Math.max(0, payload.snapshots.length - 1));
        setStatus('以下回顾数据来自归档后的持久化快照。');
      })
      .catch((error) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 401) {
          setStatus('请先登录后查看回顾页。');
          return;
        }
        setStatus('回顾页暂时不可用。');
      });
    return () => {
      active = false;
    };
  }, [apiBase, archiveId]);

  const snapshot = useMemo(() => review?.snapshots[step], [review, step]);

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Review</div>
          <h1>{review ? `${review.archive.gameType} 回顾` : '对局回顾'}</h1>
          <p>首版先聚焦高质量回放、结果摘要和关键节点标签，不伪装成深度引擎分析。</p>
        </div>
        <div className="subpageHero__status">{status}</div>
      </section>

      {review && snapshot ? (
        <section className="actionGrid">
          <article className="surfaceCard">
            {isMatchSnapshot(snapshot) ? (
              <OnlineMatchBoard
                match={snapshot}
                onSelectXiangqi={() => undefined}
                onMoveGomoku={() => undefined}
                onMoveGo={() => undefined}
              />
            ) : null}
            {isChessSnapshot(snapshot) ? <ChessLiveBoard snapshot={snapshot} /> : null}
            <div className="moveList">
              <button className="secondaryAction secondaryAction--inline" type="button" onClick={() => setStep((current) => Math.max(0, current - 1))} disabled={step === 0}>上一步</button>
              <span className="surfaceChip">第 {step + 1} / {review.snapshots.length} 步</span>
              <button className="secondaryAction secondaryAction--inline" type="button" onClick={() => setStep((current) => Math.min(review.snapshots.length - 1, current + 1))} disabled={step === review.snapshots.length - 1}>下一步</button>
            </div>
          </article>
          <article className="surfaceCard">
            <h2>回顾摘要</h2>
            <div className="detailList">
              <div><strong>结果</strong><span>{review.archive.resultText}</span></div>
              <div><strong>来源</strong><span>{review.archive.sourceType}</span></div>
              <div><strong>总步数</strong><span>{review.archive.moveCount}</span></div>
              <div><strong>创建时间</strong><span>{new Date(review.archive.createdAt).toLocaleString('zh-CN')}</span></div>
            </div>
            <div className="moveList">
              {review.archive.reviewTags.map((tag) => (
                <span key={tag} className="surfaceChip">{tag}</span>
              ))}
            </div>
            <div className="historyList">
              {review.archive.participants.map((participant, index) => (
                <div key={`${participant.displayName}-${index}`} className="historyList__item">
                  <strong>{participant.displayName}</strong>
                  <span>{participant.seat} · {participant.outcome}</span>
                </div>
              ))}
            </div>
          </article>
        </section>
      ) : (
        <section className="surfaceCard"><a className="primaryAction" href="/history">返回历史页</a></section>
      )}
    </div>
  );
}
