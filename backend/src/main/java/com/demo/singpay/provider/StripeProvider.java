package com.demo.singpay.provider;

import com.demo.singpay.dto.CreateIntentRequest;
import com.demo.singpay.dto.CreateIntentResponse;
import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.exception.PaymentProviderUnavailableException;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.model.enums.TxnStatus;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
                    + "?reference=" + URLEncoder.encode(request.getOrderReference(), StandardCharsets.UTF_8)
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

    /**
     * Crée un PaymentIntent Stripe (paiement intégré via Stripe Elements).
     * Retourne le client_secret nécessaire au frontend pour monter le PaymentElement.
     */
    public CreateIntentResponse createPaymentIntent(CreateIntentRequest request, Long txnId) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(request.getAmount().longValue())
                .setCurrency(request.getCurrency().toLowerCase())
                .setReceiptEmail(request.getCustomerEmail())
                .putMetadata("order_reference", request.getOrderReference())
                .putMetadata("internal_txn_id", String.valueOf(txnId))
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                )
                .build();

            PaymentIntent intent = PaymentIntent.create(params, requestOptions());
            log.info("Stripe PaymentIntent {} créé pour ref {}",
                     intent.getId(), request.getOrderReference());

            return new CreateIntentResponse(intent.getClientSecret(), txnId, request.getOrderReference());

        } catch (StripeException e) {
            log.error("Erreur Stripe — création PaymentIntent : {}", e.getMessage());
            throw new PaymentProviderUnavailableException(PaymentProviderEnum.STRIPE, e.getMessage());
        }
    }

    /**
     * Vérifie le statut d'une transaction via l'API Stripe.
     * providerRef = session ID (cs_…) ou PaymentIntent ID (pi_…).
     */
    @Override
    public PaymentStatusResponse getStatus(Long transactionId, String providerRef) {
        if (providerRef == null || providerRef.isBlank()) {
            return PaymentStatusResponse.minimal(transactionId, TxnStatus.PENDING, null, null);
        }
        try {
            if (providerRef.startsWith("pi_")) {
                return getStatusFromPaymentIntent(transactionId, providerRef);
            } else {
                return getStatusFromSession(transactionId, providerRef);
            }
        } catch (StripeException e) {
            log.error("Erreur Stripe — vérification {} : {}", providerRef, e.getMessage());
            throw new PaymentProviderUnavailableException(PaymentProviderEnum.STRIPE, e.getMessage());
        }
    }

    private PaymentStatusResponse getStatusFromSession(Long txnId, String providerRef)
            throws StripeException {
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
        log.info("Stripe session {} — statut: {} → {}", providerRef, session.getStatus(), status);
        return PaymentStatusResponse.minimal(txnId, status, providerRef, failureReason);
    }

    private PaymentStatusResponse getStatusFromPaymentIntent(Long txnId, String providerRef)
            throws StripeException {
        PaymentIntent intent = PaymentIntent.retrieve(providerRef, requestOptions());
        TxnStatus status;
        String failureReason = null;
        switch (intent.getStatus()) {
            case "succeeded" -> status = TxnStatus.SUCCESS;
            case "canceled"  -> {
                status = TxnStatus.FAILED;
                failureReason = "Paiement annulé";
            }
            case "payment_failed" -> {
                status = TxnStatus.FAILED;
                failureReason = "Paiement échoué";
            }
            default -> status = TxnStatus.PENDING;
        }
        log.info("Stripe PaymentIntent {} — statut: {} → {}",
                 providerRef, intent.getStatus(), status);
        return PaymentStatusResponse.minimal(txnId, status, providerRef, failureReason);
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
