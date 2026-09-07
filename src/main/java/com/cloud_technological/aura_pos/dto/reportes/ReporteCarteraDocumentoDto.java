package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Un documento de cartera con sus abonos: el estado de cuenta propiamente. */
@Getter
@Setter
public class ReporteCarteraDocumentoDto {

    private Long id;
    private String numeroCuenta;
    /** Solo en CxP: el número de la factura del proveedor. */
    private String numeroFacturaExterno;

    private Long terceroId;
    private String terceroNombre;
    private String terceroDocumento;

    private LocalDateTime fechaEmision;
    private LocalDateTime fechaVencimiento;

    private BigDecimal totalDeuda;
    private BigDecimal totalAbonado;
    private BigDecimal saldoPendiente;

    /**
     * Días vencidos. Negativo si aún no vence — así una sola columna dice
     * "vence en 5 días" y "lleva 30 de mora" sin necesitar dos.
     */
    private Integer diasMora;
    /** CORRIENTE | 1-30 | 31-60 | 61-90 | +90 */
    private String edad;
    /** El estado calculado: PENDIENTE | VENCIDA | PAGADA. */
    private String estado;

    private List<ReporteCarteraAbonoDto> abonos = new ArrayList<>();

    private long totalRows;
}
