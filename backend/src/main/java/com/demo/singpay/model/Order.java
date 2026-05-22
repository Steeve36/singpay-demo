// ═══════════════════════════════════════════════════════
// model/Order.java  — Entité JPA stockée en MySQL
// ═══════════════════════════════════════════════════════
package com.demo.singpay.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_reference", columnList = "reference", unique = true)
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Référence unique générée par l'application (ex: CMD-1718000000000-A1B2C3D4).
     * C'est la clé de traçabilité entre ton app et SingPay.
     */
    @Column(unique = true, nullable = false, length = 64)
    private String reference;

    /** Montant en FCFA */
    @Column(nullable = false)
    private Integer amount;

    /** PENDING | PAID | FAILED_BalanceError | FAILED_PasswordError | FRAUD_SUSPECTED */
    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 100)
    private String customerName;

    @Column(length = 150)
    private String customerEmail;

    /** ID retourné par Airtel Money après succès */
    @Column(length = 100)
    private String airtelMoneyId;

    /** Date d'expiration du lien SingPay /ext */
    @Column(length = 50)
    private String paymentLinkExpiry;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public Long getId()                        { return id; }
    public String getReference()               { return reference; }
    public void setReference(String r)         { this.reference = r; }
    public Integer getAmount()                 { return amount; }
    public void setAmount(Integer a)           { this.amount = a; }
    public String getStatus()                  { return status; }
    public void setStatus(String s)            { this.status = s; }
    public String getCustomerName()            { return customerName; }
    public void setCustomerName(String n)      { this.customerName = n; }
    public String getCustomerEmail()           { return customerEmail; }
    public void setCustomerEmail(String e)     { this.customerEmail = e; }
    public String getAirtelMoneyId()           { return airtelMoneyId; }
    public void setAirtelMoneyId(String id)    { this.airtelMoneyId = id; }
    public String getPaymentLinkExpiry()       { return paymentLinkExpiry; }
    public void setPaymentLinkExpiry(String e) { this.paymentLinkExpiry = e; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(LocalDateTime dt) { this.updatedAt = dt; }
    public void setCreatedAt(LocalDateTime dt) { this.createdAt = dt; }
}
