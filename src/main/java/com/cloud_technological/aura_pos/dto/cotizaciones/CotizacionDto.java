package com.cloud_technological.aura_pos.dto.cotizaciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CotizacionDto {
    private Long id;
    private Integer empresaId;
    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;
    private Long turnoCajaId;
    private String numero;
    private LocalDate fecha;
    private LocalDate fechaVencimiento;
    private BigDecimal subtotal;
    private BigDecimal iva;
    private BigDecimal descuento;
    private BigDecimal total;
    private String observaciones;
    private String estado;
    private Integer diasVigencia;
    private List<CotizacionDetalleDto> detalles;
}
