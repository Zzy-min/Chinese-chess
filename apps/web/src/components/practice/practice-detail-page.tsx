'use client';

import type { ChessSnapshot, MatchSnapshot, PracticeMoveRecord, PracticeSession } from '@qiju/core';
import { useEffect, useMemo, useState, useTransition } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';
import { OnlineMatchBoard } from '../play/online-match-board';
import { ChessLiveBoard } from './chess-live-board';

function pieceBelongsToTurn(piece: string, turn: string) {
  const redPieces = new Set(['车', '马', '相', '士', '帅', '炮', '兵']);
  const blackPieces = new Set(['車', '馬', '象', '仕', '将', '砲', '卒']);
  return turn === 'RED' ? redPieces.has(piece) : blackPieces.has(piece);
}

function isMatchSnapshot(snapshot: PracticeSession['currentSnapshot']): snapshot is MatchSnapshot {
  return Boolean(snapshot && 'board' in snapshot && 'currentTurn' in snapshot);
}

function isChessSnapshot(snapshot: PracticeSession['currentSnapshot']): snapshot is ChessSnapshot {
  return Boolean(snapshot && 'fen' in snapshot && 'legalMoves' in snapshot && !('board' in snapshot));
}

function describeStatus(session: PracticeSession) {
  const snapshot = session.currentSnapshot;
  if (!snapshot) {
    return '练习局尚未初始化。';
  }
  if (session.archiveId) {
    return '练习已归档，可以进入回顾页。';
  }
  if (isMatchSnapshot(snapshot)) {
    if (snapshot.status === 'finished') {
      return snapshot.resultText || '练习结束';
    }
    return `当前行棋：${snapshot.currentTurn}`;
  }
  if (isChessSnapshot(snapshot)) {
    if (snapshot.status !== 'active') {
      return snapshot.status;
    }
    return `当前行棋：${snapshot.turn}`;
  }
  return '练习局已创建。';
}

export function PracticeDetailPage({ apiBase, session }: { apiBase?: string; session: PracticeSession }) {
  const [currentSession, setCurrentSession] = useState(session);
  const [selected, setSelected] = useState<{ x: number; y: number } | null>(null);
  const [status, setStatus] = useState(describeStatus(session));
  const [isPending, startTransition] = useTransition();

  const currentSnapshot = currentSession.currentSnapshot;
  const moveHistory = useMemo<PracticeMoveRecord[]>(() => currentSession.moveHistory ?? [], [currentSession.moveHistory]);

  useEffect(() => {
    setCurrentSession(session);
    setSelected(null);
    setStatus(describeStatus(session));
  }, [session]);

  function runAction(path: string, payload?: Record<string, unknown>) {
    if (!apiBase) {
      return;
    }
    startTransition(() => {
      fetchApiJson<PracticeSession>(apiBase, path, {
        method: 'POST',
        ...(payload ? { body: JSON.stringify(payload) } : { headers: {} })
      })
        .then((nextSession) => {
          setCurrentSession(nextSession);
          setSelected(null);
          setStatus(describeStatus(nextSession));
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 401) {
            setStatus('请先登录后操作练习局。');
            return;
          }
          setStatus(error instanceof Error ? error.message : '练习局操作失败，请稍后再试。');
        });
    });
  }

  function handleXiangqiSelect(x: number, y: number) {
    if (!isMatchSnapshot(currentSnapshot) || currentSnapshot.gameType !== 'XIANGQI') {
      return;
    }
    const piece = currentSnapshot.board[y]?.[x] ?? '';
    if (!selected) {
      if (piece && pieceBelongsToTurn(piece, currentSnapshot.currentTurn)) {
        setSelected({ x, y });
        setStatus(`已选中 ${piece}，请选择落点。`);
      }
      return;
    }
    if (selected.x === x && selected.y === y) {
      setSelected(null);
      setStatus(describeStatus(currentSession));
      return;
    }
    runAction(`/api/practice-games/${currentSession.practiceGameId}/move`, { fromX: selected.x, fromY: selected.y, toX: x, toY: y });
  }

  function handleGomokuMove(row: number, col: number) {
    if (!isMatchSnapshot(currentSnapshot) || currentSnapshot.gameType !== 'GOMOKU') {
      return;
    }
    if (currentSnapshot.board[row]?.[col]) {
      return;
    }
    runAction(`/api/practice-games/${currentSession.practiceGameId}/move`, { row, col });
  }

  function handleGoMove(row: number, col: number) {
    if (!isMatchSnapshot(currentSnapshot) || currentSnapshot.gameType !== 'GO') {
      return;
    }
    if (currentSnapshot.board[row]?.[col]) {
      return;
    }
    runAction(`/api/practice-games/${currentSession.practiceGameId}/move`, { row, col });
  }

  function handleGoPass() {
    if (!isMatchSnapshot(currentSnapshot) || currentSnapshot.gameType !== 'GO') {
      return;
    }
    runAction(`/api/practice-games/${currentSession.practiceGameId}/move`, { pass: true });
  }

  function handleChessMove(move: string) {
    if (!isChessSnapshot(currentSnapshot)) {
      return;
    }
    runAction(`/api/practice-games/${currentSession.practiceGameId}/move`, { move });
  }

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Practice Session</div>
          <h1>练习局 {currentSession.practiceGameId}</h1>
          <p>当前四棋练习都走服务端会话状态，结束后可直接沉淀到历史和回顾链路。</p>
        </div>
        <div className="subpageHero__status">{currentSession.gameType} · {currentSession.difficulty} · {currentSession.humanFirst ? '玩家先手' : 'AI 先手'}</div>
      </section>

      <section className="actionGrid">
        <article className="surfaceCard">
          {isChessSnapshot(currentSnapshot) ? <ChessLiveBoard snapshot={currentSnapshot} /> : null}
          {isMatchSnapshot(currentSnapshot) ? (
            <OnlineMatchBoard
              match={currentSnapshot}
              selected={selected}
              onSelectXiangqi={handleXiangqiSelect}
              onMoveGomoku={handleGomokuMove}
              onMoveGo={handleGoMove}
            />
          ) : null}

          {isChessSnapshot(currentSnapshot) ? (
            <div className="moveList">
              {currentSnapshot.legalMoves.map((move) => (
                <button key={move} className="surfaceChip surfaceChip--button" type="button" onClick={() => handleChessMove(move)} disabled={isPending || Boolean(currentSession.archiveId)}>
                  走子 {move}
                </button>
              ))}
            </div>
          ) : null}

          {isMatchSnapshot(currentSnapshot) && currentSnapshot.gameType === 'GO' ? (
            <div className="moveList">
              <button className="surfaceChip surfaceChip--button" type="button" onClick={handleGoPass} disabled={isPending || Boolean(currentSession.archiveId)}>
                停一手
              </button>
              <span className="surfaceChip">连续停手 {currentSnapshot.consecutivePasses ?? 0}</span>
            </div>
          ) : null}

          <div className="practiceStatus">{status}</div>
        </article>

        <article className="surfaceCard">
          <h2>练习信息</h2>
          <div className="detailList">
            <div><strong>练习局 ID</strong><span>{currentSession.practiceGameId}</span></div>
            <div><strong>难度</strong><span>{currentSession.difficulty}</span></div>
            <div><strong>先后手</strong><span>{currentSession.humanFirst ? '玩家先手' : 'AI 先手'}</span></div>
            <div><strong>起始局面</strong><span>{currentSession.initialSnapshot.fen ?? currentSession.initialSnapshot.notation}</span></div>
          </div>
          <div className="moveList">
            <button className="secondaryAction secondaryAction--inline" type="button" onClick={() => runAction(`/api/practice-games/${currentSession.practiceGameId}/finish`)} disabled={isPending || Boolean(currentSession.archiveId)}>
              结束并归档
            </button>
            {currentSession.archiveId ? <a className="secondaryAction secondaryAction--inline" href={`/review/${currentSession.archiveId}`}>打开回顾</a> : null}
          </div>
          <div className="historyList">
            {(moveHistory.length ? moveHistory : [{ actor: 'ai', move: '尚未落子' }]).map((entry, index) => (
              <div key={`${entry.actor}-${entry.move}-${index}`} className="historyList__item">
                <strong>{entry.actor === 'player' ? '玩家' : 'AI'}</strong>
                <span>{entry.move}</span>
              </div>
            ))}
          </div>
        </article>
      </section>
    </div>
  );
}
