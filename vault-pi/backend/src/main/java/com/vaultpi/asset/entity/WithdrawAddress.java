package com.vaultpi.asset.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "withdraw_address", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "member_id", "coin_id", "address" })
})
@Data
public class WithdrawAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "coin_id", nullable = false)
    private Long coinId;

    @Column(nullable = false, length = 128)
    private String address;

    @Column(length = 64)
    private String remark;
}
