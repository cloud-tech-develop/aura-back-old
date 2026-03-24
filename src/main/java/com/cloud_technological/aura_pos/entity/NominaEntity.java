package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "nomina")
public class NominaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "periodo_id")
    private PeriodoNominaEntity periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private EmpleadoEntity empleado;

    @Column(name = "salario_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal salarioBase;

    @Column(name = "dias_trabajados", nullable = false)
    private Integer diasTrabajados = 30;

    // Devengados
    @Column(name = "salario_proporcional", nullable = false, precision = 15, scale = 2)
    private BigDecimal salarioProporcional = BigDecimal.ZERO;

    @Column(name = "auxilio_transporte", nullable = false, precision = 15, scale = 2)
    private BigDecimal auxilioTransporte = BigDecimal.ZERO;

    @Column(name = "total_novedades_dev", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalNovedadesDev = BigDecimal.ZERO;

    @Column(name = "total_devengado", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDevengado = BigDecimal.ZERO;

    // Deducciones empleado
    @Column(name = "deduccion_salud", nullable = false, precision = 15, scale = 2)
    private BigDecimal deduccionSalud = BigDecimal.ZERO;

    @Column(name = "deduccion_pension", nullable = false, precision = 15, scale = 2)
    private BigDecimal deduccionPension = BigDecimal.ZERO;

    @Column(name = "deduccion_otros", nullable = false, precision = 15, scale = 2)
    private BigDecimal deduccionOtros = BigDecimal.ZERO;

    @Column(name = "total_deducciones", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeducciones = BigDecimal.ZERO;

    // Neto a pagar
    @Column(name = "neto_pagar", nullable = false, precision = 15, scale = 2)
    private BigDecimal netoPagar = BigDecimal.ZERO;

    // Aportes empleador (solo modo COMPLETO)
    @Column(name = "aporte_salud", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteSalud = BigDecimal.ZERO;

    @Column(name = "aporte_pension", nullable = false, precision = 15, scale = 2)
    private BigDecimal aportePension = BigDecimal.ZERO;

    @Column(name = "aporte_arl", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteArl = BigDecimal.ZERO;

    @Column(name = "aporte_caja", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteCaja = BigDecimal.ZERO;

    @Column(name = "aporte_icbf", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteIcbf = BigDecimal.ZERO;

    @Column(name = "aporte_sena", nullable = false, precision = 15, scale = 2)
    private BigDecimal aporteSena = BigDecimal.ZERO;

    // Provisiones (solo modo COMPLETO)
    @Column(name = "provision_prima", nullable = false, precision = 15, scale = 2)
    private BigDecimal provisionPrima = BigDecimal.ZERO;

    @Column(name = "provision_cesantias", nullable = false, precision = 15, scale = 2)
    private BigDecimal provisionCesantias = BigDecimal.ZERO;

    @Column(name = "provision_int_cesantias", nullable = false, precision = 15, scale = 2)
    private BigDecimal provisionIntCesantias = BigDecimal.ZERO;

    @Column(name = "provision_vacaciones", nullable = false, precision = 15, scale = 2)
    private BigDecimal provisionVacaciones = BigDecimal.ZERO;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = "BORRADOR"; // BORRADOR | APROBADO | PAGADO | ANULADO

    @OneToMany(mappedBy = "nomina", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NominaNovedadEntity> novedades = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
