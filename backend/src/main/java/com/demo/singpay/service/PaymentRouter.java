package com.demo.singpay.service;

import com.demo.singpay.exception.PaymentMethodUnavailableException;
import com.demo.singpay.exception.PaymentProviderUnavailableException;
import com.demo.singpay.model.PaymentProviderConfig;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.provider.PaymentProviderPort;
import com.demo.singpay.repository.PaymentProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sélectionne le provider actif pour une méthode de paiement donnée.
 *
 * Règle : lit payment_provider_config en DB (pas de config hardcodée).
 * Si plusieurs providers sont actifs pour la même méthode, celui avec la
 * priorité la plus basse l'emporte. Si le provider sélectionné est marqué
 * isAvailable()=false, on tente le suivant dans la liste.
 */
@Service
public class PaymentRouter {

    private static final Logger log = LoggerFactory.getLogger(PaymentRouter.class);

    private final PaymentProviderConfigRepository configRepo;
    private final List<PaymentProviderPort> providers;

    public PaymentRouter(PaymentProviderConfigRepository configRepo,
                         List<PaymentProviderPort> providers) {
        this.configRepo = configRepo;
        this.providers  = providers;
    }

    public PaymentProviderPort route(PaymentMethod method) {
        List<PaymentProviderConfig> configs =
            configRepo.findByMethodAndActiveOrderByPriorityAsc(method, true);

        if (configs.isEmpty()) {
            throw new PaymentMethodUnavailableException(method);
        }

        for (PaymentProviderConfig config : configs) {
            PaymentProviderEnum targetProvider = config.getProvider();
            PaymentProviderPort port = providers.stream()
                .filter(p -> p.getProviderName() == targetProvider)
                .findFirst()
                .orElse(null);

            if (port == null) {
                log.warn("Provider {} configuré en DB mais aucun bean trouvé", targetProvider);
                continue;
            }
            if (!port.isAvailable()) {
                log.warn("Provider {} indisponible, tentative du suivant", targetProvider);
                continue;
            }
            log.info("Méthode {} routée vers {}", method, targetProvider);
            return port;
        }

        throw new PaymentProviderUnavailableException(
            configs.get(0).getProvider()
        );
    }
}
