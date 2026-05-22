package com.demo.singpay.model;

import jakarta.validation.constraints.*;

public class CreatePaymentRequest {

    @NotNull @Min(1)
    private Integer amount;

    @NotBlank
    private String reference;

    @NotBlank
    private String customerName;

    @NotBlank @Email
    private String customerEmail;

    public Integer getAmount()            { return amount; }
    public void setAmount(Integer a)      { this.amount = a; }
    public String getReference()          { return reference; }
    public void setReference(String r)    { this.reference = r; }
    public String getCustomerName()       { return customerName; }
    public void setCustomerName(String n) { this.customerName = n; }
    public String getCustomerEmail()      { return customerEmail; }
    public void setCustomerEmail(String e){ this.customerEmail = e; }
}
