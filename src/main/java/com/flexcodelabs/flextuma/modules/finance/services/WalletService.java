package com.flexcodelabs.flextuma.modules.finance.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.finance.Wallet;
import com.flexcodelabs.flextuma.core.entities.finance.WalletTransaction;
import com.flexcodelabs.flextuma.core.enums.TransactionType;
import com.flexcodelabs.flextuma.core.repositories.WalletRepository;
import com.flexcodelabs.flextuma.core.repositories.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public Wallet getOrCreateWallet(User user) {
        Optional<Wallet> optionalWallet = walletRepository.findByCreatedBy(user);
        if (optionalWallet.isPresent()) {
            return optionalWallet.get();
        }

        Wallet newWallet = new Wallet();
        newWallet.setBalance(BigDecimal.ZERO);
        newWallet.setCurrency("TZS");
        newWallet.setCreatedBy(user);

        return walletRepository.save(newWallet);
    }

    @Transactional
    public WalletTransaction debit(User user, BigDecimal amount, String description, String reference) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit amount must be positive");
        }

        Wallet wallet = getOrCreateWallet(user);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Insufficient wallet balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        Wallet savedWallet = walletRepository.save(wallet);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(savedWallet);
        transaction.setType(TransactionType.DEBIT);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setReference(reference);
        transaction.setBalanceAfter(savedWallet.getBalance());

        return transactionRepository.save(transaction);
    }

    @Transactional
    public WalletTransaction credit(User user, BigDecimal amount, String description, String reference) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit amount must be positive");
        }

        Wallet wallet = getOrCreateWallet(user);

        wallet.setBalance(wallet.getBalance().add(amount));
        Wallet savedWallet = walletRepository.save(wallet);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(savedWallet);
        transaction.setType(TransactionType.CREDIT);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setReference(reference);
        transaction.setBalanceAfter(savedWallet.getBalance());

        return transactionRepository.save(transaction);
    }
}
