package com.demo.singpay.exception;

import com.demo.singpay.model.enums.PaymentProviderEnum;

public class PaymentProviderUnavailableException extends PaymentException {
    public PaymentProviderUnavailableException(PaymentProviderEnum provider) {
        super("Provider indisponible : " + provider);
    }
    public PaymentProviderUnavailableException(PaymentProviderEnum provider, String reason) {
        super("Provider indisponible : " + provider + " — " + reason);
    }
}
