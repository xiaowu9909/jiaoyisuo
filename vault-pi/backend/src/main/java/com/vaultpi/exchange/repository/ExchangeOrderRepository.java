package com.vaultpi.exchange.repository;

import com.vaultpi.exchange.entity.ExchangeOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrder, String> {

    /** 内存订单簿启动加载：所有未成交限价单 */
    List<ExchangeOrder> findByStatusAndType(String status, String type);

    List<ExchangeOrder> findByMemberIdAndStatusOrderByCreateTimeDesc(Long memberId, String status);

    Page<ExchangeOrder> findByMemberId(Long memberId, Pageable pageable);

    Page<ExchangeOrder> findByMemberIdOrderByCreateTimeDesc(Long memberId, Pageable pageable);

    /** 有成交的订单（历史交易） */
    Page<ExchangeOrder> findByMemberIdAndTradedAmountGreaterThanOrderByCreateTimeDesc(Long memberId, BigDecimal tradedAmount, Pageable pageable);

    Page<ExchangeOrder> findBySymbol(String symbol, Pageable pageable);
 
    Page<ExchangeOrder> findByMemberIdAndSymbol(Long memberId, String symbol, Pageable pageable);
 
    /** 撮合用：卖单按价格升序（先吃低价卖单） */
    List<ExchangeOrder> findBySymbolAndDirectionAndStatusOrderByPriceAscCreateTimeAsc(
        String symbol, String direction, String status, Pageable pageable);
 
    /** 撮合用：买单按价格降序（先吃高价买单） */
    List<ExchangeOrder> findBySymbolAndDirectionAndStatusOrderByPriceDescCreateTimeAsc(
        String symbol, String direction, String status, Pageable pageable);
}
