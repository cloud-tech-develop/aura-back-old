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
    private String username;
    private String nombreCompleto;
    private boolean facturaElectronica;
    private String rol;
    private String logo_url;
    private List<SucursalSimpleDto> sucursales;
}
