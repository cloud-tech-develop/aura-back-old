package com.cloud_technological.aura_pos.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.auth.SesionAuraDto;
import com.cloud_technological.aura_pos.services.SesionAuraService;
import com.cloud_technological.aura_pos.utils.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SesionAuraController {
    private final SesionAuraService sesiones;

    @GetMapping("/sesion")
    public ApiResponse<SesionAuraDto> sesion() {
        return new ApiResponse<>(200, "Sesión vigente", false, sesiones.actual());
    }
}
