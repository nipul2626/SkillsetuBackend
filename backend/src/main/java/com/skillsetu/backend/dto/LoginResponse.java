package com.skillsetu.backend.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String email;
    private String role;
    private String message;
}