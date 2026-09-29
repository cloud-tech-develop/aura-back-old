package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * Embargo sobre el salario (V113).
 *
 * <p>Antes era una novedad plana de tipo {@code EMBARGO} con un valor. Sin
 * expediente, sin juzgado, sin prelación y sin saldo que se consuma.
 *
 * <h2>La prelación es lo difícil, no la tabla</h2>
 * Ver {@code CalculadoraEmbargos}. Resumen de los límites (CST art. 154-156):
 * <ul>
 *   <li>Regla general: el salario es inembargable hasta 1 SMMLV. Del excedente,
 *       solo la <b>quinta parte</b> (20%).</li>
 *   <li>Excepción — alimentos y cooperativas: hasta el <b>50% del salario
 *       total</b>, sin el piso del SMMLV.</li>
 *   <li>Los embargos por <b>alimentos tienen prelación</b> sobre todos.</li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "embargo")
public class EmbargoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoLaboralEntity contrato;

    @Column(name = "expediente", length = 60, nullable = false)
    private String expediente;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    /** Menor = primero. Alimentos siempre va en 1. */
    @Column(name = "prioridad", nullable = false)
    private Integer prioridad = 1;

    /** Al juzgado se le GIRA: por eso es tercero, no texto. */
    @Column(name = "juzgado_id")
    private Long juzgadoId;

    @Column(name = "demandante_id")
    private Long demandanteId;

    /** O monto total (se descuenta hasta agotarlo) o porcentaje (indefinido). */
    @Column(name = "valor_total", precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "porcentaje", precision = 5, scale = 2)
    private BigDecimal porcentaje;

    /** Lo que falta por descontar. Se consume mes a mes. */
    @Column(name = "saldo", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = Estado.ACTIVO;

    @Column(name = "observacion", length = 500)
    private String observacion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (saldo == null || saldo.signum() == 0) {
            saldo = valorTotal != null ? valorTotal : BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static final class Tipo {
        /** Prelación absoluta. Hasta el 50% del salario total. */
        public static final String ALIMENTOS = "ALIMENTOS";
        /** También hasta el 50%. */
        public static final String COOPERATIVA = "COOPERATIVA";
        /** Regla general: quinta parte del excedente sobre 1 SMMLV. */
        public static final String JUDICIAL_ORDINARIO = "JUDICIAL_ORDINARIO";
        public static final String FISCAL = "FISCAL";
        private Tipo() {}
    }

    public static final class Estado {
        public static final String ACTIVO     = "ACTIVO";
        public static final String SUSPENDIDO = "SUSPENDIDO";
        public static final String TERMINADO  = "TERMINADO";
        private Estado() {}
    }

    /** ¿Puede llegar al 50% del salario, sin el piso del SMMLV? */
    @Transient
    public boolean tieneCupoAmpliado() {
        return Tipo.ALIMENTOS.equals(tipo) || Tipo.COOPERATIVA.equals(tipo);
    }

    @Transient
    public boolean estaVigenteEn(LocalDate fecha) {
        if (!Estado.ACTIVO.equals(estado)) return false;
        if (fecha.isBefore(fechaInicio)) return false;
        return fechaFin == null || !fecha.isAfter(fechaFin);
    }
}
