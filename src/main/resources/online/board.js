/**
 * 棋盘渲染器 - 状态驱动 + DOM diff + FLIP 动画
 *
 * 借鉴 chessground 的设计模式：
 * - 单一状态对象驱动渲染
 * - 只更新变化的 DOM 节点（diff）
 * - FLIP 动画实现平滑走子效果
 */
class XiangqiBoard {
  constructor(container) {
    this.container = container;
    this.state = { pieces: {}, selected: null, lastMove: null, hint: null, disabled: false };
    this.prevState = null;
    this.cells = [];
    this.onClick = null;
    this._init();
  }

  _init() {
    this.container.innerHTML = '';
    this.container.classList.add('xiangqiBoard');
    for (let r = 0; r < 10; r++) {
      for (let c = 0; c < 9; c++) {
        const cell = document.createElement('button');
        cell.className = 'xiangqiCell';
        cell.dataset.row = r;
        cell.dataset.col = c;
        cell.addEventListener('click', () => this._handleClick(r, c));
        this.cells[r * 9 + c] = cell;
        this.container.appendChild(cell);
      }
    }
  }

  _handleClick(row, col) {
    if (this.state.disabled) return;
    if (this.onClick) this.onClick(row, col);
  }

  /**
   * 更新状态并触发 diff 渲染
   * @param {Object} update - 部分状态更新
   */
  set(update) {
    this.prevState = { ...this.state };
    Object.assign(this.state, update);
    this._render();
  }

  /**
   * 执行走子动画
   */
  animateMove(from, to, piece, duration = 200) {
    const fromCell = this.cells[from.row * 9 + from.col];
    const toCell = this.cells[to.row * 9 + to.col];

    // First: 记录起始位置
    const fromRect = fromCell.getBoundingClientRect();
    const toRect = toCell.getBoundingClientRect();

    // Last: 把棋子放到目标位置
    toCell.textContent = piece;
    toCell.classList.toggle('is-black', !this._isRedPiece(piece));
    fromCell.textContent = '';
    fromCell.classList.remove('is-black');

    // Invert: 计算位移差
    const dx = fromRect.left - toRect.left;
    const dy = fromRect.top - toRect.top;

    // Play: CSS transition
    toCell.style.transform = `translate(${dx}px, ${dy}px)`;
    toCell.style.transition = 'none';
    toCell.style.zIndex = '10';
    requestAnimationFrame(() => {
      toCell.style.transition = `transform ${duration}ms ease`;
      toCell.style.transform = '';
      setTimeout(() => { toCell.style.zIndex = ''; }, duration);
    });
  }

  _render() {
    const prev = this.prevState || {};
    const curr = this.state;

    for (let r = 0; r < 10; r++) {
      for (let c = 0; c < 9; c++) {
        const cell = this.cells[r * 9 + c];
        const key = `${r},${c}`;
        const oldPiece = prev.pieces ? prev.pieces[key] : undefined;
        const newPiece = curr.pieces ? curr.pieces[key] : undefined;

        // 棋子变化
        if (oldPiece !== newPiece) {
          const text = newPiece || '';
          cell.textContent = text;
          cell.classList.toggle('is-black', text && !this._isRedPiece(text));
        }

        // 选中状态
        const wasSelected = prev.selected && prev.selected.row === r && prev.selected.col === c;
        const isSelected = curr.selected && curr.selected.row === r && curr.selected.col === c;
        if (wasSelected !== isSelected) {
          cell.classList.toggle('is-selected', isSelected);
        }

        // 提示高亮
        const wasHintFrom = prev.hint && prev.hint.fromRow === r && prev.hint.fromCol === c;
        const isHintFrom = curr.hint && curr.hint.fromRow === r && curr.hint.fromCol === c;
        if (wasHintFrom !== isHintFrom) {
          cell.classList.toggle('is-hint-from', isHintFrom);
        }

        const wasHintTo = prev.hint && prev.hint.toRow === r && prev.hint.toCol === c;
        const isHintTo = curr.hint && curr.hint.toRow === r && curr.hint.toCol === c;
        if (wasHintTo !== isHintTo) {
          cell.classList.toggle('is-hint-to', isHintTo);
        }

        // 上一步高亮
        const wasLastMove = prev.lastMove && (
          (prev.lastMove.fromRow === r && prev.lastMove.fromCol === c) ||
          (prev.lastMove.toRow === r && prev.lastMove.toCol === c)
        );
        const isLastMove = curr.lastMove && (
          (curr.lastMove.fromRow === r && curr.lastMove.fromCol === c) ||
          (curr.lastMove.toRow === r && curr.lastMove.toCol === c)
        );
        if (wasLastMove !== isLastMove) {
          cell.classList.toggle('is-last-move', isLastMove);
        }

        // 禁用状态
        cell.disabled = !!curr.disabled;
      }
    }
  }

  _isRedPiece(text) {
    return ['帥', '帅', '仕', '相', '馬', '車', '砲', '卒'].includes(text);
  }

  destroy() {
    this.container.innerHTML = '';
    this.cells = [];
  }
}

/**
 * 五子棋棋盘渲染器
 */
class GomokuBoard {
  constructor(container, size = 15) {
    this.container = container;
    this.size = size;
    this.state = { pieces: {}, lastMove: null, disabled: false };
    this.prevState = null;
    this.cells = [];
    this.onClick = null;
    this._init();
  }

  _init() {
    this.container.innerHTML = '';
    this.container.classList.add('gomokuBoard');
    for (let r = 0; r < this.size; r++) {
      for (let c = 0; c < this.size; c++) {
        const cell = document.createElement('button');
        cell.className = 'gomokuCell';
        cell.dataset.row = r;
        cell.dataset.col = c;
        cell.addEventListener('click', () => this._handleClick(r, c));
        this.cells[r * this.size + c] = cell;
        this.container.appendChild(cell);
      }
    }
  }

  _handleClick(row, col) {
    if (this.state.disabled) return;
    if (this.onClick) this.onClick(row, col);
  }

  set(update) {
    this.prevState = { ...this.state };
    Object.assign(this.state, update);
    this._render();
  }

  _render() {
    const prev = this.prevState || {};
    const curr = this.state;

    for (let r = 0; r < this.size; r++) {
      for (let c = 0; c < this.size; c++) {
        const cell = this.cells[r * this.size + c];
        const key = `${r},${c}`;
        const oldPiece = prev.pieces ? prev.pieces[key] : undefined;
        const newPiece = curr.pieces ? curr.pieces[key] : undefined;

        if (oldPiece !== newPiece) {
          cell.classList.remove('is-black', 'is-white');
          if (newPiece === 'BLACK') cell.classList.add('is-black');
          if (newPiece === 'WHITE') cell.classList.add('is-white');
        }

        const wasLastMove = prev.lastMove && prev.lastMove.row === r && prev.lastMove.col === c;
        const isLastMove = curr.lastMove && curr.lastMove.row === r && curr.lastMove.col === c;
        if (wasLastMove !== isLastMove) {
          cell.classList.toggle('is-last-move', isLastMove);
        }

        cell.disabled = !!curr.disabled;
      }
    }
  }

  destroy() {
    this.container.innerHTML = '';
    this.cells = [];
  }
}
