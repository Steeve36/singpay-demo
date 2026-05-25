package com.demo.singpay.provider;

import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.exception.PaymentProviderUnavailableException;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import org.springframework.stereotype.Component;

/**
 * Provider virement bancaire — placeholder.
 *
 * Pour activer :
 * 1. Définir les coordonnées bancaires dans payment_provider_config.config_json :
 *    {"iban": "GA...", "bic": "BGFIGABB", "beneficiaire": "QTZ-App SARL"}
 *
 * 2. Implémenter initiate() pour :
 *    - Créer un PaymentTransaction avec status=PENDING
 *    - Retourner une réponse sans URL (le frontend affichera les coordonnées bancaires)
 *    - Optionnel : envoyer un email avec les instructions de virement
 *
 * 3. La confirmation se fait manuellement (rapprochement bancaire) ou via webhook banque.
 *
 * 4. Mettre active=true dans payment_provider_config pour BANK_TRANSFER.
 */
@Component
public class BankTransferProvider implements PaymentProviderPort {

    @Override
    public PaymentInitResponse initiate(PaymentInitRequest request) {
        throw new PaymentProviderUnavailableException(
            PaymentProviderEnum.BANK_TRANSFER,
            "Le paiement par virement bancaire n'est pas encore activé."
        );
    }

    @Override
    public PaymentStatusResponse getStatus(Long transactionId, String providerRef) {
        throw new PaymentProviderUnavailableException(
            PaymentProviderEnum.BANK_TRANSFER,
            "Le paiement par virement bancaire n'est pas encore activé."
        );
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public PaymentProviderEnum getProviderName() {
        return PaymentProviderEnum.BANK_TRANSFER;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
