package com.demo.singpay.service;

import com.demo.singpay.model.ExtLinkResponse;
import com.demo.singpay.model.TransactionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class SingPayService {

    @Value("${singpay.base-url}")       private String baseUrl;
    @Value("${singpay.client-id}")      private String clientId;
    @Value("${singpay.client-secret}")  private String clientSecret;
    @Value("${singpay.wallet-id}")      private String walletId;
    @Value("${singpay.disbursement-id}") private String disbursementId;
    @Value("${app.frontend-url}")        private String frontendUrl;

    private final RestTemplate restTemplate;

    public SingPayService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
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

        ResponseEntity<TransactionStatus> response = restTemplate.exchange(
            baseUrl + "/transaction/api/search/by-reference/" + reference,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            TransactionStatus.class
        );

        return response.getBody();
    }

    /**
     * PUT /portefeuille/api/{walletId}
     * Configure la callbackURL du portefeuille (à appeler une seule fois au démarrage
     * ou lors d'un changement d'URL de production).
     */
    public void updateCallbackUrl(String callbackUrl) {
        HttpHeaders headers = singPayHeaders();

        Map<String, String> body = Map.of("callbackURL", callbackUrl);

        restTemplate.exchange(
            baseUrl + "/portefeuille/api/" + walletId,
            HttpMethod.PUT,
            new HttpEntity<>(body, headers),
            Object.class
        );
    }
}
