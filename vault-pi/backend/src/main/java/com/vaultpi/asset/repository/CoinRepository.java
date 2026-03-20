package com.vaultpi.asset.repository;

import com.vaultpi.asset.entity.Coin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoinRepository extends JpaRepository<Coin, Long> {

    List<Coin> findByEnableTrue();

    Optional<Coin> findByUnit(String unit);
}
