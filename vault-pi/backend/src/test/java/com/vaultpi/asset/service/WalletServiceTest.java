package com.vaultpi.asset.service;

import com.vaultpi.asset.entity.MemberWallet;
import com.vaultpi.asset.repository.MemberWalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private MemberWalletRepository memberWalletRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void getOrCreateWallet_returnsExisting() {
        Long memberId = 1L;
        Long coinId = 2L;
        MemberWallet existing = new MemberWallet();
        existing.setMemberId(memberId);
        existing.setCoinId(coinId);
        existing.setBalance(BigDecimal.TEN);
        when(memberWalletRepository.findByMemberIdAndCoinId(memberId, coinId)).thenReturn(Optional.of(existing));

        MemberWallet result = walletService.getOrCreateWallet(memberId, coinId);

        assertSame(existing, result);
        verify(memberWalletRepository, never()).save(any());
    }

    @Test
    void getOrCreateWallet_createsWhenMissing() {
        Long memberId = 1L;
        Long coinId = 2L;
        when(memberWalletRepository.findByMemberIdAndCoinId(memberId, coinId)).thenReturn(Optional.empty());
        when(memberWalletRepository.save(any(MemberWallet.class))).thenAnswer(inv -> inv.getArgument(0));

        MemberWallet result = walletService.getOrCreateWallet(memberId, coinId);

        assertNotNull(result);
        assertEquals(memberId, result.getMemberId());
        assertEquals(coinId, result.getCoinId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(BigDecimal.ZERO, result.getFrozenBalance());
        verify(memberWalletRepository).save(result);
    }
}
