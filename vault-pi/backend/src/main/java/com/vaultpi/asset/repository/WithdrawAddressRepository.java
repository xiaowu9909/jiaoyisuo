package com.vaultpi.asset.repository;

import com.vaultpi.asset.entity.WithdrawAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawAddressRepository extends JpaRepository<WithdrawAddress, Long> {

    List<WithdrawAddress> findByMemberIdOrderByCoinId(Long memberId);
}
