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
import jakarta.persistence.Transient;
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

    /**
     * Identidad de la persona (V99/V100).
     *
     * <p>{@code empleados} pasa a ser el vínculo persona-empresa; los datos de
     * identificación, fiscales y bancarios se leen de {@link TerceroEntity}.
     * Evita tener a la misma persona duplicada cuando además es proveedor.
     *
     * <p><b>Nullable hasta que V100 corra.</b> V100 lo pone NOT NULL y exige
     * que la reconciliación esté completa — ver PASOS MANUALES de V99.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tercero_id")
    private TerceroEntity tercero;

    /** @deprecated Leer de {@code tercero.nombre1}/{@code nombre2}. */
    @Deprecated
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

    @Column(name = "fecha_fin_contrato")
    private LocalDate fechaFinContrato; // aplica a contratos a término FIJO

    @Column(name = "salario_base", nullable = false, precision = 15, scale = 2)
    private BigDecimal salarioBase;

    /** F3 — días de vacaciones traídos del sistema anterior (migración). */
    @Column(name = "vacaciones_saldo_inicial", nullable = false, precision = 6, scale = 2)
    private BigDecimal vacacionesSaldoInicial = BigDecimal.ZERO;

    /** F7 — cesantías acumuladas y no consignadas, traídas de otro sistema. */
    @Column(name = "cesantias_saldo_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal cesantiasSaldoInicial = BigDecimal.ZERO;

    /** F7 — ingresos del año antes de entrar a Aura (retefuente / certificado). */
    @Column(name = "ingresos_ytd", nullable = false, precision = 15, scale = 2)
    private BigDecimal ingresosYtd = BigDecimal.ZERO;

    /** F7 — retención en la fuente ya practicada en el año. */
    @Column(name = "retenciones_ytd", nullable = false, precision = 15, scale = 2)
    private BigDecimal retencionesYtd = BigDecimal.ZERO;

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

    @Column(name = "requiere_control_asistencia", nullable = false)
    private Boolean requiereControlAsistencia = false;

    @OneToOne(mappedBy = "empleado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private EmpleadoArlEntity arl;

    // ─── Lectura durante la migración a `tercero` (V99/V100) ────────────────
    //
    // Prefieren `tercero`; si aún no está reconciliado, caen a las columnas
    // legacy de `empleados`. Así los call sites no repiten el ternario y, el
    // día que V100 cierre y se borren las columnas viejas, solo cambia esto.
    //
    // Nota: `tercero` es LAZY. Estos getters se llaman desde toDto() sin
    // transacción, lo cual funciona porque `spring.jpa.open-in-view` está en
    // su default (true). Es el mismo patrón que NominaEntity.empleado, que ya
    // es LAZY y se lee igual. Si algún día se apaga OSIV, hay que revisarlo.

    /** Nombre completo. Prefiere los componentes desagregados de `tercero`. */
    @Transient
    public String getNombreCompletoResuelto() {
        if (tercero != null && tercero.getNombre1() != null) {
            return java.util.stream.Stream.of(
                        tercero.getNombre1(), tercero.getNombre2(),
                        tercero.getApellido1(), tercero.getApellido2())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(java.util.stream.Collectors.joining(" "));
        }
        return String.join(" ",
                nombres == null ? "" : nombres,
                apellidos == null ? "" : apellidos).trim();
    }

    @Transient
    public String getNumeroDocumentoResuelto() {
        return (tercero != null && tercero.getNumeroDocumento() != null)
                ? tercero.getNumeroDocumento()
                : numeroDocumento;
    }

    @Transient
    public String getBancoResuelto() {
        if (tercero != null) {
            if (tercero.getBanco() != null) return tercero.getBanco();
        }
        return banco;
    }

    @Transient
    public String getNumeroCuentaResuelto() {
        return (tercero != null && tercero.getNumeroCuenta() != null)
                ? tercero.getNumeroCuenta()
                : numeroCuenta;
    }

    @Transient
    public String getTipoCuentaResuelto() {
        return (tercero != null && tercero.getTipoCuenta() != null)
                ? tercero.getTipoCuenta()
                : tipoCuenta;
    }

    @OneToMany(mappedBy = "empleado", fetch = FetchType.LAZY)
    private List<NominaEntity> nominas = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
