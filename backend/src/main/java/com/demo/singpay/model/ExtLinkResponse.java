package com.demo.singpay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtLinkResponse {

    private String link;
    private String exp;

    public String getLink()        { return link; }
    public void setLink(String l)  { this.link = l; }
    public String getExp()         { return exp; }
    public void setExp(String e)   { this.exp = e; }
}
