package com.demo.singpay.dto;

import jakarta.validation.constraints.*;

public class CreateIntentRequest {

    @NotNull(message = "Le montant est obligatoire")
    @Min(value = 1, message = "Le montant doit être supérieur à 0")
    private Integer amount;

    @NotBlank(message = "La devise est obligatoire")
    @Size(min = 3, max = 3)
    @Pattern(regexp = "^(XAF|EUR|USD)$", message = "Devise non autorisée")
    private String currency;

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
    @Email
    @Size(max = 150)
    private String customerEmail;

    public Integer getAmount()          { return amount; }
    public void setAmount(Integer a)    { this.amount = a; }

    public String getCurrency()            { return currency; }
    public void setCurrency(String c)      { this.currency = c; }

    public String getOrderReference()          { return orderReference; }
    public void setOrderReference(String r)    { this.orderReference = r; }

    public String getIdempotencyKey()          { return idempotencyKey; }
    public void setIdempotencyKey(String k)    { this.idempotencyKey = k; }

    public String getCustomerName()         { return customerName; }
    public void setCustomerName(String n)   { this.customerName = n; }

    public String getCustomerEmail()        { return customerEmail; }
    public void setCustomerEmail(String e)  { this.customerEmail = e; }

    public String getProductSlug()          { return productSlug; }
    public void setProductSlug(String s)    { this.productSlug = s; }
}
