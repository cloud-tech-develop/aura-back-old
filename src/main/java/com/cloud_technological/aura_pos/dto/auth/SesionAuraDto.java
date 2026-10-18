package com.cloud_technological.aura_pos.dto.auth;

/** Identidad vigente para servicios de Aura; nunca incluye credenciales. */
public record SesionAuraDto(Long usuarioId, String username, String nombreCompleto,
        Integer empresaId, String empresaNombre, Long sucursalId, String rol) {
}
