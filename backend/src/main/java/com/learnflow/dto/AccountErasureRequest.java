package com.learnflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AccountErasureRequest {
    @NotBlank
    @Size(max = 200)
    private String password;

    @NotBlank
    @Size(max = 80)
    private String confirmation;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
}
