'use client';

import type { AuthUser } from '@qiju/core';
import { useEffect, useState, useTransition } from 'react';

import { fetchApiJson, fetchSessionUser } from '../../lib/api-base';

export function SiteNav({ apiBase }: { apiBase: string }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isPending, startTransition] = useTransition();

  useEffect(() => {
    let active = true;
    fetchSessionUser(apiBase).then((nextUser) => {
      if (active) {
        setUser(nextUser);
      }
    });
    return () => {
      active = false;
    };
  }, [apiBase]);

  function handleLogout() {
    startTransition(() => {
      fetchApiJson<{ ok: true }>(apiBase, '/api/auth/logout', {
        method: 'POST'
      })
        .then(() => {
          setUser(null);
          window.location.href = '/';
        })
        .catch(() => undefined);
    });
  }

  return (
    <header className="siteNav">
      <div className="siteNav__inner">
        <a className="siteNav__brand" href="/">轻棋局 2.0</a>
        <nav className="siteNav__links" aria-label="主导航">
          <a href="/play">对局</a>
          <a href="/practice">练习</a>
          <a href="/learn">学习</a>
          <a href="/history">历史</a>
          <a href="/leaderboard">排行榜</a>
          {user ? <a href="/me">{user.displayName}</a> : <a href="/login">登录</a>}
          {user ? (
            <button className="siteNav__logout" type="button" onClick={handleLogout} disabled={isPending}>
              退出
            </button>
          ) : (
            <a href="/register">注册</a>
          )}
        </nav>
      </div>
    </header>
  );
}
