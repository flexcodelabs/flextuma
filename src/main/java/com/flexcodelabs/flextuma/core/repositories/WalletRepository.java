package com.flexcodelabs.flextuma.core.repositories;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.finance.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID>, JpaSpecificationExecutor<Wallet> {
    List<Wallet> findByCreatedBy(User user);

    List<Wallet> findByCreatedByAndBalanceGreaterThan(User user, java.math.BigDecimal balance);

    java.util.Optional<Wallet> findTopByCreatedByOrderByCreatedDesc(User user);

    @Query("SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.balance IS NOT NULL")
    java.math.BigDecimal sumAllBalances();

    @Query("SELECT w FROM Wallet w WHERE w.currency IS NOT NULL ORDER BY w.created DESC")
    java.util.List<Wallet> findTopByCurrencyOrderByCreatedDesc();
}
