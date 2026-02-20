# Round 5 Open-Source Benchmark (Speed + Quality + UX)

## GitHub (high-value references)

- https://github.com/official-pikafish/Pikafish
  - High-performance Xiangqi engine ideas: aggressive pruning, practical time management.
- https://github.com/fairy-stockfish/Fairy-Stockfish
  - Mature search framework ideas for move ordering and robustness.
- https://github.com/xqbase/eleeye
  - Classic Xiangqi alpha-beta implementation details.
- https://github.com/xqbase/xqwlight
  - Lightweight Xiangqi engine structure and compatibility approach.
- https://github.com/chengstone/cchess-zero
  - AlphaZero style training/inference workflow reference.
- https://github.com/bupticybee/icyChessZero
  - Reinforcement-learning based Chinese chess implementation ideas.
- https://github.com/NeymarL/ChineseChess-AlphaZero
  - Existing baseline used in earlier upgrade rounds.

## Gitee (accessible references this round)

- https://gitee.com/mirrors/eleeye
- https://gitee.com/yyMortal/chinese-chess
- https://gitee.com/xiao_Ying/chinesechess
- https://gitee.com/chengstone/cchess-zero

## Mapping to this round's implementation focus

- Speed path:
  - Ultra-fast lane for easy/medium under high branching or time pressure.
  - Reduced quiescence budget in fast mode for stable one-move responsiveness.
  - Faster web polling and larger click hit area in web UI.
- Quality path:
  - Controlled check extension (medium/hard, bounded conditions) for tactical depth.
- UX path:
  - Recent move signal pulse for better move-sequence readability.
