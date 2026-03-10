package com.cloud_technological.aura_pos.dto.usuarios;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class CreateUsuarioDto {
        // Datos de acceso
    @NotBlank
    @Size(max = 100)
    private String username;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @Size(max = 6)
    private String pinAccesoRapido;

    @NotBlank
    private String rol; // ADMIN, CAJERO, SUPERVISOR

    // Datos personales (crea tercero automáticamente)
    @NotBlank
    private String nombres;

    @NotBlank
    private String apellidos;

    private String tipoDocumento = "CC";

    @NotBlank
    private String numeroDocumento;

    private String telefono;
    private String email;

    // Sucursales asignadas: al menos la default
    @NotNull
    private List<SucursalAsignacion> sucursales;

    @Data
    public static class SucursalAsignacion {
        @NotNull
        private Integer sucursalId;
        private Boolean esDefault = false;
    }
}
