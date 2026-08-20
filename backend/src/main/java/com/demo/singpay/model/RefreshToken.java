package com.demo.singpay.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_rt_token_hash", columnList = "token_hash", unique = true),
    @Index(name = "idx_rt_user_id",    columnList = "user_id")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // SHA-256 hex du token brut — le token brut n'est jamais stocké
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public Long getId()                      { return id; }
    public User getUser()                    { return user; }
    public void setUser(User u)              { this.user = u; }
    public String getTokenHash()             { return tokenHash; }
    public void setTokenHash(String h)       { this.tokenHash = h; }
    public LocalDateTime getExpiresAt()      { return expiresAt; }
    public void setExpiresAt(LocalDateTime t){ this.expiresAt = t; }
    public boolean isRevoked()               { return revoked; }
    public void setRevoked(boolean r)        { this.revoked = r; }
    public LocalDateTime getCreatedAt()      { return createdAt; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
