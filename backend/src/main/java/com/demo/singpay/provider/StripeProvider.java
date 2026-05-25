package com.demo.singpay.provider;

import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.exception.PaymentProviderUnavailableException;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.model.enums.TxnStatus;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provider Stripe — paiement CB via Stripe Checkout (redirect).
 *
 * Pour activer :
 *   1. Renseigner STRIPE_SECRET_KEY=sk_test_... dans backend/.env
 *   2. Exécuter la migration V4 (active=1 pour STRIPE dans payment_provider_config)
 *
 * Pour swapper vers un autre processeur CB (Adyen, PayDunia…) :
 *   - Créer une classe implémentant PaymentProviderPort
 *   - Mettre active=0 pour STRIPE et active=1 pour le nouveau en DB
 *   - Aucune autre modification nécessaire
 */
@Component
public class StripeProvider implements PaymentProviderPort {

    private static final Logger log = LoggerFactory.getLogger(StripeProvider.class);

    private final String secretKey;
    private final String frontendUrl;

    public StripeProvider(
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.secretKey   = secretKey;
        this.frontendUrl = frontendUrl;
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder().setApiKey(secretKey).build();
    }

    @Override
    public PaymentInitResponse initiate(PaymentInitRequest request) {
        try {
            Long txnId = request.getInternalTxnId();

            // XAF est une devise zero-decimal chez Stripe — montant passé tel quel
            SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/paiement/succes"
                    + "?reference=" + request.getOrderReference()
                    + "&txnId=" + txnId)
                .setCancelUrl(frontendUrl + "/choisir-methode")
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(request.getCurrency().toLowerCase())
                                .setUnitAmount(request.getAmount().longValue())
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.getOrderReference())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .setCustomerEmail(request.getCustomerEmail())
                .putMetadata("order_reference", request.getOrderReference())
                .putMetadata("internal_txn_id", String.valueOf(txnId))
                .build();

            Session session = Session.create(params, requestOptions());
            log.info("Stripe Checkout Session {} créée pour ref {}",
                     session.getId(), request.getOrderReference());

            return PaymentInitResponse.withUrl(
                txnId,
                request.getOrderReference(),
                PaymentMethod.CB,
                PaymentProviderEnum.STRIPE,
                session.getUrl(),
                session.getId()
            );

        } catch (StripeException e) {
            log.error("Erreur Stripe — création session : {}", e.getMessage());
            throw new PaymentProviderUnavailableException(PaymentProviderEnum.STRIPE, e.getMessage());
        }
    }

    /** Vérifie le statut d'une Checkout Session via l'API Stripe. providerRef = session ID (cs_…) */
    @Override
    public PaymentStatusResponse getStatus(Long transactionId, String providerRef) {
        if (providerRef == null || providerRef.isBlank()) {
            return PaymentStatusResponse.minimal(transactionId, TxnStatus.PENDING, null, null);
        }
        try {
            Session session = Session.retrieve(providerRef, requestOptions());

            TxnStatus status;
            String failureReason = null;
            switch (session.getStatus()) {
                case "complete" -> status = TxnStatus.SUCCESS;
                case "expired"  -> {
                    status = TxnStatus.FAILED;
                    failureReason = "Session Stripe expirée";
                }
                default -> status = TxnStatus.PENDING;
            }

            log.info("Stripe session {} — statut: {} → {}",
                     providerRef, session.getStatus(), status);
            return PaymentStatusResponse.minimal(transactionId, status, providerRef, failureReason);

        } catch (StripeException e) {
            log.error("Erreur Stripe — vérification session {} : {}", providerRef, e.getMessage());
            throw new PaymentProviderUnavailableException(PaymentProviderEnum.STRIPE, e.getMessage());
        }
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CB;
    }

    @Override
    public PaymentProviderEnum getProviderName() {
        return PaymentProviderEnum.STRIPE;
    }

    @Override
    public boolean isAvailable() {
        return secretKey != null && !secretKey.isBlank() && !secretKey.equals("sk_test_...");
    }
}
