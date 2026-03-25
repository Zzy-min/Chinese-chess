import type { Metadata } from 'next';
import type { ReactNode } from 'react';

import { SiteNav } from '../components/shell/site-nav';
import { getApiBase } from '../lib/api-base';

import './globals.css';

export const metadata: Metadata = {
  title: '轻棋局 2.0',
  description: '四棋统一在线、AI 练习、学习与回顾平台'
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <SiteNav apiBase={getApiBase()} />
        {children}
      </body>
    </html>
  );
}
