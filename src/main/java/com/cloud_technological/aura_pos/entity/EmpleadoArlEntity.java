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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "empleado_arl")
public class EmpleadoArlEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private EmpleadoEntity empleado;

    @Column(name = "nivel_riesgo", nullable = false)
    private Integer nivelRiesgo = 1; // 1 al 5

    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 3)
    private BigDecimal porcentaje = new BigDecimal("0.522");
}
