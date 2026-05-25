package com.demo.singpay.dto;

import com.demo.singpay.model.PaymentTransaction;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.model.enums.TxnStatus;

import java.time.LocalDateTime;

public class PaymentStatusResponse {

    private Long id;
    private String orderReference;
    private PaymentMethod method;
    private PaymentProviderEnum provider;
    private TxnStatus status;
    private Integer amount;
    private String currency;
    private String providerRef;
    private String failureReason;
    private String customerName;
    private String customerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentStatusResponse from(PaymentTransaction txn) {
        PaymentStatusResponse r = new PaymentStatusResponse();
        r.id            = txn.getId();
        r.orderReference = txn.getOrderReference();
        r.method        = txn.getMethod();
        r.provider      = txn.getProvider();
        r.status        = txn.getStatus();
        r.amount        = txn.getAmount();
        r.currency      = txn.getCurrency();
        r.providerRef   = txn.getProviderRef();
        r.failureReason = txn.getFailureReason();
        r.customerName  = txn.getCustomerName();
        r.customerEmail = txn.getCustomerEmail();
        r.createdAt     = txn.getCreatedAt();
        r.updatedAt     = txn.getUpdatedAt();
        return r;
    }

    /** Réponse minimale retournée par un provider lors d'une vérification live. */
    public static PaymentStatusResponse minimal(Long id, TxnStatus status,
                                                 String providerRef, String failureReason) {
        PaymentStatusResponse r = new PaymentStatusResponse();
        r.id            = id;
        r.status        = status;
        r.providerRef   = providerRef;
        r.failureReason = failureReason;
        return r;
    }

    public Long getId() { return id; }
    public String getOrderReference() { return orderReference; }
    public PaymentMethod getMethod() { return method; }
    public PaymentProviderEnum getProvider() { return provider; }
    public TxnStatus getStatus() { return status; }
    public Integer getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getProviderRef() { return providerRef; }
    public String getFailureReason() { return failureReason; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
