'use client';

import type { CapabilityPayload } from '@qiju/core';
import { useEffect, useState } from 'react';

import { fetchApiJson, getApiBase } from '../../lib/api-base';

export function CapabilityPanel({ apiBase = getApiBase() }: { apiBase?: string }) {
  const [capabilities, setCapabilities] = useState<CapabilityPayload | null>(null);
  const [status, setStatus] = useState('正在加载能力状态...');

  useEffect(() => {
    let active = true;

    fetchApiJson<CapabilityPayload>(apiBase, '/api/site/capabilities', { method: 'GET', headers: {} })
      .then((payload) => {
        if (!active) {
          return;
        }
        setCapabilities(payload);
        setStatus('以下状态与当前 Node/TS 新主站实现一致。');
      })
      .catch(() => {
        if (!active) {
          return;
        }
        setStatus('能力状态暂不可用，请以后端接口为准。');
      });

    return () => {
      active = false;
    };
  }, [apiBase]);

  return (
    <section className="surfaceCard capabilityPanel">
      <div className="capabilityPanel__header">
        <div>
          <div className="subpageHero__eyebrow">Capability</div>
          <h2>当前开放状态</h2>
        </div>
        <div className="capabilityPanel__status">{status}</div>
      </div>

      <div className="capabilityGrid">
        <article className="capabilityCard">
          <strong>在线多人</strong>
          <p>{capabilities ? capabilities.onlineGames.join(' / ') : '加载中'}</p>
        </article>
        <article className="capabilityCard">
          <strong>练习完整开放</strong>
          <p>{capabilities ? capabilities.practiceGames.join(' / ') : '加载中'}</p>
        </article>
        <article className="capabilityCard">
          <strong>认证</strong>
          <p>{capabilities?.authStatus ?? '加载中'}</p>
        </article>
        <article className="capabilityCard">
          <strong>持久化</strong>
          <p>{capabilities?.persistenceStatus ?? '加载中'}</p>
        </article>
        <article className="capabilityCard">
          <strong>回顾</strong>
          <p>{capabilities?.reviewStatus ?? '加载中'}</p>
        </article>
        <article className="capabilityCard">
          <strong>同步方式</strong>
          <p>{capabilities ? capabilities.onlineStatus : '加载中'}</p>
        </article>
      </div>
    </section>
  );
}
