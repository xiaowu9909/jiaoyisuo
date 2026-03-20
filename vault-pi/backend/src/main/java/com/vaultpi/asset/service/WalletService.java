package com.vaultpi.asset.service;

import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.MemberWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 钱包统一操作：获取或创建会员某币种钱包，避免 OrderController、MatchService、FuturesOrderController 等处重复逻辑。
 */
@Service
public class WalletService {

    private final MemberWalletRepository memberWalletRepository;

    public WalletService(MemberWalletRepository memberWalletRepository) {
        this.memberWalletRepository = memberWalletRepository;
    }

    /**
     * 按会员与币种获取钱包，不存在则创建（余额、冻结均为 0）。
     */
    @Transactional(readOnly = false)
    public MemberWallet getOrCreateWallet(Long memberId, Long coinId) {
        return memberWalletRepository.findByMemberIdAndCoinId(memberId, coinId)
            .orElseGet(() -> {
                MemberWallet w = new MemberWallet();
                w.setMemberId(memberId);
                w.setCoinId(coinId);
                w.setBalance(BigDecimal.ZERO);
                w.setFrozenBalance(BigDecimal.ZERO);
                return memberWalletRepository.save(w);
            });
    }
}
