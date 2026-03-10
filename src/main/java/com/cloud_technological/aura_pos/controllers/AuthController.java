package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.auth.LoginRequestDto;
import com.cloud_technological.aura_pos.dto.auth.LoginResponseDto;
import com.cloud_technological.aura_pos.dto.auth.RegisterRequestDto;
import com.cloud_technological.aura_pos.services.AuthService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.BusinessException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto loginRequest) throws BusinessException {
        LoginResponseDto responseDto = authService.login(loginRequest);
        ApiResponse<LoginResponseDto> response = new ApiResponse<>(
            HttpStatus.OK.value(), 
            "Login exitoso", 
            false, 
            responseDto
        );
        
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    @PostMapping("/register")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> register(@Valid @RequestBody RegisterRequestDto dto) {
        
        boolean success = authService.register(dto);
        
        ApiResponse<Boolean> response = new ApiResponse<>(
            HttpStatus.CREATED.value(), 
            "Empresa y Usuario creados exitosamente", 
            false, 
            success
        );
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
