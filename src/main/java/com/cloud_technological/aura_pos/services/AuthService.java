package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.auth.LoginRequestDto;
import com.cloud_technological.aura_pos.dto.auth.LoginResponseDto;
import com.cloud_technological.aura_pos.dto.auth.RegisterRequestDto;

public interface AuthService {
    LoginResponseDto login(LoginRequestDto loginDto);
    boolean register(RegisterRequestDto registerDto);
}
