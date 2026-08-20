package com.demo.singpay.dto;

import com.demo.singpay.model.enums.PaymentMethod;
import jakarta.validation.constraints.*;

public class PaymentInitRequest {

    @NotNull(message = "La méthode de paiement est obligatoire")
    private PaymentMethod method;

    @NotNull(message = "Le montant est obligatoire")
    @Min(value = 1, message = "Le montant doit être supérieur à 0")
    private Integer amount;

    @NotBlank(message = "La devise est obligatoire")
    @Size(min = 3, max = 3, message = "La devise doit faire 3 caractères (ex: XAF)")
    @Pattern(regexp = "^(XAF|EUR|USD)$", message = "Devise non autorisée")
    private String currency;

    // Slug du produit — requis pour la validation du prix côté serveur
    @NotBlank(message = "L'identifiant produit est obligatoire")
    @Size(max = 100)
    private String productSlug;

    @NotBlank(message = "La référence commande est obligatoire")
    @Size(max = 64)
    private String orderReference;

    @NotBlank(message = "La clé d'idempotence est obligatoire")
    @Size(max = 64)
    private String idempotencyKey;

    @NotBlank(message = "Le nom du client est obligatoire")
    @Size(max = 100)
    private String customerName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format email invalide")
    @Size(max = 150)
    private String customerEmail;

    // MOBILE_MONEY — USSD Push
    private String operateur;
    private String phone;

    // MOBILE_MONEY — Lien externe ou CB — URLs de retour
    private String redirectSuccess;
    private String redirectError;

    // "USSD" | "EXT_LINK" pour Mobile Money
    private String subtype;

    // Interne — rempli par PaymentOrchestrator après sauvegarde DB, jamais depuis le body HTTP
    private Long internalTxnId;

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getOrderReference() { return orderReference; }
    public void setOrderReference(String orderReference) { this.orderReference = orderReference; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getOperateur() { return operateur; }
    public void setOperateur(String operateur) { this.operateur = operateur; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRedirectSuccess() { return redirectSuccess; }
    public void setRedirectSuccess(String redirectSuccess) { this.redirectSuccess = redirectSuccess; }

    public String getRedirectError() { return redirectError; }
    public void setRedirectError(String redirectError) { this.redirectError = redirectError; }

    public String getSubtype() { return subtype; }
    public void setSubtype(String subtype) { this.subtype = subtype; }

    public String getProductSlug() { return productSlug; }
    public void setProductSlug(String s) { this.productSlug = s; }

    public Long getInternalTxnId() { return internalTxnId; }
    public void setInternalTxnId(Long internalTxnId) { this.internalTxnId = internalTxnId; }
}
