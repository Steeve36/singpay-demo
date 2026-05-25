package com.demo.singpay.exception;

public class DuplicateTransactionException extends PaymentException {
    public DuplicateTransactionException(String idempotencyKey) {
        super("Transaction déjà initiée pour la clé d'idempotence : " + idempotencyKey);
    }
}
