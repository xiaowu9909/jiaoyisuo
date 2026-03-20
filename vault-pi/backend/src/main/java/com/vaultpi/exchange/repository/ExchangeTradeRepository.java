package com.vaultpi.exchange.repository;

import com.vaultpi.exchange.entity.ExchangeTrade;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeTradeRepository extends JpaRepository<ExchangeTrade, Long> {

    List<ExchangeTrade> findBySymbolOrderByCreateTimeDesc(String symbol, PageRequest pageRequest);
}
