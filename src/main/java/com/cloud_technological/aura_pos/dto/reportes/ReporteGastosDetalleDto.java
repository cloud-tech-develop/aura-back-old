package com.cloud_technological.aura_pos.dto.reportes;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** Un gasto del detalle, con todo lo que el contador necesita para revisarlo. */
@Getter
@Setter
public class ReporteGastosDetalleDto {

    private Long id;
    private LocalDate fecha;
    private String categoria;
    private String descripcion;

    private String terceroNombre;
    private String terceroDocumento;

    private String sucursalNombre;
    private String centroCostoNombre;
    private String cuentaCodigo;
    private String cuentaNombre;
    private String proyectoNombre;

    private BigDecimal monto;
    private Boolean deducible;

    private String formaPago;
    private String metodoPago;

    private String tipoDocSoporte;
    private String numeroDocSoporte;

    private BigDecimal baseIva;
    private BigDecimal valorIva;
    private BigDecimal valorRetefuente;
    private BigDecimal valorReteica;

    /** La plata salió del cajón otro día: no toca ningún arqueo vivo. */
    private Boolean salidaCajaOtroDia;
    /** Documento retroactivo que alguien autorizó a mano. */
    private String motivoRetroactivo;

    private String estado;
    private String usuarioNombre;

    private long totalRows;
}
