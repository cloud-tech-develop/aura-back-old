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

@Entity
@Table(name = "nota_diario_plantilla_linea")
@Getter
@Setter
public class NotaDiarioPlantillaLineaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private NotaDiarioPlantillaEntity plantilla;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(length = 300)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debito = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal credito = BigDecimal.ZERO;

    @Column(name = "tercero_id")
    private Long terceroId;

    @Column(name = "centro_costo_id")
    private Long centroCostoId;

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "frente_id")
    private Long frenteId;
}
