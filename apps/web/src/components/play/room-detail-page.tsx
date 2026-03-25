'use client';

import type { MatchMoveRecord, RoomSeatName, RoomSummary } from '@qiju/core';
import { useEffect, useMemo, useState, useTransition } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';
import { GameBoardPreview } from '../boards/game-board-preview';
import { OnlineMatchBoard } from './online-match-board';

function statusCopy(room: RoomSummary) {
  if (room.status === 'finished') {
    return room.match?.resultText || '对局已结束。';
  }
  if (room.status === 'playing') {
    return `对局进行中，轮到 ${room.match?.currentTurn || 'UNKNOWN'}。`;
  }
  if (room.canStart) {
    return '双方已准备，可以开始对局。';
  }
  if (room.guest?.joined && room.host?.ready && !room.guest.ready) {
    return '房主已准备，等待访客。';
  }
  if (room.guest?.joined) {
    return '访客已加入，等待双方准备。';
  }
  return '等待对手加入。';
}

function pieceBelongsToTurn(piece: string, turn: string) {
  const redPieces = new Set(['车', '马', '相', '士', '帅', '炮', '兵']);
  const blackPieces = new Set(['車', '馬', '象', '仕', '将', '砲', '卒']);
  return turn === 'RED' ? redPieces.has(piece) : blackPieces.has(piece);
}

export function RoomDetailPage({ apiBase, room, syncHint }: { apiBase?: string; room: RoomSummary; syncHint?: string }) {
  const [currentRoom, setCurrentRoom] = useState(room);
  const [status, setStatus] = useState(statusCopy(room));
  const [isPending, startTransition] = useTransition();
  const [selected, setSelected] = useState<{ x: number; y: number } | null>(null);

  const moveHistory = useMemo<MatchMoveRecord[]>(() => currentRoom.match?.moveHistory ?? [], [currentRoom.match]);

  useEffect(() => {
    setCurrentRoom(room);
    setStatus(statusCopy(room));
    setSelected(null);
  }, [room]);

  function runAction(path: string, body?: object) {
    if (!apiBase) {
      return;
    }

    startTransition(() => {
      fetchApiJson<RoomSummary>(apiBase, path, {
        method: 'POST',
        ...(body ? { body: JSON.stringify(body) } : { headers: {} })
      })
        .then((nextRoom) => {
          setCurrentRoom(nextRoom);
          setSelected(null);
          setStatus(statusCopy(nextRoom));
        })
        .catch((error) => {
          if (error instanceof ApiError && error.status === 401) {
            setStatus('请先登录后执行房间操作。');
            return;
          }
          setStatus(error instanceof Error ? error.message : '房间操作失败，请稍后再试。');
        });
    });
  }

  function handleReady(seat: RoomSeatName) {
    const currentSeat = seat === 'host' ? currentRoom.host : currentRoom.guest;
    runAction(`/api/rooms/${currentRoom.roomId}/ready`, { seat, ready: !currentSeat?.ready });
  }

  function handleXiangqiSelect(x: number, y: number) {
    if (!currentRoom.match || currentRoom.match.gameType !== 'XIANGQI' || currentRoom.status !== 'playing') {
      return;
    }
    const piece = currentRoom.match.board[y]?.[x] ?? '';
    if (!selected) {
      if (piece && pieceBelongsToTurn(piece, currentRoom.match.currentTurn)) {
        setSelected({ x, y });
        setStatus(`已选中 ${piece}，请选择落点。`);
      }
      return;
    }
    if (selected.x === x && selected.y === y) {
      setSelected(null);
      setStatus(statusCopy(currentRoom));
      return;
    }
    runAction(`/api/rooms/${currentRoom.roomId}/move`, { fromX: selected.x, fromY: selected.y, toX: x, toY: y });
  }

  function handleGomokuMove(row: number, col: number) {
    if (!currentRoom.match || currentRoom.match.gameType !== 'GOMOKU' || currentRoom.status !== 'playing') {
      return;
    }
    if (currentRoom.match.board[row]?.[col]) {
      return;
    }
    runAction(`/api/rooms/${currentRoom.roomId}/move`, { row, col });
  }

  function handleGoMove(row: number, col: number) {
    if (!currentRoom.match || currentRoom.match.gameType !== 'GO' || currentRoom.status !== 'playing') {
      return;
    }
    if (currentRoom.match.board[row]?.[col]) {
      return;
    }
    runAction(`/api/rooms/${currentRoom.roomId}/move`, { row, col });
  }

  function handleChessMove(move: string) {
    if (!currentRoom.match || currentRoom.match.gameType !== 'CHESS' || currentRoom.status !== 'playing') {
      return;
    }
    runAction(`/api/rooms/${currentRoom.roomId}/move`, { move });
  }

  function handleGoPass() {
    if (!currentRoom.match || currentRoom.match.gameType !== 'GO' || currentRoom.status !== 'playing') {
      return;
    }
    runAction(`/api/rooms/${currentRoom.roomId}/move`, { pass: true });
  }

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Room</div>
          <h1>房间 {currentRoom.roomCode}</h1>
          <p>游客可观察房间状态；登录后可以加入、准备、开始、走子，并在结束后进入回顾页。</p>
        </div>
        <div>
          <div className="subpageHero__status">{status}</div>
          {syncHint ? <div className="syncHint">{syncHint}</div> : null}
        </div>
      </section>

      <section className="actionGrid">
        <article className="surfaceCard">
          {currentRoom.match ? (
            <>
              <OnlineMatchBoard
                match={currentRoom.match}
                selected={selected}
                onSelectXiangqi={handleXiangqiSelect}
                onMoveGomoku={handleGomokuMove}
                onMoveGo={handleGoMove}
              />
              <div className="practiceStatus">轮到 {currentRoom.match.currentTurn}</div>
              {currentRoom.match.gameType === 'CHESS' && currentRoom.match.legalMoves?.length ? (
                <div className="moveList">
                  {currentRoom.match.legalMoves.map((move) => (
                    <button key={move} className="surfaceChip surfaceChip--button" type="button" onClick={() => handleChessMove(move)} disabled={isPending || currentRoom.status !== 'playing'}>
                      走子 {move}
                    </button>
                  ))}
                </div>
              ) : null}
              {currentRoom.match.gameType === 'GO' ? (
                <div className="moveList">
                  <button className="surfaceChip surfaceChip--button" type="button" onClick={handleGoPass} disabled={isPending || currentRoom.status !== 'playing'}>
                    停一手
                  </button>
                  <span className="surfaceChip">连续停手 {currentRoom.match.consecutivePasses ?? 0}</span>
                </div>
              ) : null}
            </>
          ) : (
            <GameBoardPreview gameType={currentRoom.gameType} />
          )}
        </article>

        <article className="surfaceCard">
          <h2>房间信息</h2>
          <div className="detailList">
            <div><strong>房间码</strong><span>{currentRoom.roomCode}</span></div>
            <div><strong>棋种</strong><span>{currentRoom.gameType}</span></div>
            <div><strong>时控</strong><span>{currentRoom.timeControl}</span></div>
            <div><strong>状态</strong><span>{currentRoom.status}</span></div>
            <div><strong>可见性</strong><span>{currentRoom.visibility}</span></div>
          </div>

          <div className="seatGrid">
            <div className="seatCard">
              <strong>房主</strong>
              <span>{currentRoom.host?.joined ? currentRoom.host.label : '未加入'}</span>
              <span>{currentRoom.host?.ready ? '已准备' : '未准备'}</span>
              <button className="secondaryAction secondaryAction--full" type="button" onClick={() => handleReady('host')} disabled={isPending || currentRoom.status === 'playing' || currentRoom.status === 'finished'}>
                房主准备
              </button>
            </div>
            <div className="seatCard">
              <strong>访客</strong>
              <span>{currentRoom.guest?.joined ? currentRoom.guest.label : '等待加入'}</span>
              <span>{currentRoom.guest?.ready ? '已准备' : '未准备'}</span>
              <button className="secondaryAction secondaryAction--full" type="button" onClick={() => runAction(`/api/rooms/${currentRoom.roomId}/join`, { seat: 'guest' })} disabled={isPending || currentRoom.guest?.joined || currentRoom.status === 'playing' || currentRoom.status === 'finished'}>
                加入客位
              </button>
              <button className="secondaryAction secondaryAction--full" type="button" onClick={() => handleReady('guest')} disabled={isPending || !currentRoom.guest?.joined || currentRoom.status === 'playing' || currentRoom.status === 'finished'}>
                访客准备
              </button>
            </div>
          </div>

          <div className="moveList">
            <button className="primaryAction primaryAction--full" type="button" onClick={() => runAction(`/api/rooms/${currentRoom.roomId}/start`)} disabled={isPending || !currentRoom.canStart || currentRoom.status === 'playing' || currentRoom.status === 'finished'}>
              开始对局
            </button>
            <button className="secondaryAction secondaryAction--inline" type="button" onClick={() => runAction(`/api/rooms/${currentRoom.roomId}/resign`)} disabled={isPending || currentRoom.status !== 'playing'}>
              认输并归档
            </button>
            {currentRoom.archiveId ? <a className="secondaryAction secondaryAction--inline" href={`/review/${currentRoom.archiveId}`}>打开回顾</a> : null}
          </div>

          <div className="historyList">
            {(moveHistory.length ? moveHistory : [{ side: 'SYSTEM', notation: '尚未落子', payload: {} }]).map((entry, index) => (
              <div key={`${entry.side}-${entry.notation}-${index}`} className="historyList__item">
                <strong>{entry.side}</strong>
                <span>{entry.notation}</span>
              </div>
            ))}
          </div>
        </article>
      </section>
    </div>
  );
}
