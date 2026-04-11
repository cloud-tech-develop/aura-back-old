package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "depreciacion_periodo")
@Getter
@Setter
public class DepreciacionPeriodoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activo_id", nullable = false)
    private Long activoId;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "periodo_id", nullable = false)
    private Long periodoId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal valor;

    @Column(name = "asiento_id")
    private Long asientoId;

    @Column(name = "calculado_en", nullable = false)
    private LocalDateTime calculadoEn = LocalDateTime.now();
}
