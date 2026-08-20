package com.demo.singpay.dto.auth;

public class AuthResponse {

    private String accessToken;
    private long   expiresIn;   // secondes avant expiration de l'access token
    private Long   userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;

    public AuthResponse() {}

    public AuthResponse(String accessToken, long expiresIn, Long userId,
                        String email, String firstName, String lastName, String role) {
        this.accessToken = accessToken;
        this.expiresIn   = expiresIn;
        this.userId      = userId;
        this.email       = email;
        this.firstName   = firstName;
        this.lastName    = lastName;
        this.role        = role;
    }

    public String getAccessToken()    { return accessToken; }
    public long   getExpiresIn()      { return expiresIn; }
    public Long   getUserId()         { return userId; }
    public String getEmail()          { return email; }
    public String getFirstName()      { return firstName; }
    public String getLastName()       { return lastName; }
    public String getRole()           { return role; }
}
