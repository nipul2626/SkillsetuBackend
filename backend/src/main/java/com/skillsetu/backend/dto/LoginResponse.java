package com.skillsetu.backend.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String email;
    private String role;
    private Long studentId;  // CRITICAL: Android needs this
    private String fullName;
    private String message;
}