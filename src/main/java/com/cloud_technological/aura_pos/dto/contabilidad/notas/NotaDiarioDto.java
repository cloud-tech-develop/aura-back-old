package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Nota completa (cabecera + líneas) para el formulario y la consulta. */
@Getter @Setter
public class NotaDiarioDto {
    private Long id;
    private String numeroComprobante;
    private String fecha;
    private String descripcion;
    private BigDecimal totalDebito;
    private BigDecimal totalCredito;
    private String estado;
    private String periodo;
    private String elaboradoPor;
    private String createdAt;
    private String updatedAt;
    private String contabilizadoPor;
    private String contabilizadoAt;
    private String anuladoPor;
    private String anuladoAt;
    private String motivoAnulacion;
    private String clasificacion;
    private Boolean reversionAutomatica;
    private Long reversaDeId;
    private String reversaDeNumero;
    private Long revertidoPorId;
    private String revertidoPorNumero;
    private Long plantillaId;
    private String plantillaNombre;
    private List<NotaDiarioLineaDto> lineas;
    private List<NotaDiarioSoporteDto> soportes;
}
