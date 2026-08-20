package com.demo.singpay.repository;

import com.demo.singpay.model.PaymentTransaction;
import com.demo.singpay.model.enums.TxnStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByOrderReference(String orderReference);

    Optional<PaymentTransaction> findByProviderRef(String providerRef);

    Optional<PaymentTransaction> findByOrderReferenceAndStatus(String orderReference, TxnStatus status);

    Optional<PaymentTransaction> findFirstByOrderReferenceAndStatusIn(String orderReference, List<TxnStatus> statuses);

    Optional<PaymentTransaction> findByIdAndUserId(Long id, Long userId);
}
