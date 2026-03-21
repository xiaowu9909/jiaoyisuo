package com.vaultpi.futures.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Vault π 合约盈利与风险口径（官方定义，全站计算须与此一致）。
 * <p>
 * 记号：本金余额 {@code P}、单笔占用保证金 {@code M}、杠杆 {@code L}、名义价值 {@code V}、
 * 开仓均价 {@code P_entry}、数量 {@code Q}、标记价 {@code P_now}。
 * </p>
 * <ul>
 *   <li>名义价值：{@code V = Q × P_entry = M × L}</li>
 *   <li>初始保证金（逐仓口径，与下单时一致）：{@code M = V / L = (Q × P_entry) / L}</li>
 *   <li>多仓未实现盈亏：{@code PnL = (P_now − P_entry) × Q}</li>
 *   <li>空仓未实现盈亏：{@code PnL = (P_entry − P_now) × Q}</li>
 *   <li>收益率 ROE：{@code ROE = PnL / M × 100%}；在 {@code M} 与 {@code V} 定义一致时，近似为「标的价格变动比例 × L」</li>
 *   <li>权益（强平判断）：{@code Equity = M + PnL}</li>
 * </ul>
 * C 端快捷下单「按可用余额的 x%」时：{@code M = P_可用 × (x/100)}，{@code V = M × L}，{@code Q = V / P_entry}，与此处 {@code M,V,Q} 关系一致。
 */
public final class FuturesFormula {

    private static final int MONEY_SCALE = 8;

    private FuturesFormula() {
    }

    /** 名义价值 V = 数量 × 开仓价 */
    public static BigDecimal notional(BigDecimal quantity, BigDecimal entryPrice) {
        return quantity.multiply(entryPrice);
    }

    /**
     * 初始保证金 M = V / L（与合约下单时 requiredMargin 字段一致）。
     */
    public static BigDecimal marginFromNotionalAndLeverage(BigDecimal notional, int leverage) {
        int lv = Math.max(1, leverage);
        return notional.divide(BigDecimal.valueOf(lv), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal unrealizedPnlLong(BigDecimal markPrice, BigDecimal avgPrice, BigDecimal volume) {
        return markPrice.subtract(avgPrice).multiply(volume);
    }

    public static BigDecimal unrealizedPnlShort(BigDecimal markPrice, BigDecimal avgPrice, BigDecimal volume) {
        return avgPrice.subtract(markPrice).multiply(volume);
    }

    /**
     * @param direction {@code LONG} 或 {@code SHORT}
     */
    public static BigDecimal unrealizedPnl(String direction, BigDecimal markPrice, BigDecimal avgPrice, BigDecimal volume) {
        if ("LONG".equalsIgnoreCase(direction)) {
            return unrealizedPnlLong(markPrice, avgPrice, volume);
        }
        return unrealizedPnlShort(markPrice, avgPrice, volume);
    }

    public static BigDecimal equity(BigDecimal margin, BigDecimal unrealizedPnl) {
        return margin.add(unrealizedPnl);
    }

    /**
     * ROE（%）= PnL / M × 100；若 M=0 返回 0。
     */
    public static BigDecimal roePercent(BigDecimal unrealizedPnl, BigDecimal margin) {
        if (margin == null || margin.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return unrealizedPnl.divide(margin, MONEY_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
