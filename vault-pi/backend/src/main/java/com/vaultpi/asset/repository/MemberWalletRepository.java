package com.vaultpi.asset.repository;

import com.vaultpi.asset.entity.MemberWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberWalletRepository extends JpaRepository<MemberWallet, Long> {

    List<MemberWallet> findByMemberId(Long memberId);

    Optional<MemberWallet> findByMemberIdAndCoinId(Long memberId, Long coinId);
}
