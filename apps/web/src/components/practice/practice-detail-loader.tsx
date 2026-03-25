'use client';

import type { PracticeSession } from '@qiju/core';
import { useEffect, useState } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';
import { PracticeDetailPage } from './practice-detail-page';

export function PracticeDetailLoader({ apiBase, practiceGameId }: { apiBase: string; practiceGameId: string }) {
  const [session, setSession] = useState<PracticeSession | null>(null);
  const [status, setStatus] = useState('练习局加载中...');

  useEffect(() => {
    let active = true;
    fetchApiJson<PracticeSession>(apiBase, `/api/practice-games/${practiceGameId}`, { method: 'GET', headers: {} })
      .then((payload) => {
        if (!active) {
          return;
        }
        setSession(payload);
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        if (error instanceof ApiError && error.status === 401) {
          setStatus('请先登录后查看练习局。');
          return;
        }
        setStatus('练习局不存在或暂时不可用。');
      });

    return () => {
      active = false;
    };
  }, [apiBase, practiceGameId]);

  if (!session) {
    return (
      <div className="pageShell pageShell--subpage">
        <section className="subpageHero">
          <div>
            <div className="subpageHero__eyebrow">Practice Session</div>
            <h1>{status}</h1>
          </div>
        </section>
      </div>
    );
  }

  return <PracticeDetailPage apiBase={apiBase} session={session} />;
}
