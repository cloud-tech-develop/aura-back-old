package com.cloud_technological.aura_pos.dto.traslado_fondos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrasladoFondosDto {

    private Long id;
    private Integer sucursalId;
    private LocalDate fecha;
    private BigDecimal monto;

    private String origenTipo;
    private Long origenTurnoCajaId;
    private Long origenCuentaBancoId;
    private Long origenCuentaId;
    /** Nombre legible del origen: "Caja Principal", "Bancolombia", "Caja Menor". */
    private String origenNombre;

    private String destinoTipo;
    private Long destinoTurnoCajaId;
    private Long destinoCuentaBancoId;
    private Long destinoCuentaId;
    private String destinoNombre;

    private String concepto;
    private String observacion;
    private Integer responsableId;
    private String responsableNombre;
    private Integer usuarioId;
    private String estado;
    private LocalDateTime createdAt;
}
