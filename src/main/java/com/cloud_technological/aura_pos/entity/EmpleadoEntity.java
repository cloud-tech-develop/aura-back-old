package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "empleados")
public class EmpleadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_empleado_id")
    private TipoEmpleadoEntity tipoEmpleado;

    @Column(name = "nombres", length = 100, nullable = false)
    private String nombres;

    @Column(name = "apellidos", length = 100, nullable = false)
    private String apellidos;

    @Column(name = "tipo_documento", length = 20, nullable = false)
    private String tipoDocumento; // CC, CE, PASAPORTE, NIT

    @Column(name = "numero_documento", length = 30, nullable = false)
    private String numeroDocumento;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    @Column(name = "salario_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal salarioBase;

    @Column(name = "tipo_contrato", length = 30, nullable = false)
    private String tipoContrato; // INDEFINIDO, FIJO, OBRA_LABOR, PRESTACION_SERVICIOS

    @Column(name = "banco", length = 100)
    private String banco;

    @Column(name = "numero_cuenta", length = 50)
    private String numeroCuenta;

    @Column(name = "tipo_cuenta", length = 20)
    private String tipoCuenta; // AHORROS, CORRIENTE

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @OneToOne(mappedBy = "empleado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private EmpleadoArlEntity arl;

    @OneToMany(mappedBy = "empleado", fetch = FetchType.LAZY)
    private List<NominaEntity> nominas = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
