package com.cloud_technological.aura_pos.dto.terceros;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TerceroTableDto {
    private Long id;
    private String tipoDocumento;
    private String numeroDocumento;
    private String nombreCompleto; // razon_social o nombres + apellidos
    private String telefono;
    private String email;
    private Boolean esCliente;
    private Boolean esProveedor;
    private Boolean esEmpleado;
    private Boolean activo;
    private long totalRows;
}
