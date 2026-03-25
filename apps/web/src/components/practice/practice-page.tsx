'use client';

import type { Difficulty, GameType, PracticeSession } from '@qiju/core';
import { gameCatalog } from '@qiju/core';
import { useState, useTransition } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';

type PracticePageProps = {
  apiBase: string;
};

export function PracticePage({ apiBase }: PracticePageProps) {
  const [gameType, setGameType] = useState<GameType>('XIANGQI');
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');
  const [humanFirst, setHumanFirst] = useState(true);
  const [status, setStatus] = useState('选择棋种和难度后开始练习。登录后会自动保存到历史和回顾。');
  const [session, setSession] = useState<PracticeSession | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleStart() {
    startTransition(() => {
      setStatus('正在创建练习局...');
      fetchApiJson<PracticeSession>(apiBase, '/api/practice-games', {
        method: 'POST',
        body: JSON.stringify({
          gameType,
          difficulty,
          humanFirst
        })
      })
        .then((nextSession) => {
          setSession(nextSession);
          setStatus(`练习局已创建：${nextSession.practiceGameId}`);
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 401) {
            setStatus('请先登录后再开始练习。');
            return;
          }
          setStatus('练习局创建失败，请稍后再试。');
        });
    });
  }

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Practice</div>
          <h1>AI 练习入口</h1>
          <p>四棋练习都走服务端会话状态，结束后会直接进入归档、个人页和回顾链路。</p>
        </div>
        <div className="subpageHero__status">{status}</div>
      </section>

      <section className="actionGrid">
        <article className="surfaceCard">
          <h2>开始一局</h2>
          <label className="fieldLabel">
            棋种
            <select aria-label="棋种" value={gameType} onChange={(event) => setGameType(event.target.value as GameType)}>
              {gameCatalog.map((entry) => (
                <option key={entry.type} value={entry.type}>
                  {entry.label}
                </option>
              ))}
            </select>
          </label>
          <label className="fieldLabel">
            难度
            <select aria-label="难度" value={difficulty} onChange={(event) => setDifficulty(event.target.value as Difficulty)}>
              <option value="EASY">EASY</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HARD">HARD</option>
            </select>
          </label>
          <label className="toggleRow">
            <input
              aria-label="玩家先手"
              checked={humanFirst}
              type="checkbox"
              onChange={(event) => setHumanFirst(event.target.checked)}
            />
            玩家先手
          </label>
          <button className="primaryAction primaryAction--full" type="button" onClick={handleStart} disabled={isPending}>
            开始练习
          </button>
        </article>

        <article className="surfaceCard">
          <h2>当前练习局</h2>
          {session ? (
            <div className="sessionSummary">
              <strong>{session.practiceGameId}</strong>
              <div>{session.gameType} · {session.difficulty} · {session.humanFirst ? '玩家先手' : 'AI 先手'}</div>
              <div>起始局面：{session.initialSnapshot.fen ?? session.initialSnapshot.notation}</div>
              <a className="secondaryAction secondaryAction--inline" href={`/practice/${session.practiceGameId}`}>进入练习局 {session.practiceGameId}</a>
            </div>
          ) : (
            <p className="emptyHint">还没有创建练习局。</p>
          )}
        </article>
      </section>
    </div>
  );
}
