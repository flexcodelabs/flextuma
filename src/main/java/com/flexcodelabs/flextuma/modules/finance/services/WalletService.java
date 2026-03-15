package com.flexcodelabs.flextuma.modules.finance.services;

import com.flexcodelabs.flextuma.core.entities.auth.User;
import com.flexcodelabs.flextuma.core.entities.finance.Wallet;
import com.flexcodelabs.flextuma.core.entities.finance.WalletTransaction;
import com.flexcodelabs.flextuma.core.enums.TransactionType;
import com.flexcodelabs.flextuma.core.repositories.WalletRepository;
import com.flexcodelabs.flextuma.core.repositories.WalletTransactionRepository;
import com.flexcodelabs.flextuma.core.services.BaseService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService extends BaseService<Wallet> {

    private final WalletRepository repository;
    private final WalletTransactionRepository transactionRepository;



    public Wallet getOrCreateWallet(User user) {
        Optional<Wallet> optionalWallet = repository.findByCreatedBy(user);
        if (optionalWallet.isPresent()) {
            return optionalWallet.get();
        }

        Wallet newWallet = new Wallet();
        newWallet.setBalance(BigDecimal.ZERO);
        newWallet.setCurrency("TZS");
        newWallet.setCreatedBy(user);

        return repository.save(newWallet);
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
        Wallet savedWallet = repository.save(wallet);

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
        Wallet savedWallet = repository.save(wallet);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(savedWallet);
        transaction.setType(TransactionType.CREDIT);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setReference(reference);
        transaction.setBalanceAfter(savedWallet.getBalance());

        return transactionRepository.save(transaction);
    }


    @Override
    protected boolean isAdminEntity() {
        return false;
    }

    @Override
    protected JpaRepository<Wallet, UUID> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<Wallet> getRepositoryAsExecutor() {
        return repository;
    }

    @Override
    protected String getReadPermission() {
        return Wallet.READ;
    }

    @Override
    protected String getAddPermission() {
        return Wallet.ADD;
    }

    @Override
    protected String getUpdatePermission() {
        return Wallet.UPDATE;
    }

    @Override
    protected String getDeletePermission() {
        return Wallet.DELETE;
    }

    @Override
    public String getEntityPlural() {
        return Wallet.NAME_PLURAL;
    }

    @Override
    public String getPropertyName() {
        return Wallet.PLURAL;
    }

    @Override
    protected String getEntitySingular() {
        return Wallet.NAME_SINGULAR;
    }

    @Override
    protected void validateDelete(Wallet entity) throws ResponseStatusException {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Wallet cannot be deleted");
        
    }

    @Override
    protected void onPreSave(Wallet entity) throws ResponseStatusException {
        throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "You cannot create a wallet manually");
    }

    @Override
    protected Wallet onPreUpdate(Wallet newEntity, Wallet oldEntity) throws ResponseStatusException {
        throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "You cannot update a wallet manually");
    }
}
