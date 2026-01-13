package com.skillsetu.backend.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class RegisterRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;

    @NotBlank
    private String fullName;

    @NotBlank
    private String role;

    private Long collegeId;
    private String branch;
    private Integer yearOfStudy;
}