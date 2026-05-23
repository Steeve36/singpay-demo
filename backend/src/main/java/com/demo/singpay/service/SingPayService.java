package com.demo.singpay.service;

import com.demo.singpay.model.ExtLinkResponse;
import com.demo.singpay.model.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class SingPayService {

    private static final Logger log = LoggerFactory.getLogger(SingPayService.class);

    @Value("${singpay.base-url}")        private String baseUrl;
    @Value("${singpay.client-id}")       private String clientId;
    @Value("${singpay.client-secret}")   private String clientSecret;
    @Value("${singpay.wallet-id}")       private String walletId;
    @Value("${singpay.disbursement-id}") private String disbursementId;
    @Value("${app.frontend-url}")        private String frontendUrl;

    private final RestTemplate restTemplate;

    // BUG #5 — Timeouts via SimpleClientHttpRequestFactory (API Spring Boot 3.2)
    public SingPayService(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 secondes
        factory.setReadTimeout(15_000);     // 15 secondes
        this.restTemplate = builder.requestFactory(() -> factory).build();
    }

    // ── Headers OAuth SingPay ───────────────────────────────────────────────
    private HttpHeaders singPayHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id",     clientId);
        headers.set("x-client-secret", clientSecret);
        headers.set("x-wallet",        walletId);
        return headers;
    }

    /**
     * POST /ext
     * Crée un lien de paiement hébergé par SingPay.
     * Retourne un lien + date d'expiration.
     */
    public ExtLinkResponse createPaymentLink(String reference, int amount) {

        Map<String, Object> body = Map.of(
            "portefeuille",    walletId,
            "reference",       reference,
            "redirect_success", frontendUrl + "/paiement/succes?reference=" + reference,
            "redirect_error",   frontendUrl + "/paiement/echec?reference="  + reference,
            "amount",          amount,
            "disbursement",    disbursementId,
            "logoURL",         frontendUrl + "/assets/logo.png",
            "isTransfer",      false
        );

        // BUG #4 — Exception handling sur l'appel RestTemplate
        try {
            ResponseEntity<ExtLinkResponse> response = restTemplate.exchange(
                baseUrl + "/ext",
                HttpMethod.POST,
                new HttpEntity<>(body, singPayHeaders()),
                ExtLinkResponse.class
            );

            if (response.getBody() == null) {
                throw new RuntimeException("Réponse vide de SingPay /ext");
            }

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Erreur SingPay {} pour createPaymentLink: {}",
                      e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SingPay a rejeté la requête: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.error("SingPay injoignable: {}", e.getMessage());
            throw new RuntimeException("Passerelle SingPay temporairement indisponible");
        }
    }

    /**
     * GET /transaction/api/search/by-reference/{reference}
     * Récupère l'état final d'une transaction via sa référence marchand.
     */
    public TransactionStatus getTransactionByReference(String reference) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id",     clientId);
        headers.set("x-client-secret", clientSecret);
        headers.set("x-wallet",        walletId);

        // BUG #4 — Exception handling sur l'appel RestTemplate
        try {
            ResponseEntity<TransactionStatus> response = restTemplate.exchange(
                baseUrl + "/transaction/api/search/by-reference/" + reference,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TransactionStatus.class
            );
            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Erreur SingPay {} pour getTransactionByReference: {}",
                      e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SingPay a rejeté la requête: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.error("SingPay injoignable: {}", e.getMessage());
            throw new RuntimeException("Passerelle SingPay temporairement indisponible");
        }
    }

    /**
     * PUT /portefeuille/api/{walletId}
     * Configure la callbackURL du portefeuille.
     */
    public void updateCallbackUrl(String callbackUrl) {
        Map<String, String> body = Map.of("callbackURL", callbackUrl);

        // BUG #4 — Exception handling sur l'appel RestTemplate
        try {
            restTemplate.exchange(
                baseUrl + "/portefeuille/api/" + walletId,
                HttpMethod.PUT,
                new HttpEntity<>(body, singPayHeaders()),
                Object.class
            );
        } catch (HttpClientErrorException e) {
            log.error("Erreur SingPay {} pour updateCallbackUrl: {}",
                      e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("SingPay a rejeté la requête: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.error("SingPay injoignable: {}", e.getMessage());
            throw new RuntimeException("Passerelle SingPay temporairement indisponible");
        }
    }

    /**
     * Initie un USSD Push vers le client pour les opérateurs Airtel, Moov ou Maviance.
     * Retourne l'ID de transaction SingPay (champ "id") pour le polling.
     * Lance RuntimeException si SingPay rejette ou est injoignable.
     */
    public String initierPaiementUssd(
            String operateur, String phone, int amount, String reference) {

        String path = switch (operateur.toUpperCase()) {
            case "AIRTEL"   -> "/74/paiement";
            case "MOOV"     -> "/62/paiement";
            case "MAVIANCE" -> "/maviance/paiement";
            default -> throw new IllegalArgumentException("Opérateur inconnu: " + operateur);
        };

        Map<String, Object> body = new HashMap<>(Map.of(
            "amount",        amount,
            "reference",     reference,
            "client_msisdn", phone,
            "portefeuille",  walletId,
            "disbursement",  disbursementId,
            "isTransfer",    false
        ));

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + path,
                HttpMethod.POST,
                new HttpEntity<>(body, singPayHeaders()),
                Map.class
            );

            if (response.getBody() == null) {
                throw new RuntimeException("Réponse vide de SingPay pour l'initiation USSD");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> transaction =
                (Map<String, Object>) response.getBody().get("transaction");

            return transaction != null ? (String) transaction.get("id") : null;

        } catch (HttpClientErrorException e) {
            log.error("Erreur SingPay /paiement {} pour {}: {}",
                      e.getStatusCode(), reference, e.getResponseBodyAsString());
            throw new RuntimeException("Paiement rejeté par SingPay: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.error("SingPay injoignable lors de l'initiation USSD: {}", e.getMessage());
            throw new RuntimeException("Passerelle SingPay temporairement indisponible");
        }
    }
}
