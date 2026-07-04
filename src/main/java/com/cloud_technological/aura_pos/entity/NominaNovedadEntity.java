package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "nomina_novedad")
public class NominaNovedadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nomina_id")
    private NominaEntity nomina;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;
    // HORA_EXTRA_DIURNA | HORA_EXTRA_NOCTURNA | HORA_EXTRA_DOMINICAL | HORA_EXTRA_FESTIVO
    // INCAPACIDAD | LICENCIA_REMUNERADA | BONO | COMISION
    // PRESTAMO | EMBARGO | OTRO_DEVENGO | OTRO_DESCUENTO

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @Column(name = "cantidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(name = "valor_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "es_deduccion", nullable = false)
    private Boolean esDeduccion = false;

    @Column(name = "naturaleza", length = 20, nullable = false)
    private String naturaleza = "DEVENGADO";
    // DEVENGADO | DEDUCCION | INFORMATIVO | PROVISION | APORTE_EMPLEADOR

    @Column(name = "origen", length = 20, nullable = false)
    private String origen = "MANUAL";
    // MANUAL | ASISTENCIA | IMPORTACION | AJUSTE_ADMIN | RELIQUIDACION | SISTEMA

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "APLICADA";
    // PENDIENTE | APROBADA | RECHAZADA | APLICADA | ANULADA

    @Column(name = "requiere_aprobacion", nullable = false)
    private Boolean requiereAprobacion = false;
}
