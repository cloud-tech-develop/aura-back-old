package com.cloud_technological.aura_pos.dto.factus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** Estado persistido de una nota crédito/débito, para listar y ver en el front. */
@Getter
@Setter
public class NotaElectronicaEstadoDto {
    private Long id;
    private String tipo;            // CREDITO | DEBITO
    private String referenceCode;
    private Long billId;
    private String numero;
    private String cude;
    private String estado;
    private Integer customizationId;
    private Integer correctionConceptCode;
    private BigDecimal total;
    private Boolean tieneXml;
    private LocalDateTime createdAt;
}
