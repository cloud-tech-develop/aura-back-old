package com.cloud_technological.aura_pos.dto.auth;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {
    private String token;
    private String tipoToken; // "Bearer"
    private Integer usuarioId;
    private Long empleadoId; // ID del empleado vinculado (nullable)
    private String username;
    private String nombreCompleto;
    private boolean facturaElectronica;
    private String rol;
    private String logo_url;
    private List<SucursalSimpleDto> sucursales;
    /** Líneas de uso de la empresa (POS, COMERCIAL, CONTABILIDAD, NOMINA); null para plataforma. */
    private List<String> lineas;
    /** Línea cuyo tablero ve al entrar; null para plataforma. */
    private String inicio;
}
