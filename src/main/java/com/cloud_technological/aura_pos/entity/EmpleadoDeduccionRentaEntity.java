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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Deducciones y rentas exentas que depuran la base de retefuente (V108).
 *
 * <p>Cada tipo tiene su tope legal — ver {@code CalculadoraRetefuente}. Los
 * topes NO se guardan aquí: son de ley y se aplican en el cálculo, en UVT.
 */
@Getter
@Setter
@Entity
@Table(name = "empleado_deduccion_renta")
public class EmpleadoDeduccionRentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id", nullable = false)
    private ContratoLaboralEntity contrato;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** Referencia al documento soporte. La DIAN puede pedirlo. */
    @Column(name = "soporte", length = 300)
    private String soporte;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Tipos válidos. Deben coincidir con el CHECK de la tabla. */
    public static final class Tipo {
        /** 10% del ingreso, tope 32 UVT/mes. */
        public static final String DEPENDIENTES = "DEPENDIENTES";
        /** Tope 100 UVT/mes. */
        public static final String INTERESES_VIVIENDA = "INTERESES_VIVIENDA";
        /** Tope 16 UVT/mes. */
        public static final String MEDICINA_PREPAGADA = "MEDICINA_PREPAGADA";
        /** Renta exenta. Cuenta AFC. */
        public static final String AFC = "AFC";
        /** Renta exenta. Aporte voluntario a pensión. */
        public static final String AFP_VOLUNTARIO = "AFP_VOLUNTARIO";
        private Tipo() {}
    }

    /** ¿Es renta exenta (vs. deducción)? Cambia dónde entra en la depuración. */
    public boolean esRentaExenta() {
        return Tipo.AFC.equals(tipo) || Tipo.AFP_VOLUNTARIO.equals(tipo);
    }
}
