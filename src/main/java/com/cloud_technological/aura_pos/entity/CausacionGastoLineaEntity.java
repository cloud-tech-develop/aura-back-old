package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

/** Línea de una causación de gasto (V201): una cuenta, su base, IVA y retenciones. */
@Entity
@Table(name = "causacion_gasto_linea")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CausacionGastoLineaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "causacion_id", nullable = false)
    private Long causacionId;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(length = 300)
    private String descripcion;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal base;

    @Column(name = "tarifa_iva", nullable = false, precision = 7, scale = 4)
    private BigDecimal tarifaIva;

    @Column(name = "valor_iva", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorIva;

    /** false: el IVA no se descuenta y va al gasto (mayor valor). */
    @Column(name = "iva_descontable", nullable = false)
    private Boolean ivaDescontable;

    @Column(name = "tarifa_retencion_id")
    private Long tarifaRetencionId;

    @Column(name = "tarifa_retefuente", nullable = false, precision = 7, scale = 4)
    private BigDecimal tarifaRetefuente;

    @Column(name = "valor_retefuente", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorRetefuente;

    @Column(name = "tarifa_reteiva", nullable = false, precision = 7, scale = 4)
    private BigDecimal tarifaReteiva;

    @Column(name = "valor_reteiva", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorReteiva;

    /** ReteICA en porcentaje (9,66 por mil = 0,966). */
    @Column(name = "tarifa_reteica", nullable = false, precision = 7, scale = 4)
    private BigDecimal tarifaReteica;

    @Column(name = "valor_reteica", nullable = false, precision = 18, scale = 2)
    private BigDecimal valorReteica;
}
