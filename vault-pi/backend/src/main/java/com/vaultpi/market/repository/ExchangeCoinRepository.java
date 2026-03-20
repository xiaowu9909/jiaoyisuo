package com.vaultpi.market.repository;

import com.vaultpi.market.entity.ExchangeCoin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExchangeCoinRepository extends JpaRepository<ExchangeCoin, Long> {

    List<ExchangeCoin> findByEnableTrue();

    Optional<ExchangeCoin> findBySymbol(String symbol);
}
