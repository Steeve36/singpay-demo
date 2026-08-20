package com.demo.singpay.service;

import com.demo.singpay.model.PaymentTransaction;
import com.demo.singpay.model.enums.TxnStatus;
import com.demo.singpay.repository.PaymentTransactionRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service dédié aux opérations DB sur PaymentTransaction.
 * Chaque méthode est dans sa propre transaction (REQUIRES_NEW) pour garantir
 * que les commits se font avant les appels HTTP aux providers.
 */
@Service
public class PaymentTransactionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransactionService.class);

    private final PaymentTransactionRepository txnRepo;

    public PaymentTransactionService(PaymentTransactionRepository txnRepo) {
        this.txnRepo = txnRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction savePending(PaymentTransaction txn) {
        if (txn == null) throw new IllegalArgumentException("txn ne peut pas être null");
        return txnRepo.save(txn);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProcessing(Long id, String providerRef, String auditLog) {
        if (id == null) return;
        txnRepo.findById(id).ifPresent(txn -> {
            txn.setStatus(TxnStatus.PROCESSING);
            txn.setProviderRef(providerRef);
            txn.setAuditLog(auditLog);
            txnRepo.save(txn);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailed(Long id, String reason, String auditLog) {
        if (id == null) return;
        txnRepo.findById(id).ifPresent(txn -> {
            txn.setStatus(TxnStatus.FAILED);
            txn.setFailureReason(reason);
            txn.setAuditLog(auditLog);
            txn.setUpdatedAt(LocalDateTime.now());
            txnRepo.save(txn);
        });
    }

    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findByIdempotencyKey(String key) {
        return txnRepo.findByIdempotencyKey(key);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findById(Long id) {
        if (id == null) return Optional.empty();
        return txnRepo.findById(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSuccess(Long id, String providerRef, String auditLog) {
        if (id == null) return;
        txnRepo.findById(id).ifPresent(txn -> {
            txn.setStatus(TxnStatus.SUCCESS);
            if (providerRef != null) txn.setProviderRef(providerRef);
            txn.setAuditLog(auditLog);
            txn.setUpdatedAt(LocalDateTime.now());
            txnRepo.save(txn);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyWebhook(String orderReference, TxnStatus newStatus,
                              String providerRef, String failureReason, String auditEntry) {
        try {
            txnRepo.findFirstByOrderReferenceAndStatusIn(
                    orderReference, List.of(TxnStatus.PENDING, TxnStatus.PROCESSING))
                .ifPresent(txn -> {
                    txn.setStatus(newStatus);
                    if (providerRef != null) txn.setProviderRef(providerRef);
                    if (failureReason != null) txn.setFailureReason(failureReason);
                    txn.setUpdatedAt(LocalDateTime.now());
                    if (auditEntry != null) {
                        String existing = txn.getAuditLog();
                        txn.setAuditLog((existing == null || existing.equals("[]"))
                            ? "[" + auditEntry + "]"
                            : existing.substring(0, existing.length() - 1) + "," + auditEntry + "]");
                    }
                    txnRepo.save(txn);
                });
        } catch (ObjectOptimisticLockingFailureException e) {
            // Deux webhooks simultanés — lire l'état actuel pour vérifier l'idempotence
            txnRepo.findFirstByOrderReferenceAndStatusIn(
                    orderReference, List.of(TxnStatus.SUCCESS, TxnStatus.FAILED, TxnStatus.REFUNDED))
                .ifPresentOrElse(
                    txn -> log.info("Race condition webhook ignorée — transaction {} déjà en état terminal: {}",
                                    orderReference, txn.getStatus()),
                    ()  -> log.warn("Race condition webhook non résolue pour {} — statut attendu: {}",
                                    orderReference, newStatus)
                );
        }
    }
}
