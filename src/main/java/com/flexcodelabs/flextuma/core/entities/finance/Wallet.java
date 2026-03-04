package com.flexcodelabs.flextuma.core.entities.finance;

import com.flexcodelabs.flextuma.core.entities.base.Owner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "wallet")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Wallet extends Owner {
    public static final String PLURAL = "wallets";
    public static final String NAME_PLURAL = "Wallets";
    public static final String NAME_SINGULAR = "Wallet";

    public static final String ALL = "ALL";
    public static final String READ = ALL;
    public static final String ADD = ALL;
    public static final String DELETE = ALL;
    public static final String UPDATE = ALL;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "TZS";

    @Version
    private Long version;
}
