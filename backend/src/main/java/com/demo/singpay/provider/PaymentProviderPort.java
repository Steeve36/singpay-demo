package com.demo.singpay.provider;

import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;

/**
 * Contrat abstrait que doit implémenter chaque provider de paiement.
 * Remplacer un provider = créer une nouvelle classe implémentant cette interface
 * + modifier la config en DB (payment_provider_config).
 */
public interface PaymentProviderPort {

    /** Initie une transaction côté provider et retourne les infos nécessaires au frontend. */
    PaymentInitResponse initiate(PaymentInitRequest request);

    /** Interroge le statut d'une transaction via la référence provider. */
    PaymentStatusResponse getStatus(Long transactionId, String providerRef);

    /** Vérifie si ce provider gère la méthode de paiement donnée. */
    boolean supports(PaymentMethod method);

    /** Identifiant enum du provider. */
    PaymentProviderEnum getProviderName();

    /** Health check léger : false si le provider est connu pour être indisponible. */
    boolean isAvailable();
}
