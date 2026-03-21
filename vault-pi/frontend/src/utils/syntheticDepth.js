/**
 * 盘口/深度图展示：真实挂单不足时补「演示流动性」，并对真实挂单做微量脉冲，
 * 让 Depth 与列表看起来持续活跃（视觉高频，非真实撮合毫秒级）。
 */

export function inferTickSize(midPrice) {
  const m = Number(midPrice) || 0
  if (m <= 0) return 0.01
  if (m >= 50000) return 1
  if (m >= 5000) return 0.5
  if (m >= 500) return 0.05
  if (m >= 50) return 0.01
  if (m >= 5) return 0.001
  if (m >= 0.5) return 0.0001
  return 0.00001
}

function roundPrice(p, mid) {
  const m = Number(mid) || 1
  if (m >= 10000) return Math.round(p * 100) / 100
  if (m >= 1000) return Math.round(p * 1000) / 1000
  if (m >= 100) return Math.round(p * 10000) / 10000
  if (m >= 1) return Math.round(p * 100000) / 100000
  return Math.round(p * 1000000) / 1000000
}

function normalizeRow(x) {
  return {
    price: Number(x.price) || 0,
    amount: Math.max(0, Number(x.amount) || 0),
  }
}

function inferMidFromBook(raw, fallbackMid) {
  const ask = (raw.ask || []).map(normalizeRow).filter((r) => r.price > 0)
  const bid = (raw.bid || []).map(normalizeRow).filter((r) => r.price > 0)
  if (ask.length && bid.length) return (ask[0].price + bid[0].price) / 2
  if (ask.length) return ask[0].price * 0.9998
  if (bid.length) return bid[0].price * 1.0002
  return fallbackMid
}

function generateSide(mid, tick, side, levels, phase, midRef) {
  const rows = []
  const dir = side === 'ask' ? 1 : -1
  for (let i = 0; i < levels; i++) {
    const spread = tick * (0.55 + i * (0.9 + 0.2 * Math.sin(phase * 0.0008 + i * 1.2)))
    const wobble = tick * 0.1 * Math.sin(phase * 0.0013 + i * 0.85)
    const price = mid + dir * spread + wobble
    const base = 0.4 * Math.pow(0.8, i) * (0.5 + 0.5 * Math.sin(phase * 0.0009 + i * 2.3))
    const flicker = 1 + 0.16 * Math.sin(phase * 0.0016 + i * 1.5)
    const amount = Math.max(0.00001, base * flicker)
    rows.push({ price: roundPrice(price, midRef), amount })
  }
  if (side === 'ask') rows.sort((a, b) => a.price - b.price)
  else rows.sort((a, b) => b.price - a.price)
  return rows
}

/** 真实档不足时用合成档补齐到至少 minLevels 条 */
function padWithSynthetic(realRows, mid, tick, side, minLevels, phase, midRef) {
  const rows = [...realRows]
  if (rows.length >= minLevels) return rows
  const synth = generateSide(mid, tick, side, 16, phase, midRef)
  const seen = new Set(rows.map((r) => roundPrice(r.price, midRef)))
  for (const s of synth) {
    const rp = roundPrice(s.price, midRef)
    if (!seen.has(rp)) {
      rows.push(s)
      seen.add(rp)
    }
    if (rows.length >= minLevels + 6) break
  }
  if (side === 'ask') rows.sort((a, b) => a.price - b.price)
  else rows.sort((a, b) => b.price - a.price)
  return rows.slice(0, 28)
}

function jitterRows(rows, phase, weak) {
  const amp = weak ? 0.04 : 0.09
  const pAmp = weak ? 0.35 : 0.55
  return rows.map((r, i) => {
    const tick = inferTickSize(r.price)
    const pulse = 1 + amp * Math.sin(phase * 0.0012 + i * 0.62 + (r.price % 11) * 0.07)
    const micro = tick * pAmp * Math.sin(phase * 0.0014 + i * 0.73 + (r.price % 13) * 0.05)
    return {
      price: roundPrice(r.price + micro, r.price),
      amount: Math.max(0.00001, r.amount * pulse),
    }
  })
}

const MIN_REAL_LEVELS = 5

/**
 * @param raw {{ ask: any[]; bid: any[] }}
 * @param midPrice {number}
 * @param opts {{ jitterPhase: number; weakNetwork?: boolean }}
 */
export function mergePlateWithSyntheticDepth(raw, midPrice, opts) {
  const phase = Number(opts?.jitterPhase) || 0
  const weak = !!opts?.weakNetwork
  const ask0 = (raw?.ask || []).map(normalizeRow).filter((r) => r.price > 0)
  const bid0 = (raw?.bid || []).map(normalizeRow).filter((r) => r.price > 0)

  let mid = Number(midPrice) || 0
  if (mid <= 0) mid = inferMidFromBook(raw, 0)
  if (mid <= 0) {
    return {
      ask: jitterRows(ask0, phase, weak),
      bid: jitterRows(bid0, phase, weak),
    }
  }

  const tick = inferTickSize(mid)
  const thinAsk = ask0.length < MIN_REAL_LEVELS
  const thinBid = bid0.length < MIN_REAL_LEVELS

  let ask = thinAsk ? padWithSynthetic(ask0, mid, tick, 'ask', MIN_REAL_LEVELS, phase, mid) : jitterRows(ask0, phase, weak)
  let bid = thinBid ? padWithSynthetic(bid0, mid, tick, 'bid', MIN_REAL_LEVELS, phase, mid) : jitterRows(bid0, phase, weak)

  return { ask, bid }
}
