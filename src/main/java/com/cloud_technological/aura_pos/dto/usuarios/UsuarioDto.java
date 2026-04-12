package com.cloud_technological.aura_pos.dto.usuarios;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioDto {
    private Integer id;
    private String username;
    private String rol;
    private Boolean activo;
    private LocalDateTime createdAt;

    // ID del empleado vinculado (nullable)
    private Long empleadoId;

    // ID del cargo (tipo empleado) vinculado
    private Long tipoEmpleadoId;
    private String tipoEmpleadoNombre; // Nombre del cargo para mostrar

    // Datos personales del tercero
    private String nombres;
    private String apellidos;
    private String tipoDocumento;
    private String numeroDocumento;
    private String telefono;
    private String email;

    // Sucursales asignadas
    private List<UsuarioSucursalDto> sucursales;

    @Getter
    @Setter
    public static class UsuarioSucursalDto {
        private Integer sucursalId;
        private String sucursalNombre;
        private Boolean esDefault;
    }
}
