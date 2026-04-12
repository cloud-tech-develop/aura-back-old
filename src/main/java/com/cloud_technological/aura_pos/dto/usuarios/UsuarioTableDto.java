package com.cloud_technological.aura_pos.dto.usuarios;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioTableDto {
    private Integer id;
    private String username;
    private String rol;
    private String nombreCompleto;
    private String numeroDocumento;
    private String telefono;
    private Boolean activo;
    
    // Información del empleado vinculado
    private Long empleadoId;
    private Long tipoEmpleadoId;
    private String tipoEmpleadoNombre;
    
    private Long totalRows;
}
