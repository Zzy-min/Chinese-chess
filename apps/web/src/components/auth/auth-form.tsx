'use client';

import { useState, useTransition } from 'react';

import { ApiError, fetchApiJson } from '../../lib/api-base';

export function AuthForm({ apiBase, mode }: { apiBase: string; mode: 'login' | 'register' }) {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [status, setStatus] = useState(mode === 'login' ? '登录后可创建房间、开始练习并查看完整历史。' : '创建站内账号后即可保存对局和练习记录。');
  const [isPending, startTransition] = useTransition();

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    startTransition(() => {
      setStatus(mode === 'login' ? '正在登录...' : '正在创建账号...');
      fetchApiJson<{ user: { displayName: string } }>(apiBase, mode === 'login' ? '/api/auth/login' : '/api/auth/register', {
        method: 'POST',
        body: JSON.stringify(mode === 'login' ? { email, password } : { email, password, displayName })
      })
        .then(() => {
          window.location.href = '/me';
        })
        .catch((error) => {
          if (error instanceof ApiError) {
            setStatus(error.message);
            return;
          }
          setStatus('认证失败，请稍后再试。');
        });
    });
  }

  return (
    <div className="pageShell pageShell--subpage">
      <section className="subpageHero">
        <div>
          <div className="subpageHero__eyebrow">{mode === 'login' ? 'Login' : 'Register'}</div>
          <h1>{mode === 'login' ? '登录轻棋局' : '创建轻棋局账号'}</h1>
          <p>账号会把你的在线对局、AI 练习、历史归档、排行榜和回顾统一到一个身份下。</p>
        </div>
        <div className="subpageHero__status">{status}</div>
      </section>

      <section className="authCard">
        <form className="authForm" onSubmit={handleSubmit}>
          {mode === 'register' ? (
            <label className="fieldLabel">
              昵称
              <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="2-24 个字符" />
            </label>
          ) : null}
          <label className="fieldLabel">
            邮箱
            <input value={email} type="email" onChange={(event) => setEmail(event.target.value)} placeholder="name@example.com" />
          </label>
          <label className="fieldLabel">
            密码
            <input value={password} type="password" onChange={(event) => setPassword(event.target.value)} placeholder="至少 8 位" />
          </label>
          <button className="primaryAction primaryAction--full" type="submit" disabled={isPending || !email || !password || (mode === 'register' && !displayName)}>
            {mode === 'login' ? '登录' : '注册'}
          </button>
        </form>
      </section>
    </div>
  );
}
