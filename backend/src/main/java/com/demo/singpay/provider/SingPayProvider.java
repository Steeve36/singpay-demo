package com.demo.singpay.provider;

import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.exception.PaymentProviderUnavailableException;
import com.demo.singpay.model.PaymentTransaction;
import com.demo.singpay.model.TransactionStatus;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.model.enums.TxnStatus;
import com.demo.singpay.repository.PaymentTransactionRepository;
import com.demo.singpay.service.SingPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SingPayProvider implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(SingPayProvider.class);

    private final SingPayService singPayService;
    private final PaymentTransactionRepository txnRepo;

    public SingPayProvider(SingPayService singPayService,
                           PaymentTransactionRepository txnRepo) {
        this.singPayService = singPayService;
        this.txnRepo        = txnRepo;
    }

    @Override
    public PaymentInitResponse initiate(PaymentInitRequest request) {
        String subtype = request.getSubtype();
        // Seul "EXT_LINK" explicite déclenche le lien externe ; tout le reste (USSD, null, vide) → USSD
        if ("EXT_LINK".equalsIgnoreCase(subtype)) {
            return initiateExtLink(request);
        }
        return initiateUssd(request);
    }

    private PaymentInitResponse initiateUssd(PaymentInitRequest request) {
        if (request.getOperateur() == null || request.getPhone() == null) {
            throw new PaymentProviderUnavailableException(PaymentProviderEnum.SINGPAY,
                "Opérateur et numéro de téléphone requis pour le paiement USSD");
        }

        String singpayTxnId = singPayService.initierPaiementUssd(
            request.getOperateur(),
            request.getPhone(),
            request.getAmount(),
            request.getOrderReference()
        );

        log.info("USSD Push initié pour {} — SingPay txnId: {}",
                 request.getOrderReference(), singpayTxnId);

        // La transaction est déjà persistée par PaymentOrchestrator avant cet appel.
        // On récupère l'ID via la clé d'idempotence pour construire la réponse.
        Long txnId = txnRepo.findByIdempotencyKey(request.getIdempotencyKey())
            .map(PaymentTransaction::getId)
            .orElse(null);

        return PaymentInitResponse.ussd(txnId, request.getOrderReference(),
                                        PaymentProviderEnum.SINGPAY, singpayTxnId);
    }

    private PaymentInitResponse initiateExtLink(PaymentInitRequest request) {
        var extResponse = singPayService.createPaymentLink(
            request.getOrderReference(),
            request.getAmount()
        );

        log.info("Lien externe créé pour {} — expiry: {}",
                 request.getOrderReference(), extResponse.getExp());

        Long txnId = txnRepo.findByIdempotencyKey(request.getIdempotencyKey())
            .map(PaymentTransaction::getId)
            .orElse(null);

        return PaymentInitResponse.withUrl(txnId, request.getOrderReference(),
                                           PaymentMethod.MOBILE_MONEY,
                                           PaymentProviderEnum.SINGPAY,
                                           extResponse.getLink());
    }

    @Override
    public PaymentStatusResponse getStatus(Long transactionId, String providerRef) {
        if (transactionId == null) {
            throw new RuntimeException("transactionId requis pour getStatus");
        }
        TransactionStatus singPayStatus = singPayService.getTransactionByReference(providerRef);

        return txnRepo.findById(transactionId)
            .map(txn -> {
                // Synchronise le statut local avec la réponse SingPay
                if ("Terminate".equals(singPayStatus.getStatus())) {
                    TxnStatus mapped = "Success".equals(singPayStatus.getResult())
                        ? TxnStatus.SUCCESS
                        : TxnStatus.FAILED;
                    txn.setStatus(mapped);
                    if (mapped == TxnStatus.FAILED) {
                        txn.setFailureReason(singPayStatus.getResult());
                    }
                    txnRepo.save(txn);
                }
                return PaymentStatusResponse.from(txn);
            })
            .orElseThrow(() -> new RuntimeException("Transaction introuvable : " + transactionId));
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.MOBILE_MONEY;
    }

    @Override
    public PaymentProviderEnum getProviderName() {
        return PaymentProviderEnum.SINGPAY;
    }

    @Override
    public boolean isAvailable() {
        return true; // SingPay n'expose pas d'endpoint de health check public
    }
}
