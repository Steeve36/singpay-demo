package com.demo.singpay.model;

import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import jakarta.persistence.*;

@Entity
@Table(name = "payment_provider_config")
public class PaymentProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private PaymentProviderEnum provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(nullable = false)
    private boolean active;

    // Priorité : si plusieurs providers pour une méthode, le plus bas l'emporte
    @Column(nullable = false)
    private int priority;

    // Configuration non sensible au format JSON (noms d'opérateurs, IBANs affichés, etc.)
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }

    public PaymentProviderEnum getProvider() { return provider; }
    public void setProvider(PaymentProviderEnum provider) { this.provider = provider; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
}
