// RegisterRequest.java
package com.mentorplatform.mentor_platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Pattern(regexp = "MENTOR|STUDENT", flags = Pattern.Flag.CASE_INSENSITIVE)
        String role
) {}