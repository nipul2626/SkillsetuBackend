package com.skillsetu.backend.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private String email;
    private String role;

    private Long studentId;   // for STUDENT
    private Long collegeId;   // ✅ REQUIRED for TPO & STUDENT

    private String fullName;
    private String message;
}
