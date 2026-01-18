package com.skillsetu.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class RegisterRequest {

    @NotBlank
    private String fullName;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$")
    private String phoneNumber;

    @NotBlank
    private String collegeName;

    @NotBlank
    private String branch;

    private Integer yearOfStudy;
}
