package com.demo.singpay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionStatus {

    private String status;
    private String result;
    private String reference;
    private String amount;

    @JsonProperty("client_msisdn")
    private String clientMsisdn;

    @JsonProperty("airtel_money_id")
    private String airtelMoneyId;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("created_at")
    private String createdAt;

    public String getStatus()            { return status; }
    public void setStatus(String s)      { this.status = s; }
    public String getResult()            { return result; }
    public void setResult(String r)      { this.result = r; }
    public String getReference()         { return reference; }
    public void setReference(String r)   { this.reference = r; }
    public String getAmount()            { return amount; }
    public void setAmount(String a)      { this.amount = a; }
    public String getClientMsisdn()      { return clientMsisdn; }
    public void setClientMsisdn(String m){ this.clientMsisdn = m; }
    public String getAirtelMoneyId()     { return airtelMoneyId; }
    public void setAirtelMoneyId(String id) { this.airtelMoneyId = id; }
    public String getUpdatedAt()         { return updatedAt; }
    public void setUpdatedAt(String d)   { this.updatedAt = d; }
    public String getCreatedAt()         { return createdAt; }
    public void setCreatedAt(String d)   { this.createdAt = d; }
}
