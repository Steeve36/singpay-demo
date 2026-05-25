package com.demo.singpay.model;

import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.model.enums.TxnStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_pt_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_pt_order_reference", columnList = "order_reference"),
    @Index(name = "idx_pt_provider_ref",    columnList = "provider_ref")
})
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Clé d'idempotence envoyée par le frontend — garantit l'unicité de la tentative
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    // Référence de la commande côté marchand
    @Column(name = "order_reference", nullable = false, length = 64)
    private String orderReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProviderEnum provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TxnStatus status;

    // Montant toujours en unité entière (jamais de float)
    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 3)
    private String currency;

    // ID de la transaction côté provider (SingPay txnId, Stripe PaymentIntent, etc.)
    @Column(name = "provider_ref", length = 100)
    private String providerRef;

    // Raison d'échec lisible
    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    // Données spécifiques au provider (JSON) — non sensibles uniquement
    @Column(name = "provider_metadata", columnDefinition = "TEXT")
    private String providerMetadata;

    // Log d'audit immuable — append-only JSON array
    @Column(name = "audit_log", columnDefinition = "TEXT")
    private String auditLog;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = TxnStatus.PENDING;
        if (auditLog == null) auditLog = "[]";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getOrderReference() { return orderReference; }
    public void setOrderReference(String orderReference) { this.orderReference = orderReference; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public PaymentProviderEnum getProvider() { return provider; }
    public void setProvider(PaymentProviderEnum provider) { this.provider = provider; }

    public TxnStatus getStatus() { return status; }
    public void setStatus(TxnStatus status) { this.status = status; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getProviderMetadata() { return providerMetadata; }
    public void setProviderMetadata(String providerMetadata) { this.providerMetadata = providerMetadata; }

    public String getAuditLog() { return auditLog; }
    public void setAuditLog(String auditLog) { this.auditLog = auditLog; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
