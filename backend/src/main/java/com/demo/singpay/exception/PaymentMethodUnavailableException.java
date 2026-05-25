package com.demo.singpay.exception;

import com.demo.singpay.model.enums.PaymentMethod;

public class PaymentMethodUnavailableException extends PaymentException {
    public PaymentMethodUnavailableException(PaymentMethod method) {
        super("Aucun provider actif pour la méthode de paiement : " + method);
    }
}
