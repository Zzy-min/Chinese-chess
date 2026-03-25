import type { GameCatalogEntry } from './types';

export const gameCatalog: GameCatalogEntry[] = [
  {
    type: 'XIANGQI',
    label: '中国象棋',
    shortDescription: '在线对局、残局训练与局后回顾',
    supportsLearning: true,
    supportsReview: true
  },
  {
    type: 'GOMOKU',
    label: '五子棋',
    shortDescription: '支持禁手规则、练习和复盘',
    supportsLearning: true,
    supportsReview: true
  },
  {
    type: 'GO',
    label: '围棋',
    shortDescription: '围棋 AI 练习、题库与关键节点分析',
    supportsLearning: true,
    supportsReview: true
  },
  {
    type: 'CHESS',
    label: '国际象棋',
    shortDescription: '标准棋规、AI 练习与 PGN 回放',
    supportsLearning: true,
    supportsReview: true
  }
];
