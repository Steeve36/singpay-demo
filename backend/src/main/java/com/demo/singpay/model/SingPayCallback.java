package com.demo.singpay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SingPayCallback {

    private String reference;
    private String status;
    private String result;
    private Integer amount;

    @JsonProperty("airtel_money_id")
    private String airtelMoneyId;

    @JsonProperty("client_msisdn")
    private String clientMsisdn;

    public String getReference()            { return reference; }
    public void setReference(String r)      { this.reference = r; }
    public String getStatus()               { return status; }
    public void setStatus(String s)         { this.status = s; }
    public String getResult()               { return result; }
    public void setResult(String r)         { this.result = r; }
    public Integer getAmount()              { return amount; }
    public void setAmount(Integer a)        { this.amount = a; }
    public String getAirtelMoneyId()        { return airtelMoneyId; }
    public void setAirtelMoneyId(String id) { this.airtelMoneyId = id; }
    public String getClientMsisdn()         { return clientMsisdn; }
    public void setClientMsisdn(String m)   { this.clientMsisdn = m; }
}
