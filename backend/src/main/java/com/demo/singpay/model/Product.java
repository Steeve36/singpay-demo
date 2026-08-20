package com.demo.singpay.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_catalog", indexes = {
    @Index(name = "idx_product_slug", columnList = "slug", unique = true)
})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_xaf", nullable = false)
    private Integer priceXaf;

    @Column(nullable = false, length = 3)
    private String currency = "XAF";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long    getId()          { return id; }
    public String  getSlug()        { return slug; }
    public String  getName()        { return name; }
    public String  getDescription() { return description; }
    public Integer getPriceXaf()    { return priceXaf; }
    public String  getCurrency()    { return currency; }
    public boolean isActive()       { return active; }
}
