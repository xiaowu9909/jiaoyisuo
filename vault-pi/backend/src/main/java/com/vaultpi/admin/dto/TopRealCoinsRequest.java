package com.vaultpi.admin.dto;

import lombok.Data;

/**
 * 从 Kraken 获取热门交易对并导入为实盘交易对（exchange_coin）。
 */
@Data
public class TopRealCoinsRequest {
    /** 需要导入的数量（默认 100） */
    private Integer count;

    /** dryRun=true 时仅返回将要导入的 symbol，不写库 */
    private Boolean dryRun;

    /**
     * 可选：如果传入 symbols，则直接使用该列表生成快照并应用（不再访问 Kraken）。
     * 用于确保本地与服务器“交易对集合严格一致”。
     * 元素形如 "ETH/USDT"。
     */
    private java.util.List<String> symbols;
}

