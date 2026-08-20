package com.demo.singpay.dto;

public class CreateIntentResponse {

    private String clientSecret;
    private Long   transactionId;
    private String orderReference;

    public CreateIntentResponse() {}

    public CreateIntentResponse(String clientSecret, Long transactionId, String orderReference) {
        this.clientSecret   = clientSecret;
        this.transactionId  = transactionId;
        this.orderReference = orderReference;
    }

    public String getClientSecret()   { return clientSecret; }
    public Long   getTransactionId()  { return transactionId; }
    public String getOrderReference() { return orderReference; }
}
