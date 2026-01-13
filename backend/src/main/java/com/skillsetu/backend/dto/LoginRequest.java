package com.skillsetu.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.*; // Note: use jakarta for Spring Boot 3+

@Data
public class LoginRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;
}