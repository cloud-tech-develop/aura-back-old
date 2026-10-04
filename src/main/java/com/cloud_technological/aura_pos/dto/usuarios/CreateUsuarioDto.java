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
    /**
     * La persona: un tercero que ya existe en la empresa (obligatorio). Nombre,
     * documento y correo salen de él; así el usuario queda relacionado con el
     * empleado, vendedor o cliente que ya es.
     */
    @NotNull(message = "Elija el tercero del usuario")
    private Long terceroId;

    // Datos de acceso. Sin username, se usa el correo del tercero.
    @Size(max = 100)
    private String username;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @Size(max = 6)
    private String pinAccesoRapido;

    @NotBlank
    private String rol; // ADMIN, CAJERO, SUPERVISOR
    /** Perfil de permisos; null = el perfil de sistema de su rol. */
    private Long perfilId;

    // Ya no se usan: los datos personales salen del tercero (terceroId).
    private String nombres;
    private String apellidos;
    private String tipoDocumento = "CC";
    private String numeroDocumento;
    private String telefono;
    private String email;
    private Boolean granContribuyente = Boolean.FALSE;

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
