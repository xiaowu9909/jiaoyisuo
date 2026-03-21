/**
 * Vault π 合约盈利公式（与后端 FuturesFormula、docs/FUTURES_PROFIT_FORMULA.md 一致）
 *
 * V = Q × P_entry = M × L，M = V/L，多仓 PnL = (P_now - P_entry)×Q，ROE = PnL/M×100%
 */

/**
 * @param {object} pos - { direction, avgPrice|entryPrice, volume|amount }
 * @param {number} markPrice - P_now
 */
export function unrealizedPnl(pos, markPrice) {
  const entry = Number(pos?.avgPrice ?? pos?.entryPrice ?? 0)
  const q = Number(pos?.volume ?? pos?.amount ?? 0)
  const m = Number(markPrice)
  if (!Number.isFinite(entry) || !Number.isFinite(q) || q === 0) return 0
  if (!Number.isFinite(m)) return 0
  if (pos?.direction === 'LONG') return (m - entry) * q
  return (entry - m) * q
}

/** ROE（百分比数值，如 12.5 表示 12.5%） */
export function roePercent(unrealizedPnlValue, margin) {
  const m = Number(margin)
  if (!Number.isFinite(m) || m === 0) return 0
  return (Number(unrealizedPnlValue) / m) * 100
}

/**
 * 快捷下单：可用余额 P、比例 x%、杠杆 L、价格 P_order → 数量 Q
 * M = P×(x/100), V = M×L, Q = V/P_order
 */
export function quantityFromAvailablePct(available, pct, leverage, orderPrice) {
  const p = Number(available)
  const x = Number(pct)
  const l = Number(leverage)
  const px = Number(orderPrice)
  if (!Number.isFinite(p) || !Number.isFinite(x) || !Number.isFinite(l) || !Number.isFinite(px) || px <= 0 || l < 1) {
    return 0
  }
  const margin = p * (x / 100)
  const notional = margin * l
  return notional / px
}
