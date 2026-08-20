package com.demo.singpay.dto.auth;

import jakarta.validation.constraints.*;

public class RegisterRequest {

    @NotBlank
    @Size(min = 2, max = 80)
    private String firstName;

    @NotBlank
    @Size(min = 2, max = 80)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100, message = "Le mot de passe doit contenir au moins 8 caractères")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]{8,}$",
        message = "Le mot de passe doit contenir au moins une majuscule, une minuscule, un chiffre et un caractère spécial"
    )
    private String password;

    public String getFirstName()        { return firstName; }
    public void setFirstName(String n)  { this.firstName = n; }
    public String getLastName()         { return lastName; }
    public void setLastName(String n)   { this.lastName = n; }
    public String getEmail()            { return email; }
    public void setEmail(String e)      { this.email = e; }
    public String getPassword()         { return password; }
    public void setPassword(String p)   { this.password = p; }
}
