package com.cloud_technological.aura_pos.dto.terceros;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TerceroDto {
    private Long id;
    private Integer empresaId;
    private String tipoDocumento;
    private String numeroDocumento;
    private String dv;
    private String razonSocial;
    private String nombres;
    private String apellidos;
    private String direccion;
    private String telefono;
    private String email;
    private String emailFe;
    private String responsabilidadFiscal;
    private Boolean esCliente;
    private Boolean esProveedor;
    private Boolean esEmpleado;
    private Long municipioId;
    private Boolean activo;
}
