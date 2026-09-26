package com.cloud_technological.aura_pos.dto.cartera.ficha;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FichaClienteDto {
    private Long terceroId;
    private String nombre;
    private String tipoDocumento;
    private String numeroDocumento;
    private String telefono;
    private String email;
    private String direccion;
    private String municipio;
    private Long creditoId;
    private BigDecimal cupoCredito;
    private Integer plazoDias;
    private String estadoCredito;
    private String nivelRiesgo;
    private Integer scoreCrediticio;
    private Integer diasMoraTolerancia;
    private Boolean requiereAutorizacion;
}
