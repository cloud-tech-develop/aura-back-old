package com.cloud_technological.aura_pos.dto.documento_soporte;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class DocumentoSoporteDto {
    private Long id;
    private String origenTipo;
    private Long origenId;
    private Long terceroId;
    private String terceroNombre;
    private String referenceCode;
    private String numero;
    private String cude;
    private String estado;
    private BigDecimal total;
    private BigDecimal retenciones;
    private String mensajeError;
    private LocalDateTime createdAt;
}
