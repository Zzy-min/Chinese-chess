'use client';

import type { ProfileSummary } from '@qiju/core';
import { useEffect, useState } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';

export function ProfilePage({ apiBase }: { apiBase: string }) {
  const [profile, setProfile] = useState<ProfileSummary | null>(null);
  const [status, setStatus] = useState('正在加载个人页...');

  useEffect(() => {
    let active = true;
    fetchApiJson<ProfileSummary>(apiBase, '/api/me/profile', { method: 'GET', headers: {} })
      .then((payload) => {
        if (!active) return;
        setProfile(payload);
        setStatus('以下数据已从归档和排行榜统计中汇总。');
      })
      .catch((error) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 401) {
          setStatus('请先登录后查看个人页。');
          return;
        }
        setStatus('个人页暂时不可用。');
      });
    return () => {
      active = false;
    };
  }, [apiBase]);

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">Profile</div>
          <h1>{profile ? `${profile.user.displayName} 的个人页` : '个人页'}</h1>
          <p>查看你的四棋累计战绩、最近归档和后续回顾入口。</p>
        </div>
        <div className="subpageHero__status">{status}</div>
      </section>

      {profile ? (
        <section className="actionGrid">
          <article className="surfaceCard">
            <h2>基础资料</h2>
            <div className="detailList">
              <div><strong>昵称</strong><span>{profile.user.displayName}</span></div>
              <div><strong>邮箱</strong><span>{profile.user.email}</span></div>
              <div><strong>注册时间</strong><span>{new Date(profile.user.createdAt).toLocaleString('zh-CN')}</span></div>
            </div>
          </article>
          <article className="surfaceCard">
            <h2>四棋统计</h2>
            <div className="statGrid">
              {profile.stats.map((stat) => (
                <div key={stat.gameType} className="statCard">
                  <strong>{stat.gameType}</strong>
                  <span>积分 {stat.points}</span>
                  <span>{stat.wins} 胜 / {stat.draws} 和 / {stat.losses} 负</span>
                  <span>练习完成 {stat.practiceCompleted}</span>
                </div>
              ))}
              {!profile.stats.length ? <p className="emptyHint">还没有统计数据，先完成一盘对局或练习。</p> : null}
            </div>
          </article>
          <article className="surfaceCard surfaceCard--wide">
            <h2>最近归档</h2>
            <div className="historyList">
              {profile.recentArchives.map((archive) => (
                <a key={archive.archiveId} href={`/review/${archive.archiveId}`} className="historyList__item historyList__item--link">
                  <strong>{archive.gameType}</strong>
                  <span>{archive.resultText}</span>
                </a>
              ))}
              {!profile.recentArchives.length ? <p className="emptyHint">还没有归档记录。</p> : null}
            </div>
          </article>
        </section>
      ) : (
        <section className="surfaceCard"><a className="primaryAction" href="/login">前往登录</a></section>
      )}
    </div>
  );
}
