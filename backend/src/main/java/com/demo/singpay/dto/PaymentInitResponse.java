package com.demo.singpay.dto;

import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.model.enums.TxnStatus;

public class PaymentInitResponse {

    private Long transactionId;
    private String orderReference;
    private PaymentMethod method;
    private PaymentProviderEnum provider;
    private TxnStatus status;
    private String paymentUrl;    // lien de paiement pour /ext et CB
    private String providerRef;   // ID de transaction côté provider
    private String message;

    private PaymentInitResponse() {}

    public static PaymentInitResponse ussd(Long transactionId, String orderReference,
                                            PaymentProviderEnum provider, String providerRef) {
        PaymentInitResponse r = new PaymentInitResponse();
        r.transactionId  = transactionId;
        r.orderReference = orderReference;
        r.method         = PaymentMethod.MOBILE_MONEY;
        r.provider       = provider;
        r.status         = TxnStatus.PROCESSING;
        r.providerRef    = providerRef;
        return r;
    }

    public static PaymentInitResponse withUrl(Long transactionId, String orderReference,
                                               PaymentMethod method, PaymentProviderEnum provider,
                                               String paymentUrl) {
        PaymentInitResponse r = new PaymentInitResponse();
        r.transactionId  = transactionId;
        r.orderReference = orderReference;
        r.method         = method;
        r.provider       = provider;
        r.status         = TxnStatus.PENDING;
        r.paymentUrl     = paymentUrl;
        return r;
    }

    public static PaymentInitResponse withUrl(Long transactionId, String orderReference,
                                               PaymentMethod method, PaymentProviderEnum provider,
                                               String paymentUrl, String providerRef) {
        PaymentInitResponse r = withUrl(transactionId, orderReference, method, provider, paymentUrl);
        r.providerRef = providerRef;
        return r;
    }

    public Long getTransactionId() { return transactionId; }
    public String getOrderReference() { return orderReference; }
    public PaymentMethod getMethod() { return method; }
    public PaymentProviderEnum getProvider() { return provider; }
    public TxnStatus getStatus() { return status; }
    public String getPaymentUrl() { return paymentUrl; }
    public String getProviderRef() { return providerRef; }
    public String getMessage() { return message; }
}
