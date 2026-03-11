package com.cloud_technological.aura_pos.dto.auth;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordDto {

    @NotBlank
    @Email
    private String email;
}
