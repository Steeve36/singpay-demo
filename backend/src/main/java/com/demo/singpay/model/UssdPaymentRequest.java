package com.demo.singpay.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class UssdPaymentRequest {

    @NotNull
    @Min(1)
    private Integer amount;

    @NotBlank
    private String reference;

    @NotBlank
    private String phone;

    @NotBlank
    @Pattern(
        regexp = "AIRTEL|MOOV|MAVIANCE",
        message = "Opérateur doit être AIRTEL, MOOV ou MAVIANCE"
    )
    private String operateur;

    @NotBlank
    private String customerName;

    @NotBlank
    private String customerEmail;

    public Integer getAmount()              { return amount; }
    public void setAmount(Integer a)        { this.amount = a; }
    public String getReference()            { return reference; }
    public void setReference(String r)      { this.reference = r; }
    public String getPhone()                { return phone; }
    public void setPhone(String p)          { this.phone = p; }
    public String getOperateur()            { return operateur; }
    public void setOperateur(String o)      { this.operateur = o; }
    public String getCustomerName()         { return customerName; }
    public void setCustomerName(String n)   { this.customerName = n; }
    public String getCustomerEmail()        { return customerEmail; }
    public void setCustomerEmail(String e)  { this.customerEmail = e; }
}
