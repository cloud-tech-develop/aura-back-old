package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AsientoContableTableDto {
    private Long id;
    private String numeroComprobante;
    private String fecha;
    private String descripcion;
    private String tipoOrigen;
    private Long origenId;
    private BigDecimal totalDebito;
    private BigDecimal totalCredito;
    private String estado;
    private String createdAt;
    private Long totalRows;
    // Cabecera de comprobante manual (null en asientos automáticos)
    private String tipoComprobante;
    private Long beneficiarioTerceroId;
    private String beneficiarioNombre;
    private String beneficiarioDireccion;
    private String beneficiarioTelefono;
    private String ciudad;
    private String fechaVencimiento;
    private List<AsientoDetalleDto> detalles;
}
