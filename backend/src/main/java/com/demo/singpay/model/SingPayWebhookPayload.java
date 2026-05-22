package com.demo.singpay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SingPayWebhookPayload {

    private SingPayCallback transaction;

    public SingPayCallback getTransaction() { return transaction; }
    public void setTransaction(SingPayCallback t) { this.transaction = t; }
}
